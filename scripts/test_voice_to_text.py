from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items/voicetotext';libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'));cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect']
stubs={
'X.kt':'''package de.robv.android.xposed
open class XC_MethodHook {open fun afterHookedMethod(param:MethodHookParam){};class MethodHookParam{val args=arrayOfNulls<Any>(4);fun hasThrowable()=false};class Unhook{fun unhook(){}}}''',
'C.kt':'''package t.mma.hooks.core
import android.content.Context
import t.mma.ui.SimpleFeatureSettingsProvider
import de.robv.android.xposed.XC_MethodHook
open class BaseFeature{open fun featureId()="";open fun name()="";open fun isEnabled(c:FeatureContext)=true;open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){};open fun onFeatureDestroy(c:FeatureContext){};fun registerSettingsProvider(p:SimpleFeatureSettingsProvider){};fun trackResource(f:()->Unit){};fun logError(s:String,t:Throwable){};fun <T> subscribe(c:Class<T>,f:(T)->Unit){}}
class FeatureContext{fun hostContext():Context=TODO();fun hostClassLoader():ClassLoader=TODO();fun loadPackageParam()=Param()};class Param{val processName="";val packageName=""}
object DexInstallScheduler{class Installation{fun schedule(f:()->Boolean){};fun close(){}};fun installation(id:String,n:String)=Installation()}
object HookRegistry{fun get()=this;fun unhook(u:XC_MethodHook.Unhook?){};fun hook(m:java.lang.reflect.Method,h:XC_MethodHook):XC_MethodHook.Unhook?=null}''',
'P.kt':'''package t.mma.preferences
object HchatStorage{fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()}''',
'U.kt':'''package t.mma.ui
interface FeatureSettingsProvider{companion object{const val CATEGORY_PRACTICAL=""}}
open class SimpleFeatureSettingsProvider(id:String,t:String,s:String,c:String,n:Boolean)''',
'E.kt':'''package t.mma.event
object Events{class DexReady}''',
'R.kt':'''package t.mma.utils
object KavaReflector{fun loadClass(n:String,l:ClassLoader):Class<*>?=null;fun findField(c:Class<*>,n:String):java.lang.reflect.Field?=null;fun findMethod(c:Class<*>,n:String,vararg p:Class<*>):java.lang.reflect.Method?=null;fun readField(f:java.lang.reflect.Field?,r:Any?):Any?=null;fun invoke(m:java.lang.reflect.Method?,r:Any?,vararg a:Any?):Any?=null;fun invokeOrThrow(m:java.lang.reflect.Method?,r:Any?,vararg a:Any?):Any?=null}
object HLog{fun e(s:String,t:Throwable){}}''',
'Test.kt':'''package t.mma.hooks.items.voicetotext
fun main(){val g=VoiceToTextGate();check(!g.offer(0));check(g.offer(1));check(!g.offer(1));g.finish(1);check(!g.offer(1));for(i in 2L..17L)check(g.offer(i));check(!g.offer(18));g.release(2);check(g.offer(18));g.clearPending();check(g.offer(2));g.clear();check(g.offer(1));println("PASS bounded queue, duplicate suppression, release and disable/destroy cleanup")}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,text in stubs.items():(p/name).write_text(text)
 subprocess.run(cmd+['-classpath',str(std)+':/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'test.jar')]+list(map(str,b.glob('*.kt')))+[str(r/'app/src/main/java/t/mma/hooks/core/PreferenceRuntimeOwner.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')]+list(map(str,p.glob('*.kt'))),check=True,timeout=50)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.voicetotext.TestKt'],check=True)
print('PASS complete production feature SDK compile with host/Xposed stubs')
# Compile the exact runtime teardown with observable synthetic collaborators.
source=(b/'VoiceToTextFeature.kt').read_text()
destroy=source[source.index('    @Synchronized fun destroy() {'):].rsplit('}',1)[0]
fixture="""
class Looper { companion object { val main=Looper(); var current:Looper?=main; fun myLooper()=current; fun getMainLooper()=main } }
class Main { val jobs=mutableListOf<Runnable>(); fun removeCallbacksAndMessages(x:Any?){jobs.clear()}; fun post(r:Runnable){jobs+=r}; fun flush(){val copy=jobs.toList(); jobs.clear();copy.forEach{it.run()}} }
class Ticket { var closes=0; fun close(){closes++} }
object HookRegistry { var calls=0; var fail=false; fun get()=this; fun unhook(x:Any?){calls++;if(fail)error("unhook")} }
class Gate {var clears=0;fun clear(){clears++}}
class Runtime {var closed=false;val installation=Ticket();var unhook:Any?=Any();val main=Main();val boundIds=mutableMapOf(1 to 1);val gate=Gate();var bridge:Any?=Any();var clears=0;fun clear(){clears++}
"""+destroy+"""
}
fun main(){
 val old=Runtime();Looper.current=null;old.destroy();check(old.closed && old.installation.closes==1 && old.unhook==null && old.bridge==null);check(old.clears==0)
 val fresh=Runtime();old.destroy();check(old.installation.closes==1);old.main.flush();check(old.clears==1 && old.boundIds.isEmpty() && old.gate.clears==1);check(!fresh.closed && fresh.clears==0 && fresh.boundIds.isNotEmpty())
 Looper.current=Looper.main;HookRegistry.fail=true;check(runCatching{fresh.destroy()}.isFailure);check(fresh.clears==1 && fresh.bridge==null && fresh.unhook==null && fresh.installation.closes==1);fresh.destroy();check(fresh.clears==1)
 println("PASS production teardown: off-main cleanup, stale-generation isolation, idempotence, cleanup after unhook failure")
}
"""
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(fixture)
 subprocess.run(cmd+['-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=50)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
