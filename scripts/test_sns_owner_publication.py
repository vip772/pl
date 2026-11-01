"""Execute production SNS owned initialization during close."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/api/sns/WeChatSnsApi.kt').read_text();a=s.index('    private fun <T : Any> ownedValue(');b=s.index('    private val interactionLocator:',a)
code='''import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class Owner {val lifecycleLock=Any();var closed=false;var value:Any?=null
METHOD
fun get(create:()->Any?,dispose:(Any)->Unit):Any?=ownedValue({value},{value=it},create,dispose)
fun close(){synchronized(lifecycleLock){closed=true;value=null}}
}
fun main(){
 repeat(200){
 val o=Owner();val entered=CountDownLatch(1);val finish=CountDownLatch(1);val disposed=java.util.concurrent.atomic.AtomicInteger();val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>()
 val t=Thread{try{check(o.get({entered.countDown();check(finish.await(3,TimeUnit.SECONDS));Any()},{disposed.incrementAndGet()})==null)}catch(e:Throwable){failure.set(e)}}
 t.start();check(entered.await(3,TimeUnit.SECONDS));o.close();finish.countDown();t.join(3000);check(!t.isAlive);check(failure.get()==null);check(o.value==null);check(disposed.get()==1)
 check(o.get({error("closed initializer")},{})==null)
 }
 val o=Owner();val first=Any();check(o.get({first},{})===first);check(o.get({error("duplicate init")},{})===first);o.close()
 println("PASS SNS production ownedValue: 200 late-construction/close races; loser disposal; closed admission")
}
'''.replace('METHOD',s[a:b]);libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=30)
