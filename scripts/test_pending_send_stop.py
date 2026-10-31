"""Extract production pending-stop methods; synthetic batches, no host calls."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items/selectedmessages'
classes=[]
for filename,klass in [('OfficialMassSendSender.kt','Official'),('SelectedMessageModuleSender.kt','Module')]:
 s=(b/filename).read_text();a=s.index('    private fun stopPending(');z=s.index('    private fun cancel(',a);methods=s[a:z]
 assert '"cancel"' not in methods and '.finish()' not in methods
 classes.append('class '+klass+''' {
 class Main {fun post(action:()->Unit)=action();fun removeCallbacks(r:Runnable){}}
 data class SendBatch(val id:String,var stopRequested:Boolean=false,val total:Int=4,var success:Int=1,
 val snapshots:List<Int> = listOf(1,2),val targets:List<Int> = listOf(1,2),val onComplete:((Int,Int,Boolean)->Unit)?=null)
 val main=Main();val batches=java.util.ArrayDeque<SendBatch>();var activeBatch:SendBatch?=null
 var activeScene:Any?=null;var activeInfo:Any?=null;var activeToken="";var activeRetransmitTargets=listOf<String>()
 var timeoutRunnable:Runnable?=null;var nextCalls=0
 fun startNextBatch(){nextCalls++}
'''+methods+'''
 fun stop(id:String)=stopPending(id)
 fun complete(){activeBatch?.let{finishStopped(it)}}
}
''')
code='\n'.join(classes)+'\nfun main(){\n'
for klass in ['Official','Module']:
 code+='''run {
 val o='''+klass+'''();var completions=0
 val active='''+klass+'''.SendBatch("active",onComplete={success,total,canceled->check(canceled&&total==4);completions++})
 o.activeBatch=active;o.activeScene=Any();o.activeToken="in-flight"
 o.stop("active");check(active.stopRequested&&o.activeBatch===active&&completions==0)
 val queued='''+klass+'''.SendBatch("queued",onComplete={_,_,canceled->check(canceled);completions++})
 o.batches.add(queued);o.stop("queued");check(o.batches.isEmpty()&&completions==1&&o.activeBatch===active)
 o.complete();check(o.activeBatch==null&&completions==2&&o.nextCalls==1)
 o.stop("active");check(completions==2)
 val idle='''+klass+'''.SendBatch("idle",onComplete={_,_,_->completions++})
 o.activeBatch=idle;o.activeScene=null;o.activeToken="";o.stop("idle");check(completions==3&&o.activeBatch==null)
}
'''
code+='println("PASS production pending-only stop: in-flight preserved, queued removed, idle stopped, completion once")\n}'
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)