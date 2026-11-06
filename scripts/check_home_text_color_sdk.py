"""Compile complete production homepage color feature, real reflector and DexKit API.
Host registration/services and member color parser are fixtures; no APK/R8 build.
"""
from pathlib import Path
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[1]
LIB=Path('/tmp/pl-kotlin-libs')
jars=list(LIB.glob('*.jar'))+list(Path('/root/.gradle/caches/modules-2/files-2.1/com.highcapable.kavaref').glob('*/*/*/*.jar'))
jars += [Path('/tmp/pl-dexkit.jar'),Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
files={
'Core.kt':'''package t.mma.hooks.core
open class BaseFeature {open fun featureId()="";open fun name()="";open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){};open fun onFeatureDestroy(c:FeatureContext){};fun registerSettingsProvider(p:Any){};fun <T> subscribe(c:Class<T>,f:(T)->Unit){}}
class FeatureContext {fun hostContext():android.content.Context=TODO();fun hostClassLoader():ClassLoader=TODO();fun dexKitManager()=Manager()}
class Manager {fun <T> withCurrentBridge(f:(org.luckypray.dexkit.DexKitBridge)->T):T=TODO()}
object HookRegistry {fun get()=this;fun hook(m:java.lang.reflect.Method,h:de.robv.android.xposed.XC_MethodHook){}}
object DexInstallScheduler {fun schedule(id:String,name:String,f:()->Boolean){}}
''',
'Xposed.kt':'''package de.robv.android.xposed
open class XC_MethodHook(priority:Int=0) {class MethodHookParam {val args:Array<Any?>?=null;val result:Any?=null;fun hasThrowable()=false};open fun beforeHookedMethod(param:MethodHookParam){};open fun afterHookedMethod(param:MethodHookParam){}}
''',
'Callback.kt':'package de.robv.android.xposed.callbacks\nobject XCallback {const val PRIORITY_HIGHEST=10000}',
'Event.kt':'package t.mma.event\nobject Events {class DexReady}',
'Cache.kt':'''package t.mma.dexkit
object DexMethodCache {
 fun prefs(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()
 fun runtimeKey(c:android.content.Context,l:ClassLoader)=""
 fun load(p:android.content.SharedPreferences,k:String,l:ClassLoader,n:String):java.lang.reflect.Method?=null
 fun loadList(p:android.content.SharedPreferences,k:String,l:ClassLoader,n:String)=emptyList<java.lang.reflect.Method>()
 fun save(p:android.content.SharedPreferences,k:String,n:String,m:java.lang.reflect.Method){}
 fun saveList(p:android.content.SharedPreferences,k:String,n:String,m:List<java.lang.reflect.Method>){}
 fun clear(p:android.content.SharedPreferences,k:String,n:String){}
}
''',
'Storage.kt':'package t.mma.preferences\nobject HchatStorage {fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()}',
'Log.kt':'package t.mma.utils\nobject HLog {fun e(s:String,t:Throwable?=null){}}',
'Api.kt':'package t.mma.hooks.api.core\nobject WeChatApis {fun currentActivity():Api?=null}\nclass Api {fun currentActivity():android.app.Activity?=null}',
'Settings.kt':'package t.mma.hooks.items.hometextcolor\nclass HomeTextColorSettingsProvider',
'Member.kt':'''package t.mma.hooks.items.membertitle
object MemberTitleStore {class ColorSpec(val startColor:Int,val endColor:Int,val isGradient:Boolean);fun parseColorSpec(v:String?):ColorSpec?=null;fun cleanColorSpec(v:String?)=""}
''',
}
src=ROOT/'app/src/main/java/t/mma'
feature=src/'hooks/items/hometextcolor/HomeTextColorFeature.kt'
import re
ids=sorted(set(re.findall(r'R.id.(\w+)',feature.read_text())))
files['R.kt']='package t.mma\nobject R {object id {'+';'.join(f'const val {name}={i}' for i,name in enumerate(ids,1))+'}}'
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,text in files.items():(p/name).write_text(text)
 sources=list(p.glob('*.kt'))+[feature,src/'hooks/items/hometextcolor/HomeTextColorSettings.kt',src/'utils/KavaReflector.kt']+list((src/'compat/kavaref').rglob('*.kt'))
 subprocess.run(['java','-Xmx384m','-cp',':'.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-nowarn','-no-stdlib','-no-reflect','-classpath',':'.join(map(str,jars)),'-d',str(p/'sdk.jar'),*map(str,sources)],check=True,timeout=60)
print('PASS complete production HomeTextColorFeature + settings + real KavaReflector/compat with Android SDK and DexKit API')