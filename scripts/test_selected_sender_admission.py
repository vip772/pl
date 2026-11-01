"""Execute whole production enqueue methods with fake preparation/Handler; never invoke WeChat."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]; base=r/'app/src/main/java/t/mma/hooks/items/selectedmessages'
def method(s, needle):
    a=s.index(needle); i=s.index('{',a); depth=1; end=i+1
    while depth:
        depth+=(s[end]=='{')-(s[end]=='}'); end+=1
    return s[a:end]
prelude='''package t.mma.hooks.items.selectedmessages
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentHashMap
class Handler {
 val queue=java.util.concurrent.ConcurrentLinkedQueue<()->Unit>();var reject=false
 fun post(f:()->Unit):Boolean {if(reject)return false;queue.add(f);return true}
 fun drain(){while(true){val f=queue.poll()?:break;f()}}
}
class Context {fun hostContext()=this}
object SelectedMessagesSettings {fun sendIntervalSeconds(c:Context)=0;fun officialIntervalMinutes(c:Context)=0}
object ForwardSendPolicy {const val MIN_SEND_INTERVAL_MS=1L}
class SelectedMessageSendHandle(val cancel:()->Unit){fun withStopPending(f:()->Unit)=this}
class SelectedMessageSnapshot(val type:Int=1,val retransmit:Any?=Any(),val voicePath:String="",val supported:Boolean=true)
class PreparedSnapshot(val snapshot:SelectedMessageSnapshot,val emojiPayload:Any?,val voicePayload:Any?)
class SendJob(val snapshot:SelectedMessageSnapshot,val targets:List<String>,val emojiPayload:Any?,val voicePayload:Any?,val targetBatchIndex:Int)
class SendBatch(val id:String,val snapshots:List<SelectedMessageSnapshot> = emptyList(),val targets:List<String> = emptyList(),
 val onComplete:((Int,Int,Boolean)->Unit)?=null,val jobs:List<SendJob> = emptyList(),var total:Int=0,
 val targetDelayMillis:Long=0,val itemDelayMillis:Long=0,val targetBatchDelayMillis:Long=0){var success=0}
'''
parts=[prelude,(base/'SelectedSendBudget.kt').read_text().split('\n',1)[1]]
for name,needle in [('SelectedMessageModuleSender','    private fun enqueueInternal('),('OfficialMassSendSender','    fun enqueue(')]:
    source=(base/(name+'.kt')).read_text()
    enqueue=method(source,needle)
    if name.startswith('Selected'): enqueue=enqueue.replace('private fun enqueueInternal(','fun enqueue(')
    parts+=['class '+name+' {', '''
 @Volatile var closed=false;private val sendBudget=SelectedSendBudget();val main=Handler();val context=Context()
 val admissions=ConcurrentHashMap<String,SendBatch>();val retainedBatches=ConcurrentHashMap<String,SendBatch>()
 val batches=ConcurrentLinkedDeque<SendBatch>();var activeBatch:SendBatch?=null;var runtime:Any?=Any()
 var available=true;var preparationFails=false;var closeDuringPrepare=false;var prepares=0;var starts=0
 var prepareHook:(()->Unit)?=null
 val NEXT_MESSAGE_DELAY_MS=1L
 fun logger(s:String,e:Throwable?){error("logger failure")}
 fun locateRuntime():Any?=if(available)Any()else null
 fun isSupported(s:SelectedMessageSnapshot,r:Any)=s.supported
 fun contactLimit(r:Any)=2
 fun prepareEmojiPayload(s:SelectedMessageSnapshot):Any?=prepare()
 fun prepareVoicePayload(s:SelectedMessageSnapshot):Any?=prepare()
 fun prepare():Any? {prepares++;prepareHook?.invoke();if(closeDuringPrepare)close();return if(preparationFails)null else Any()}
 fun startNextBatch(){starts++}
 fun notifyStopped(batch:SendBatch){batch.onComplete?.invoke(batch.success,batch.total,true)}
 fun cancel(id:String){};fun stopPending(id:String){}
 fun close(){closed=true;sendBudget.close();val stopped=retainedBatches.values.toList();retainedBatches.clear();admissions.clear();batches.clear();stopped.forEach{notifyStopped(it)}}
 fun finish(){val batch=batches.pollFirst()?:error("missing batch");batch.onComplete?.invoke(1,1,false)}
''',enqueue,'}']
    call=lambda snap='listOf(SelectedMessageSnapshot())',targets='listOf("target")',cb='null':f'o.enqueue({snap},{targets},0,0,{cb})'
    test='''
fun testNAME(){
 val o=NAME();var callbacks=0
 repeat(16){check(CALL!=null)}
 check(CALL==null);check(o.retainedBatches.size==16&&o.admissions.size==16)
 o.main.drain();check(o.batches.size==16&&o.admissions.isEmpty());o.finish()
 check(CALL!=null);o.main.drain();o.close();check(o.retainedBatches.isEmpty())
 val rejected=NAME();rejected.main.reject=true
 repeat(40){check(rejected.enqueue(listOf(SelectedMessageSnapshot()),listOf("x"),0,0,null)==null)}
 rejected.main.reject=false;check(rejected.enqueue(listOf(SelectedMessageSnapshot()),listOf("x"),0,0,null)!=null)
 val invalid=NAME()
 repeat(40){check(invalid.enqueue(emptyList(),listOf("x"),0,0,null)==null)}
 repeat(40){check(invalid.enqueue(listOf(SelectedMessageSnapshot()),listOf(" "),0,0,null)==null)}
 check(invalid.enqueue(listOf(SelectedMessageSnapshot()),listOf("x"),0,0,null)!=null)
 val throwing=NAME();var ended=0
 check(throwing.enqueue(listOf(SelectedMessageSnapshot()),listOf("x"),0,0,{_,_,_->ended++;error("consumer callback")})!=null)
 throwing.main.drain();val batch=throwing.batches.first();throwing.finish();batch.onComplete?.invoke(1,1,false)
 check(ended==1&&throwing.retainedBatches.isEmpty())
 repeat(16){check(throwing.enqueue(listOf(SelectedMessageSnapshot()),listOf("x"),0,0,null)!=null)}
 throwing.close();check(throwing.enqueue(listOf(SelectedMessageSnapshot()),listOf("x"),0,0,null)==null)
 val reenter=NAME();var reentered=false
 check(reenter.enqueue(listOf(SelectedMessageSnapshot()),listOf("x"),0,0,{_,_,_->reentered=reenter.enqueue(listOf(SelectedMessageSnapshot()),listOf("x"),0,0,null)!=null})!=null)
 reenter.main.drain();reenter.finish();check(reentered)
}
'''.replace('NAME',name).replace('CALL',call())
    parts.append(test)
parts.append('''
fun officialPreparation(){
 for(type in listOf(34,47)) {
  val o=OfficialMassSendSender();o.preparationFails=true
  repeat(40){check(o.enqueue(listOf(SelectedMessageSnapshot(type)),listOf("x"),0,0,null)==null)}
  check(o.prepares==40);o.preparationFails=false
  check(o.enqueue(listOf(SelectedMessageSnapshot(type)),listOf("x"),0,0,null)!=null)
  val late=OfficialMassSendSender();late.closeDuringPrepare=true
  check(late.enqueue(listOf(SelectedMessageSnapshot(type)),listOf("x"),0,0,null)==null)
  check(late.admissions.isEmpty()&&late.retainedBatches.isEmpty())
 }
 val bounded=OfficialMassSendSender()
 repeat(16){check(bounded.enqueue(listOf(SelectedMessageSnapshot()),listOf("x"),0,0,null)!=null)}
 check(bounded.enqueue(listOf(SelectedMessageSnapshot(47)),listOf("x"),0,0,null)==null)
 check(bounded.prepares==0)
 val entered=java.util.concurrent.CountDownLatch(1);val finish=java.util.concurrent.CountDownLatch(1)
 val inFlight=OfficialMassSendSender();var interrupted=false;var result:SelectedMessageSendHandle?=null
 inFlight.prepareHook={entered.countDown();try{check(finish.await(5,java.util.concurrent.TimeUnit.SECONDS))}catch(e:InterruptedException){interrupted=true}}
 val t=Thread{result=inFlight.enqueue(listOf(SelectedMessageSnapshot(34)),listOf("x"),0,0,null)}
 t.start();check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));inFlight.close();finish.countDown();t.join(5000)
 check(!t.isAlive&&!interrupted&&result==null&&inFlight.admissions.isEmpty()&&inFlight.retainedBatches.isEmpty())
}
fun main(){testSelectedMessageModuleSender();testOfficialMassSendSender();officialPreparation();println("PASS whole production enqueue methods: aggregate capacity, rejected/invalid/preparation refunds, callback+logger exceptions, duplicate callback, reentrant enqueue, voice+emoji, close while preparation entered without interruption")}
''')
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    p=Path(td);(p/'Test.kt').write_text('\n'.join(parts))
    subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
    subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'t.mma.hooks.items.selectedmessages.TestKt'],check=True,timeout=30)
