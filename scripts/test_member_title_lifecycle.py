"""Run production title cleanup and delayed-apply methods with host fixtures."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];base=r/'app/src/main/java/t/mma'
s=(base/'hooks/items/membertitle/MemberTitleRenderer.kt').read_text()
a=s.index('    fun destroy()');b=s.index('    private val msgSenderCache',a);destroy=s[a:b]
a=s.index('    private fun scheduleDelayedApply(');b=s.index('    private fun locateUsernameBindMethod',a);delayed=s[a:b]
code='''package t.mma.hooks.items.membertitle
import java.util.concurrent.ConcurrentHashMap
import t.mma.hooks.core.FeatureResourceScope
class TextView
class Tasks{val pending=linkedMapOf<String,()->Unit>();val cancelled=mutableListOf<String>();fun cancel(k:String){cancelled+=k;pending.remove(k)};fun runOnMainDelayed(k:String,d:Long,r:()->Unit){pending[k]=r};fun drain(){pending.values.toList().also{pending.clear()}.forEach{it()}}}
object WeChatApis{val task=Tasks();fun tasks()=task}
data class Target(val roomId:String,val sender:String)
internal class Renderer{
 val active=java.util.concurrent.atomic.AtomicBoolean(true);var errors=0;val logger:(String,Throwable?)->Unit={_,_->errors++}
 val resources=FeatureResourceScope{errors++};val pendingApplyKeys=ConcurrentHashMap.newKeySet<String>();val boundViews=linkedMapOf<TextView,Target>()
 val msgSenderCache=mutableMapOf<Any,String>();val holderTextFieldCache=mutableMapOf<Any,Any>();val likelyMsgCache=mutableMapOf<Any,Boolean>()
 var removed=0;var applied=0
 fun removeTitleOnly(v:TextView){removed++;if(removed==1)error("page failure")}
 fun applyTitle(v:TextView,r:String,s:String){applied++}
'''+destroy+delayed+'''
 fun queue(v:TextView)=scheduleDelayedApply(v,"room","sender")
}
fun main(){val renderer=Renderer();val a=TextView();val b=TextView();var unhooks=0
 renderer.resources.register{unhooks++};renderer.queue(a);renderer.queue(b)
 val stale=WeChatApis.task.pending.values.toList();check(WeChatApis.task.pending.size==2)
 renderer.destroy();renderer.destroy();check(unhooks==1&&renderer.removed==2&&renderer.errors==1&&renderer.boundViews.isEmpty()&&renderer.pendingApplyKeys.isEmpty()&&WeChatApis.task.pending.isEmpty())
 stale.forEach{it()};check(renderer.applied==0);renderer.resources.register{unhooks++};check(unhooks==2)
 println("PASS title lifecycle: delayed tasks cancelled, stale apply rejected, hook release including late registration, page cleanup exception isolation, duplicate destroy")}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 (p/'Android.kt').write_text('package android.os\nobject Looper{fun myLooper()=1;fun getMainLooper()=1}\nclass Handler(l:Int){fun post(r:()->Unit){r()}}')
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(p/'Android.kt'),str(base/'hooks/core/FeatureResourceScope.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.membertitle.TestKt'],check=True,timeout=15)
f=(base/'hooks/items/membertitle/MemberTitleFeature.kt').read_text()
assert 'installation.schedule { installed.install() }' in f
assert f.index('trackResource { installed.destroy()') < f.index('installation.schedule')
assert 'resources.register { HookRegistry.get().unhook(unhook) }' in s
assert 'DexInstallScheduler.queryDexKit { installLocked() }' in s