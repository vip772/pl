"""Execute production feature with synthetic preferences, installation tickets and constructor hooks."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
stubs={
'Prefs.kt':"""package android.content
interface SharedPreferences {fun getBoolean(k:String,d:Boolean):Boolean;fun getInt(k:String,d:Int):Int;fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener);fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener);fun interface OnSharedPreferenceChangeListener{fun onSharedPreferenceChanged(p:SharedPreferences,k:String?)}}
class Prefs:SharedPreferences {var enabled=false;var factor=2;val listeners=mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>();override fun getBoolean(k:String,d:Boolean)=enabled;override fun getInt(k:String,d:Int)=factor;override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners+=l};override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners-=l};fun toggle(v:Boolean){enabled=v;listeners.toList().forEach{it.onSharedPreferenceChanged(this,"enable")}}}
""",
'Core.kt':"""package t.mma.hooks.core
import de.robv.android.xposed.XC_MethodHook
import t.mma.ui.SimpleFeatureSettingsProvider
open class BaseFeature {val releases=mutableListOf<()->Unit>();open fun featureId()="";open fun name()="";open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){};fun registerSettingsProvider(p:SimpleFeatureSettingsProvider,f:()->Boolean){};fun trackResource(f:()->Unit){releases+=f};fun logError(s:String,t:Throwable){};fun destroy(){releases.asReversed().forEach{it()};releases.clear()}}
class FeatureContext(val prefs:android.content.Prefs){fun hostContext()=prefs;fun hostClassLoader()=javaClass.classLoader}
object DexInstallScheduler {enum class Stage{WARMUP};class Installation {var closed=false;var job:(()->Boolean)?=null;fun schedule(f:()->Boolean){if(!closed)job=f};fun close(){closed=true;job=null};fun attempt():Boolean=job?.invoke()?:true};val tickets=mutableListOf<Installation>();fun installation(k:String,l:String,s:Stage)=Installation().also{tickets+=it}}
object HookRegistry {val handles=mutableListOf<XC_MethodHook.Unhook>();var fail=false;var duringHook:(()->Unit)?=null;fun get()=this;fun hook(m:java.lang.reflect.Constructor<*>,h:XC_MethodHook):XC_MethodHook.Unhook{if(fail){fail=false;error("synthetic hook failure")};val u=XC_MethodHook.Unhook(h);handles+=u;duringHook?.invoke();return u};fun unhook(u:XC_MethodHook.Unhook){u.active=false}}
""",
'X.kt':"""package de.robv.android.xposed
open class XC_MethodHook {open fun beforeHookedMethod(p:MethodHookParam){};class MethodHookParam(val args:Array<Any?>);class Unhook(val callback:XC_MethodHook){var active=true}}
""",
'Storage.kt':"""package t.mma.preferences
object HchatStorage{fun preferences(c:android.content.Prefs,n:String):android.content.SharedPreferences=c}
""",
'UI.kt':"""package t.mma.ui
interface FeatureSettingsProvider {companion object{const val CATEGORY_ENHANCE=""}}
open class SimpleFeatureSettingsProvider(a:String,b:String,c:String,d:String,e:Boolean)
""",
'Api.kt':"""package t.mma.hooks.api.core
object WeChatApis{var versionName="8.0.78";fun version()=Version();class Version{fun current()=Info()};class Info{val versionName get()=WeChatApis.versionName}}
""",
'Utils.kt':"""package t.mma.utils
object KavaReflector{fun findConstructor(c:Class<*>,vararg p:Class<*>)=c.getDeclaredConstructor(*p)}
object HLog{fun w(s:String){};fun i(s:String){};fun e(s:String,t:Throwable){}}
""",
'Host.kt':"""package com.tencent.mm.plugin.sport.model
class j(a:String,b:String,c:Int,d:Int,e:Int,f:String,g:Int)
""",
'Test.kt':"""package t.mma.hooks.items.stepmultiplier
import t.mma.hooks.core.*
import t.mma.hooks.api.core.WeChatApis
import de.robv.android.xposed.XC_MethodHook
fun main(){
 val p=android.content.Prefs();val c=FeatureContext(p);val f=StepMultiplierFeature();f.onFeatureInstall(c);check(DexInstallScheduler.tickets.isEmpty()&&p.listeners.size==1)
 p.toggle(true);check(DexInstallScheduler.tickets.last().attempt());val old=HookRegistry.handles.last();val param=XC_MethodHook.MethodHookParam(arrayOf(null,null,null,null,100,null,null));old.callback.beforeHookedMethod(param);check(param.args[4]==200)
 p.toggle(false);check(!old.active);param.args[4]=100;old.callback.beforeHookedMethod(param);check(param.args[4]==100);p.toggle(true);check(DexInstallScheduler.tickets.last().attempt());check(HookRegistry.handles.last()!==old);f.destroy();check(p.listeners.isEmpty()&&HookRegistry.handles.none{it.active})
 val fresh=StepMultiplierFeature();p.enabled=true;fresh.onFeatureInstall(c);check(DexInstallScheduler.tickets.last().attempt());fresh.destroy()
 val failing=StepMultiplierFeature();failing.onFeatureInstall(c);HookRegistry.fail=true;check(!DexInstallScheduler.tickets.last().attempt());check(DexInstallScheduler.tickets.last().attempt());failing.destroy()
 val raced=StepMultiplierFeature();raced.onFeatureInstall(c);HookRegistry.duringHook={p.toggle(false)};check(DexInstallScheduler.tickets.last().attempt());HookRegistry.duringHook=null;check(HookRegistry.handles.none{it.active});raced.destroy()
 WeChatApis.versionName="unverified";p.enabled=true;val unsupported=StepMultiplierFeature();val count=HookRegistry.handles.size;unsupported.onFeatureInstall(c);check(DexInstallScheduler.tickets.last().attempt());check(HookRegistry.handles.size==count);unsupported.destroy()
 check(StepMultiplierSettings.multiply(100,20,true)==1000);check(StepMultiplierSettings.multiply(Int.MAX_VALUE,10,true)==Int.MAX_VALUE);check(StepMultiplierSettings.multiply(-1,10,true)==-1);check(StepMultiplierSettings.multiply(100,10,false)==100)
 println("PASS production step feature: disabled startup, repeated reopen, destroyed feature reinstall, late handle cleanup, installation failure retry, unsupported version, original clamp semantics; no host reporting")
}
"""}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in stubs.items():(p/n).write_text(s)
 sources=list(map(str,p.glob('*.kt')))+[str(r/'app/src/main/java/t/mma/hooks/core'/n) for n in ['PreferenceRuntimeOwner.kt','FeatureResourceScope.kt']]+[str(r/'app/src/main/java/t/mma/hooks/items/stepmultiplier/StepMultiplierFeature.kt')]
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar')]+sources,check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.stepmultiplier.TestKt'],check=True,timeout=15)
