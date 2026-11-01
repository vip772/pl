"""Complete production GameEmojiRuntime with synthetic host; not device acceptance."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/gameemoji/GameEmojiFeature.kt').read_text()
assert 'DexInstallScheduler.schedule(' not in s
assert 'resources.register { owner.close() }' in s
assert 'resources.register { installation.close() }' in s
assert 'installation.schedule { owner.install() }' in s
assert 'private fun replay(pending: PendingSend, selected: GameResult?)' in s
assert 'fallback?.let { replay(it, null) }' in s
code="""import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
class Activity {var isFinishing=false;var isDestroyed=false}
object Looper {fun getMainLooper()=Any()}
class Handler(l:Any) {
 val tasks=mutableListOf<()->Unit>();var reject=false;var failClear=false
 fun post(f:()->Unit):Boolean{if(reject)return false;tasks.add(f);return true}
 fun removeCallbacksAndMessages(token:Any?){if(failClear)error("clear");tasks.clear()}
 fun drain(){while(tasks.isNotEmpty())tasks.removeAt(0)()}
}
open class XC_MethodHook {
 open fun beforeHookedMethod(p:MethodHookParam){}
 class MethodHookParam(var args:Array<Any?>,var thisObject:Any?){var result:Any?="original"}
 class Unhook(val callback:XC_MethodHook){var calls=0}
}
object HookRegistry {
 val hooks=mutableListOf<XC_MethodHook.Unhook>();var failHook=false;var failUnhook=false
 fun get()=this
 fun hook(m:Method,c:XC_MethodHook):XC_MethodHook.Unhook{if(failHook)error("hook");return XC_MethodHook.Unhook(c).also{hooks.add(it)}}
 fun unhook(h:XC_MethodHook.Unhook?){if(h==null)return;h.calls++;hooks.remove(h);if(failUnhook)error("unhook")}
}
class Host {
 fun send(to:String,e:com.tencent.mm.storage.emotion.EmojiInfo,x:Int,y:Int){sends++;probe?.invoke();probe=null;lastEmoji=e;lastTo=to}
 companion object{var probe:(()->Unit)?=null;var sends=0;var lastEmoji:com.tencent.mm.storage.emotion.EmojiInfo?=null;var lastTo:String?=null}
}
class Finder {var ready=true
 val emojiSendMethod:Method? get()=if(ready)Host::class.java.declaredMethods.single{it.name=="send"} else null}
