"""Compile complete production MessageApi; exercise owned workers only, no send/network/host call."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
cp=[r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes',r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
cp+=list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))+list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar'))+list(Path('/tmp/pl-kotlin-libs').glob('*.jar'))
test='''import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import t.mma.hooks.api.message.WeChatMessageApi;
public class MessageOwnerTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static Object get(Object o,String n)throws Exception {Field f=WeChatMessageApi.class.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static WeChatMessageApi owner(){return new WeChatMessageApi(null,null,null,null,null,null,null);}
 static Method schedule;
 static void schedule(WeChatMessageApi o,String key)throws Exception {schedule.invoke(o,"fixture",key,null,null);}
 static void await(CountDownLatch latch){try{check(latch.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError("Interrupted entered work",e);}}
 public static void main(String[] args)throws Exception {
  schedule=WeChatMessageApi.class.getDeclaredMethod("scheduleLocalSendFallback",String.class,String.class,Class.forName("t.mma.event.EventBus"),Class.forName("t.mma.event.Events$MessageReceived"));schedule.setAccessible(true);
  WeChatMessageApi a=owner(),b=owner();
  ThreadPoolExecutor thumbs=(ThreadPoolExecutor)get(a,"THUMB_PREFETCH_EXECUTOR");
  ThreadPoolExecutor sends=(ThreadPoolExecutor)get(a,"textSendExecutor");
  ScheduledThreadPoolExecutor fallback=(ScheduledThreadPoolExecutor)get(a,"LOCAL_SEND_FALLBACK_EXECUTOR");
  check(thumbs.getMaximumPoolSize()==2&&thumbs.getQueue().remainingCapacity()==16);
  check(sends.getMaximumPoolSize()==1&&sends.getQueue().remainingCapacity()==32);
  check(thumbs.allowsCoreThreadTimeOut()&&sends.allowsCoreThreadTimeOut()&&fallback.getRemoveOnCancelPolicy());
  CountDownLatch entered=new CountDownLatch(4),release=new CountDownLatch(1);
  Runnable block=()->{entered.countDown();await(release);};
  thumbs.execute(block);thumbs.execute(block);sends.execute(block);fallback.execute(block);
  check(entered.await(5,TimeUnit.SECONDS));AtomicInteger callbacks=new AtomicInteger(),failures=new AtomicInteger();
  for(int i=0;i<32;i++)a.sendTextAsync("not-sent","fixture",(ok,error)->{check(!ok&&error!=null);callbacks.incrementAndGet();});
  a.sendTextAsync("not-sent","overflow",(ok,error)->{check(!ok&&error instanceof RejectedExecutionException);failures.incrementAndGet();});
  check(failures.get()==1&&sends.getQueue().size()==32);
  for(int i=0;i<16;i++)thumbs.execute(()->{throw new AssertionError("queued thumbnail must not run");});
  ((Map<String,byte[]>)get(a,"THUMB_DATA_CACHE")).put("fixture",new byte[16]);
  ((Set<String>)get(a,"THUMB_PREFETCHING")).add("fixture");
  for(int i=0;i<300;i++)schedule(a,"old-"+i);
  check((int)get(a,"pendingFallbackCount")==256);schedule(b,"fresh");
  check((int)get(b,"pendingFallbackCount")==1);
  check(WeChatMessageApi.cancelPendingLocalSend("fixture","old-0"));check((int)get(a,"pendingFallbackCount")==255);
  a.close();a.close();check(callbacks.get()==32);
  check(thumbs.isShutdown()&&sends.isShutdown()&&fallback.isShutdown());
  check(thumbs.getQueue().isEmpty()&&sends.getQueue().isEmpty()&&fallback.getQueue().isEmpty());
  check(((Map<?,?>)get(a,"THUMB_DATA_CACHE")).isEmpty()&&((Set<?>)get(a,"THUMB_PREFETCHING")).isEmpty());
  check((int)get(a,"pendingFallbackCount")==0&&(int)get(b,"pendingFallbackCount")==1);
  schedule(a,"late");check((int)get(a,"pendingFallbackCount")==0);
  a.sendTextAsync("not-sent","late",(ok,error)->{check(!ok&&error!=null);callbacks.incrementAndGet();});check(callbacks.get()==33);
  check(!a.isAvailable()&&!a.sendText("not-sent","late")&&!a.canSendXml());
  release.countDown();check(thumbs.awaitTermination(5,TimeUnit.SECONDS)&&sends.awaitTermination(5,TimeUnit.SECONDS)&&fallback.awaitTermination(5,TimeUnit.SECONDS));
  check(WeChatMessageApi.cancelPendingLocalSend("fixture","fresh"));b.close();
  check(((Map<?,?>)get(a,"PENDING_LOCAL_SENDS")).isEmpty());
  for(int round=0;round<30;round++){
   WeChatMessageApi o=owner();CountDownLatch start=new CountDownLatch(1);AtomicInteger delivered=new AtomicInteger();
   Thread submit=new Thread(()->{await(start);for(int i=0;i<50;i++)o.sendTextAsync(null,null,(ok,error)->delivered.incrementAndGet());});
   Thread close=new Thread(()->{await(start);o.close();});submit.start();close.start();start.countDown();submit.join(5000);close.join(5000);
   check(!submit.isAlive()&&!close.isAlive());check(((ThreadPoolExecutor)get(o,"textSendExecutor")).awaitTermination(5,TimeUnit.SECONDS));check(delivered.get()==50);
  }
  System.out.println("PASS complete production MessageApi: owner-isolated fallback, 256 cap, 2+16 thumbnail and 1+32 async queues, 30 close/submit races, 1500 callbacks exactly once, no interruption of entered work, cleared caches and rejected late work");
 }
}'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'MessageOwnerTest.java').write_text(test)
 stub=p/'android/text/TextUtils.java';stub.parent.mkdir(parents=True);stub.write_text('package android.text;public class TextUtils {public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}}')
 classpath=':'.join(map(str,cp))
 source=r/'app/src/main/java/t/mma/hooks/api/message/WeChatMessageApi.java'
 subprocess.run(['javac','-encoding','UTF-8','-cp',classpath,'-d',td,str(stub),str(source),str(r/'app/src/main/java/t/mma/event/EventBus.java'),str(p/'MessageOwnerTest.java')],check=True,timeout=90)
 subprocess.run(['java','-cp',td+':'+classpath,'MessageOwnerTest'],check=True,timeout=90)