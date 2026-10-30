"""Compile real scheduler and send-admission code; verify isolation and lifecycle."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
agent=(r/'app/src/main/java/t/mma/hooks/items/script/agent/ScriptPluginAgentLocalReverseTools.kt').read_text()
start=agent.index('            DexInstallScheduler.queryDexKit {')
end=agent.index('        }.getOrElse {',start)
section=agent[start:end]
assert section.index('queryDexKit') < section.index('synchronized(targetLock)') < section.index('findClasses(target,')
assert section.index('attachTarget(targetResult, target)') < section.rindex('result')
scheduler=(r/'app/src/main/java/t/mma/hooks/core/DexInstallScheduler.kt').read_text()
s=(r/'app/src/main/java/t/mma/hooks/items/scheduledtask/ScheduledTaskFeature.kt').read_text()
a=s.index('    private val executionWorker');b=s.index('    @Volatile private var started',a)
worker=s[a:b]
a=s.index('    @Synchronized\n    private fun submitSend');b=s.index('    private fun sendTask',a)
submit=s[a:b]
code='''import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import t.mma.hooks.core.DexInstallScheduler as D
import android.os.Handler
class ScheduledTaskItem(val id:String,val planTime:Long,val status:Int){fun copy(status:Int)=ScheduledTaskItem(id,planTime,status)}
object ScheduledTaskSettings {const val STATUS_RUNNING=1;const val STATUS_PENDING=0;const val STATUS_DEFERRED=2}
class Settings {val tasks=ConcurrentHashMap<String,ScheduledTaskItem>();fun findTask(id:String)=tasks[id];fun saveTask(t:ScheduledTaskItem){tasks[t.id]=t};fun isEnabled()=true}
class Sender {
 val settings=Settings();var errors=0;var started=true
 val deferredSends=ConcurrentHashMap<String,Long>();val admissionRetries=ConcurrentHashMap<String,Int>()
 fun schedule(t:ScheduledTaskItem,d:Long){}
 val acceptedSends=ConcurrentHashMap.newKeySet<String>()
 val uncertainSends=ConcurrentHashMap<String,Long>()
 var reloads=0
 fun reload(){reloads++}
 fun logger(s:String,t:Throwable){errors++}
'''+worker+submit+'''
 fun send(id:String,job:Runnable):Boolean {val t=ScheduledTaskItem(id,1,1);settings.saveTask(t);return submitSend(t,job)}
 fun shutdown()=executionWorker.shutdown()
}
fun state(key:String):Any {val f=D.javaClass.getDeclaredField("states");f.isAccessible=true;return (f.get(D) as Map<*,*>)[key]!!}
fun installer(key:String):Any? {val t=state(key);val f=t.javaClass.getDeclaredField("installer");f.isAccessible=true;return f.get(t)}
fun flush(){val f=D.javaClass.getDeclaredField("executor");f.isAccessible=true;(f.get(D) as ThreadPoolExecutor).submit{}.get(3,TimeUnit.SECONDS)}
fun main(){
 val active=AtomicInteger();val maximum=AtomicInteger();val parallel=Executors.newFixedThreadPool(4)
 val checks=(0 until 40).map{parallel.submit{D.queryDexKit{val n=active.incrementAndGet();maximum.updateAndGet{old->maxOf(old,n)};Thread.yield();active.decrementAndGet()}}}
 checks.forEach{it.get(3,TimeUnit.SECONDS)};parallel.shutdown();check(maximum.get()==1)
 check(D.queryDexKit{D.queryDexKit{42}}==42)
 try{D.queryDexKit{error("query failure")}}catch(_:IllegalStateException){}
 check(D.queryDexKit{43}==43)
 val n=AtomicInteger();D.schedule("first"){n.incrementAndGet();true};check(n.get()==0)
 D.markDexBridgeReady();flush();check(n.get()==1 && installer("first")==null)
 repeat(30){D.schedule("first"){error("duplicate")}};flush();check(n.get()==1 && installer("first")==null)
 val entered=CountDownLatch(1);val release=CountDownLatch(1)
 D.schedule("block"){entered.countDown();release.await(3,TimeUnit.SECONDS);true};check(entered.await(3,TimeUnit.SECONDS))
 D.schedule("marked"){error("marked queued installer executed")};D.markInstalled("marked");check(installer("marked")==null)
 release.countDown();flush();check(installer("block")==null)
 val attempts=AtomicInteger();D.schedule("retry"){attempts.incrementAndGet()>1};flush();check(attempts.get()==1 && installer("retry")!=null)
 Handler.drain();flush();check(attempts.get()==2 && installer("retry")==null)
 val sender=Sender();val gate=CountDownLatch(1);val started=CountDownLatch(2);val finished=CountDownLatch(3)
 repeat(2){check(sender.send("s$it",Runnable{started.countDown();gate.await(3,TimeUnit.SECONDS);finished.countDown()}))}
 check(started.await(3,TimeUnit.SECONDS));check(!sender.send("s0",Runnable{error("duplicate send")}));val other=Executors.newSingleThreadExecutor()
 try {other.submit{}.get(1,TimeUnit.SECONDS)}finally{other.shutdownNow()}
 check(sender.send("queued",Runnable{finished.countDown()}))
 repeat(31){check(sender.send("fill$it",Runnable{}))}
 check(!sender.send("busy",Runnable{error("busy task ran")}))
 check(sender.settings.findTask("busy")!!.status==2 && sender.deferredSends["busy"]==1L)
 sender.shutdown()
 check(!sender.send("rejected",Runnable{error("rejected ran")}));check(sender.settings.findTask("rejected")!!.status==2 && sender.errors==2)
 gate.countDown();check(finished.await(3,TimeUnit.SECONDS))
 val failed=Sender();val failedDone=CountDownLatch(1)
 check(failed.send("failure",Runnable{throw IllegalStateException("persistence failed")}))
 failed.shutdown()
 val fworker=failed.javaClass.getDeclaredField("executionWorker");fworker.isAccessible=true
 check((fworker.get(failed) as ThreadPoolExecutor).awaitTermination(3,TimeUnit.SECONDS))
 check(failed.errors==1 && failed.reloads==0 && failed.acceptedSends.isEmpty() && failed.uncertainSends["failure"]==1L)
 println("PASS production schedulers: staged install, success closure release, duplicate/marked skip, retry retention, two send workers, isolated queue, graceful drain, rejected submission restores pending")
 val f=D.javaClass.getDeclaredField("executor");f.isAccessible=true;(f.get(D) as ThreadPoolExecutor).shutdownNow()
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 (p/'Scheduler.kt').write_text(scheduler)
 (p/'Test.kt').write_text(code)
 (p/'Log.kt').write_text('package t.mma.utils\nobject HLog {fun d(s:String){};fun i(s:String){};fun w(s:String){};fun e(s:String,t:Throwable?=null){}}')
 (p/'Handler.kt').write_text('''package android.os
class Looper {companion object {fun getMainLooper()=Looper()}}
class Handler(l:Looper) {fun postDelayed(r:Runnable,d:Long):Boolean {synchronized(tasks){tasks+=r};return true}
 companion object {val tasks=mutableListOf<Runnable>();fun drain(){val a=synchronized(tasks){tasks.toList().also{tasks.clear()}};a.forEach{it.run()}}}}
''')
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt'))],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
