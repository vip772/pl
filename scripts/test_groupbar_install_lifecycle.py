"""Production group bar installer with synthetic retry/runtime; no host actions."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path("/tmp/pl-kotlin-libs");std=next(libs.glob("kotlin-stdlib-[0-9]*.jar"))
stubs = {
'Core.kt': """package t.mma.hooks.core
import de.robv.android.xposed.XC_MethodHook
import t.mma.ui.SimpleFeatureSettingsProvider
open class BaseFeature {val releases=mutableListOf<()->Unit>();open fun featureId()="";open fun name()="";open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){};fun registerSettingsProvider(p:Any){};fun <T> subscribe(c:Class<T>,f:(T)->Unit){};fun trackResource(f:()->Unit){releases+=f};fun logError(s:String,t:Throwable){};fun destroy(){releases.asReversed().forEach{it()};releases.clear()}}
class FeatureContext(val prefs:android.content.Prefs){fun hostContext()=prefs;fun hostClassLoader()=javaClass.classLoader}
object DexInstallScheduler {enum class Stage{WARMUP,BRIDGE};class Installation {var closed=false;var job:(()->Boolean)?=null;fun schedule(f:()->Boolean){if(!closed)job=f};fun close(){closed=true;job=null};fun attempt():Boolean=job?.invoke()?:true};val tickets=mutableListOf<Installation>();fun installation(k:String,l:String,stage:Stage=Stage.WARMUP,priority:Int=0)=Installation().also{tickets+=it}}
object HookRegistry {val handles=mutableListOf<XC_MethodHook.Unhook>();var fail=false;var duringHook:(()->Unit)?=null;fun get()=this;fun hook(m:java.lang.reflect.Method,h:XC_MethodHook):XC_MethodHook.Unhook{if(fail){fail=false;error("synthetic hook failure")};val u=XC_MethodHook.Unhook(h);handles+=u;duringHook?.invoke();return u};fun unhook(u:XC_MethodHook.Unhook){u.active=false}}
""",
'X.kt': """package de.robv.android.xposed
open class XC_MethodHook(priority:Int=0) {open fun afterHookedMethod(p:MethodHookParam){};class MethodHookParam(val args:Array<Any?>);class Unhook(val callback:XC_MethodHook){var active=true}}
""",
'UI.kt': """package t.mma.ui
interface FeatureSettingsProvider {companion object{const val CATEGORY_ENHANCE=""}}
open class SimpleFeatureSettingsProvider(a:String,b:String,c:String,d:String,e:Boolean)
""",
'Prefs.kt': """package android.content
class Prefs
""",
'Runtime.kt': """package t.mma.hooks.items.groupbar
object GroupBarStore {const val ID="groupbar"}
class GroupBarRuntime(c:t.mma.hooks.core.FeatureContext) {var destroyed=false;init{all+=this};fun install():Boolean {check(!destroyed);entered?.invoke();release?.await();return succeed};fun destroy(){destroyed=true};companion object{val all=mutableListOf<GroupBarRuntime>();var succeed=false;var entered:(()->Unit)?=null;var release:java.util.concurrent.CountDownLatch?=null}}
""",
'Test.kt': """package t.mma.hooks.items.groupbar
import t.mma.hooks.core.*
fun main(){val f=GroupBarFeature();val c=FeatureContext(android.content.Prefs());f.onFeatureInstall(c);val old=DexInstallScheduler.tickets.last();val callback=old.job!!;f.destroy();check(old.closed&&GroupBarRuntime.all.all{it.destroyed});val n=GroupBarRuntime.all.size;check(callback());check(n==GroupBarRuntime.all.size)
 GroupBarRuntime.succeed=true;f.onFeatureInstall(c);val next=GroupBarRuntime.all.last();check(callback());check(!next.destroyed);f.destroy();check(next.destroyed)
 GroupBarRuntime.succeed=false;f.onFeatureInstall(c);val job=DexInstallScheduler.tickets.last().job!!;val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1);GroupBarRuntime.entered={entered.countDown()};GroupBarRuntime.release=release
 val t=Thread{job()};t.start();check(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));val closing=Thread{f.destroy()};closing.start();release.countDown();t.join(2000);closing.join(2000);check(!t.isAlive&&!closing.isAlive&&GroupBarRuntime.all.all{it.destroyed})
 println("PASS actual group bar feature: retry cancellation, stale callback inert after reinstall, install/destroy serialization")}
""",
}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in stubs.items():(p/n).write_text(s)
 sources=list(map(str,p.glob('*.kt')))+[str(r/'app/src/main/java/t/mma/hooks/core'/n) for n in []]+[str(r/'app/src/main/java/t/mma/hooks/items/groupbar/GroupBarFeature.kt')]
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar')]+sources,check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.groupbar.TestKt'],check=True,timeout=15)
