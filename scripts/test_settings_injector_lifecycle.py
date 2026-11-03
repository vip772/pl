"""Extract production Java settings owner and listener cleanup; synthetic JVM stubs."""
from pathlib import Path
import subprocess,tempfile,re
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/api/ui/SettingsInjector.java').read_text()
def block(needle,start=0):
 a=s.index(needle,start);b=s.index('{',a);i=b+1;depth=1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
pre='''
import java.util.*;
class XC_MethodHook {static class Unhook {int calls;boolean fail;Unhook(boolean f){fail=f;}void unhook(){calls++;if(fail)throw new RuntimeException();}}}
class HookRegistry {static final HookRegistry INSTANCE=new HookRegistry();static HookRegistry get(){return INSTANCE;}int count;XC_MethodHook.Unhook hook(java.lang.reflect.Member m,XC_MethodHook c){count++;return new XC_MethodHook.Unhook(false);}void add(XC_MethodHook.Unhook h){count++;}void unhook(XC_MethodHook.Unhook h){h.unhook();count--;}}
class Looper {static boolean main=true;static int myLooper(){return main?1:2;}static int getMainLooper(){return 1;}}
class Handler {boolean failRemove;ArrayList<Runnable> queue=new ArrayList<>();void removeCallbacksAndMessages(Object x){queue.clear();if(failRemove)throw new IllegalStateException("injected cancellation failure");}boolean post(Runnable r){queue.add(r);return true;}void drain(){ArrayList<Runnable> q=new ArrayList<>(queue);queue.clear();for(Runnable r:q)r.run();}}
class View {interface OnLongClickListener{boolean onLongClick(View v);}static class Info {OnLongClickListener mOnLongClickListener;}Info mListenerInfo=new Info();Object tag;boolean longClickable;Object getTag(int id){return tag;}void setTag(int id,Object v){tag=v;}boolean isLongClickable(){return longClickable;}void setLongClickable(boolean b){longClickable=b;}void setOnLongClickListener(OnLongClickListener l){mListenerInfo.mOnLongClickListener=l;longClickable=true;}}
class KavaReflector {static Object readField(Object o,String name){try{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}}
public class Test implements AutoCloseable {
static final int HCHAT_PLUS_LONG_PRESS_TAG=1;boolean closed;final OwnedHooks ownedHooks=new OwnedHooks();final Handler main=new Handler();final WeakHashMap<View,LongPressBinding> longPressBindings=new WeakHashMap<>();final HashSet<Class<?>> plusMenuAdapterViewHookedClasses=new HashSet<>();XC_MethodHook.Unhook contextGetStringUnhook;Class<?> modernLocationParentClass,modernLocationFrontClass;boolean isMarkedPlusView(View v){return Boolean.TRUE.equals(v.tag);}boolean openPlusLongPressSettings(View v){return true;}
'''
a=s.index('    @Override public void close() {',s.index('    private static final class OwnedHooks'));outer=s.index('    @Override public void close() {',a+1)
code=pre+block('    private static final class LongPressBinding')+block('    private static final class OwnedHooks')+block('    @Override public void close() {',outer)+block('    private synchronized void markPlusView(View plusView) {')+'''
static void check(boolean ok){if(!ok)throw new AssertionError();}
public static void main(String[] args){
 OwnedHooks h=new OwnedHooks();XC_MethodHook.Unhook a=new XC_MethodHook.Unhook(true), b=new XC_MethodHook.Unhook(false);h.add(a);h.add(b);h.close();h.close();check(a.calls==1&&b.calls==1);XC_MethodHook.Unhook late=new XC_MethodHook.Unhook(false);h.add(late);check(late.calls==1);check(h.hook(null,new XC_MethodHook())==null);
 for(boolean mainThread:new boolean[]{true,false}){
  Looper.main=mainThread;Test t=new Test();View v=new View(),other=new View();View.OnLongClickListener original=x->false,replacement=x->true;v.setOnLongClickListener(original);v.setLongClickable(false);v.tag="old-tag";t.markPlusView(v);t.markPlusView(other);other.setOnLongClickListener(replacement);t.main.post(()->{throw new AssertionError("late callback");});t.close();t.main.drain();check(v.mListenerInfo.mOnLongClickListener==original&&!v.longClickable&&v.tag.equals("old-tag"));check(other.mListenerInfo.mOnLongClickListener==replacement);check(t.longPressBindings.isEmpty());t.close();View lateView=new View();t.markPlusView(lateView);check(lateView.mListenerInfo.mOnLongClickListener==null);
 }
 for(boolean mainThread:new boolean[]{true,false}){
  Looper.main=mainThread;Test t=new Test();View v=new View();View.OnLongClickListener original=x->false;v.setOnLongClickListener(original);v.setLongClickable(false);v.tag="original";t.markPlusView(v);t.main.failRemove=true;
  boolean threw=false;try{t.close();}catch(IllegalStateException expected){threw=true;}check(threw);t.main.drain();check(v.mListenerInfo.mOnLongClickListener==original&&!v.longClickable&&v.tag.equals("original"));check(t.longPressBindings.isEmpty());t.close();
 }
 System.out.println("PASS production settings Hook owner and listener cleanup: failures isolated, late acquisitions released, idempotent, original listener/tag restored, replacement preserved, main/off-main callback drain");
}
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.java').write_text(code)
 subprocess.run(['javac','-d',td,str(p/'Test.java')],check=True,timeout=30)
 subprocess.run(['java','-cp',td,'Test'],check=True,timeout=15)
callbacks=re.findall(r'protected void (?:before|after)HookedMethod\(MethodHookParam param\)\s*\{([^}]+)',s)
assert len(callbacks)>=14 and all('if (closed) return;' in c for c in callbacks)
body=s[s.index('    public SettingsInjector('):]
assert 'HookRegistry.get().hook(' not in body and 'HookRegistry.get().add(' not in body
f=(r/'app/src/main/java/t/mma/hooks/items/settings/SettingsFeature.kt').read_text()
assert 'BaseFeature()' in f and f.count('registerSettingsProvider(')==3
assert f.index('trackResource { injector.close() }')<f.index('injector.hookSettings()')
print('PASS structural ownership: three providers and injector registered before install; all hook acquisition paths local; all callbacks gated')
