"""Extract production Java installation/rollback/close; synthetic hook failures only."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/protobuf/ProtobufPacketHook.java').read_text()
install=s[s.index('    public synchronized boolean install()'):s.index('    public boolean send(')]
feature=(r/'app/src/main/java/t/mma/hooks/items/protobuf/ProtobufPacketFeature.kt').read_text()
assert feature.count('ProtobufPacketHook(')==1
assert feature.index('trackResource {') < feature.index('fun schedule()')
assert 'ticket.close()' in feature and 'ProtobufPacketRuntime.detach(hook)' in feature
assert 'captureExecutor.allowCoreThreadTimeOut(true)' in s
assert 'if (closed || !installed) return;' in s
code="""import java.util.*;import java.lang.reflect.*;import java.util.concurrent.*;
class XC_MethodHook {static class MethodHookParam{};protected void beforeHookedMethod(MethodHookParam p){};static class Unhook{boolean active=true;}}
class HookRegistry {static HookRegistry value=new HookRegistry();static HookRegistry get(){return value;}int attempts;int failAt;boolean failRemove;List<XC_MethodHook.Unhook> all=new ArrayList<>();XC_MethodHook.Unhook hook(Method m,XC_MethodHook h){attempts++;if(attempts==failAt)throw new RuntimeException("install");XC_MethodHook.Unhook u=new XC_MethodHook.Unhook();all.add(u);return u;}void unhook(XC_MethodHook.Unhook h){if(failRemove)throw new RuntimeException("remove");h.active=false;}long active(){return all.stream().filter(x->x.active).count();}}
class KavaReflector{static Method[] declaredMethods(Class<?> c){return c.getDeclaredMethods();}}
class HostA{void dispatch(Object a,Object b,Object c){}}
class HostB{void dispatch(Object a,Object b,Object c){}}
class Owner{boolean closed;boolean installed;List<XC_MethodHook.Unhook> handles=new ArrayList<>();ThreadPoolExecutor captureExecutor=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64));Map<String,Object> snapshots=new HashMap<>();Map<String,Long> recentLogs=new HashMap<>();List<Class<?>> collectBaseClasses(){return Arrays.asList(HostA.class,HostB.class);}void handleDispatch(XC_MethodHook.MethodHookParam p){};void error(String s){};
"""+install+"""}
public class Test{static void check(boolean b){if(!b)throw new AssertionError();}public static void main(String[] args)throws Exception{
 HookRegistry h=HookRegistry.get();Owner first=new Owner();h.failAt=2;check(!first.install());check(first.handles.isEmpty()&&h.active()==0&&!first.installed);h.failAt=0;check(first.install());check(h.active()==2);int attempts=h.attempts;check(first.install()&&h.attempts==attempts);
 CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1), done=new CountDownLatch(1);java.util.concurrent.atomic.AtomicBoolean interrupted=new java.util.concurrent.atomic.AtomicBoolean();first.captureExecutor.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){interrupted.set(true);}done.countDown();});check(entered.await(3,TimeUnit.SECONDS));first.captureExecutor.execute(()->{throw new AssertionError("queued task ran");});first.snapshots.put("synthetic",new Object());first.close();first.close();check(h.active()==0&&first.captureExecutor.isShutdown()&&first.captureExecutor.getQueue().isEmpty()&&first.snapshots.isEmpty()&&!first.install());release.countDown();check(done.await(3,TimeUnit.SECONDS)&&!interrupted.get());
 Owner second=new Owner();h.failAt=h.attempts+2;h.failRemove=true;check(!second.install());check(second.handles.size()==1&&h.active()==1);attempts=h.attempts;check(!second.install()&&attempts==h.attempts);h.failRemove=false;h.failAt=0;check(second.install());check(h.active()==2);second.close();check(h.active()==0);
 Owner third=new Owner();check(third.install());h.failRemove=true;third.close();check(!third.handles.isEmpty()&&third.captureExecutor.isShutdown());h.failRemove=false;third.close();check(third.handles.isEmpty()&&h.active()==0);
 System.out.println("PASS production Protobuf rollback/close: partial install cleanup, retained failed unhook blocks duplicate retry, idempotence, retry recovery, queued cancellation and in-flight no interruption");}}
"""
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.java').write_text(code)
 subprocess.run(['javac','-d',td,str(p/'Test.java')],check=True,timeout=30)
 subprocess.run(['java','-cp',td,'Test'],check=True,timeout=15)
