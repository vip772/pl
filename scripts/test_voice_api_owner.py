"""Execute production voice queue and close against a synthetic main Handler."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/api/media/WeChatVoiceApi.java').read_text()
a=s.index('    private volatile boolean closed;');b=s.index('    private static final int LONG_VOICE',a)
c=s.index('    private boolean runOnMainThread(');d=s.index('    public String resolvePath',c)
code='''import java.util.concurrent.*;
import java.lang.reflect.Method;
public class VoiceOwnerTest {
interface BooleanTask {boolean run();}
static class Looper {static Object getMainLooper(){return Thread.currentThread();}static Object myLooper(){return null;}}
static class Handler {boolean reject;java.util.List<Runnable> queue=new java.util.ArrayList<>();synchronized boolean post(Runnable r){if(reject)return false;queue.add(r);return true;}synchronized void removeCallbacks(Runnable r){queue.remove(r);}synchronized void removeCallbacksAndMessages(Object x){queue.clear();}}
final Handler MAIN_HANDLER=new Handler();final Object playbackLock=new Object();long playbackGeneration;Object playbackPlayer;Object playbackListener;Method voiceInfoValuesMethod;
void stopPlaybackPlayer(Object p){} void log(String s){}
PRODUCTION
static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args)throws Exception {
 for(int i=0;i<100;i++){
 VoiceOwnerTest o=new VoiceOwnerTest();java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
 Thread t=new Thread(()->check(!o.runOnMainThread(()->{calls.incrementAndGet();return true;})));t.start();
 long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(true){synchronized(o.pendingMainTasks){if(!o.pendingMainTasks.isEmpty())break;}if(System.nanoTime()>until)throw new AssertionError();Thread.yield();}
 o.close();t.join(2000);check(!t.isAlive());check(calls.get()==0);check(o.pendingMainTasks.isEmpty());check(o.MAIN_HANDLER.queue.isEmpty());check(!o.runOnMainThread(()->true));o.close();
 }
 VoiceOwnerTest o=new VoiceOwnerTest();o.MAIN_HANDLER.reject=true;check(!o.runOnMainThread(()->true));check(o.pendingMainTasks.isEmpty());
 VoiceOwnerTest x=new VoiceOwnerTest();CountDownLatch entered=new CountDownLatch(1),finish=new CountDownLatch(1);
 MainTaskHandle.run(x,entered,finish);
 System.out.println("PASS voice production task ownership: 100 close/queue races; reject cleanup; admitted task survives close");
}
static class MainTaskHandle {static void run(VoiceOwnerTest x,CountDownLatch entered,CountDownLatch finish)throws Exception {
 Thread caller=new Thread(()->check(x.runOnMainThread(()->{entered.countDown();try{check(finish.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}return true;})));caller.start();
 Runnable task=null;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(task==null){synchronized(x.MAIN_HANDLER){if(!x.MAIN_HANDLER.queue.isEmpty())task=x.MAIN_HANDLER.queue.remove(0);}if(System.nanoTime()>until)throw new AssertionError();}
 Thread host=new Thread(task);host.start();check(entered.await(2,TimeUnit.SECONDS));x.close();finish.countDown();host.join(2000);caller.join(2000);check(!caller.isAlive()&&!host.isAlive());
}}
}
'''.replace('PRODUCTION',s[a:b]+s[c:d]).replace('    @Override public void close()', '    public void close()')
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'VoiceOwnerTest.java').write_text(code)
 subprocess.run(['javac','-d',td,str(p/'VoiceOwnerTest.java')],check=True,timeout=30)
 subprocess.run(['java','-cp',td,'VoiceOwnerTest'],check=True,timeout=30)
