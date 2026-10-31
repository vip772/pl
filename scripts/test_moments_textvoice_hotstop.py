"""Extract production executors; synthetic blocking jobs only, never host operations."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items'
parts=[]
for name in ['Like','Comment','Forward']:
 s=(b/f'moments/MomentsAuto{name}Feature.kt').read_text()
 assert 'PreferenceRuntimeOwner' in s and 'if (destroyed) return' in s
 assert 'worker.shutdownNow()' not in s and '.cancel(true)' not in s
 assert 'worker.queue.clear()' in s and 'worker.shutdown()' in s
 a=s.index('    private val worker =');z=s.index('    private val ',a+len('    private val worker ='))
 executor=s[a:z]
 parts.append('class '+name+' {\n'+executor+'''\n fun run(job:()->Unit)=worker.execute(job)
 fun later(job:()->Unit)=worker.schedule(job,60,TimeUnit.SECONDS)
 fun close(){worker.queue.clear();worker.shutdown()}
 fun await()=worker.awaitTermination(3,TimeUnit.SECONDS)
}\n''')
s=(b/'textvoice/TextVoiceRuntime.kt').read_text()
assert 'executor.shutdownNow()' not in s
assert 'hookResources.register { HookRegistry.get().unhook(handle) }' in s
assert 'if (!active.get()) return false' in s
assert 'executor.queue.clear()' in s
f=(b/'textvoice/TextVoiceFeature.kt').read_text()
assert 'PreferenceRuntimeOwner' in f and 'subscription.unsubscribe()' in f
refresh=(b/'moments/MomentsAutoRefreshFeature.kt').read_text()
assert 'scheduler?.shutdownNow()' not in refresh
assert 'if (destroyed)' in refresh and 'ticket.close()' in refresh
code='import java.util.concurrent.*\n'+''.join(parts)+'\nfun main(){\n'
for name in ['Like','Comment','Forward']:
 code+='''run {
 val o='''+name+'''();val started=CountDownLatch(1);val release=CountDownLatch(1)
 val count=java.util.concurrent.atomic.AtomicInteger();var interrupted=false
 o.run{started.countDown();try{release.await()}catch(e:InterruptedException){interrupted=true};count.incrementAndGet()}
 check(started.await(3,TimeUnit.SECONDS));o.later{count.addAndGet(100)};o.run{count.addAndGet(1000)}
 o.close();o.close();release.countDown();check(o.await());check(!interrupted && count.get()==1)
 var rejected=false;try{o.run{}}catch(e:RejectedExecutionException){rejected=true};check(rejected)
 val next='''+name+'''();val done=CountDownLatch(1);next.run{done.countDown()};check(done.await(3,TimeUnit.SECONDS));next.close();check(next.await())
}
'''
code+='println("PASS extracted SNS production pools: pending cancellation, running job not interrupted, late rejection, reopen; textvoice/refresh source guards")\n}'
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)