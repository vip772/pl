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
open class BaseFeature{open fun featureId()="";open fun name()="";open fun isEnabled(c:FeatureContext)=true;open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){};open fun onFeatureDestroy(c:FeatureContext){};fun registerSettingsProvider(p:SimpleFeatureSettingsProvider){};fun <T> subscribe(c:Class<T>,f:(T)->Unit){}}
class FeatureContext{fun hostContext():Context=TODO();fun hostClassLoader():ClassLoader=TODO();fun loadPackageParam()=Param()};class Param{val processName="";val packageName=""}
object DexInstallScheduler{fun schedule(id:String,n:String,f:()->Boolean){}}
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
 subprocess.run(cmd+['-classpath',str(std)+':/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'test.jar')]+list(map(str,b.glob('*.kt')))+list(map(str,p.glob('*.kt'))),check=True,timeout=50)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.voicetotext.TestKt'],check=True)
print('PASS complete production feature SDK compile with host/Xposed stubs')