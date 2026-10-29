from pathlib import Path
import tempfile, subprocess
r=Path(__file__).resolve().parents[1]; libs=Path('/tmp/pl-kotlin-libs'); std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 d=Path(td)
 stubs={
 'Core.kt':'''package t.mma.hooks.core
import android.content.Context
open class BaseFeature {open fun isEnabled(c:FeatureContext)=true;open fun onConfigChanged(c:FeatureContext,k:String?){};open fun featureId()="";open fun name()="";open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){};open fun onFeatureDestroy(c:FeatureContext){};fun registerSettingsProvider(p:Any){}}
class FeatureContext {fun hostContext():Context=TODO();fun hostClassLoader():ClassLoader=TODO();fun configStore()=Config();fun dexKitBridge():org.luckypray.dexkit.DexKitBridge=TODO()}
class Config {fun getGlobalBoolean(k:String,b:Boolean)=b}
object DexInstallScheduler {fun schedule(k:String,n:String,f:()->Boolean){}}
object HookRegistry {fun get()=this;fun hook(m:java.lang.reflect.Member,h:de.robv.android.xposed.XC_MethodHook){}}
''',
 'Xposed.kt':'''package de.robv.android.xposed
open class XC_MethodHook {open fun beforeHookedMethod(p:MethodHookParam){};open fun afterHookedMethod(p:MethodHookParam){};class MethodHookParam {lateinit var thisObject:Any;var args:Array<Any?> = emptyArray();var result:Any?=null}}
''',
 'Dex.kt':'package t.mma.dexkit\nobject DexMethodCache {fun prefs(c:android.content.Context,n:String):android.content.SharedPreferences=TODO();fun runtimeKey(c:android.content.Context,l:ClassLoader)="";fun load(p:android.content.SharedPreferences,k:String,l:ClassLoader,n:String):java.lang.reflect.Method?=null;fun clear(p:android.content.SharedPreferences,k:String,n:String){};fun save(p:android.content.SharedPreferences,k:String,n:String,m:java.lang.reflect.Method){} }\n',
 'Utils.kt':'''package t.mma.utils
object HLog {fun i(s:String){};fun w(s:String){};fun e(s:String,t:Throwable?=null){}}
object PerformanceSample {fun start()=0L;fun finish(n:String,t:Long){}}
object KavaReflector {fun findDeclaredField(c:Class<*>,n:String):java.lang.reflect.Field?=null;fun readField(f:java.lang.reflect.Field,o:Any):Any?=null;fun loadClass(n:String,c:ClassLoader):Class<*>?=null;fun findDeclaredMethod(c:Class<*>,n:String,vararg p:Class<*>):java.lang.reflect.Method?=null;fun isStatic(m:java.lang.reflect.Member)=false;fun findMethodRecursive(c:Class<*>?,n:String,vararg p:Class<*>):java.lang.reflect.Method?=null}
''',
 'Api.kt':'package t.mma.hooks.api.core\nobject WeChatApis {fun currentActivity():Current?=null};class Current {fun currentActivity():android.app.Activity?=null}',
 'Storage.kt':'package t.mma.preferences\nobject HchatStorage {fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()}',
 'Underlay.kt':'package t.mma.hooks.items.backgroundbeauty\nobject ChatInputMessageUnderlay {fun releaseForFloatingFooter(v:android.view.View){}}',
 'Settings.kt':'package t.mma.hooks.items.floatingchat\nclass FloatingChatFooterSettingsProvider'
 }
 for n,s in stubs.items():(d/n).write_text(s)
 cmd=['java','-Xmx512m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',f'{std}:/usr/lib/android-sdk/platforms/android-36/android.jar:/tmp/pl-dexkit.jar','-d',str(d/'out.jar')]+list(map(str,d.glob('*.kt')))+[str(r/'app/src/main/java/t/mma/hooks/items/floatingchat'/n) for n in ['FloatingChatFooterFeature.kt','FloatingChatCardVisuals.kt','FloatingFooterInputSurfaces.kt','ChatListBottomInset.kt','ChatBottomTrace.kt','FloatingChatOpacity.kt','SendButtonSettlement.kt']]
 result=subprocess.run(cmd,capture_output=True,text=True)
 if result.returncode: print(result.stdout,result.stderr);raise SystemExit(result.returncode)
print('PASS: production FloatingChatFooterFeature/CardVisuals compile against Android 36 SDK and DexKit (project helpers stubbed)')