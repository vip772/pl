"""Production executor/stop/schedule behavior with synthetic jobs, never host sends."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/automessageforward/AutoMessageForwardRuntime.kt').read_text()
a=s.index('    private val executor');b=s.index('    @Volatile private var closed',a);executor=s[a:b]
a=s.index('    @Synchronized\n    fun shutdown');b=s.index('    private fun acceptMessage',a);stop=s[a:b]
a=s.index('    private fun schedule(delayMillis:');b=s.index('    private fun isSupportedKind',a);schedule=s[a:b]
code='''import java.util.concurrent.*
class Owner {
'''+executor+'''
 @Volatile private var closed=false
 val recentMessages=linkedMapOf<String,Long>();val pendingKeys=mutableSetOf<String>()
 val pendingForwards=mutableMapOf<String,String>();val protectedMediaPaths=mutableSetOf<String>()
 val moduleOutgoing=mutableListOf<String>();val recallRecords=mutableListOf<String>()
 val recentRecallKeys=mutableMapOf<String,Long>();val recallRevocationsInFlight=mutableSetOf<String>()
 var recallAccount="account";var nextSendAllowedAt=1L
'''+stop+schedule+'''
 fun submit(job:()->Unit)=schedule(0,job)
 fun later(job:()->Unit)=schedule(60_000,job)
 fun await()=executor.awaitTermination(3,TimeUnit.SECONDS)
}
fun main(){
 val owner=Owner();val started=CountDownLatch(1);val release=CountDownLatch(1)
 var sent=0;var interrupted=false
 owner.submit{started.countDown();try{release.await()}catch(e:InterruptedException){interrupted=true};owner.recallRecords.add("finished");sent++}
 check(started.await(3,TimeUnit.SECONDS));owner.later{sent+=100}
 owner.shutdown();owner.shutdown();check(!owner.submit{sent+=1000})
 release.countDown();check(owner.await());check(sent==1 && !interrupted)
 check(owner.recallRecords.isEmpty() && owner.recallAccount.isEmpty())
 val next=Owner();val done=CountDownLatch(1);check(next.submit{done.countDown()});check(done.await(3,TimeUnit.SECONDS));next.shutdown();check(next.await())
 println("PASS production auto-forward: delayed work cancelled, no interrupt of active send, cleanup serialized after send, late admission rejected, fresh runtime reusable")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)