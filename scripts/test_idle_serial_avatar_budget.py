"""Production idle executor and custom-avatar cache budget, no Android network or UI."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/customfriendavatar/CustomFriendAvatarStore.kt').read_text()
a=s.index('    private data class CacheEntry');z=s.index('    private val preloader',a);cache=s[a:z]
code='''package t.mma.hooks.core
import java.util.concurrent.*
class Bitmap(val allocationByteCount:Int)
class Cache {
'''+cache+'''
 fun put(id:String,bytes:Int){cacheEntry(id,CacheEntry(0,0,Bitmap(bytes)))}
 fun has(id:String)=cache.containsKey(id)
 fun bytes()=synchronized(cache){cache.values.sumOf{it.bitmap.allocationByteCount.toLong()}}
 fun count()=cache.size
}
fun main(){
 val c=Cache();repeat(65){c.put("k$it",4)};check(c.count()==64&&!c.has("k0"))
 c.put("large",5*1024*1024);c.put("second",5*1024*1024);check(!c.has("large")&&c.bytes()<=8*1024*1024)
 c.put("oversize",9*1024*1024);check(!c.has("oversize"));c.put("second",4);check(c.bytes()<1024)
 val pool=IdleSerialExecutor.create("fixture-idle");pool.setKeepAliveTime(40,TimeUnit.MILLISECONDS)
 val result=java.util.Collections.synchronizedList(mutableListOf<Int>())
 val done=CountDownLatch(20);repeat(20){i->pool.execute{result.add(i);done.countDown()}}
 check(done.await(2,TimeUnit.SECONDS));check(result==(0..19).toList());Thread.sleep(200);check(pool.poolSize==0)
 val reopened=CountDownLatch(1);pool.execute{reopened.countDown()};check(reopened.await(2,TimeUnit.SECONDS));pool.shutdown();check(pool.awaitTermination(2,TimeUnit.SECONDS))
 println("PASS production idle executor serial ordering/exit/reopen and avatar count/byte/oversize budgets")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/IdleSerialExecutor.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)