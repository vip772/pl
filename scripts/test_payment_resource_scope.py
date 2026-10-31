"""Compile production payment scope against deterministic Handler/Hook stubs; no business calls."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
source=r/'app/src/main/java/t/mma/hooks/items/payment/core/PaymentResourceScope.java'
fixtures={
'android/os/Looper.java':'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'''package android.os; import java.util.*; public class Handler {
 public static List<Handler> all=new ArrayList<>(); public List<Runnable> queue=new ArrayList<>();
 public Handler(Looper l){all.add(this);} public boolean postDelayed(Runnable r,long d){queue.add(r);return true;}
 public void removeCallbacks(Runnable r){queue.remove(r);} public void removeCallbacksAndMessages(Object o){queue.clear();}
 public static void drain(){for(Handler h:new ArrayList<>(all)){for(Runnable r:new ArrayList<>(h.queue)){h.queue.remove(r);r.run();}}}
}''',
'de/robv/android/xposed/XC_MethodHook.java':'''package de.robv.android.xposed; public class XC_MethodHook {public static class Unhook{}}''',
'de/robv/android/xposed/XposedBridge.java':'package de.robv.android.xposed; public class XposedBridge{public static void log(Throwable t){}}',
't/mma/hooks/core/HookRegistry.java':'''package t.mma.hooks.core; import de.robv.android.xposed.XC_MethodHook;import java.lang.reflect.Member;
public class HookRegistry {static HookRegistry i=new HookRegistry();public static int installed,removed;
public static HookRegistry get(){return i;} public XC_MethodHook.Unhook hook(Member m,XC_MethodHook c){installed++;return new XC_MethodHook.Unhook();}
public void unhook(XC_MethodHook.Unhook h){removed++;}}''',
'Test.java':'''import t.mma.hooks.items.payment.core.PaymentResourceScope; import android.os.Handler;
import de.robv.android.xposed.XC_MethodHook; import t.mma.hooks.core.HookRegistry;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class Test {
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  PaymentResourceScope p=new PaymentResourceScope();AtomicInteger count=new AtomicInteger();
  p.post("one",1,()->count.addAndGet(1));p.post("one",1,()->count.addAndGet(2));Handler.drain();check(count.get()==2);
  p.post("cancel",1,()->count.addAndGet(100));p.cancel("cancel");Handler.drain();check(count.get()==2);
  p.hook(Test.class.getMethod("main",String[].class),new XC_MethodHook());
  p.hook(Test.class.getMethod("main",String[].class),new XC_MethodHook());check(HookRegistry.installed==1);
  StringBuilder order=new StringBuilder();p.register(()->order.append("a"));p.register(()->{throw new RuntimeException();});p.register(()->order.append("b"));
  p.post("pending",100,()->count.addAndGet(1000));Runnable stale=Handler.all.get(0).queue.get(0);
  p.close();p.close();stale.run();Handler.drain();check(count.get()==2 && order.toString().equals("ba") && HookRegistry.removed==1);
  p.register(()->order.append("c"));p.post("late",0,()->count.incrementAndGet());Handler.drain();check(order.toString().equals("bac") && count.get()==2);
  PaymentResourceScope next=new PaymentResourceScope();CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean interrupted=new AtomicBoolean();
  next.post("active",0,()->{started.countDown();try{release.await();}catch(InterruptedException e){interrupted.set(true);}count.incrementAndGet();});
  Thread runner=new Thread(Handler::drain);runner.start();check(started.await(2,TimeUnit.SECONDS));next.close();release.countDown();runner.join(2000);check(!runner.isAlive()&&!interrupted.get()&&count.get()==3);
  PaymentResourceScope reopened=new PaymentResourceScope();reopened.post("new",0,()->count.incrementAndGet());Handler.drain();reopened.close();check(count.get()==4);
  System.out.println("PASS production PaymentResourceScope: replacement, cancellation, exact hook ownership, reverse exception-safe cleanup, stale rejection, uninterrupted active work, reopen");
 }
}'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,text in fixtures.items():
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 subprocess.run(['javac','-d',str(p/'classes'),str(source),*map(str,p.rglob('*.java'))],check=True,timeout=60)
 subprocess.run(['java','-cp',str(p/'classes'),'Test'],check=True,timeout=15)
b=r/'app/src/main/java/t/mma/hooks/items/payment'
for rel in ['detect/RedPacketMessageHook.java','detect/RedPacketDatabaseHook.java','grab/RedPacketSilentHandler.java','grab/RedPacketUiAutomator.java','fake/RedPacketFakePacketHook.java']:
 s=(b/rel).read_text();assert 'resources.close()' in s and 'resources.hook(' in s and 'resources.isClosed()' in s,rel
 assert 'HookRegistry.get().hook(' not in s,rel
transfer=(b/'transfer/AutoTransferFeature.kt').read_text()
assert 'postAtTime(action, token,' in transfer and 'if (!isCurrent(token)) return@query' in transfer
assert 'removeCallbacksAndMessages(token)' in transfer
print('PASS payment consumer ownership and transfer generation source contracts')