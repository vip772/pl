"""JVM synthetic test of extracted production sender admission and shutdown only."""
from pathlib import Path
import subprocess, tempfile
r = Path(__file__).resolve().parents[1]
base = r / 'app/src/main/java/t/mma/hooks/items/selectedmessages'
def method(s, needle):
    a = s.index(needle); b = s.index('{', a); depth = 1; i = b + 1
    while depth:
        depth += (s[i] == '{') - (s[i] == '}'); i += 1
    return s[a:i]
prelude = '''
import java.util.ArrayDeque
object Looper { var mainThread=true; fun myLooper()=if(mainThread)1 else 2; fun getMainLooper()=1 }
class Handler { val queue=ArrayList<()->Unit>(); var reject=false; fun post(f:()->Unit):Boolean {if(reject)return false;queue.add(f);return true}; fun removeCallbacksAndMessages(x:Any?){queue.clear()}; fun drain(){val q=queue.toList();queue.clear();q.forEach{it()}} }
class Resources {var calls=0;fun close(){calls++}}
class SelectedMessageSendHandle(val f:()->Unit){fun withStopPending(f:()->Unit)=this}
class SendBatch(val id:String,val onComplete:((Int,Int,Boolean)->Unit)?){var success=2;val snapshots=listOf(1,2);val targets=listOf(1,2,3);val total=6}
'''
parts = [prelude, (base / 'SelectedSendBudget.kt').read_text().split('\n', 1)[1]]
for name in ('SelectedMessageModuleSender', 'OfficialMassSendSender'):
    s = (base / (name + '.kt')).read_text()
    a = s.index('            synchronized(this) {', s.index('fun enqueue'))
    b = s.index('\n            accepted = true', a)
    admission = 'fun admit(batch:SendBatch):SelectedMessageSendHandle? {\n' + s[a:b] + '\nreturn SelectedMessageSendHandle { cancel(batch.id) }.withStopPending { stopPending(batch.id) }\n}'
    fields = '''
private val sendBudget=SelectedSendBudget();var closed=false;val main=Handler();val batches=ArrayDeque<SendBatch>();val admissions=java.util.concurrent.ConcurrentHashMap<String,SendBatch>();val retainedBatches=java.util.concurrent.ConcurrentHashMap<String,SendBatch>();var activeBatch:SendBatch?=null;var activeToken="token";var activeRetransmitTargets=listOf("x");var timeoutRunnable:Any?=Any();val resources=Resources();var callbackHookInstalled=true;var runtime:Any?=Any();var activeScene:Any?=Any();var activeInfo:Any?=Any();var starts=0
fun logger(s:String,e:Throwable?){error("logger failure")};fun cancel(s:String){};fun stopPending(s:String){};fun startNextBatch(){starts++}
'''
    parts.extend(['class ' + name + ' {', fields, admission, method(s,'    fun shutdown()'), method(s,'    private fun notifyStopped('), '}'])
    parts.append('''
fun testNAME(){
 val c=NAME();var calls=0
 val callback:(Int,Int,Boolean)->Unit={a,b,stopped->check(a==2&&b==6&&stopped);calls++}
 c.activeBatch=SendBatch("active",callback);c.batches.add(SendBatch("queued"){_,_,_->calls++;error("callback failure")});c.retainedBatches["active"]=c.activeBatch!!;c.retainedBatches["queued"]=c.batches.first()
 check(c.admit(SendBatch("pending",callback))!=null);check(c.admissions.size==1)
 c.shutdown();check(c.closed&&c.activeBatch==null&&c.batches.isEmpty()&&c.admissions.isEmpty()&&c.main.queue.isEmpty());check(calls==3)
 c.shutdown();c.main.drain();check(calls==3&&c.starts==0);check(c.admit(SendBatch("late",callback))==null)
 Looper.mainThread=false;val off=NAME();var offCalls=0;off.admit(SendBatch("off"){_,_,_->offCalls++});off.shutdown();off.shutdown();off.main.drain();check(offCalls==1&&off.admissions.isEmpty()&&off.starts==0);Looper.mainThread=true
 val rejected=NAME();rejected.main.reject=true;check(rejected.admit(SendBatch("reject",callback))==null);check(rejected.admissions.isEmpty());rejected.shutdown()
 Looper.mainThread=false;val dead=NAME();var deadCalls=0;dead.admit(SendBatch("accepted-before-rejection"){_,_,_->deadCalls++});dead.main.reject=true;dead.shutdown();check(deadCalls==1&&dead.retainedBatches.isEmpty()&&dead.admissions.isEmpty()&&dead.main.queue.isEmpty());Looper.mainThread=true
 val normal=NAME();normal.admit(SendBatch("accepted",callback));normal.main.drain();check(normal.admissions.isEmpty()&&normal.batches.size==1&&normal.starts==1);normal.shutdown();check(calls==4)
}
'''.replace('NAME',name))
parts.append('fun main(){testSelectedMessageModuleSender();testOfficialMassSendSender();println("PASS production sender admission/shutdown: pending+active+queued release, callback/logger isolation, admission rejection rollback and off-main shutdown rejection fallback, late admission blocked, idempotence; no host send invoked")}')
libs=Path('/tmp/pl-kotlin-libs'); std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    p=Path(td);(p/'Test.kt').write_text('\n'.join(parts))
    subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
    subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=15)
