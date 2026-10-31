"""Production export-session callback ownership, deterministic Handler fixture."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/monetgenerator/MonetModuleGeneratorRuntime.kt').read_text()
a=s.index('    private class ExportSession');b=s.index('    @Volatile\n    private var binding',a)
body=s[a:b]
assert 'checkActive(export)' in s and 'if (session === export) session = null' in s
assert s.index('checkActive(export)',s.index('MonetModuleGenerator.generate')) < s.index('openOutputStream')
assert 'worker.start() } catch' in s
code='''class Binding
class MonetGenerationEvent
class ExportResult
object SystemClock {fun uptimeMillis()=0L}
class Handler {
 val tasks=mutableListOf<Pair<Any,()->Unit>>()
 fun postAtTime(task:()->Unit,token:Any,time:Long){tasks+=token to task}
 fun removeCallbacksAndMessages(token:Any){tasks.removeAll{it.first===token}}
 fun flush(){val old=tasks.toList();tasks.clear();old.forEach{it.second()}}
}
object Owner {
 val mainHandler=Handler();var binding:Binding?=null
'''+body+'''
 fun test(){
  val first=Binding();binding=first;var events=0;var complete=0
  val old=ExportSession(first,{events++},{complete++})
  postEvent(old,MonetGenerationEvent());val late=mainHandler.tasks.last().second
  old.cancel();mainHandler.removeCallbacksAndMessages(old);binding=Binding()
  late();check(events==0 && old.event==null && old.complete==null)
  check(runCatching{checkActive(old)}.exceptionOrNull() is java.util.concurrent.CancellationException)
  val next=ExportSession(binding!!,{events++},{complete++})
  checkActive(next);postEvent(next,MonetGenerationEvent());postComplete(next,Result.success(ExportResult()));mainHandler.flush()
  check(events==1 && complete==1 && next.event==null && next.complete==null)
  println("PASS production Monet export session: stale owner rejection, callback-reference release, completion cleanup")
 }
}
fun main(){Owner.test()}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)