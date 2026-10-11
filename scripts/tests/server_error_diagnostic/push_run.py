#!/usr/bin/env python3
"""JVM regression for the production push diagnostic; no Gradle/APK build."""
from pathlib import Path
import subprocess,tempfile
repo=Path(__file__).resolve().parents[3]
base=Path(__file__).with_name('run.py').read_text().split('with tempfile.TemporaryDirectory')[0]
ns={'__file__':str(Path(__file__).with_name('run.py'))};exec(base,ns)
sources=ns['sources'];sources.pop('Test.java')
sources['android/os/Process.java']='package android.os; public class Process {public static int myPid(){return 12;}public static int myUid(){return 10314;}}'
sources['de/robv/android/xposed/XposedBridge.java']=sources['de/robv/android/xposed/XposedBridge.java'].replace('public static void log(String s){}','public static java.util.List<String> logs=new java.util.ArrayList<>();public static void log(String s){logs.add(s);}')
sources['com/tencent/mm/network/t2.java']='package com.tencent.mm.network; public class t2 {public Object[] b;public void e(int a,int b,String c){}}'
sources['com/tencent/mm/network/j1.java']='package com.tencent.mm.network; public class j1 {public void d0(int a,int b,String c){}}'
sources['com/tencent/mm/network/a3.java']='package com.tencent.mm.network; public class a3 {public static t2 adapter;public static t2 h(){return adapter;}}'
sources['com/tencent/mm/network/s2.java']='package com.tencent.mm.network; public class s2 {public Object g=new com.tencent.mm.modelbase.m1();public int k=55;}'
sources['com/tencent/mars/stn/StnManager.java']='''package com.tencent.mars.stn;public class StnManager {public static class CgiProfile {
public int channelType=2,protocolType=1;public long startTaskTick=10,endTaskTick=20,startConnectTime=11,connectSuccessfulTime=12,startHandshakeTime=13,handshakeSuccessfulTime=14,startSendPacketTime=15,sendPacketFinishedTime=16,startReadPacketTime=17,readPacketFinishedTime=18;
}}'''
sources['com/tencent/mars/stn/StnManagerCallback.java']='package com.tencent.mars.stn; public class StnManagerCallback {public int onTaskEnd(int a,Object b,int c,int d,StnManager.CgiProfile e,byte[] f){return 0;}}'
sources['PushTest.java']='''import de.robv.android.xposed.*;import t.mma.utils.NetworkOriginDiagnostic;
public class PushTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Throwable{
 ClassLoader loader=PushTest.class.getClassLoader();NetworkOriginDiagnostic.install(loader,"com.tencent.mm");check(XposedBridge.callbacks.isEmpty());
 NetworkOriginDiagnostic.install(loader,"com.tencent.mm:push");check(XposedBridge.callbacks.size()==3);
 NetworkOriginDiagnostic.install(loader,"com.tencent.mm:push");check(XposedBridge.handles.size()==3);
 com.tencent.mm.network.t2 adapter=new com.tencent.mm.network.t2();adapter.b=new Object[]{null,new com.tencent.mm.network.s2()};com.tencent.mm.network.a3.adapter=adapter;
 XC_MethodHook.MethodHookParam task=new XC_MethodHook.MethodHookParam();task.args=new Object[]{55,new Object(),4,-7,new com.tencent.mars.stn.StnManager.CgiProfile(),new byte[]{3,4}};
 Object[] original=task.args.clone();XposedBridge.callbacks.get("onTaskEnd").before(task);
 String logs=String.join("",XposedBridge.logs);check(logs.contains("nativeErrType=4 nativeErrCode=-7 netId=1 requestType=139"));check(logs.contains("startConnectTime=11"));
 XC_MethodHook.MethodHookParam auth=new XC_MethodHook.MethodHookParam();auth.args=new Object[]{1,-7,"auth secret"};XposedBridge.callbacks.get("d0").before(auth);
 XC_MethodHook.MethodHookParam clear=new XC_MethodHook.MethodHookParam();clear.thisObject=adapter;clear.args=new Object[]{1,-7,"clear secret"};Object[] saved=adapter.b.clone();XposedBridge.callbacks.get("e").before(clear);
 logs=String.join("",XposedBridge.logs);check(logs.contains("autoAuthFail inErrType=1 inErrCode=-7"));check(logs.contains("clearTaskAndCallback errType=1 errCode=-7"));check(logs.contains("queue netId=1 taskId=55 requestType=139"));check(!logs.contains("auth secret")&&!logs.contains("clear secret"));check(java.util.Arrays.equals(saved,adapter.b));check(java.util.Arrays.equals(original,task.args));check(task.result==null);
 int before=XposedBridge.logs.size();for(int i=0;i<30;i++)XposedBridge.callbacks.get("d0").before(auth);check(XposedBridge.logs.size()-before==5);
 android.os.SystemClock.now+=600001;before=XposedBridge.logs.size();XposedBridge.callbacks.get("onTaskEnd").before(task);check(XposedBridge.logs.size()==before);
 java.lang.reflect.Method stop=NetworkOriginDiagnostic.class.getDeclaredMethod("stop");stop.setAccessible(true);stop.invoke(null);check(XposedBridge.handles.stream().allMatch(u->u.removed));
 System.out.println("PASS push-only/idempotent installation, native task-to-netId mapping, profile stages, auth/clear paths, read-only state, secret exclusion, log limits, expiry and cleanup");
 }
}'''
with tempfile.TemporaryDirectory(prefix='pl-push-origin-') as d:
 p=Path(d);files=[]
 for name,text in sources.items():
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text);files.append(str(f))
 subprocess.run(['javac','-d',str(p/'classes'),*files,str(repo/'app/src/main/java/t/mma/utils/NetworkOriginDiagnostic.java')],check=True)
 subprocess.run(['java','-cp',str(p/'classes'),'PushTest'],check=True)