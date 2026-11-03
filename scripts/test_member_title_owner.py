"""Execute extracted production install/destroy with synthetic HookRegistry/gate.
Locator and UI rendering are synthetic; not Android/R8 or device evidence.
"""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/membertitle/MemberTitleRenderer.kt').read_text()
def method(needle):
 a=s.index(needle);b=s.index('{',a);i=b+1;depth=1
 while depth:depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
code='''package t.mma.hooks.items.membertitle
import java.lang.reflect.Member
import t.mma.hooks.core.FeatureResourceScope
class Param
open class Hook {open fun afterHookedMethod(p:Param) {}}
class Handle(val callback:Hook) {var releases=0;var fail=false;fun unhook(){releases++;if(fail)error("unhook")}}
class Registry {val installed=mutableListOf<Handle>();var reject=false;var before:(()->Unit)?=null
 fun hook(m:Member,h:Hook):Handle {before?.invoke();if(reject)error("hook rejected");return Handle(h).also{installed+=it}}
 fun unhook(h:Handle?){h?.unhook()}
 companion object{val instance=Registry();fun get()=instance}}
object HookRegistry {fun get()=Registry.get()}
class Gate {var releases=0;var fail=false;fun release(){releases++;if(fail)error("gate")}}
object NativeGroupNicknameGate {var acquireFail=false;var last:Gate?=null
 fun acquire(c:Any,m:Member,logger:(String,Throwable?)->Unit):Gate {if(acquireFail)error("acquire");return Gate().also{last=it}}}
class Tasks{var cancels=0;var fail=false;fun cancel(k:String){cancels++;if(fail)error("cancel")};fun runOnMain(f:()->Unit){f()}}
object WeChatApis{val tasksApi=Tasks();fun tasks()=tasksApi}
class TextView
class Target(val roomId:String,val sender:String)
internal class Renderer {
 val context=Any();val active=java.util.concurrent.atomic.AtomicBoolean(true);var usernameBindInstalled=false;val generationSequence=java.util.concurrent.atomic.AtomicLong();val generation=generationSequence.incrementAndGet()
 val resources=FeatureResourceScope { errors++ };var errors=0;var failLogger=false
 val logger:(String,Throwable?)->Unit={_,_->errors++;if(failLogger)error("logger")}
 var nativeNicknameGate:Gate?=null;var binds=0
 val pendingApplyKeys=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
 val boundViews=java.util.Collections.synchronizedMap(java.util.WeakHashMap<TextView,Target>())
 val msgSenderCache=mutableMapOf<Any,String>();val holderTextFieldCache=mutableMapOf<Any,Any>()
 val likelyMsgCache=mutableMapOf<Any,Boolean>();val inSetText=ThreadLocal<Boolean>()
 fun locateUsernameBindMethod()=Fixture::class.java.getDeclaredMethod("bind")
 fun handleUsernameBind(param:Param){if(active.get())binds++}
 fun removeTitleOnly(view:TextView){errors++;error("ui")}
''' + method('    @Synchronized fun destroy()') + method('    private fun installUsernameBindHook()') + '''
 fun install()=if (!active.get()) false else installUsernameBindHook()
}
object Fixture{@JvmStatic fun bind(){}}
fun main(){
 val registry=Registry.get();val rejected=Renderer();registry.reject=true
 check(!rejected.install());check(NativeGroupNicknameGate.last!!.releases==1&&rejected.nativeNicknameGate==null)
 registry.reject=false;check(rejected.install());val h=registry.installed.last();h.fail=true
 rejected.pendingApplyKeys.add("one");rejected.pendingApplyKeys.add("two")
 val v=TextView();rejected.boundViews[v]=Target("room","member");WeChatApis.tasksApi.fail=true
 rejected.failLogger=true;rejected.destroy();rejected.destroy()
 check(h.releases==1&&NativeGroupNicknameGate.last!!.releases==1&&WeChatApis.tasksApi.cancels==2)
 check(rejected.pendingApplyKeys.isEmpty()&&rejected.boundViews.isEmpty()&&!rejected.install())
 h.callback.afterHookedMethod(Param());check(rejected.binds==0)
 registry.before={registry.before=null;registry.reject=false;late.destroy()}
 println("PASS title rejected hook releases gate; retry, cleanup faults, stale callback and repeated destroy")
}
'''
code=code.replace(' registry.before={registry.before=null;registry.reject=false;late.destroy()}', """ val late=Renderer();registry.before={registry.before=null;late.destroy()}
 check(!late.install());check(registry.installed.last().releases==1&&NativeGroupNicknameGate.last!!.releases==1)
 check(late.nativeNicknameGate==null&&!late.install())
 val fresh=Renderer();check(fresh.install());fresh.destroy()""")
# Production uses Android Thread/Looper; behavior fixture does not exercise Looper actual implementation.
android='''package android.os
object Looper{fun myLooper()=1;fun getMainLooper()=1}
class Handler(l:Int){fun post(r:()->Unit)=r()}
'''
# The extracted method requires the Xposed type name: preserve its callback path via a thin alias.
code=code.replace('object HookRegistry {', 'object HookRegistry {')
code=code.replace('object Fixture{@JvmStatic fun bind(){}}','object Fixture{@JvmStatic fun bind(){}}')
code=code.replace('object HookRegistry {fun get()=Registry.get()}','object HookRegistry {fun get()=Registry.get()}')
# Replace just class references in extracted method with synthetic equivalents.
code=code.replace('object : XC_MethodHook()', 'object : Hook()').replace('MethodHookParam','Param')
with tempfile.TemporaryDirectory(prefix='pl-title-owner-') as td:
 p=Path(td);(p/'Test.kt').write_text(code);(p/'Android.kt').write_text(android)
 libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'out'),str(p/'Test.kt'),str(p/'Android.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'out')+':'+str(std),'t.mma.hooks.items.membertitle.TestKt'],check=True,timeout=15)
