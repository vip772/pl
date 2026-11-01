"""Production mention notice ownership with synthetic runtime; no host actions."""
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
class FeatureContext(val prefs:android.content.Prefs){fun hostContext()=prefs;fun hostClassLoader()=javaClass.classLoader;fun loadPackageParam()=Package();class Package{val processName="wx";val packageName="wx"}}
object DexInstallScheduler {enum class Stage{WARMUP};class Installation {var closed=false;var job:(()->Boolean)?=null;fun schedule(f:()->Boolean){if(!closed)job=f};fun close(){closed=true;job=null};fun attempt():Boolean=job?.invoke()?:true};val tickets=mutableListOf<Installation>();fun installation(k:String,l:String,s:Stage=Stage.WARMUP)=Installation().also{tickets+=it}}
object HookRegistry {val handles=mutableListOf<XC_MethodHook.Unhook>();var fail=false;var duringHook:(()->Unit)?=null;fun get()=this;fun hook(m:java.lang.reflect.Method,h:XC_MethodHook):XC_MethodHook.Unhook{if(fail){fail=false;error("synthetic hook failure")};val u=XC_MethodHook.Unhook(h);handles+=u;duringHook?.invoke();return u};fun unhook(u:XC_MethodHook.Unhook){u.active=false}}
""",
'X.kt': """package de.robv.android.xposed
open class XC_MethodHook(priority:Int=0) {open fun afterHookedMethod(p:MethodHookParam){};class MethodHookParam(val args:Array<Any?>);class Unhook(val callback:XC_MethodHook){var active=true}}
""",
'UI.kt': """package t.mma.ui
interface FeatureSettingsProvider {companion object{const val CATEGORY_ENHANCE=""}}
open class SimpleFeatureSettingsProvider(a:String,b:String,c:String,d:String,e:Boolean)
""",
'Utils.kt': """package t.mma.utils
object HLog
""",
'Events.kt': """package t.mma.event
object Events {class DexReady}
""",
'Settings.kt': """package t.mma.hooks.items.mentionextension
object MentionNoticeSettings {const val ID="menu_extension";const val ENABLE="hide_chat_menu_enable";const val PREFS="mention";fun preferences(c:android.content.Prefs):android.content.SharedPreferences=c}
class MentionNoticeSettingsProvider
internal class MentionNoticeRuntime(c:t.mma.hooks.core.FeatureContext,val resources:t.mma.hooks.core.FeatureResourceScope){var destroyed=false;var count=0;init{all+=this;resources.register{destroy()}};fun install():Boolean{if(resources.isClosed())return true;check(!destroyed);count++;return true};fun destroy(){destroyed=true};companion object{val all=mutableListOf<MentionNoticeRuntime>()}}
""",
'Test.kt': """package t.mma.hooks.items.mentionextension
import t.mma.hooks.core.*
fun main(){val p=android.content.Prefs();val c=FeatureContext(p);val f=MentionNoticeFeature();f.onFeatureInstall(c);check(MentionNoticeRuntime.all.isEmpty());p.toggle(true);val t=DexInstallScheduler.tickets.last();val old=t.job!!;check(old());val first=MentionNoticeRuntime.all.last();p.toggle(false);check(t.closed&&first.destroyed);check(old());check(first.count==1);p.toggle(true);val second=MentionNoticeRuntime.all.last();check(second!==first);check(old());check(second.count==0);check(DexInstallScheduler.tickets.last().attempt());f.destroy();check(second.destroyed&&p.listeners.isEmpty());f.onFeatureInstall(c);check(DexInstallScheduler.tickets.last().attempt());f.destroy();check(MentionNoticeRuntime.all.all{it.destroyed});println("PASS actual mention notice feature: disabled startup, close/reopen, stale installer isolated, same feature reinstall, listeners/tickets/runtime released")}
""",
}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in stubs.items():(p/n).write_text(s)
 (p/'Storage.kt').write_text("""package t.mma.preferences
object HchatStorage {fun preferences(c:android.content.Prefs,n:String)=c}
""")
 source=(r/'app/src/main/java/t/mma/hooks/items/mentionextension/MentionNoticeFeature.kt').read_text()
 feature=source[source.index('class MentionNoticeFeature :'):source.index('private class MentionNoticeRuntime(')]
 (p/'Feature.kt').write_text("""package t.mma.hooks.items.mentionextension
import t.mma.hooks.core.*
import t.mma.event.Events
import t.mma.preferences.HchatStorage
"""+feature)
 sources=list(map(str,p.glob('*.kt')))+[str(r/'app/src/main/java/t/mma/hooks/core'/n) for n in ['PreferenceRuntimeOwner.kt','FeatureResourceScope.kt']]
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar')]+sources,check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.mentionextension.TestKt'],check=True,timeout=15)
