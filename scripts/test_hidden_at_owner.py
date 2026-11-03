"""Compile complete production HiddenAt feature/runtime against synthetic host APIs. No Gradle/device."""
from pathlib import Path
import subprocess, tempfile
r = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
fixtures = {
'Android.kt': '''package android.content
open class Context
interface SharedPreferences {
 fun getBoolean(k:String,d:Boolean):Boolean
 fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun interface OnSharedPreferenceChangeListener {fun onSharedPreferenceChanged(p:SharedPreferences,k:String?)}
}
class Prefs:SharedPreferences {
 var enabled=true;var failRegister=false;var failUnregister=false;var registrations=0;var removals=0
 val listeners=mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
 override fun getBoolean(k:String,d:Boolean)=enabled
 override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){registrations++;listeners+=l;if(failRegister)error("register")}
 override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){removals++;listeners-=l;if(failUnregister)error("unregister")}
 fun toggle(v:Boolean){enabled=v;listeners.toList().forEach{it.onSharedPreferenceChanged(this,"enable")}}
}
''',
'Clock.kt': '''package android.os
object SystemClock {fun elapsedRealtime()=0L}
''',
'Xposed.kt': '''package de.robv.android.xposed
open class XC_MethodHook {
 class MethodHookParam {var thisObject:Any=Any();var args:Array<Any?> = emptyArray();var result:Any?=null;fun hasThrowable()=false}
 class Unhook {var releases=0;var fail=false;fun release(){releases++;if(fail)error("unhook")}}
 open fun beforeHookedMethod(p:MethodHookParam){}
 open fun afterHookedMethod(p:MethodHookParam){}
}
''',
'Core.kt': '''package t.mma.hooks.core
class FeatureContext {fun hostContext()=android.content.Context();fun hostClassLoader()=javaClass.classLoader;fun loadPackageParam()=Package()}
class Package {val processName="wechat";val packageName="wechat"}
open class BaseFeature {open fun featureId()="";open fun name()="";open fun isEnabled(c:FeatureContext)=true;open fun onFeatureInstall(c:FeatureContext){};open fun onFeatureDestroy(c:FeatureContext){}}
object DexInstallScheduler {
 val tickets=mutableListOf<Installation>()
 class Installation {var closed=false;var closes=0;var failClose=false;var installer:(()->Boolean)?=null;var old:(()->Boolean)?=null
  fun schedule(task:()->Boolean){check(!closed);installer=task;old=task}
  fun run()=installer?.invoke()
  fun close(){closes++;closed=true;installer=null;if(failClose)error("ticket close")}
 }
 fun installation(k:String,l:String)=Installation().also{tickets+=it}
}
object HookRegistry {
 val handles=mutableListOf<de.robv.android.xposed.XC_MethodHook.Unhook>();var calls=0;var failAt=0;var reenter:(()->Unit)?=null
 fun get()=this
 fun hook(m:java.lang.reflect.Member,c:de.robv.android.xposed.XC_MethodHook):de.robv.android.xposed.XC_MethodHook.Unhook {
  calls++;if(calls==failAt)error("hook install")
  return de.robv.android.xposed.XC_MethodHook.Unhook().also {handles+=it;val f=reenter;reenter=null;f?.invoke()}
 }
 fun unhook(h:de.robv.android.xposed.XC_MethodHook.Unhook){h.release()}
}
''',
'Storage.kt': '''package t.mma.preferences
object HchatStorage {var prefs=android.content.Prefs();fun preferences(c:android.content.Context,n:String)=prefs}
''',
'Utils.kt': '''package t.mma.utils
object HLog {var fail=false;fun e(s:String,t:Throwable){if(fail)error("logger")}}
class Host {fun U(x:Host)=true;fun a(x:Any)="";fun insert(x:Any)=1L;@JvmField var G=""}
object KavaReflector {
 fun loadClass(n:String,c:ClassLoader):Class<*>?=Host::class.java
 fun findMethodRecursive(c:Class<*>,n:String,vararg p:Class<*>):java.lang.reflect.Method?=when(n){"U"->Host::class.java.getDeclaredMethod("U",Host::class.java);"a"->Host::class.java.getDeclaredMethod("a",Any::class.java);else->null}
 fun invokeOrThrow(m:java.lang.reflect.Method?,o:Any,vararg args:Any?):Any?=null
 fun findFieldRecursive(c:Class<*>,n:String)=Host::class.java.getDeclaredField("G")
 fun readField(f:java.lang.reflect.Field,o:Any):Any?=""
 fun writeField(f:java.lang.reflect.Field,o:Any,v:Any?){}
 fun isAbstract(m:java.lang.reflect.Method)=false
}
''',
'Apis.kt': '''package t.mma.hooks.api.core
object WeChatApis {fun contact()=Contact();fun messages():Messages?=Messages()}
class Contact {fun account():Account?=Account();fun contacts():Contacts?=Contacts()}
class Account {fun selfWxId()="self"}
class Contacts {fun getGroupMemberDisplayName(t:String,i:String)=i;fun getDisplayName(i:String)=i}
class Messages {fun canSendXml()=true;fun sendAppMsgXmlWithLocalId(t:String,x:String)=1L}
''',
'Outgoing.kt': '''package t.mma.hooks.api.message
object OutgoingAtMessageHook {
 interface Observer {fun before(p:de.robv.android.xposed.XC_MethodHook.MethodHookParam);fun after(p:de.robv.android.xposed.XC_MethodHook.MethodHookParam)}
 class Handle:java.io.Closeable {var closes=0;var fail=false;override fun close(){closes++;if(fail)error("observer")}}
 val handles=mutableListOf<Handle>();var failRegister=false
 fun locate(c:t.mma.hooks.core.FeatureContext)=t.mma.utils.Host::class.java.getDeclaredMethod("insert",Any::class.java)
 fun register(m:java.lang.reflect.Method,k:String,o:Observer):java.io.Closeable {if(failRegister)error("observer install");return Handle().also{handles+=it}}
}
''',
'Test.kt': '''package t.mma.hooks.items.hiddenat
import t.mma.hooks.core.*
import t.mma.preferences.HchatStorage
import t.mma.hooks.api.message.OutgoingAtMessageHook as Out
fun main(){
 val c=FeatureContext()
 HchatStorage.prefs=android.content.Prefs();val p=HchatStorage.prefs;val a=HiddenAtRuntime(c)
 a.install();a.install();check(p.registrations==1);val listener=p.listeners.single();val ticket=DexInstallScheduler.tickets.last();check(ticket.run()==true)
 val observer=Out.handles.last();val hooks=HookRegistry.handles.takeLast(2);observer.fail=true;hooks.forEach{it.fail=true};ticket.failClose=true;p.failUnregister=true;t.mma.utils.HLog.fail=true
 a.destroy();a.destroy();check(p.removals==1&&observer.closes==1&&ticket.closes==1&&hooks.all{it.releases==1});check(p.listeners.isEmpty())
 val count=DexInstallScheduler.tickets.size;p.enabled=true;listener.onSharedPreferenceChanged(p,"enable");a.install();check(DexInstallScheduler.tickets.size==count&&p.registrations==1)
 check(ticket.old!!.invoke());check(HookRegistry.handles.takeLast(2)==hooks)
 t.mma.utils.HLog.fail=false;HchatStorage.prefs=android.content.Prefs();val b=HiddenAtRuntime(c);b.install();val retry=DexInstallScheduler.tickets.last();HookRegistry.failAt=HookRegistry.calls+2
 check(retry.run()==false);check(HookRegistry.handles.last().releases==1);HookRegistry.failAt=0;check(retry.run()==true)
 val before=HookRegistry.handles.takeLast(2);HchatStorage.prefs.toggle(false);check(retry.closed&&before.all{it.releases==1});HchatStorage.prefs.toggle(true);val next=DexInstallScheduler.tickets.last();check(next!==retry&&next.run()==true);b.destroy()
 HchatStorage.prefs=android.content.Prefs().also{it.failRegister=true;it.failUnregister=true};val d=HiddenAtRuntime(c);var failed=false;try{d.install()}catch(_:IllegalStateException){failed=true};check(failed&&HchatStorage.prefs.removals==1&&HchatStorage.prefs.listeners.isEmpty());d.destroy()
 HchatStorage.prefs=android.content.Prefs();val reentrant=HiddenAtRuntime(c);reentrant.install();val reentrantTicket=DexInstallScheduler.tickets.last();val offset=HookRegistry.handles.size;HookRegistry.reenter={reentrant.destroy()};reentrantTicket.run();check(reentrantTicket.closed&&HookRegistry.handles.drop(offset).all{it.releases==1})
 println("PASS full production HiddenAt runtime: idempotent install/destroy, ticket ownership, disable/re-enable, partial hook rollback/retry, registration failure, multi-cleanup plus logger failure, stale listeners and stale installer")
}
'''
}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name, text in fixtures.items(): (p/name).write_text(text)
 src=r/'app/src/main/java/t/mma/hooks/items/hiddenat/HiddenAtFeature.kt'
 jar=p/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),str(src),str(src.with_name('HiddenAtState.kt')),str(src.with_name('HiddenAtText.kt')),*map(str,p.glob('*.kt'))],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{std}','t.mma.hooks.items.hiddenat.TestKt'],check=True,timeout=20)
