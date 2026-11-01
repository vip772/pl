"""Compile production delayed notice owner; synthetic tasks only, no host operations."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
fixture="""package t.mma.hooks.core
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
fun main(){
 val owner=PendingNoticeTasks("NoticeTest",2);val entered=CountDownLatch(1);val release=CountDownLatch(1);val done=CountDownLatch(1);val interrupted=AtomicBoolean();val effects=AtomicInteger()
 check(owner.schedule(0){entered.countDown();try{release.await()}catch(e:InterruptedException){interrupted.set(true)};done.countDown()})
 check(entered.await(3,TimeUnit.SECONDS));check(owner.schedule(60000){effects.incrementAndGet()});check(owner.schedule(60000){effects.incrementAndGet()});check(!owner.schedule(0){error("overflow")})
 owner.close();owner.close();check(!owner.schedule(0){error("closed")});release.countDown();check(done.await(3,TimeUnit.SECONDS));check(!interrupted.get()&&effects.get()==0)
 val fresh=PendingNoticeTasks("FreshNotice",1);val next=CountDownLatch(1);check(fresh.schedule(0){next.countDown()});check(next.await(3,TimeUnit.SECONDS));fresh.close()
 repeat(30){
  val raced=PendingNoticeTasks("RacedNotice",8);val errors=java.util.concurrent.ConcurrentLinkedQueue<Throwable>();val threads=(1..4).map{Thread{try{repeat(25){raced.schedule(60000){effects.incrementAndGet()}}}catch(t:Throwable){errors.add(t)}}};threads.forEach{it.start()};raced.close();threads.forEach{it.join()};check(errors.isEmpty());check(!raced.schedule(0){error("late")})
 }
 val recovery=PendingNoticeTasks("RecoverNotice",1);val thrown=CountDownLatch(1);val recovered=CountDownLatch(1);check(recovery.schedule(0){thrown.countDown();error("synthetic")});check(thrown.await(3,TimeUnit.SECONDS));check(recovery.schedule(0){recovered.countDown()});check(recovered.await(3,TimeUnit.SECONDS));recovery.close()
 println("PASS production notice tasks: bounded queue, shutdown cancellation, no in-flight interruption, fresh generation, concurrent close/submit and exception recovery")
}
"""
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(fixture)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/PendingNoticeTasks.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=20)
