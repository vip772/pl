"""Production detection close with synthetic state; never probes or deletes friends."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/zombiecheck/ZombieCheckRuntime.kt').read_text()
a=s.index('    fun close()');z=s.index('    private fun scheduleDispatch',a);close=s[a:z]
code='''import java.util.concurrent.*
class Owner {
 val lock=Any();var closed=false;var ready=true;val stopSignal=CountDownLatch(1)
 var running=true;var deleting=true;var actionGeneration=0L;var hooker:Any?=Any()
 data class Item(val wxid:String)
 data class Probe(val item:Item,val timeout:ScheduledFuture<*>?)
 val worker=ScheduledThreadPoolExecutor(1);val actionWorker=ScheduledThreadPoolExecutor(1)
 val queue=java.util.ArrayDeque<Item>();val pendingResults=linkedMapOf<String,Item>()
 var activeProbe:Probe?=null;var saved=listOf<String>();var releases=0;var notifications=0
 fun persistLocked(){saved=queue.map{it.wxid}}
 fun releaseWakeLock(){releases++};fun cancelNotification(){notifications++}
'''+close+'''}
fun main(){
 val o=Owner();o.queue.add(Owner.Item("queued"));o.pendingResults["result"]=Owner.Item("result")
 o.activeProbe=Owner.Probe(Owner.Item("active"),o.worker.schedule({},60,TimeUnit.SECONDS))
 val started=CountDownLatch(1);val release=CountDownLatch(1);var interrupted=false;var effects=0
 o.actionWorker.execute{started.countDown();try{release.await()}catch(e:InterruptedException){interrupted=true};effects++}
 check(started.await(2,TimeUnit.SECONDS));o.actionWorker.execute{effects+=100}
 o.close();o.close();release.countDown()
 check(o.worker.awaitTermination(2,TimeUnit.SECONDS));check(o.actionWorker.awaitTermination(2,TimeUnit.SECONDS))
 check(o.saved.toSet()==setOf("active","result","queued"));check(o.pendingResults.isEmpty()&&o.activeProbe==null)
 check(o.closed&&!o.ready&&!o.running&&!o.deleting&&o.hooker==null&&o.stopSignal.count==0L)
 check(o.releases==1&&o.notifications==1&&o.actionGeneration==1L&&!interrupted&&effects==1)
 println("PASS production detection close: pending/result/probe preserved, callbacks drained, active job uninterrupted, repeated close idempotent")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)