"""Exercise production external-target idle cleanup with deterministic timer and real Dex gate."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/script/agent/ScriptPluginAgentLocalReverseTools.kt').read_text()
a=s.index('    private var externalIdleTicket:');b=s.index('    @JvmStatic',a)
idle=s[a:b]
assert 'finally {\n                        armExternalIdleClose()' in s
assert s.index('dex.setThreadNum(2)') < s.index('return Binding.external')
assert 'removeOnCancelPolicy = true' in s and 'allowCoreThreadTimeOut(true)' in s
code='''import java.util.concurrent.*
import t.mma.hooks.core.DexInstallScheduler as D
import t.mma.hooks.core.DexInstallScheduler
class Binding {var closes=0;fun close(){closes++}}
class Timer {
 val tasks=mutableListOf<Runnable>()
 fun schedule(r:Runnable,d:Long,u:TimeUnit):ScheduledFuture<*> {tasks.add(r);return Ticket()}
 class Ticket:ScheduledFuture<Unit>{override fun cancel(b:Boolean)=true;override fun isCancelled()=false;override fun isDone()=false;override fun get(){};override fun get(t:Long,u:TimeUnit){};override fun getDelay(u:TimeUnit)=0L;override fun compareTo(o:Delayed)=0}
}
class Owner {
 val targetLock=Any();val idleCloser=Timer();val EXTERNAL_IDLE_SECONDS=30L
 var activeExternalBinding:Binding?=null
'''+idle+'''
 fun arm()=D.queryDexKit{synchronized(targetLock){armExternalIdleClose()}}
 fun replace(b:Binding?)=D.queryDexKit{synchronized(targetLock){activeExternalBinding=b;armExternalIdleClose()}}
}
fun main(){
 val o=Owner();val first=Binding();o.replace(first)
 val stale=o.idleCloser.tasks.last();o.arm();stale.run();check(first.closes==0)
 o.idleCloser.tasks.last().run();check(first.closes==1 && o.activeExternalBinding==null)
 stale.run();check(first.closes==1)
 val next=Binding();o.replace(next);val old=o.idleCloser.tasks.last();val replacement=Binding();o.replace(replacement)
 old.run();check(next.closes==0 && replacement.closes==0 && o.activeExternalBinding===replacement)
 val pending=o.idleCloser.tasks.last();o.replace(null);pending.run();check(replacement.closes==0)
 val busy=Binding();o.replace(busy);val close=o.idleCloser.tasks.last()
 val launched=CountDownLatch(1);val pool=Executors.newSingleThreadExecutor()
 lateinit var future:Future<*>
 D.queryDexKit {future=pool.submit {launched.countDown();close.run()};check(launched.await(3,TimeUnit.SECONDS));check(busy.closes==0);o.arm()}
 future.get(3,TimeUnit.SECONDS);check(busy.closes==0)
 o.idleCloser.tasks.last().run();check(busy.closes==1 && o.activeExternalBinding==null);pool.shutdown()
 println("PASS external Dex idle cleanup: stale ticket, replacement, explicit close/rebind cancellation, active query exclusion, reschedule and single release")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 (p/'Test.kt').write_text(code)
 (p/'Log.kt').write_text('package t.mma.utils\nobject HLog{fun d(s:String){};fun i(s:String){};fun w(s:String){};fun e(s:String,t:Throwable?=null){}}')
 (p/'Handler.kt').write_text('package android.os\nclass Looper{companion object{fun getMainLooper()=Looper()}}\nclass Handler(l:Looper){fun removeCallbacks(r:Runnable){};fun postDelayed(r:Runnable,d:Long)=true}')
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt')),str(r/'app/src/main/java/t/mma/hooks/core/DexInstallScheduler.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
