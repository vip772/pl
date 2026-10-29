#!/usr/bin/env python3
"""Compile actual recovery guard and exercise boundaries using JVM hook/host stubs."""
from pathlib import Path
import subprocess,tempfile,sys
repo=Path(__file__).resolve().parents[3]
sources={
'android/os/SystemClock.java':'package android.os;public class SystemClock {public static long now=100;public static long elapsedRealtime(){return now;}}',
'android/os/Looper.java':'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'package android.os;public class Handler {public static boolean accepted=true;public static Runnable expiry;public Handler(Looper l){}public boolean postDelayed(Runnable r,long t){expiry=r;return accepted;}}',
'de/robv/android/xposed/XC_MethodHook.java':'''package de.robv.android.xposed;public class XC_MethodHook {public static class Unhook{public boolean removed;}public static class MethodHookParam{public Object[] args;public Object result;}protected void beforeHookedMethod(MethodHookParam p)throws Throwable{}public void before(MethodHookParam p)throws Throwable{beforeHookedMethod(p);}}''',
 't/mma/utils/HLog.java':'package t.mma.utils;public class HLog {public static int errors;public static void e(String s,Throwable t){errors++;}}',
 't/mma/utils/KavaReflector.java':'''package t.mma.utils;public class KavaReflector {public static Class<?> loadClass(String n,ClassLoader l){try{return Class.forName(n,false,l);}catch(Exception e){return null;}}public static java.lang.reflect.Method findDeclaredMethod(Class<?> c,String n,Class<?>... p){try{return c.getDeclaredMethod(n,p);}catch(Exception e){return null;}}public static java.lang.reflect.Field findDeclaredField(Class<?> c,String n){try{return c.getDeclaredField(n);}catch(Exception e){return null;}}}''',
 't/mma/hooks/core/HookRegistry.java':'''package t.mma.hooks.core;import de.robv.android.xposed.*;public class HookRegistry {static final HookRegistry I=new HookRegistry();public static XC_MethodHook cb;public static XC_MethodHook.Unhook handle;public static int count;public static HookRegistry get(){return I;}public XC_MethodHook.Unhook hook(java.lang.reflect.Method m,XC_MethodHook c){cb=c;count++;return handle=new XC_MethodHook.Unhook();}public void unhook(XC_MethodHook.Unhook h){h.removed=true;}}''',
 'com/tencent/mars/stn/StnManager.java':'''package com.tencent.mars.stn;public class StnManager {public void startTask(Task t){}public static class Task {public long cgiID=138;public int cmdID=121,retryCount=1;public boolean longPolling;public String secret="unchanged";}}''',
 'RecoveryTest.java':'''import android.os.*;import t.mma.utils.*;import t.mma.hooks.core.*;import de.robv.android.xposed.*;import com.tencent.mars.stn.StnManager.Task;
public class RecoveryTest {
static void check(boolean b){if(!b)throw new AssertionError();}
static void submit(Task t)throws Throwable{XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.args=new Object[]{t};HookRegistry.cb.before(p);check(p.args[0]==t&&p.result==null&&t.secret.equals("unchanged"));}
public static void main(String[]args)throws Throwable{
String mode=args[0];ClassLoader l=RecoveryTest.class.getClassLoader();
StartupSyncRetryGuard.install(l,"8.0.77","com.tencent.mm:push");StartupSyncRetryGuard.install(l,"8.0.78","com.tencent.mm");StartupSyncRetryGuard.install(l,"8.0.78","other:push");check(HookRegistry.count==0);
if(mode.equals("timer"))Handler.accepted=false;
StartupSyncRetryGuard.install(l,"8.0.78","com.tencent.mm:push");check(HookRegistry.count==1);
if(mode.equals("timer")){check(HookRegistry.handle.removed&&HLog.errors==1);System.out.println("PASS timer rejection cleanup");return;}
StartupSyncRetryGuard.install(l,"8.0.78","com.tencent.mm:push");check(HookRegistry.count==1);
Task unrelated=new Task();unrelated.cgiID=99;submit(unrelated);check(unrelated.retryCount==1&&!HookRegistry.handle.removed);
Task poll=new Task();poll.longPolling=true;submit(poll);check(poll.retryCount==1&&!HookRegistry.handle.removed);
Task t=new Task();if(mode.equals("expired")){SystemClock.now+=60000;submit(t);check(t.retryCount==1&&HookRegistry.handle.removed);}
else if(mode.equals("expiry")){Handler.expiry.run();check(HookRegistry.handle.removed);}
else {if(mode.equals("zero"))t.retryCount=0;if(mode.equals("host2"))t.retryCount=2;int old=t.retryCount;submit(t);check(t.retryCount==(old==1?2:old)&&HookRegistry.handle.removed);Task second=new Task();submit(second);check(second.retryCount==1);}
check(HLog.errors==0);System.out.println("PASS recovery "+mode);
}}
'''}
with tempfile.TemporaryDirectory(prefix='pl-recovery-') as d:
 p=Path(d);files=[]
 for name,text in sources.items():
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text);files.append(str(f))
 production=str(repo/'app/src/main/java/t/mma/utils/StartupSyncRetryGuard.java')
 subprocess.run(['javac','-d',str(p/'classes'),*files,production],check=True)
 for mode in ['normal','zero','host2','expired','expiry','timer']:
  subprocess.run(['java','-cp',str(p/'classes'),'RecoveryTest',mode],check=True)
 # Real Android API compile; Xposed, registry and Kava boundaries remain stubs.
 files=[f for f in files if not f.startswith(str(p/'android')+'/') and not f.endswith('RecoveryTest.java')]
 subprocess.run(['javac','-cp','/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk'),*files,production],check=True)
 print('PASS real Android SDK targeted javac; no Gradle/APK')
entry=(repo/'app/src/main/java/t/mma/ModuleEntry.java').read_text()
assert 'StartupSyncRetryGuard.install(loader, version, lpparam.processName)' in entry
assert 'NetworkOriginDiagnostic.install(' not in entry and 'StartupNetworkDiagnostic.install(' not in entry
print('PASS entry guard integration and old diagnostic registrations removed')