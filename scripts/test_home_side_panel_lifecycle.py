"""Run extracted production shutdown and callback gate with JVM host fixtures."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/homesidepanel/HomeSidePanelRuntime.kt').read_text()
fields=s[s.index('    private val active ='):s.index('    private val HOME_TITLE')]
destroy=s[s.index('    fun destroy() {'):s.index('    private fun isLauncher')]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
code='''import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
object HLog{fun e(s:String,t:Throwable){}}
object Looper{fun myLooper()=1;fun getMainLooper()=1}
class Handler{val tasks=mutableListOf<()->Unit>();fun post(r:()->Unit){synchronized(tasks){tasks+=r}};fun removeCallbacksAndMessages(v:Any?){synchronized(tasks){tasks.clear()}};fun drain(){val all=synchronized(tasks){tasks.toList().also{tasks.clear()}};all.forEach{it()}}}
class Prefs{var removed=0;fun unregisterOnSharedPreferenceChangeListener(l:Any){removed++}}
class Session(val fail:Boolean=false){var detached=0;fun detach(){detached++;if(fail)error("detach")}}
class Hook{var removed=0}
object HookRegistry{fun get()=this;fun unhook(h:Hook){h.removed++}}
class Runtime{
 val handler=Handler();val prefs=Prefs();val effectPrefs=Prefs();val listener=Any();val effectListener=Any()
 val sessions=linkedMapOf<Int,Session>();val scrollTitleSessions=mutableMapOf<Int,Session>();val hooks=mutableListOf<Hook>()
'''+fields+destroy+'''
 fun submit(r:Runnable)=profileLoader.execute(r)
 fun pending()=profileLoader.queue.size
 fun await()=profileLoader.awaitTermination(5,TimeUnit.SECONDS)
 fun callback(r:()->Unit)=postActive(r)
}
fun main(){
 val runtime=Runtime();val entered=CountDownLatch(1);val release=CountDownLatch(1);val done=CountDownLatch(1)
 val a=Session(true);val b=Session();val hook=Hook();runtime.sessions[1]=a;runtime.sessions[2]=b;runtime.scrollTitleSessions[1]=a;runtime.hooks+=hook
 runtime.submit(Runnable{entered.countDown();release.await(5,TimeUnit.SECONDS);check(!Thread.currentThread().isInterrupted);runtime.callback{error("late callback ran")};done.countDown()})
 check(entered.await(3,TimeUnit.SECONDS));runtime.submit(Runnable{error("queued avatar ran")});check(runtime.pending()==1)
 runtime.callback{error("old callback ran")};runtime.destroy();runtime.destroy()
 check(runtime.pending()==0&&a.detached==1&&b.detached==1&&hook.removed==1&&runtime.sessions.isEmpty()&&runtime.scrollTitleSessions.isEmpty())
 check(runtime.prefs.removed==1&&runtime.effectPrefs.removed==1)
 release.countDown();check(done.await(3,TimeUnit.SECONDS));check(runtime.await());runtime.handler.drain()
 try{runtime.submit(Runnable{});error("accepted after shutdown")}catch(_:RejectedExecutionException){}
 println("PASS home panel: queued avatar dropped, running work not interrupted, callback gate, exception-isolated page cleanup, hooks/listeners released once")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
assert 'if (!active.get() || !isLauncher(activity) || activity.isFinishing' in s
assert 'if (!active.get() || !attached) return' in s
avatar=s[s.index('        private fun loadAvatar()'):s.index('        private fun findActionBarContent')]
assert 'handler.post {' not in avatar
assert 'catch (error: java.util.concurrent.RejectedExecutionException)' in avatar