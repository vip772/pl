"""Production mass-send shutdown and voice pacing; synthetic work only."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items'
s=(b/'selectedmessages/CustomMassSendSender.kt').read_text()
a=s.index('    @Synchronized\n    fun shutdown()');z=s.index('    private fun sendToTalker',a);stop=s[a:z]
a=s.index('    private fun cancel(operationId:');z=s.index('\n}',a);cancel=s[a:z]
v=(b/'voiceforward/VoiceForwardFeature.kt').read_text()
a=v.index('    private fun waitBetweenForwards()');z=v.index('    @Synchronized\n    fun shutdown()',a);wait=v[a:z]
assert 'forwardExecutor.shutdownNow()' not in v
assert 'hookResources.register { HookRegistry.get().unhook(handle) }' in v
assert 'restartOnChange = true' in v and 'if (closed) return@execute' in v
code='''import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
class Main {fun removeCallbacksAndMessages(token:Any?) {}}
object ForwardSendPolicy {const val MIN_SEND_INTERVAL_MS=60000L}
class Owner {
 @Volatile private var closed=false
 private val main=Main()
 private val executor=Executors.newSingleThreadExecutor()
 private val operations=ConcurrentHashMap<String,SendOperation>()
'''+stop+cancel+'''
 @Synchronized fun submit(id:String,job:()->Unit):Boolean {
  if(closed)return false
  val op=SendOperation(id);operations[id]=op
  executor.execute {if(!closed&&!op.canceled.get())job();operations.remove(id)}
  return true
 }
 fun await()=executor.awaitTermination(3,TimeUnit.SECONDS)
}
class Voice {
 private val stopSignal=CountDownLatch(1)
'''+wait+'''
 fun pause()=waitBetweenForwards()
 fun close()=stopSignal.countDown()
}
fun main(){
 val o=Owner();val started=CountDownLatch(1);val finish=CountDownLatch(1)
 var count=0;var interrupted=false
 check(o.submit("first"){started.countDown();try{finish.await()}catch(e:InterruptedException){interrupted=true};count++})
 check(started.await(2,TimeUnit.SECONDS));check(o.submit("queued"){count+=100})
 o.shutdown();o.shutdown();check(!o.submit("late"){count+=1000});finish.countDown()
 check(o.await()&&!interrupted&&count==1)
 val voice=Voice();val done=CountDownLatch(1);var result=true
 val t=Thread{result=voice.pause();done.countDown()};t.start();voice.close()
 check(done.await(2,TimeUnit.SECONDS)&&!result);t.join()
 println("PASS production mass-send shutdown: no interrupted active job, queued and late jobs blocked; voice pacing wakes cooperatively")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)