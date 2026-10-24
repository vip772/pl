"""Exercise production request sampling and frame context with deterministic callbacks."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
files={
'Os.kt':'''package android.os
object SystemClock {var now=1000L;fun elapsedRealtime()=now}
class Looper{companion object{val main=Looper();var current=main;fun myLooper()=current;fun getMainLooper()=main}}
class Handler(l:Looper){fun post(b:()->Unit){b()}}
''',
'View.kt':'''package android.view
open class View {var isAttachedToWindow=true;var isShown=true;val windowVisibility=0;val measuredWidth=1;val measuredHeight=1;val left=0;val top=0;val width=1;val height=1;val isLayoutRequested=false;var parent:Any?=null;fun requestLayout(){};companion object{const val VISIBLE=0}}
class Choreographer {fun interface FrameCallback {fun doFrame(time:Long)};var callback:FrameCallback?=null;fun postFrameCallback(c:FrameCallback){callback=c};fun removeFrameCallback(c:FrameCallback){if(callback===c)callback=null};fun fire(t:Long){callback?.doFrame(t)};companion object{val testInstance=Choreographer();fun getInstance()=testInstance}}
''',
'Xposed.kt':'''package de.robv.android.xposed
open class XC_MethodHook {open fun beforeHookedMethod(param:MethodHookParam){};class MethodHookParam(var thisObject:Any?);class Unhook{fun unhook(){XposedBridge.hook=null}}}
object XposedBridge {var hook:XC_MethodHook?=null;fun hookMethod(m:java.lang.reflect.Method,h:XC_MethodHook):XC_MethodHook.Unhook{hook=h;return XC_MethodHook.Unhook()}}
''',
'Diagnostic.kt':'''package t.mma.utils
object KavaReflector{fun findDeclaredMethod(c:Class<*>,n:String)=c.getDeclaredMethod(n)}
object ChatScrollDiagnostic {var active=false;val details=linkedMapOf<String,String>();val hits=mutableMapOf<String,Int>();fun isActive()=active;fun begin()=if(active)android.os.SystemClock.now*1000000 else 0L;fun end(n:String,t:Long){if(t>0)ChatScrollTrace.stage(n,t,android.os.SystemClock.now*1000000)};fun hit(n:String){hits[n]=(hits[n]?:0)+1};fun detail(n:String,v:String){details[n]=v};fun observe(n:String,v:Long){}}
''',
'Test.kt':'''import t.mma.utils.*
import android.os.SystemClock
import android.view.*
import de.robv.android.xposed.*
fun request(v:View){XposedBridge.hook?.beforeHookedMethod(XC_MethodHook.MethodHookParam(v))}
fun main(){
 ChatScrollTrace.start();check(XposedBridge.hook==null)
 ChatScrollDiagnostic.active=true;ChatScrollTrace.start();val view=View();ChatScrollTrace.watch(view)
 request(view);val first=ChatScrollDiagnostic.details["request.source"]!!
 SystemClock.now=1001;request(view);check(ChatScrollDiagnostic.details["request.source"]==first)
 SystemClock.now=6000;request(view);check(ChatScrollDiagnostic.details["request.source"]!!.contains("atMs=6000"))
 check(ChatScrollDiagnostic.hits["requestLayout.outside"]==3)
 val frame=Choreographer.testInstance;frame.fire(1_000_000L)
 repeat(20){ChatScrollTrace.stage("underlay.apply",6_000_000_000L+it,6_001_000_000L+it)}
 ChatScrollTrace.stage("irrelevant",1,2)
 SystemClock.now=6100;frame.fire(51_000_000L)
 val context=ChatScrollDiagnostic.details["frame.longContext"]!!
 check(context.contains("dropped=4"));check(context.contains("requestAtMs=6000"));check(context.split("underlay.apply[").size-1==16);check(!context.contains("irrelevant"))
 SystemClock.now=6200;frame.fire(101_000_000L)
 check(ChatScrollDiagnostic.details["frame.longContext"]!!.endsWith("recentCompleted="))
 ChatScrollTrace.nativeBegin();SystemClock.now++;ChatScrollTrace.nativeEnd();SystemClock.now++;frame.fire(151_000_000L)
 check(ChatScrollDiagnostic.details["frame.longContext"]!!.contains("scroll.nativeLayoutDuration["))
 // One sample per eligible time bucket, session cap 60 including two already taken.
 repeat(70){SystemClock.now=10000L+it*5000L;request(view)}
 check(ChatScrollDiagnostic.details["request.source"]!!.contains("atMs=295000"))
 ChatScrollTrace.stop();check(XposedBridge.hook==null);check(frame.callback==null)
 ChatScrollTrace.start();ChatScrollTrace.watch(view);SystemClock.now=500000;request(view)
 check(ChatScrollDiagnostic.details["request.source"]!!.contains("atMs=500000"))
 frame.fire(1_000_000L);frame.fire(51_000_000L)
 check(ChatScrollDiagnostic.details["frame.longContext"]!!.contains("dropped=0 recentCompleted="))
 ChatScrollDiagnostic.active=false;frame.fire(101_000_000L);check(frame.callback==null);check(XposedBridge.hook==null)
 println("PASS first request/window, no 128 gap, cap60, bounded16, overflow, frame clear, native duration, restart, stop and disabled cleanup")
}
'''}
with tempfile.TemporaryDirectory() as td:
 d=Path(td)
 for n,s in files.items():(d/n).write_text(s)
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(d/'out.jar')]+list(map(str,d.glob('*.kt')))+[str(r/'app/src/main/java/t/mma/utils/ChatScrollTrace.kt')]
 x=subprocess.run(cmd,capture_output=True,text=True);assert x.returncode==0,x.stdout+x.stderr
 subprocess.run(['java','-cp',str(d/'out.jar')+':'+str(std),'TestKt'],check=True,timeout=15)
