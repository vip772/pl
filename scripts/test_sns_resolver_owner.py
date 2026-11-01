"""Execute extracted production SNS resolver MainCall/close/invokeOnMain; no host calls."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/api/sns/SnsForwardContentResolver.kt').read_text()
a=s.index('    private val lifecycleLock = Any()');b=s.index('    private val mainHandler',a)
c=s.index('    private fun invokeOnMain(');d=s.index('    private fun accepts(',c)
code="""import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
object Looper {fun myLooper():Any?=null;fun getMainLooper():Any=Unit}
class Handler {var reject=false;val queue=java.util.concurrent.ConcurrentLinkedQueue<Runnable>();fun post(r:Runnable):Boolean {if(reject)return false;queue.add(r);return true};fun removeCallbacks(r:Runnable){queue.remove(r)}}
class Pool {var evictions=0;fun evictAll(){evictions++}}
class Client {val connectionPool=Pool()}
class Owner:AutoCloseable {val mainHandler=Handler();val client=Client();val NATIVE_INVOKE_TIMEOUT_MS=5000L
PRODUCTION
fun invoke(action:()->Boolean)=invokeOnMain(action)
fun count()=synchronized(lifecycleLock){pendingMain.size}
}
fun checkJoin(t:Thread){t.join(6000);check(!t.isAlive)}
fun main(){
repeat(100){
 val o=Owner();val result=AtomicBoolean(true);val called=AtomicBoolean(false)
 val t=Thread{result.set(o.invoke{called.set(true);true})};t.start()
 val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3)
 while(o.mainHandler.queue.isEmpty()){check(System.nanoTime()<deadline);Thread.yield()}
 o.close();checkJoin(t);check(!result.get()&&!called.get());check(o.count()==0&&o.mainHandler.queue.isEmpty());o.close();check(o.client.connectionPool.evictions==1);check(!o.invoke{error("late")})
}
val rejected=Owner();rejected.mainHandler.reject=true;check(!rejected.invoke{true});check(rejected.count()==0);rejected.close()
val o=Owner();val entered=CountDownLatch(1);val release=CountDownLatch(1);val result=AtomicBoolean(false)
val caller=Thread{result.set(o.invoke{entered.countDown();check(release.await(3,TimeUnit.SECONDS));true})};caller.start()
val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(o.mainHandler.queue.isEmpty()){check(System.nanoTime()<deadline);Thread.yield()}
val work=o.mainHandler.queue.poll();val host=Thread(work);host.start();check(entered.await(3,TimeUnit.SECONDS));o.close();release.countDown();checkJoin(host);checkJoin(caller);check(result.get())
println("PASS resolver production MainCall: 100 queue/close races, rejected post cleanup, no late action, pool eviction once, entered work not interrupted")
}
""".replace('PRODUCTION',s[a:b]+s[c:d])
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=30)
