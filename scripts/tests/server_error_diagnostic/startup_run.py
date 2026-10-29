"""Exercise production main-process sync trace; no Gradle or APK build."""
from pathlib import Path
import subprocess,tempfile
repo=Path(__file__).resolve().parents[3]
ns={'__file__':str(Path(__file__).with_name('run.py'))}
exec(Path(__file__).with_name('run.py').read_text().split('with tempfile.TemporaryDirectory')[0],ns)
sources=ns['sources'];sources.pop('Test.java')
sources['com/tencent/mm/modelbase/m1.java']='package com.tencent.mm.modelbase; public class m1 {public int netId=2; public int getType(){return 138;}public void cancel(){}}'
sources['com/tencent/mm/modelbase/r1.java']='package com.tencent.mm.modelbase; public class r1 {public boolean h(m1 s,int i){return true;}public void d(m1 s){}public void f(){}public void r(){}public void s(){}public void onSceneEnd(int a,int b,String c,m1 s){}}'
sources['t/mma/utils/KavaReflector.java']='''package t.mma.utils;public class KavaReflector {
public static java.lang.reflect.Method findMethodRecursive(Class<?> c,String n)throws Exception{return c.getMethod(n);}
public static Object invokeOrThrow(java.lang.reflect.Method m,Object o,Object...a)throws Exception{m.setAccessible(true);return m.invoke(o,a);}
public static Object readField(Object o,String n)throws Exception{return o.getClass().getField(n).get(o);}}
'''
sources['de/robv/android/xposed/XposedBridge.java']=sources['de/robv/android/xposed/XposedBridge.java'].replace('public static void log(String s){}','public static java.util.List<String> logs=new java.util.ArrayList<>();public static void log(String s){logs.add(s);}')
sources['MainTest.java']='''import de.robv.android.xposed.*;import t.mma.utils.StartupNetworkDiagnostic;
public class MainTest {static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[]a)throws Throwable {
StartupNetworkDiagnostic.install(MainTest.class.getClassLoader(),"com.tencent.mm");check(XposedBridge.handles.size()==8);
StartupNetworkDiagnostic.install(MainTest.class.getClassLoader(),"com.tencent.mm");check(XposedBridge.handles.size()==8);
com.tencent.mm.modelbase.m1 scene=new com.tencent.mm.modelbase.m1();
XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=scene;p.args=new Object[0];
XposedBridge.callbacks.get("cancel").before(p);check(p.result==null&&scene.netId==2);
String log=String.join("",XposedBridge.logs);check(log.contains("sceneCancel")&&log.contains("netId=2")&&log.contains("at MainTest"));
p.thisObject=new com.tencent.mm.modelbase.m1(){public int getType(){return 139;}};
int count=XposedBridge.logs.size();XposedBridge.callbacks.get("cancel").before(p);check(count==XposedBridge.logs.size());
p.thisObject=scene;p.args=new Object[]{2,1,-7,"SECRET",new com.tencent.mm.network.y0(),new byte[]{1}};
Object[] original=p.args.clone();XposedBridge.callbacks.get("onGYNetEnd").before(p);check(java.util.Arrays.equals(original,p.args));
log=String.join("",XposedBridge.logs);check(log.contains("syncNetworkEnd")&&log.contains("errCode=-7")&&!log.contains("SECRET"));
for(int i=0;i<100;i++)XposedBridge.callbacks.get("onGYNetEnd").before(p);count=XposedBridge.logs.size();XposedBridge.callbacks.get("onGYNetEnd").before(p);check(count==XposedBridge.logs.size());
android.os.SystemClock.now+=180001;XposedBridge.callbacks.get("onGYNetEnd").before(p);check(count==XposedBridge.logs.size());
java.lang.reflect.Method stop=StartupNetworkDiagnostic.class.getDeclaredMethod("stop");stop.setAccessible(true);stop.invoke(null);check(XposedBridge.handles.stream().allMatch(h->h.removed));
System.out.println("PASS main sync submit/cancel/reset/end installation, idempotency, type filter, caller stack, unmodified arguments/results, payload exclusion, event cap, expiry and unhook");}}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);files=[]
 for name,text in sources.items():
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text);files.append(str(f))
 subprocess.run(['javac','-d',str(p/'classes'),*files,str(repo/'app/src/main/java/t/mma/utils/StartupNetworkDiagnostic.java')],check=True)
 subprocess.run(['java','-cp',str(p/'classes'),'MainTest'],check=True)
