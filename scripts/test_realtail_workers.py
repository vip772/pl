"""Extract unchanged production worker helpers; deterministic Thread fault fixture.
Checks registration/cleanup only, not Android thread scheduling or network completion.
"""
from pathlib import Path
import subprocess, tempfile
r = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
def method(source, needle):
    start = source.rfind('\n', 0, source.index(needle)) + 1
    pos = source.index('{', start); depth = 1; end = pos + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}'); end += 1
    return source[start:end]
fixture = '''
class Thread(val action:()->Unit, val name:String) {
 companion object {
  var failCreate=false;var failStart=false;var failInterrupt=false
  var beforeStart:(()->Unit)?=null;var current:Thread?=null
  val created=mutableListOf<Thread>()
  fun currentThread():Thread=current!!
  fun reset(){failCreate=false;failStart=false;failInterrupt=false;beforeStart=null;created.clear()}
 }
 var interrupted=0
 init {if(failCreate)error("construction fault");created+=this}
 fun start(){beforeStart?.invoke();if(failStart)error("start fault")}
 fun interrupt(){interrupted++;if(failInterrupt)error("interrupt fault")}
 fun run(){current=this;try{action()}finally{current=null}}
}
'''
checks = '''
fun main(){
 for(factory in listOf<()->Worker>({SchedulerWorker()},{ProfileWorker()})){
  Thread.reset();var owner=factory();var effects=0
  check(owner.launch{effects++});check(owner.count()==1);Thread.created.last().run()
  check(effects==1&&owner.count()==0)
  check(owner.launch{error("body fault")});Thread.created.last().run()
  check(owner.count()==0&&owner.errors()==1)
  Thread.failCreate=true;check(!owner.launch{});check(owner.count()==0);Thread.failCreate=false
  Thread.failStart=true;check(!owner.launch{});check(owner.count()==0);Thread.failStart=false
  check(owner.launch{effects++});val late=Thread.created.last()
  check(owner.launch{effects++});val late2=Thread.created.last()
  Thread.failInterrupt=true;owner.close();owner.close()
  check(late.interrupted==1&&late2.interrupted==1&&owner.count()==0)
  late.run();late2.run();check(effects==1);check(!owner.launch{effects++})
  Thread.reset();owner=factory();Thread.beforeStart={owner.close();Thread.beforeStart=null}
  check(owner.launch{effects++});val reentrant=Thread.created.last();reentrant.run()
  check(reentrant.interrupted==1&&owner.count()==0&&effects==1)
  Thread.reset();val fresh=factory();check(fresh.launch{effects++});Thread.created.last().run()
  check(effects==2&&fresh.count()==0);fresh.close()
 }
 println("PASS production worker helpers: registration, normal/body-fault exit, construction/start rejection, interrupt/logger isolation, close reentry, stale work, new owner")
}
'''
code = fixture + '''
interface Worker {fun launch(f:()->Unit):Boolean;fun close();fun count():Int;fun errors():Int}
'''
for source_name, name in [('RealNameTailScheduler','SchedulerWorker'),('ContactProfilePrefetcher','ProfileWorker')]:
    source=(r/f'app/src/main/java/t/mma/hooks/items/realtail/{source_name}.kt').read_text()
    code += f'class {name}:Worker {{\n' + '''
 private val workers=java.util.Collections.synchronizedSet(HashSet<Thread>())
 @Volatile private var closed=false
 private var failures=0
 private val logger:(String,Throwable?)->Unit={_,_->failures++;error("logger fault")}
 override fun launch(f:()->Unit)=launchOwnedWorker(f)
 override fun close(){if(closed)return;closed=true;stopOwnedWorkers()}
 override fun count()=workers.size
 override fun errors()=failures
''' + method(source,'fun launchOwnedWorker(') + '\n' + method(source,'fun stopOwnedWorkers(') + '\n}\n'
code += checks
with tempfile.TemporaryDirectory(prefix='pl-realtail-workers-') as td:
    p=Path(td);(p/'Check.kt').write_text(code)
    subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'out'),str(p/'Check.kt')],check=True,timeout=90)
    subprocess.run(['java','-cp',str(p/'out')+':'+str(std),'CheckKt'],check=True,timeout=15)
