"""Execute the production touch guard with synthetic host hooks, then SDK compile."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
source=r/'app/src/main/java/t/mma/hooks/items/mentionextension/MentionNoticeTouchGuard.kt'
stubs = {
'X.kt': """package de.robv.android.xposed
open class XC_MethodHook {open fun beforeHookedMethod(param:MethodHookParam){};class MethodHookParam(val args:Array<Any?>){var result:Any?=null}}
""",
'Core.kt': """package t.mma.hooks.core
import de.robv.android.xposed.XC_MethodHook
class FeatureContext {fun hostContext()=Any();fun hostClassLoader()=javaClass.classLoader}
class FeatureResourceScope {var closed=false;val releases=mutableListOf<()->Unit>();fun isClosed()=closed;fun register(f:()->Unit){releases+=f};fun close(){closed=true;releases.forEach{it()}}}
object HookRegistry {var callback:XC_MethodHook?=null;var active=true;var fail=false;var calls=0;fun get()=this;fun hook(m:java.lang.reflect.Method,c:XC_MethodHook):Any?{calls++;if(fail)return null;callback=c;active=true;return c};fun unhook(h:Any){active=false}}
""",
'Utils.kt': """package t.mma.utils
object HLog {var errors=0;fun e(s:String,t:Throwable){errors++}}
object KavaReflector {var throws=false;var missing=false;fun loadClass(n:String,c:ClassLoader):Class<*>?=if(missing)null else Class.forName(n);fun findMethodRecursive(c:Class<*>,n:String,vararg p:Class<*>)=c.getMethod(n,*p);fun invokeOrThrow(m:java.lang.reflect.Method,r:Any)=if(throws)error("synthetic reflection failure") else m.invoke(r)}
""",
'Settings.kt': """package t.mma.hooks.items.mentionextension
object MentionNoticeSettings {var active=true;const val MARKER="⁣⁠⁣";const val PREFIX="[艾特提醒] ";fun enabled(c:Any)=active}
""",
'View.kt': """package android.view
open class View {var isPressed=false;val background=Any();val listener=Any()}
class MotionEvent(val action:Int)
""",
'Text.kt': """package android.widget
open class TextView:android.view.View(){var text:CharSequence?=""}
""",
'Neat.kt': """package com.tencent.neattextview.textview.view
class NeatTextView:android.view.View(){val inner=android.widget.TextView();fun getWrappedTextView()=inner}
""",
'Listener.kt': """package com.tencent.mm.pluginsdk.ui.span
class y {fun onTouch(v:android.view.View,e:android.view.MotionEvent)=false}
""",
'Test.kt': """package t.mma.hooks.items.mentionextension
import t.mma.hooks.core.*
import de.robv.android.xposed.XC_MethodHook
import android.view.*
import android.widget.TextView
import com.tencent.neattextview.textview.view.NeatTextView
fun main(){
 val c=FeatureContext();val scope=FeatureResourceScope();val methods=hashSetOf<java.lang.reflect.Method>()
 check(MentionNoticeTouchGuard.install(c,scope,methods));check(MentionNoticeTouchGuard.install(c,scope,methods));check(HookRegistry.calls==1)
 fun dispatch(v:View,action:Int):Any? {val p=XC_MethodHook.MethodHookParam(arrayOf(v,MotionEvent(action)));HookRegistry.callback!!.beforeHookedMethod(p);return p.result}
 val v=NeatTextView();val bg=v.background;val listener=v.listener
 for(prefix in listOf(MentionNoticeSettings.MARKER,MentionNoticeSettings.PREFIX)){
  v.inner.text=prefix+"张三 艾特了你"
  for(action in listOf(0,2,1,3)){v.isPressed=true;v.inner.isPressed=true;check(dispatch(v,action)==true);check(!v.isPressed&&!v.inner.isPressed)}
 }
 check(v.background===bg&&v.listener===listener)
 // Same view recycled to unrelated system messages must keep host behavior.
 for(text in listOf("张三撤回了一条消息","张三 艾特了你","ordinary","")){v.inner.text=text;v.isPressed=true;check(dispatch(v,0)==null);check(v.isPressed)}
 v.inner.text=MentionNoticeSettings.MARKER+"reminder"
 MentionNoticeSettings.active=false;check(dispatch(v,0)==null);MentionNoticeSettings.active=true
 val direct=TextView();direct.text=MentionNoticeSettings.MARKER+"reminder";check(dispatch(direct,0)==true)
 t.mma.utils.KavaReflector.throws=true
 val errors=t.mma.utils.HLog.errors;check(dispatch(v,0)==null);check(dispatch(v,0)==null);check(t.mma.utils.HLog.errors==errors+1)
 t.mma.utils.KavaReflector.throws=false
 scope.close();check(!HookRegistry.active);check(dispatch(v,0)==null)
 val reopened=FeatureResourceScope();check(MentionNoticeTouchGuard.install(c,reopened,hashSetOf()));check(dispatch(v,0)==true)
 t.mma.utils.KavaReflector.missing=true;check(!MentionNoticeTouchGuard.install(c,FeatureResourceScope(),hashSetOf()));t.mma.utils.KavaReflector.missing=false
 HookRegistry.fail=true;check(!MentionNoticeTouchGuard.install(c,FeatureResourceScope(),hashSetOf()))
 println("PASS actual touch guard: down/move/up/cancel, wrapped/direct text, legacy marker, recycled ordinary rows, disabled/closed/reopened, idempotent install and missing/failed hook; no listener/background mutation")
}
""",
}
stubs['Api.kt'] = """package t.mma.hooks.api.core
object WeChatApis {fun version():Version?=Version();class Version {fun current()=Info()};class Info {val versionName="8.0.78";val versionCode=1}}
"""
def compile(files,jar,cp):
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(jar),*map(str,files)],check=True,timeout=60)
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,text in stubs.items():(p/name).write_text(text)
 compile([source,*p.glob('*.kt')],p/'test.jar',str(std))
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.mentionextension.TestKt'],check=True,timeout=15)
 # Compile the production guard against real Android APIs as well.
 files=[source,*[p/n for n in ['X.kt','Core.kt','Utils.kt','Settings.kt','Api.kt']]]
 compile(files,p/'sdk.jar',f'{std}:/usr/lib/android-sdk/platforms/android-36/android.jar')
 print('PASS production touch guard compiled against real Android SDK; host hook/query interfaces stubbed')
