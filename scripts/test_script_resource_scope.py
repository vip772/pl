"""Run production async scope and HTTP client cache against real OkHttp on JVM."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/script/ScriptWaBridge.kt').read_text()
a=s.index('    private val baseHttpClient');b=s.index('    private val asyncScope',a)
fields=s[a:b]
a=s.index('    private fun httpClient(timeoutSeconds: Long)');b=s.index('    private fun firstNotBlank',a)
methods=s[a:b]
code='''package t.mma.hooks.items.script
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
class Clients {
'''+fields+methods+'''
 fun get(t:Long)=httpClient(t)
 fun size()=httpClients.size
}
fun main(){
 val queued=mutableListOf<Runnable>();var calls=0;var errors=0
 val scope=ScriptAsyncScope({queued+=it},{errors++})
 scope.execute{calls++};scope.execute{calls++};scope.dispose();queued.forEach{it.run()};check(calls==0)
 scope.execute{calls++};check(queued.size==2)
 val pool=Executors.newSingleThreadExecutor();val entered=CountDownLatch(1);val release=CountDownLatch(1);val done=CountDownLatch(1)
 val active=ScriptAsyncScope({pool.execute(it)},{errors++})
 active.execute{entered.countDown();check(release.await(3,TimeUnit.SECONDS));calls++;done.countDown()}
 check(entered.await(3,TimeUnit.SECONDS));active.execute{error("queued after running must cancel")}
 active.dispose();release.countDown();check(done.await(3,TimeUnit.SECONDS));pool.submit{}.get(3,TimeUnit.SECONDS);pool.shutdownNow();check(calls==1 && errors==0)
 val rejected=ScriptAsyncScope({throw RejectedExecutionException()},{errors++});rejected.execute{error("rejected ran")};check(errors==1);rejected.dispose()
 val direct=ScriptAsyncScope({it.run()},{errors++});direct.execute{error("failure")};direct.dispose();check(errors==2)
 val waiting=mutableListOf<Runnable>();var overflow=0
 val bounded=ScriptAsyncScope({waiting+=it},{overflow++})
 repeat(300){bounded.execute{error("cancelled")}}
 check(waiting.size==256 && overflow==44)
 bounded.dispose();waiting.forEach{it.run()};check(overflow==44)
 val clients=Clients();val first=clients.get(1)
 for(t in 1L..300L){val client=clients.get(t);check(client.connectionPool===first.connectionPool);check(client.dispatcher===first.dispatcher);check(client.readTimeoutMillis==(t*1000).toInt());check(clients.size()<=8)}
 check(clients.get(0).readTimeoutMillis==1000 && clients.get(999).readTimeoutMillis==300000)
 println("PASS production script resources: unload cancels queued closures, active operation finishes, rejection/error cleanup, 8 HTTP configs share dispatcher/pool and preserve timeout")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
cache=Path('/root/.gradle/caches/modules-2/files-2.1')
http=next((cache/'com.squareup.okhttp3/okhttp/4.12.0').rglob('*.jar'))
okio=next((cache/'com.squareup.okio/okio-jvm/3.6.0').rglob('*.jar'))
cp=':'.join(map(str,[std,http,okio]))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/items/script/ScriptAsyncScope.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+cp,'t.mma.hooks.items.script.TestKt'],check=True,timeout=15)