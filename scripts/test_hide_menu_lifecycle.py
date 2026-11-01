"""Execute actual menu feature against synthetic hooks/preferences; no host operations."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path("/tmp/pl-kotlin-libs");std=next(libs.glob("kotlin-stdlib-[0-9]*.jar"))
stubs = {
'Prefs.kt': """package android.content
interface SharedPreferences {fun getBoolean(k:String,d:Boolean):Boolean;fun getInt(k:String,d:Int):Int;fun getString(k:String,d:String):String;fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener);fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener);fun interface OnSharedPreferenceChangeListener{fun onSharedPreferenceChanged(p:SharedPreferences,k:String?)}}
class Prefs:SharedPreferences {var enabled=false;var factor=2;val listeners=mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>();override fun getBoolean(k:String,d:Boolean)=enabled;override fun getInt(k:String,d:Int)=factor;override fun getString(k:String,d:String)=d;override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners+=l};override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners-=l};fun toggle(v:Boolean){enabled=v;listeners.toList().forEach{it.onSharedPreferenceChanged(this,"hide_chat_menu_enable")}}}
""",
'Core.kt': """package t.mma.hooks.core
import de.robv.android.xposed.XC_MethodHook
import t.mma.ui.SimpleFeatureSettingsProvider
open class BaseFeature {val releases=mutableListOf<()->Unit>();open fun featureId()="";open fun name()="";open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){};fun registerSettingsProvider(p:Any){};fun <T> subscribe(c:Class<T>,f:(T)->Unit){};fun trackResource(f:()->Unit){releases+=f};fun logError(s:String,t:Throwable){};fun destroy(){releases.asReversed().forEach{it()};releases.clear()}}
class FeatureContext(val prefs:android.content.Prefs){fun hostContext()=prefs;fun hostClassLoader()=javaClass.classLoader}
object DexInstallScheduler {enum class Stage{WARMUP};class Installation {var closed=false;var job:(()->Boolean)?=null;fun schedule(f:()->Boolean){if(!closed)job=f};fun close(){closed=true;job=null};fun attempt():Boolean=job?.invoke()?:true};val tickets=mutableListOf<Installation>();fun installation(k:String,l:String,s:Stage=Stage.WARMUP)=Installation().also{tickets+=it}}
object HookRegistry {val handles=mutableListOf<XC_MethodHook.Unhook>();var fail=false;var duringHook:(()->Unit)?=null;fun get()=this;fun hook(m:java.lang.reflect.Method,h:XC_MethodHook):XC_MethodHook.Unhook{if(fail){fail=false;error("synthetic hook failure")};val u=XC_MethodHook.Unhook(h);handles+=u;duringHook?.invoke();return u};fun unhook(u:XC_MethodHook.Unhook){u.active=false}}
""",
'X.kt': """package de.robv.android.xposed
open class XC_MethodHook(priority:Int=0) {open fun afterHookedMethod(p:MethodHookParam){};class MethodHookParam(val args:Array<Any?>);class Unhook(val callback:XC_MethodHook){var active=true}}
""",
'Storage.kt': """package t.mma.preferences
object HchatStorage{fun preferences(c:android.content.Prefs,n:String):android.content.SharedPreferences=c}
""",
'UI.kt': """package t.mma.ui
interface FeatureSettingsProvider {companion object{const val CATEGORY_ENHANCE=""}}
open class SimpleFeatureSettingsProvider(a:String,b:String,c:String,d:String,e:Boolean)
""",
'Api.kt': """package t.mma.hooks.api.core
object WeChatApis{var versionName="8.0.78";fun version()=Version();class Version{fun current()=Info()};class Info{val versionName get()=WeChatApis.versionName}}
""",
'Utils.kt': """package t.mma.utils
object KavaReflector {fun invokeMethod(o:Any,n:String,vararg args:Any?):Any? {val m=o as TestMenu;return when(n){"size"->m.items.size;"getItem"->m.items[args[0] as Int];"removeItem"->{m.items.removeAll{it.itemId==args[0]};null};else->null}}}
class TestItem(override val title:CharSequence,override val itemId:Int):android.view.MenuItem
class TestMenu {val items=mutableListOf(TestItem("收藏",1),TestItem("转发",2))}
object HLog {fun e(s:String,t:Throwable?=null){}}
""",
'Host.kt': """package com.tencent.mm.plugin.sport.model
class j(a:String,b:String,c:Int,d:Int,e:Int,f:String,g:Int)
""",
'Test.kt': """package t.mma.hooks.items.hidemenu
import t.mma.hooks.core.*
import de.robv.android.xposed.XC_MethodHook
import t.mma.utils.TestMenu
fun main(){
 val p=android.content.Prefs();val c=FeatureContext(p);val f=HideChatMenuFeature();f.onFeatureInstall(c)
 check(DexInstallScheduler.tickets.isEmpty());p.toggle(true);val ticket=DexInstallScheduler.tickets.last();check(ticket.attempt());check(ticket.attempt());check(HookRegistry.handles.size==1)
 val old=HookRegistry.handles.last();val m=TestMenu();old.callback.afterHookedMethod(XC_MethodHook.MethodHookParam(arrayOf(m)));check(m.items.map{it.itemId}==listOf(2))
 p.toggle(false);check(!old.active&&ticket.closed);val unchanged=TestMenu();old.callback.afterHookedMethod(XC_MethodHook.MethodHookParam(arrayOf(unchanged)));check(unchanged.items.size==2)
 p.toggle(true);check(DexInstallScheduler.tickets.last().attempt());f.destroy();check(p.listeners.isEmpty()&&HookRegistry.handles.none{it.active})
 f.onFeatureInstall(c);check(DexInstallScheduler.tickets.last().attempt());f.destroy()
 val fail=HideChatMenuFeature();fail.onFeatureInstall(c);HookRegistry.fail=true;check(!DexInstallScheduler.tickets.last().attempt());check(DexInstallScheduler.tickets.last().attempt());fail.destroy()
 val race=HideChatMenuFeature();race.onFeatureInstall(c);HookRegistry.duringHook={p.toggle(false)};check(DexInstallScheduler.tickets.last().attempt());HookRegistry.duringHook=null;race.destroy();check(HookRegistry.handles.none{it.active})
 println("PASS production hide menu: disabled startup, exact menu filtering, retry deduplication, close/reopen, stale callback inert, same feature reinstall, failed hook retry, late handle release")
}
""",
'Callback.kt': """package de.robv.android.xposed.callbacks
object XCallback {const val PRIORITY_HIGHEST=10000}
""",
'Menu.kt': """package android.view
interface MenuItem {val title:CharSequence?;val itemId:Int}
""",
'Settings.kt': """package t.mma.hooks.items.hidemenu
class HideChatMenuSettingsProvider
object HideChatMenuSettings {const val PREFS_NAME="prefs";const val KEY_ENABLE="hide_chat_menu_enable";const val KEY_TITLES="titles";const val DEFAULT_ENABLE=false;const val DEFAULT_TITLES="收藏";fun parseTitles(s:String?)=s.orEmpty().split(',').toSet()}
""",
'Events.kt': """package t.mma.event
object Events {class DexReady}
""",
'Locator.kt': """package t.mma.hooks.api.message
object SingleMessageMenuLocator {fun menuCreateMethods(c:t.mma.hooks.core.FeatureContext,f:(String,Throwable)->Unit)=listOf(Host::class.java.getDeclaredMethod("menu"))}
class Host {fun menu(){}}
""",
}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in stubs.items():(p/n).write_text(s)
 sources=list(map(str,p.glob('*.kt')))+[str(r/'app/src/main/java/t/mma/hooks/core'/n) for n in ['PreferenceRuntimeOwner.kt','FeatureResourceScope.kt']]+[str(r/'app/src/main/java/t/mma/hooks/items/hidemenu/HideChatMenuFeature.kt')]
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar')]+sources,check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.hidemenu.TestKt'],check=True,timeout=15)
