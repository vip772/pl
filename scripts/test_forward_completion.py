"""Execute production completion handlers with fake Activity/queue; no host send."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/selectedmessages/SelectedMessageModuleSender.kt').read_text()
def method(needle):
 a=s.index(needle);b=s.index('{',a);i=b+1;depth=1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
code="""
class Intent {var token="";fun getStringExtra(k:String)=token;fun putStringArrayListExtra(k:String,v:ArrayList<String>){};fun putExtra(k:String,v:Int){}}
class Activity(val intent:Intent?){var finishes=0;fun setResult(a:Int,b:Intent){};fun finish(){finishes++};companion object{const val RESULT_OK=-1}}
class Handler{var removed=0;fun removeCallbacks(r:Runnable){removed++}}
object ForwardSendPolicy{const val RETRANSMIT_BATCH_INTERVAL_MS=750L}
class Batch{var success=0}
class Sender{var closed=false;var activeToken="one";var activeBatch:Batch?=Batch();var activeRetransmitTargets=listOf("friend","group");val main=Handler();var timeoutRunnable:Runnable?=Runnable{};var advanced=0;var logs=0
fun logger(s:String,t:Throwable?){logs++;error("logger")}
fun advanceTargets(b:Batch,n:Int,d:Long){advanced+=n;activeToken="";activeRetransmitTargets=emptyList();timeoutRunnable=null}
companion object{const val EXTRA_TOKEN="token"}
"""+method('    fun handleRetransmitDone(')+method('    fun handleRetransmitFinished(')+"""
}
fun main(){
 fun activity(token:String)=Activity(Intent().apply{this.token=token})
 val s=Sender();val a=activity("one");check(s.handleRetransmitDone(a));check(s.activeBatch!!.success==2&&s.advanced==2&&a.finishes==1);s.handleRetransmitFinished(a);check(s.advanced==2);check(!s.handleRetransmitDone(a))
 val f=Sender();val b=activity("one");f.handleRetransmitFinished(b);check(f.advanced==2&&f.activeBatch!!.success==0&&f.logs==1);f.handleRetransmitFinished(b);check(f.advanced==2)
 for(token in listOf("","old")){val c=Sender();c.handleRetransmitFinished(activity(token));check(c.advanced==0);check(!c.handleRetransmitDone(activity(token)))}
 val c=Sender();c.closed=true;c.handleRetransmitFinished(a);check(c.advanced==0);check(!c.handleRetransmitDone(a))
 val missing=Sender();missing.activeBatch=null;missing.handleRetransmitFinished(a);check(missing.advanced==0)
 println("PASS production completion: success, finish without result, duplicate signals, stale/blank tokens, closed owner, missing batch, throwing logger; no host delivery asserted")
}
"""
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=15)
