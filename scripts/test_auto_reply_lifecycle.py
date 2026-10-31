"""Exercise extracted production generation ownership; no network or host sends.
Android/OkHttp cleanup endpoints are fixtures; full linkage requires Actions.
"""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/autoreply/AutoReplyRuntime.kt').read_text()
a=s.index('    private class RuntimeGeneration');b=s.index('    private const val XIAOZHI_MCP_SESSION_TTL_MS',a)
production=s[a:b]
assert 'old.worker.shutdown()' in production and 'old.worker.shutdownNow()' not in production
assert '?: generation?.timer' not in production
assert 'Thread.sleep(XIAOZHI_MCP_RECONNECT_MS)' not in s
feature=(r/'app/src/main/java/t/mma/hooks/items/autoreply/AutoReplyFeature.kt').read_text()
assert 'unregisterOnSharedPreferenceChangeListener(listener)' in feature
assert 'stopRuntime(); updateRuntime(context, prefs)' in feature
assert 'resources.register { subscription.unsubscribe() }' in feature
code='''import java.util.concurrent.*
import java.util.concurrent.atomic.*
class TextToSpeech {fun stop(){};fun shutdown(){}}
class AiMessage
class XiaozhiSocketSession {fun close(reason:String){}}
class XiaozhiMcpBridge {fun close(reason:String){}}
object HLog {fun e(message:String,error:Throwable){}}
class OkHttpClient {
 val dispatcher=Dispatcher();val connectionPool=Pool()
 class Builder {fun pingInterval(n:Long,u:TimeUnit)=this;fun build()=OkHttpClient()}
 class Dispatcher {val executorService=Executors.newSingleThreadExecutor();fun cancelAll(){}}
 class Pool {fun evictAll(){}}
}
object Owner {
 const val TAG="fixture"
'''+production+'''
 fun test(){
  val entered=CountDownLatch(1);val release=CountDownLatch(1);val done=CountDownLatch(1)
  val delivered=AtomicInteger();val interrupted=AtomicBoolean(false)
  start();val old=generation!!;start();check(generation===old)
  enqueue {
   entered.countDown()
   try{release.await()}catch(e:InterruptedException){interrupted.set(true)}
   if(canRun())delivered.incrementAndGet()
   state().replyCounts["old-job"]=1
   done.countDown()
  }
  check(entered.await(3,TimeUnit.SECONDS))
  enqueue {delivered.addAndGet(100)}
  old.timer.schedule({delivered.addAndGet(1000)},1,TimeUnit.SECONDS)
  stop();stop();check(!old.active && old.worker.isShutdown && old.timer.isShutdown)
  start();val next=generation!!;check(next!==old)
  release.countDown();check(done.await(3,TimeUnit.SECONDS))
  check(!interrupted.get() && delivered.get()==0)
  check(next.replyCounts.isEmpty() && old.replyCounts["old-job"]==1)
  val fresh=CountDownLatch(1)
  enqueue{check(canRun());delivered.incrementAndGet();fresh.countDown()}
  check(fresh.await(3,TimeUnit.SECONDS));check(delivered.get()==1)
  withOwner(old){error("late old callback executed")}
  check(processingGeneration.get()==null)
  stop();check(next.worker.awaitTermination(3,TimeUnit.SECONDS))
  check(old.worker.awaitTermination(3,TimeUnit.SECONDS))
  println("PASS production auto reply generation: repeated start/stop, queue cancellation, graceful in-flight drain, stale callback rejection, old/new cache isolation, executor exit")
 }
}
fun main(){Owner.test()}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
