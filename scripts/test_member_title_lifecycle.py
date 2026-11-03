"""Run production title cleanup and delayed-apply methods with host fixtures."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];base=r/'app/src/main/java/t/mma'
s=(base/'hooks/items/membertitle/MemberTitleRenderer.kt').read_text()
a=s.index('    @Synchronized fun destroy()');b=s.index('    private val msgSenderCache',a);destroy=s[a:b]
a=s.index('    private fun scheduleDelayedApply(');b=s.index('    private fun locateUsernameBindMethod',a);delayed=s[a:b]
a=s.index('    private fun restoreVisibleBindings(');b=s.index('    fun refreshRoom',a);restore=s[a:b]
code='''package t.mma.hooks.items.membertitle
import java.util.concurrent.ConcurrentHashMap
import t.mma.hooks.core.FeatureResourceScope
class TextView(var isAttachedToWindow:Boolean=true,var isShown:Boolean=true)
class Tasks{var failCancel=false;var hold=false;val held=mutableListOf<()->Unit>();val pending=linkedMapOf<String,()->Unit>();val cancelled=mutableListOf<String>();fun cancel(k:String){cancelled+=k;pending.remove(k);if(failCancel)error("cancel")};fun runOnMain(r:()->Unit){if(hold)held+=r else r()};fun runOnMainDelayed(k:String,d:Long,r:()->Unit){pending[k]=r};fun drain(){pending.values.toList().also{pending.clear()}.forEach{it()}}}
object WeChatApis{val task=Tasks();fun tasks()=task}
data class Target(val roomId:String,val sender:String)
object Sequence {val value=java.util.concurrent.atomic.AtomicLong()}
object VisibleBindings{val values=java.util.Collections.synchronizedMap(java.util.WeakHashMap<TextView,Target>())}
class Store{fun isEnabled()=true}
internal class Renderer{
 val active=java.util.concurrent.atomic.AtomicBoolean(true);val generation=Sequence.value.incrementAndGet();val generationSequence=Sequence.value;var errors=0;val store=Store();var roomId="room@chatroom";val visibleBindings=VisibleBindings.values;var failLogger=false;val logger:(String,Throwable?)->Unit={_,_->errors++;if(failLogger)error("logger")}
 val resources=FeatureResourceScope{errors++};val pendingApplyKeys=ConcurrentHashMap.newKeySet<String>();val boundViews=linkedMapOf<TextView,Target>()
 val msgSenderCache=mutableMapOf<Any,String>();val holderTextFieldCache=mutableMapOf<Any,Any>();val likelyMsgCache=mutableMapOf<Any,Boolean>();val inSetText=ThreadLocal<Boolean>()
 class Gate{var calls=0;var fail=false;fun release(){calls++;if(fail)error("gate")}};var nativeNicknameGate:Gate?=null
 var removed=0;var applied=0
 fun currentRoomId()=roomId
 fun isRoom(id:String)=id.endsWith("@chatroom")
 fun removeTitleOnly(v:TextView){removed++;if(removed==1)error("page failure")}
 fun applyTitle(v:TextView,r:String,s:String){applied++}
'''+destroy+restore+delayed+'''
 fun queue(v:TextView)=scheduleDelayedApply(v,"room","sender")
 fun restoreVisible()=restoreVisibleBindings()
}
fun main(){val renderer=Renderer();val a=TextView();val b=TextView();var unhooks=0
 renderer.resources.register{unhooks++};renderer.queue(a);renderer.queue(b)
 val stale=WeChatApis.task.pending.values.toList();check(WeChatApis.task.pending.size==2)
 renderer.destroy();renderer.destroy();check(unhooks==1&&renderer.removed==2&&renderer.errors==1&&renderer.boundViews.isEmpty()&&renderer.pendingApplyKeys.isEmpty()&&WeChatApis.task.pending.isEmpty())
 stale.forEach{it()};check(renderer.applied==0);renderer.resources.register{unhooks++};check(unhooks==2)
 val failing=Renderer();failing.failLogger=true;val gate=Renderer.Gate();gate.fail=true;failing.nativeNicknameGate=gate
 failing.queue(TextView());failing.queue(TextView());failing.msgSenderCache[Any()]="retained";WeChatApis.task.failCancel=true
 failing.destroy();failing.destroy();check(gate.calls==1&&failing.nativeNicknameGate==null&&failing.removed==2&&failing.pendingApplyKeys.isEmpty()&&failing.boundViews.isEmpty()&&failing.msgSenderCache.isEmpty()&&WeChatApis.task.pending.isEmpty());WeChatApis.task.failCancel=false
 val restarted=Renderer();val visible=TextView();val hidden=TextView(isShown=false);val detached=TextView(isAttachedToWindow=false);val otherRoom=TextView()
 VisibleBindings.values[visible]=Target("room@chatroom","member1")
 VisibleBindings.values[hidden]=Target("room@chatroom","member2")
 VisibleBindings.values[detached]=Target("room@chatroom","member3")
 VisibleBindings.values[otherRoom]=Target("other@chatroom","member4")
 restarted.restoreVisible()
 check(restarted.applied==1&&VisibleBindings.values[detached]==null&&VisibleBindings.values[hidden]!=null&&VisibleBindings.values[otherRoom]!=null)
 restarted.roomId="other@chatroom";restarted.restoreVisible();check(restarted.applied==2)
 val previous=Renderer();val staleView=TextView();previous.boundViews[staleView]=Target("room","sender")
 WeChatApis.task.held.clear();WeChatApis.task.hold=true;android.os.Looper.onMain=false;previous.destroy();android.os.Looper.onMain=true
check(WeChatApis.task.held.size==1);val next=Renderer();WeChatApis.task.held.toList().forEach{it()};WeChatApis.task.held.clear();WeChatApis.task.hold=false
check(previous.removed==0&&next.active.get());next.destroy()
 println("PASS title lifecycle: delayed tasks cancelled, stale apply rejected, hook release including late registration, page cleanup exception isolation, duplicate destroy, visible row restoration scoped to current room")}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 (p/'Android.kt').write_text('package android.os\nobject Looper{var onMain=true;fun myLooper()=if(onMain)1 else 0;fun getMainLooper()=1}\nclass Handler(l:Int){fun post(r:()->Unit){t.mma.hooks.items.membertitle.WeChatApis.task.runOnMain(r)}}')
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(p/'Android.kt'),str(base/'hooks/core/FeatureResourceScope.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.membertitle.TestKt'],check=True,timeout=15)
f=(base/'hooks/items/membertitle/MemberTitleFeature.kt').read_text()
assert 'installation.schedule { installed.install() }' in f
assert f.index('resources.register {') < f.index('installation.schedule')
assert 'PreferenceRuntimeOwner(prefs, setOf(MemberTitleSettings.KEY_ENABLE)' in f
assert 'resources.isClosed() || !store.isEnabled()' in f
assert '@Synchronized fun destroy()' in destroy
assert 'resources.register { HookRegistry.get().unhook(unhook) }' in s
assert 'DexInstallScheduler.queryDexKit { installLocked() }' in s
assert 'visibleBindings.entries.map { it.key to it.value }' in restore
assert 'view.isShown && target.roomId == roomId' in restore
assert 'Collections.synchronizedMap(WeakHashMap<TextView, Target>())' in s