"""Run production Mars lifecycle methods with synthetic hooks."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/api/media/WeChatImageApi.java').read_text()
def block(needle):
 a=s.index(needle);j=s.index('{',a)+1;depth=1
 while depth:
  depth+=(s[j]=='{')-(s[j]=='}');j+=1
 return s[a:j]
a=s.index('    private static final ConcurrentMap<Class<?>, Boolean>');b=s.index('    public WeChatImageApi(',a)
code='''import java.util.*;import java.util.concurrent.*;
public class MarsTest {
interface Logger{void log(String s);}
static class XC_MethodHook {static class MethodHookParam {Object thisObject;}protected void afterHookedMethod(MethodHookParam p){}protected void beforeHookedMethod(MethodHookParam p){}static class Unhook{int calls;void unhook(){calls++;}}}
static class XposedBridge {static boolean fail;static XC_MethodHook callback;static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> c,XC_MethodHook h){callback=h;return Set.of(new XC_MethodHook.Unhook());}static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> c,String n,XC_MethodHook h){if(fail)throw new RuntimeException();return Set.of(new XC_MethodHook.Unhook());}}
static class HookRegistry {static final HookRegistry r=new HookRegistry();static HookRegistry get(){return r;}List<XC_MethodHook.Unhook> all=new ArrayList<>();void add(XC_MethodHook.Unhook h){all.add(h);}void unhook(XC_MethodHook.Unhook h){h.unhook();all.remove(h);}}
static void logStatic(Logger l,String s){}
PRODUCTION
static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args){
 for(int i=0;i<100;i++){
 openMarsRuntime();installMarsCdnManagerHook(Object.class,null);check(marsUnhooks.size()==2);installMarsCdnManagerHook(Object.class,null);check(marsUnhooks.size()==2);
 Object manager=new Object();XC_MethodHook old=XposedBridge.callback;XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=manager;old.afterHookedMethod(p);check(marsCdnManagers.get(Object.class).get()==manager);
 closeMarsRuntime();check(marsCdnManagers.isEmpty()&&marsUnhooks.isEmpty()&&HookRegistry.get().all.isEmpty());old.afterHookedMethod(p);check(marsCdnManagers.isEmpty());
 installMarsCdnManagerHook(Object.class,null);check(marsUnhooks.isEmpty());openMarsRuntime();installMarsCdnManagerHook(Object.class,null);old.afterHookedMethod(p);check(marsCdnManagers.isEmpty());closeMarsRuntime();
 XposedBridge.fail=true;openMarsRuntime();installMarsCdnManagerHook(Object.class,null);check(marsUnhooks.isEmpty()&&HookRegistry.get().all.isEmpty());XposedBridge.fail=false;closeMarsRuntime();
 }
 System.out.println("PASS production Mars runtime: 100 reopen cycles; partial rollback; stale hook suppression; weak manager ownership");
}
}
'''.replace('PRODUCTION',s[a:b]+block('    public static void installMarsCdnManagerHook(Class<?>')+block('    private static Object rememberMarsCdnManager('))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'MarsTest.java').write_text(code)
 subprocess.run(['javac','-d',td,str(p/'MarsTest.java')],check=True,timeout=30)
 subprocess.run(['java','-cp',td,'MarsTest'],check=True,timeout=20)
