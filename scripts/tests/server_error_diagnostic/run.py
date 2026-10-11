#!/usr/bin/env python3
"""Compile and exercise the actual read-only diagnostic against JVM host/Xposed stubs."""
from pathlib import Path
import subprocess,tempfile
repo=Path(__file__).resolve().parents[3]
sources={
'android/content/Context.java':'package android.content; public class Context {public CharSequence getText(int id){return "";} public ClassLoader getClassLoader(){return getClass().getClassLoader();}}',
'android/app/Application.java':'package android.app; public class Application extends android.content.Context {}',
'android/os/Looper.java':'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'package android.os; public class Handler {public Handler(Looper l){} public boolean postDelayed(Runnable r,long d){return true;}}',
'android/os/SystemClock.java':'package android.os; public class SystemClock {public static long now=100;public static long elapsedRealtime(){return now;}}',
'android/os/Process.java':'package android.os; public class Process {public static int myPid(){return 1;}}',
'android/util/Log.java':'package android.util; public class Log {public static String getStackTraceString(Throwable t){return t.toString();}}',
'android/view/View.java':'package android.view; public class View {}',
'android/view/ViewGroup.java':'package android.view; public class ViewGroup extends View {public int getChildCount(){return 0;} public View getChildAt(int i){return null;}}',
'android/widget/Toast.java':'package android.widget; public class Toast {public android.view.View getView(){return null;}}',
'android/widget/TextView.java':'package android.widget; public class TextView extends android.view.View {public enum BufferType{NORMAL} public void setText(CharSequence s,BufferType b){} public CharSequence getText(){return "";}}',
't/mma/BuildConfig.java':'package t.mma; public class BuildConfig {public static String VERSION_NAME="test";public static int VERSION_CODE=1;}',
't/mma/utils/HLog.java':'package t.mma.utils; public class HLog {public static java.util.List<String> logs=new java.util.ArrayList<>();public static void initialize(android.app.Application a){}public static void w(String s){logs.add(s);}}',
'com/tencent/mm/modelbase/m1.java':'package com.tencent.mm.modelbase; public class m1 {public int getType(){return 139;}}',
'com/tencent/mm/ui/conversation/x5.java':'package com.tencent.mm.ui.conversation; public class x5 {public void onSceneEnd(int a,int b,String c,com.tencent.mm.modelbase.m1 d){}}',
'de/robv/android/xposed/XC_MethodHook.java':'''package de.robv.android.xposed;
public class XC_MethodHook {
 public static class Unhook {public boolean removed; public void unhook(){removed=true;}}
 public static class MethodHookParam {public Object[] args; public Object thisObject;public Object result;
 private final java.util.Map<String,Object> extras=new java.util.HashMap<>();
 public Object getResult(){return result;}public void setObjectExtra(String k,Object v){extras.put(k,v);}public Object getObjectExtra(String k){return extras.get(k);}}
 protected void beforeHookedMethod(MethodHookParam p) throws Throwable {}
 protected void afterHookedMethod(MethodHookParam p) throws Throwable {}
 public void before(MethodHookParam p)throws Throwable{beforeHookedMethod(p);} public void after(MethodHookParam p)throws Throwable{afterHookedMethod(p);}
}
''',
'de/robv/android/xposed/XposedBridge.java':'''package de.robv.android.xposed;
public class XposedBridge {
 public static java.util.Map<String,XC_MethodHook> callbacks=new java.util.HashMap<>();
 public static java.util.List<XC_MethodHook.Unhook> handles=new java.util.ArrayList<>();
 public static XC_MethodHook.Unhook hookMethod(java.lang.reflect.Method m,XC_MethodHook c){callbacks.put(m.getName(),c);XC_MethodHook.Unhook u=new XC_MethodHook.Unhook();handles.add(u);return u;}
 public static java.util.Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> cls,String name,XC_MethodHook c){callbacks.put(name,c);XC_MethodHook.Unhook u=new XC_MethodHook.Unhook();handles.add(u);return java.util.Collections.singleton(u);}
 public static void log(String s){}
}
''',
'Test.java':'''import de.robv.android.xposed.*;
import t.mma.utils.*;
public class Test {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static XC_MethodHook.MethodHookParam scene(int type,int code){XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.args=new Object[]{type,code,"do not log this secret",new com.tencent.mm.modelbase.m1()};return p;}
 static void toast()throws Throwable{XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.args=new Object[]{new android.content.Context(),"无法连接到服务器(1,-7)"};p.result=new Object();XposedBridge.callbacks.get("makeText").after(p);}
 public static void main(String[] args)throws Throwable{
 ServerErrorToastDiagnostic.install("com.tencent.mm");ServerErrorToastDiagnostic.onApplicationReady(new android.app.Application());
 check(XposedBridge.callbacks.containsKey("onSceneEnd"));XC_MethodHook callback=XposedBridge.callbacks.get("onSceneEnd");
 XC_MethodHook.MethodHookParam outer=scene(1,-7);Object[] original=outer.args.clone();callback.before(outer);
 XC_MethodHook.MethodHookParam inner=scene(0,0);callback.before(inner);callback.after(inner);
 toast();callback.after(outer);
 String target=HLog.logs.stream().filter(s->s.contains("targetToast")).findFirst().orElseThrow();
 check(target.contains("correlatedScene=sequence=1") && target.contains("errType=1 errCode=-7") && target.contains("sceneType=139"));
 check(!String.join("",HLog.logs).contains("do not log this secret"));check(java.util.Arrays.equals(original,outer.args) && outer.result==null);
 toast();String second=HLog.logs.get(HLog.logs.size()-1);check(second.contains("correlatedScene=none"));
 System.out.println("PASS: verified callback/type/error correlated to Toast, nested context cleared, arguments/results unchanged, error body excluded");
 int before=HLog.logs.size();android.os.SystemClock.now+=180001;callback.before(scene(1,-7));check(HLog.logs.size()==before);
 java.lang.reflect.Method stop=ServerErrorToastDiagnostic.class.getDeclaredMethod("stop");stop.setAccessible(true);stop.invoke(null);
 check(XposedBridge.handles.stream().allMatch(u->u.removed));
 System.out.println("PASS: diagnostic time window and hook cleanup");
 }
}
'''
}
with tempfile.TemporaryDirectory(prefix='pl-server-diagnostic-') as d:
 p=Path(d); files=[]
 for name,text in sources.items():
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text);files.append(f)
 production=repo/'app/src/main/java/t/mma/utils/ServerErrorToastDiagnostic.java'
 subprocess.run(['javac','-d',str(p/'classes'),*map(str,files),str(production)],check=True)
 subprocess.run(['java','-cp',str(p/'classes'),'Test'],check=True)