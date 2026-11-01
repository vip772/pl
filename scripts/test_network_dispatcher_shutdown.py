"""Compile the complete production dispatcher with reflection-only stubs; no host/network calls."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
fixtures={
'android/content/Context.java':'package android.content; public class Context {}',
'android/app/Activity.java':'package android.app; public class Activity extends android.content.Context {}',
'android/os/SystemClock.java':'package android.os; public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000;}}',
'de/robv/android/xposed/XC_MethodHook.java':'''package de.robv.android.xposed;
public class XC_MethodHook {public static class Unhook {}
 public static class MethodHookParam {public Object thisObject;public Object[] args;public boolean hasThrowable(){return false;}public Throwable getThrowable(){return null;}}
 protected void beforeHookedMethod(MethodHookParam p) throws Throwable {}
 protected void afterHookedMethod(MethodHookParam p) throws Throwable {} }''',
'de/robv/android/xposed/XposedBridge.java':'package de.robv.android.xposed; public class XposedBridge {public static void log(Throwable t){}}',
 't/mma/hooks/core/HookRegistry.java':'''package t.mma.hooks.core; import de.robv.android.xposed.XC_MethodHook;
 public class HookRegistry {private static final HookRegistry INSTANCE=new HookRegistry();public static HookRegistry get(){return INSTANCE;}
 public XC_MethodHook.Unhook hook(java.lang.reflect.Member m,XC_MethodHook h){return new XC_MethodHook.Unhook();}
 public void unhook(XC_MethodHook.Unhook h){} }''',
 't/mma/utils/HLog.java':'''package t.mma.utils; public class HLog {public static void i(String s){} public static void w(String s){} public static void e(String s,Throwable t){} }''',
 't/mma/utils/KavaReflector.java':'''package t.mma.utils;import java.lang.reflect.*;
 public class KavaReflector {
 public static Method[] declaredMethods(Class<?> c){return c.getDeclaredMethods();}
 public static Field[] declaredFields(Class<?> c){return c.getDeclaredFields();}
 public static boolean isStatic(Member m){return Modifier.isStatic(m.getModifiers());}
 public static Object readField(Field f,Object o){try{f.setAccessible(true);return f.get(o);}catch(Exception e){return null;}}
 public static Method findMethodRecursive(Class<?> c,String n){try{return c.getMethod(n);}catch(Exception e){return null;}}
 public static Object invokeOrThrow(Method m,Object o,Object...a)throws Throwable {m.setAccessible(true);return m.invoke(o,a);}
 public static Object invoke(Method m,Object o,Object...a){try{return invokeOrThrow(m,o,a);}catch(Throwable e){return null;}}
 public static Object invokeMethod(Object o,String n,Object...a){try{return invoke(o.getClass().getMethod(n),o,a);}catch(Exception e){return null;}}
 }''',
'DispatcherShutdownTest.java':'''import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import t.mma.hooks.api.net.WeChatNetworkDispatcher;
public class DispatcherShutdownTest {
 static class Request {public int getType(){return 1;}}
 public static class Queue {final AtomicInteger calls=new AtomicInteger();volatile CountDownLatch entered,release;volatile boolean fail;volatile boolean interrupted;
  public boolean g(Request r)throws Exception {calls.incrementAndGet();if(entered!=null){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError();}catch(InterruptedException e){interrupted=true;throw e;}}if(fail)throw new IllegalStateException("entered host error");return true;}
 }
 static void check(boolean v){if(!v)throw new AssertionError();}
 static Object get(Object o,String n)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static WeChatNetworkDispatcher ready(Queue q)throws Exception {WeChatNetworkDispatcher d=new WeChatNetworkDispatcher(s->{});Method m=d.getClass().getDeclaredMethod("cacheDispatcher",Object.class,Method.class,String.class);m.setAccessible(true);m.invoke(d,q,Queue.class.getMethod("g",Request.class),"fixture");return d;}
 public static void main(String[] a)throws Exception {
  Queue q=new Queue();WeChatNetworkDispatcher d=ready(q);check(d.send(new Request()));check(q.calls.get()==1);
  List<Request> retained=new ArrayList<>();for(int i=0;i<1000;i++){Request r=new Request();retained.add(r);check(d.send(r));}check(((Map<?,?>)get(d,"moduleRequests")).size()<=256);
  d.destroy();d.destroy();check(!d.isReady()&&!d.send(new Request()));check(((Map<?,?>)get(d,"moduleRequests")).isEmpty());check(get(d,"dispatcherInstance")==null);
  Queue active=new Queue();active.entered=new CountDownLatch(1);active.release=new CountDownLatch(1);WeChatNetworkDispatcher inFlight=ready(active);AtomicBoolean result=new AtomicBoolean();
  Thread t=new Thread(()->result.set(inFlight.send(new Request())));t.start();check(active.entered.await(5,TimeUnit.SECONDS));
  Thread closer=new Thread(inFlight::destroy);closer.start();closer.join(2000);check(!closer.isAlive());check(((Map<?,?>)get(inFlight,"moduleRequests")).isEmpty());check(!inFlight.send(new Request()));
  active.release.countDown();t.join(5000);check(!t.isAlive()&&result.get()&&!active.interrupted&&active.calls.get()==1);
  check(((Map<?,?>)get(inFlight,"moduleRequests")).isEmpty());
  Queue bad=new Queue();bad.fail=true;WeChatNetworkDispatcher failures=ready(bad);check(!failures.send(new Request()));check(bad.calls.get()==1&&((Map<?,?>)get(failures,"moduleRequests")).isEmpty());failures.destroy();
  for(int round=0;round<100;round++){Queue concurrent=new Queue();WeChatNetworkDispatcher owner=ready(concurrent);CountDownLatch start=new CountDownLatch(1);Thread sender=new Thread(()->{try{start.await();}catch(Exception e){throw new RuntimeException(e);}owner.send(new Request());});Thread close=new Thread(()->{try{start.await();}catch(Exception e){throw new RuntimeException(e);}owner.destroy();});sender.start();close.start();start.countDown();sender.join(5000);close.join(5000);check(!sender.isAlive()&&!close.isAlive());check(concurrent.calls.get()<=1&&((Map<?,?>)get(owner,"moduleRequests")).isEmpty());}
  check(ready(new Queue()).send(new Request()));
  System.out.println("PASS complete production dispatcher: bounded trace ownership, 100 send/close races, entered invocation not interrupted, destroy nonblocking, no retry, late admission rejected, fresh owner");
 }
}'''
}
with tempfile.TemporaryDirectory() as td:
 p=Path(td);files=[]
 for name,text in fixtures.items():
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text);files.append(str(f))
 source=r/'app/src/main/java/t/mma/hooks/api/net/WeChatNetworkDispatcher.java'
 subprocess.run(['javac','-encoding','UTF-8','-d',td,*files,str(source)],check=True,timeout=60)
 subprocess.run(['java','-cp',td,'DispatcherShutdownTest'],check=True,timeout=45)