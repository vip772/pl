"""Notification generation and executor production expressions; no Android notifications."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items'
s=(b/'customnotify/CustomNotificationRuntime.kt').read_text()
a=s.index('    private fun epochActive(');z=s.index('    private val installed',a);methods=s[a:z]
code='''import java.util.concurrent.*
class Context {var enabled=true}
class CustomNotificationSettings(val context:Context){fun isEnabled()=context.enabled}
class Owner {
 private val notificationEpoch=java.util.concurrent.atomic.AtomicLong(0)
'''+methods+'''
 val worker=newIdleExecutor("fixture-notify")
 fun epoch()=notificationEpoch.get()
 fun active(c:Context,e:Long)=epochActive(c,e)
 fun toggle(c:Context,on:Boolean){c.enabled=on;notificationEpoch.incrementAndGet();worker.queue.clear()}
}
fun main(){
 val c=Context();val o=Owner();val first=o.epoch();check(o.active(c,first))
 val started=CountDownLatch(1);val release=CountDownLatch(1);var effects=0;var interrupted=false
 o.worker.execute{started.countDown();try{release.await()}catch(e:InterruptedException){interrupted=true};if(o.active(c,first))effects++}
 check(started.await(2,TimeUnit.SECONDS));o.worker.execute{effects+=100}
 o.toggle(c,false);check(!o.active(c,first));o.toggle(c,true);check(!o.active(c,first)&&o.active(c,o.epoch()))
 release.countDown();val done=CountDownLatch(1);o.worker.execute{done.countDown()};check(done.await(2,TimeUnit.SECONDS))
 check(!interrupted&&effects==0&&o.worker.allowsCoreThreadTimeOut())
 o.worker.setKeepAliveTime(40,TimeUnit.MILLISECONDS);Thread.sleep(200);check(o.worker.poolSize==0)
 val again=CountDownLatch(1);o.worker.execute{again.countDown()};check(again.await(2,TimeUnit.SECONDS));o.worker.shutdown();check(o.worker.awaitTermination(2,TimeUnit.SECONDS))
 println("PASS notification production epoch/worker: old generation rejected after reopen, pending work cleared, active work not interrupted, idle thread exits and recreates")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
assert 'if (bitmap != null && epochActive(context, epoch))' in s
assert 'if (avatarCache.size >= 64)' in s
assert 'if (!epochActive(context, epoch)) return@execute' in s
for rel in ['customnotify/CustomNotificationFeature.kt','atallnotify/AtAllNotificationBlockFeature.kt']:
 f=(b/rel).read_text();assert 'PreferenceRuntimeOwner' in f and 'subscription.unsubscribe()' in f
print('PASS separate subscriber ownership and non-null bounded avatar cache contracts')