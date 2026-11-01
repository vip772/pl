"""Compile extracted production installation owner and task/close gates; synthetic UI only."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/floatingshortcut/FloatingShortcutRuntime.kt').read_text()
def block(needle):
 a=s.index(needle);b=s.index('{',a);i=b+1;depth=1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
wrapper=s[s.index('object FloatingShortcutRuntime {'):s.index('    fun attach(activity: Activity)',s.index('object FloatingShortcutRuntime {'))]+'}'+chr(10)
code='''import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class Context
object Looper {val owner=Thread.currentThread();fun getMainLooper():Any=owner;fun myLooper():Any=Thread.currentThread()}
class Handler(looper:Any) {val tasks=java.util.concurrent.ConcurrentLinkedQueue<Runnable>();var reject=false;fun post(r:Runnable):Boolean {if(reject)return false;return tasks.add(r)};fun removeCallbacks(r:Runnable){tasks.remove(r)};fun removeCallbacksAndMessages(x:Any?){tasks.clear()};fun drain(){while(true)(tasks.poll()?:return).run()}}
class App {var calls=0;fun unregisterActivityLifecycleCallbacks(x:Any){calls++;error("unregister fails")}}
class Pref {var calls=0;fun unregisterOnSharedPreferenceChangeListener(x:Any){calls++}}
class Icons {var calls=0;fun close(){calls++}}
object HLog {fun e(s:String,t:Throwable){error("logger fails")}}
'''+wrapper+'''
class FloatingShortcutInstallation:AutoCloseable {
 @Volatile private var closed=false
 private val lifecycleLock=Any();private val pendingMain=linkedSetOf<MainTask>()
 val mainHandler=Handler(Looper.getMainLooper());val icons=Icons();val app=App();val pref=Pref()
 var installed=true;var enabled=true;var enabledItems=listOf(1);var chatPageVisible=true
 var lifecycleApplication=WeakReference(app);var prefs:Pref?=pref;val activityLifecycleCallbacks=Any();val preferenceListener=Any()
 var cleanups=0;var executions=0
 fun install(c:Context){}
 fun cleanupMain(){cleanups++}
 fun submit(){runOnMain{executions++}}
 fun pending()=synchronized(lifecycleLock){pendingMain.size}
'''+block('    private inner class MainTask(')+block('    private fun safely(')+block('    private fun runOnMain(')+block('    override fun close()')+block('    internal fun closeOwned()')+'''
}
fun main(){
 Looper.getMainLooper()
 repeat(200){
  val old=FloatingShortcutRuntime.acquire(Context());check(FloatingShortcutRuntime.acquire(Context())===old)
  val thread=Thread{old.submit();old.close()};thread.start();thread.join(5000);check(!thread.isAlive)
  check(old.pending()==0&&old.mainHandler.tasks.isEmpty());check(old.icons.calls==1&&old.pref.calls==1&&old.app.calls==1)
  val fresh=FloatingShortcutRuntime.acquire(Context());check(fresh!==old);old.close();check(FloatingShortcutRuntime.acquire(Context())===fresh)
  FloatingShortcutRuntime.drainRetired();check(old.cleanups==1&&fresh.cleanups==0);old.mainHandler.drain();check(old.executions==0)
  old.submit();check(old.pending()==0);fresh.close();check(fresh.cleanups==1)
 }
 val o=FloatingShortcutRuntime.acquire(Context());val t=Thread{repeat(100){o.submit()}};t.start();t.join(5000);check(o.pending()==64);o.close();check(o.pending()==0&&o.mainHandler.tasks.isEmpty())
 val q=FloatingShortcutRuntime.acquire(Context());q.mainHandler.reject=true;val rejected=Thread{q.submit()};rejected.start();rejected.join(5000);check(q.pending()==0);q.close()
 println("PASS 200 production acquire/retire/reinstall sequences; exact old-owner release; cleanup failure isolation; 64 task cap; rejected post rollback; no late execution")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=30)
