"""Production generation and guarded EventBus execution; no host business calls."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/api/core/WechatApiFeature.java').read_text()
a=s.index('    private static final class Generation');b=s.index('    private <T extends AutoCloseable>',a)
code='''import t.mma.event.*;
import t.mma.hooks.core.DexInstallScheduler;
public class GenerationTest {
GENERATION
static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args)throws Exception {
 for(int round=0;round<300;round++){
  Generation g=new Generation();java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
  g.resources.add(()->calls.incrementAndGet());check(g.enter());
  Thread t=new Thread(g::close);t.start();t.join();check(g.closed);check(calls.get()==0);check(!g.enter());
  g.leave();check(calls.get()==1);g.close();check(calls.get()==1);check(g.resources.isEmpty());
 }
 Generation g=new Generation();g.resources.add(()->{throw new Exception();});g.resources.add(()->{});g.close();check(g.resources.isEmpty());
 EventBus bus=EventBus.get();bus.clearAll();Object lock=new Object();java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean(true);
 java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
 bus.subscribe(Events.DexReady.class,e->{check(!Thread.holdsLock(lock));calls.incrementAndGet();active.set(false);bus.removeSticky(e);});
 bus.subscribe(Events.DexReady.class,e->calls.incrementAndGet());
 Events.DexReady old=new Events.DexReady();bus.postGuarded(old,lock,active::get);check(calls.get()==1);
 bus.clearAll();active.set(true);Events.DexReady fresh=new Events.DexReady();bus.postGuarded(fresh,lock,active::get);bus.removeSticky(old);
 bus.subscribe(Events.DexReady.class,e->{check(e==fresh);calls.incrementAndGet();});check(calls.get()==2);
 bus.removeSticky(fresh);bus.subscribe(Events.DexReady.class,e->calls.incrementAndGet());check(calls.get()==2);bus.clearAll();
 System.out.println("PASS production generation: 300 close/in-flight races; cleanup; guarded sticky publication and subscriber reentry");
}
}
'''.replace('GENERATION',s[a:b])
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'GenerationTest.java').write_text(code)
 for name,text in {'t/mma/hooks/core/DexInstallScheduler.java':'package t.mma.hooks.core; public class DexInstallScheduler {public static class Installation {public void close(){}}}', 't/mma/utils/HLog.java':'package t.mma.utils;public class HLog {public static void e(String s,Throwable t){throw new AssertionError(t);}}', 't/mma/event/EventHandler.java':'package t.mma.event; public interface EventHandler<E> {void handleEvent(E e);}', 't/mma/event/Events.java':'package t.mma.event;public class Events {public static class DexReady{}}'}.items():
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 subprocess.run(['javac','-d',td,*map(str,p.rglob('*.java')),str(r/'app/src/main/java/t/mma/event/EventBus.java')],check=True,timeout=30)
 subprocess.run(['java','-cp',td,'GenerationTest'],check=True,timeout=20)