class FeatureContext {val finder=Finder();fun dexFinder()=finder;fun hostContext()=Any()}
class ActivityApi {fun currentActivity():Activity=WeChatApis.activity}
object WeChatApis {var activity=Activity();fun currentActivity()=ActivityApi()}
object GameEmojiSettings {
 var pick=true;var fixed=false;var dice=1;var rps=1
 fun pickBeforeSendEnabled(c:Any)=pick
 fun fixedResultEnabled(c:Any)=fixed
 fun diceResult(c:Any)=dice
 fun rpsResult(c:Any)=rps
}
object KavaReflector {
 fun findFieldRecursive(c:Class<*>,n:String):Field?=runCatching{c.getDeclaredField(n).apply{isAccessible=true}}.getOrNull()
 fun readField(f:Field?,o:Any):Any?=f?.get(o)
 fun writeField(f:Field?,o:Any,v:Any?):Boolean{if(f==null)return false;f.set(o,v);return true}
 fun invokeOrThrow(m:Method,o:Any?,vararg args:Any?){m.invoke(o,*args)}
}
object VoiceForwardMiuixDialog {
 interface DialogHandle {fun close();fun isShowing():Boolean}
 class Handle(val selected:(Int)->Unit,val dismissed:()->Unit):DialogHandle{
  var showing=true;var fail=false
  override fun isShowing()=showing
  override fun close(){if(!showing)return;showing=false;dismissed();if(fail)error("close")}
  fun choose(i:Int){close();selected(i)}
 }
 var last:Handle?=null;var failShow=false;var empty=false;var shows=0
 fun showChoices(activity:Activity,title:String,summary:String,choices:List<Pair<String,String>>,onSelected:(Int)->Unit,onDismiss:()->Unit,dispatchAfterClose:Boolean):DialogHandle{
  check(!dispatchAfterClose)
  shows++
  if(failShow)error("show")
  return Handle(onSelected,onDismiss).also{last=it;if(empty)it.showing=false}
 }
}
"""
code+=s[s.index('private class GameEmojiRuntime('):].replace('private class GameEmojiRuntime(','internal class GameEmojiRuntime(',1)
code+="""
fun field(o:Any,n:String):Any?{
 var c:Class<*>?=o.javaClass
 while(c!=null){try{return c.getDeclaredField(n).apply{isAccessible=true}.get(o)}catch(e:NoSuchFieldException){c=c.superclass}}
 error("no field "+n)
}
fun emoji(name:String="dice_1.png"):com.tencent.mm.storage.emotion.EmojiInfo{
 val e=com.tencent.mm.storage.emotion.EmojiInfo();e.field_name=name;eo=e;return e
}
var eo:com.tencent.mm.storage.emotion.EmojiInfo?=null
fun fire(h:XC_MethodHook.Unhook,e:com.tencent.mm.storage.emotion.EmojiInfo=com.tencent.mm.storage.emotion.EmojiInfo()):XC_MethodHook.MethodHookParam{
 val p=XC_MethodHook.MethodHookParam(arrayOf("user",e,0,0),Host());h.callback.beforeHookedMethod(p);return p
}
fun pendingClear(p:Any){check(field(p,"method")==null&&field(p,"receiver")==null&&field(p,"args")==null&&field(p,"fields")==null)}
fun handlerOf(o:Any)=field(o,"mainHandler") as Handler
fun drainAll(r:Any){handlerOf(r).drain()}
fun seq(){
 val runtime=GameEmojiRuntime(FeatureContext()){_,_->error("logger")};check(runtime.install());check(runtime.install());check(HookRegistry.hooks.size==1)
 val h=HookRegistry.hooks.single();val handler=handlerOf(runtime)
 val before=Host.sends;check(fire(h).result==null);val pending=field(runtime,"pendingSend")!!;handler.drain()
 val dialog=VoiceForwardMiuixDialog.last!!;check(dialog.isShowing())
 HookRegistry.failUnhook=true;dialog.fail=true;runtime.close();HookRegistry.failUnhook=false
 check(HookRegistry.hooks.isEmpty()&&handler.tasks.isEmpty());pendingClear(pending)
 dialog.selected(0);handler.drain();check(Host.sends==before)
 check(fire(h).result=="original");check(!runtime.install());runtime.close()
 check((field(runtime,"emojiFields") as Map<*,*>).isEmpty()&&field(runtime,"dialog")==null&&field(runtime,"pendingSend")==null)
}
fun main(){
 repeat(200){seq()}
 // 两 owner 精确释放隔离
 run{
  val a=GameEmojiRuntime(FeatureContext()){_,_->};val b=GameEmojiRuntime(FeatureContext()){_,_->}
  check(a.install());check(b.install());val bh=HookRegistry.hooks.last();a.close();check(HookRegistry.hooks.single()===bh);b.close()
 }
 // Hook 安装失败可重试
 run{
  val fail=GameEmojiRuntime(FeatureContext()){_,_->};HookRegistry.failHook=true;check(!fail.install())
  HookRegistry.failHook=false;check(fail.install());fail.close()
 }
 // 弹窗创建抛异常：不发送、待选状态复位、payload 清空
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());val h=HookRegistry.hooks.single();val handler=handlerOf(rt);val before=Host.sends
  VoiceForwardMiuixDialog.failShow=true;fire(h);val pending=field(rt,"pendingSend")!!;handler.drain()
  check(Host.sends==before);pendingClear(pending);check(field(rt,"pendingSend")==null)
  check(!(field(rt,"dialogShowing") as AtomicBoolean).get()) { "dialogShowing must reset after show failure" }
  VoiceForwardMiuixDialog.failShow=false;rt.close();check(HookRegistry.hooks.isEmpty())
 }
 // EmptyHandle：不展示时立即透传原发送一次
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());val h=HookRegistry.hooks.single();val handler=handlerOf(rt);val before=Host.sends
  VoiceForwardMiuixDialog.empty=true;fire(h);handler.drain();check(Host.sends==before+1)
  VoiceForwardMiuixDialog.empty=false;check(field(rt,"pendingSend")==null);rt.close()
 }
 // Activity 已销毁：直接透传
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());val h=HookRegistry.hooks.single();val handler=handlerOf(rt);val before=Host.sends
  WeChatApis.activity.isDestroyed=true;check(fire(h).result=="original");handler.drain();check(Host.sends==before)
  WeChatApis.activity.isDestroyed=false
  fire(h);WeChatApis.activity.isFinishing=true;handler.drain();check(Host.sends==before+1)
  WeChatApis.activity.isFinishing=false;rt.close()
 }
 // Handler 拒绝投递：直接透传一次
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());val h=HookRegistry.hooks.single();val handler=handlerOf(rt);val before=Host.sends
  handler.reject=true;fire(h);check(Host.sends==before+1);check(field(rt,"pendingSend")==null)
  handler.reject=false;check(!(field(rt,"dialogShowing") as AtomicBoolean).get());rt.close()
 }
 // 已排队 UI 任务在关闭后不得开弹窗
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());fire(HookRegistry.hooks.single())
  val pending=field(rt,"pendingSend")!!;val handler=handlerOf(rt);val late=handler.tasks.single()
  rt.close();late();pendingClear(pending);check(handler.tasks.isEmpty())
 }
 // 弹窗后 close 期间到达的 onDismiss/onSelected 都不能再发送
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());val h=HookRegistry.hooks.single();val handler=handlerOf(rt);val before=Host.sends
  fire(h);handler.drain();val dialog=VoiceForwardMiuixDialog.last!!
  rt.close();dialog.selected(2);dialog.dismissed();handler.drain();check(Host.sends==before)
 }
 // 选择只发送一次；重复回调不能再发
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());val h=HookRegistry.hooks.single();val handler=handlerOf(rt);val before=Host.sends
  fire(h);handler.drain();val dialog=VoiceForwardMiuixDialog.last!!
  dialog.choose(0);dialog.selected(0);handler.drain();check(Host.sends==before+1)
  check(field(rt,"pendingSend")==null);check(!(field(rt,"dialogShowing") as AtomicBoolean).get());rt.close()
 }
 // 取消不发送，且取消后可以重新弹出
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());val h=HookRegistry.hooks.single();val handler=handlerOf(rt);val before=Host.sends
  fire(h);handler.drain();VoiceForwardMiuixDialog.last!!.close();handler.drain()
  check(Host.sends==before);check(field(rt,"pendingSend")==null)
  check(!(field(rt,"dialogShowing") as AtomicBoolean).get())
  fire(h);handler.drain();check(VoiceForwardMiuixDialog.last!!.isShowing())
  VoiceForwardMiuixDialog.last!!.choose(1);handler.drain();check(Host.sends==before+1);rt.close()
 }
 // 已有待选时第二次发送被抑制，不产生第二个弹窗/NPE
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());val h=HookRegistry.hooks.single();val handler=handlerOf(rt)
  fire(h);val first=field(rt,"pendingSend");val shows=VoiceForwardMiuixDialog.shows
  check(fire(h).result==null);check(field(rt,"pendingSend")===first);check(VoiceForwardMiuixDialog.shows==shows)
  handler.drain();rt.close()
 }
 // 重发期间重入 Hook 不得二次改写（replaying 门）
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());val h=HookRegistry.hooks.single()
  (field(rt,"replaying") as ThreadLocal<Boolean>).set(true);check(fire(h).result=="original")
  (field(rt,"replaying") as ThreadLocal<Boolean>).remove();rt.close()
 }
 // 宿主调用不持 runtime 监视器：重发中其它线程可以 close（锁顺序回归）
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());val h=HookRegistry.hooks.single();val handler=handlerOf(rt)
  fire(h);handler.drain();var closed=false
  Host.probe={val t=Thread{rt.close();closed=true};t.start();t.join(3000)}
  VoiceForwardMiuixDialog.last!!.choose(0)
  check(closed) { "close() blocked while host send in flight" };handler.drain();rt.close()
 }
 // 关闭时清理疼痛路径：clear/unhook/logger/弹窗关闭异常均隔离
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->throw IllegalStateException("logger")}
  check(rt.install());fire(HookRegistry.hooks.single());handlerOf(rt).drain()
  val dialog=VoiceForwardMiuixDialog.last!!;dialog.fail=true;handlerOf(rt).failClear=true
  HookRegistry.failUnhook=true;rt.close();HookRegistry.failUnhook=false;handlerOf(rt).failClear=false;dialog.fail=false
  check(HookRegistry.hooks.isEmpty());check(field(rt,"hook")==null);check(field(rt,"dialog")==null)
 }
 // 固定结果模式：骰子/猜拳协议字段不得变动
 GameEmojiSettings.pick=false;GameEmojiSettings.fixed=true
 for((v,md5,name) in listOf(Triple(1,"da1c289d4e363f3ce1ff36538903b92f","dice_1.png"),Triple(6,"5ba8e9694b853df10b9f2a77b312cc09","dice_6.png"))){
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());GameEmojiSettings.dice=v;val before=Host.sends
  val e=emoji("dice_3.png");fire(HookRegistry.hooks.single(),e)
  check(e.field_md5==md5&&e.field_name==name&&e.field_content=="<gameext type=\\"2\\" content=\\""+(v+3)+"\\" ></gameext>")
  check(e.field_catalog==50&&e.field_groupId=="50"&&e.field_svrid==""&&e.field_reserved3==0&&e.field_reserved4==0&&e.field_source==0&&e.field_size>0)
  check(field(rt,"pendingSend")==null&&Host.sends==before);rt.close()
 }
 for((v,md5,name) in listOf(Triple(1,"514914788fc461e7205bf0b6ba496c49","jsb_j.png"),Triple(3,"091577322c40c05aa3dd701da29d6423","jsb_b.png"))){
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install());GameEmojiSettings.rps=v;val before=Host.sends
  val e=emoji("jsb_s.png");fire(HookRegistry.hooks.single(),e)
  check(e.field_md5==md5&&e.field_name==name&&e.field_content=="<gameext type=\\"1\\" content=\\""+v+"\\" ></gameext>")
  rt.close()
 }
 // 非游戏表情透传
 run{
  val rt=GameEmojiRuntime(FeatureContext()){_,_->};check(rt.install())
  val before=Host.sends;val e=emoji("plain.png");check(fire(HookRegistry.hooks.single(),e).result=="original");check(Host.sends==before);rt.close()
 }
 // Dex 未就绪：install 返回 false，不装 Hook，不泄漏句柄
 run{
  val ctx=FeatureContext();ctx.finder.ready=false
  val rt=GameEmojiRuntime(ctx){_,_->}
  check(!rt.install());check(HookRegistry.hooks.isEmpty());check(field(rt,"hook")==null)
  ctx.finder.ready=true;check(rt.install());check(HookRegistry.hooks.size==1);rt.close()
  check(HookRegistry.hooks.isEmpty())
 }
 GameEmojiSettings.pick=true;GameEmojiSettings.fixed=false
 check(HookRegistry.hooks.isEmpty()) { "no hook may outlive its runtime" }
 println("PASS 200 owner sequences; exact hook ownership; show failure/EmptyHandle/destroyed Activity/post rejection fallback; queued UI and late dismiss/selection after close; single claim per dialog; replaying gate; lock not held across host send; unhook/logger/dialog-clear failure isolation; dice/RPS protocol fields unchanged; non-game passthrough")
}
"""
emoji="""package com.tencent.mm.storage.emotion
class EmojiInfo {
 @JvmField var field_name="dice_1.png";@JvmField var field_content="";@JvmField var field_md5=""
 @JvmField var field_size=0;@JvmField var field_svrid="";@JvmField var field_catalog=0
 @JvmField var field_reserved3=0;@JvmField var field_reserved4=0;@JvmField var field_groupId="";@JvmField var field_source=0
 @JvmField var field_designerID:String?=null;@JvmField var field_thumbUrl:String?=null
}
"""
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code);(p/'Emoji.kt').write_text(emoji)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(p/'Emoji.kt')],check=True,timeout=120)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=60)
