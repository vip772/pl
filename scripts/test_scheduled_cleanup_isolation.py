"""Extract production shutdown/cancel methods; synthetic task API, real executor."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/scheduledtask/ScheduledTaskFeature.kt').read_text()
def block(needle):
 a=s.index(needle);b=s.index('{',a);i=b+1;depth=1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
code='''import java.util.concurrent.*
class Fixture {
 var started=true;val scheduled=linkedMapOf("first" to true,"second" to true,"third" to true)
 val attempted=mutableListOf<String>();val executionWorker=Executors.newSingleThreadExecutor();var failLogger=true
 val logger:(String,Throwable?)->Unit={_,_->if(failLogger)error("logger failure")}
 fun taskApi()=this
 fun cancel(k:String){attempted+=k;error("cancel failure")}
'''+block('    fun shutdown()')+block('    private fun cancelAll()')+block('    private fun cancelScheduledKey(')+'''}
fun main(){
 val f=Fixture();val entered=CountDownLatch(1);val release=CountDownLatch(1);var completed=false;var interrupted=false
 f.executionWorker.execute{entered.countDown();try{release.await()}catch(_:InterruptedException){interrupted=true};completed=true}
 check(entered.await(2,TimeUnit.SECONDS));f.shutdown();f.shutdown();check(!f.started&&f.scheduled.isEmpty()&&f.attempted==listOf("first","second","third")&&f.executionWorker.isShutdown)
 release.countDown();check(f.executionWorker.awaitTermination(2,TimeUnit.SECONDS)&&completed&&!interrupted)
 println("PASS scheduled production shutdown/cancel: all keys attempted despite cancellation and reporter failure; executor closed; accepted work not interrupted; duplicate shutdown safe")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
