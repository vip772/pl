"""Execute extracted production publication blocks and batch/coordinator methods; synthetic host only."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items/selectedmessages'
def block(s,needle,start=0):
 a=s.index(needle,start);i=s.index('{',a);z=i+1;depth=1
 while depth:
  depth+=(s[z]=='{')-(s[z]=='}');z+=1
 return s[a:z]
module=(b/'SelectedMessageModuleSender.kt').read_text();official=(b/'OfficialMassSendSender.kt').read_text()
m=block(module,'synchronized(this) {',module.index('.onSuccess {',module.index('launchContext.startActivity')))
o=block(official,'synchronized(this) {',official.index('if (!sent)'))
coordinator=(b/'CustomMassSendSender.kt').read_text();coordinator=coordinator[coordinator.index('object SelectedMessagesRuntimeCoordinator'):]
attach=block(coordinator,'    internal fun attach(').replace('internal fun attach','fun attach');detach=block(coordinator,'    internal fun detach(').replace('internal fun detach','fun detach')
code='''import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class Main {val queue=java.util.ArrayList<Runnable>();fun post(r:Runnable):Boolean {queue.add(r);return true};fun postDelayed(r:Runnable,delay:Long):Boolean{queue.add(r);return true}}
class Snapshot(val type:Int=1)
class Job(val snapshot:Snapshot=Snapshot())
class Batch(val id:String="fixture",val snapshots:List<Snapshot> = listOf(Snapshot()),val targets:List<String> = listOf("x"),var total:Int=0)
'''
for name,source,publication in [('Module',module,m),('Official',official,o)]:
 start=block(source,'    private fun startNextBatch()').replace('private fun startNextBatch','fun startNextBatch')
 code+='class '+name+''' {
 @Volatile var closed=false;var activeBatch:Batch?=null;val batches=java.util.concurrent.ConcurrentLinkedDeque<Batch>()
 val main=Main();var timeoutRunnable:Runnable?=null;var activeToken="t";var activeScene:Any?=Any();var activeInfo:Any?=Any()
 val SEND_TIMEOUT_MS=1L;var calls=0
 fun sendCurrent(batch:Batch){check(!Thread.holdsLock(this));calls++}
 fun handleTimeout(token:String){};fun timeoutFor(snapshot:Snapshot)=1L
 fun completeCurrent(batch:Batch,success:Boolean){};fun logger(s:String,e:Throwable?){}
 fun publish(batch:Batch,token:String,scene:Any?,job:Job){
'''+publication+'\n}\n'+start+'\n}\n'
custom=(b/'CustomMassSendSender.kt').read_text()
publication=block(custom,'            synchronized(this) {',custom.index('operations.remove(operation.id)',custom.index('targets.forEachIndexed')))
code+='class CustomPublication {var closed=false;val main=Main();fun publish(onComplete:((Int,Int,Boolean)->Unit)?){val success=1;val contents=listOf(1);val targets=listOf(1);val operation=java.util.concurrent.atomic.AtomicBoolean();'+publication.replace('operation.canceled.get()','operation.get()')+'}}'+chr(10)
code+='''class CustomMassSendModuleSender
class SelectedMessageModuleSender
class OfficialMassSendSender
class Coordinator {
 data class State(val moduleSender:CustomMassSendModuleSender,val selectedModuleSender:SelectedMessageModuleSender,val officialSender:OfficialMassSendSender)
 @Volatile var state:State?=null
 @Synchronized
'''+attach+'\n @Synchronized\n'+detach+'''
}
fun main(){
'''
code+='val custom=CustomPublication();custom.closed=true;custom.publish{_,_,_->error("late callback")};check(custom.main.queue.isEmpty())'+chr(10)
for name in ['Module','Official']:
 code+='''run {
 val o=NAME();val batch=Batch();o.batches.add(batch);o.startNextBatch();check(o.activeBatch===batch&&o.calls==1&&batch.total>=0)
 o.publish(batch,"t",o.activeScene,Job());check(o.main.queue.size==1);o.timeoutRunnable=null;o.main.queue.clear()
 o.closed=true;o.publish(batch,"t",o.activeScene,Job());check(o.timeoutRunnable==null&&o.main.queue.isEmpty())
 val late=NAME();val old=Batch();late.activeBatch=Batch("new");late.publish(old,"t",late.activeScene,Job());check(late.main.queue.isEmpty())
 val stopped=NAME();stopped.closed=true;stopped.batches.add(Batch());stopped.startNextBatch();check(stopped.calls==0&&stopped.activeBatch==null)
}
'''.replace('NAME',name)
code+='''val c=Coordinator();val old=CustomMassSendModuleSender();val fresh=CustomMassSendModuleSender();val s=SelectedMessageModuleSender();val official=OfficialMassSendSender()
c.attach(old,s,official);c.attach(fresh,s,official);c.detach(old);check(c.state?.moduleSender===fresh);c.detach(fresh);check(c.state==null)
for(round in 1..200){c.attach(old,s,official);val start=CountDownLatch(1);val a=Thread{check(start.await(5,TimeUnit.SECONDS));c.attach(fresh,s,official)};val d=Thread{check(start.await(5,TimeUnit.SECONDS));c.detach(old)};a.start();d.start();start.countDown();a.join(5000);d.join(5000);check(!a.isAlive&&!d.isAlive&&c.state?.moduleSender===fresh)}
println("PASS production publication blocks: closed/replaced batch creates no timeout, dequeue/close gate, no host call under lifecycle monitor, 200 coordinator old-detach/new-attach races")
}
'''
assert '@Synchronized\n    internal fun attach(' in coordinator and '@Synchronized\n    internal fun detach(' in coordinator
for source in [module,official]:
 assert 'SelectedMessageSendHandle { cancel(batchId) }.withStopPending { stopPending(batchId) }' in source
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=30)