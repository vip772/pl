"""Compile early installers and Monet collector with SDK and real DexKit ABI, without Gradle."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
base=r/'app/src/main/java/t/mma'
libs=Path('/tmp/pl-kotlin-libs')
std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
sdk=Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
dex=Path('/tmp/pl-dexkit.jar')
compiler=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-jvm-target','17']
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 stubs={
 'Core.kt':'''package t.mma.hooks.core
open class BaseFeature {open fun featureId()="";open fun name()="";open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){};fun registerSettingsProvider(p:Any){}}
class FeatureContext {fun hostContext():android.content.Context=error("fixture");fun hostClassLoader():ClassLoader=error("fixture");fun dexKitBridge():org.luckypray.dexkit.DexKitBridge=error("fixture")}
object HookRegistry {fun get()=this;fun hook(m:java.lang.reflect.Member,c:de.robv.android.xposed.XC_MethodHook){}}
''',
 'Xposed.kt':'''package de.robv.android.xposed
open class XC_MethodHook {class MethodHookParam{var result:Any?=null;var args:Array<Any?> = emptyArray();var thisObject:Any?=null};open fun beforeHookedMethod(param:MethodHookParam){};open fun afterHookedMethod(param:MethodHookParam){}}
object XposedBridge
''',
 'Log.kt':'''package t.mma.utils
object HLog {fun d(s:String){};fun i(s:String){};fun w(s:String){};fun e(s:String,t:Throwable?=null){}}
''',
 'Storage.kt':'''package t.mma.preferences
object HchatStorage {fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=error("fixture");fun runtimePreferences(c:android.content.Context,n:String):android.content.SharedPreferences=error("fixture")}
''',
 'Version.kt':'''package t.mma.hooks.api.runtime
object WeChatVersionApi {fun build(c:android.content.Context,l:ClassLoader)=t.mma.hooks.api.model.WeChatVersionInfo("","",0L,"","","",0L,"","");fun buildCacheKey(c:android.content.Context,l:ClassLoader)="fixture"}
''',
 'Tablet.kt':'''package t.mma.hooks.items.tablet
class WeChatTabletSettingsProvider
object WeChatTabletSettings {const val PREFS_NAME="fixture";const val KEY_ENABLE="enable";const val DEFAULT_ENABLE=false}
''',
 'Hot.kt':'''package t.mma.hooks.items.hotupdate
class DisableHotUpdateSettingsProvider
object DisableHotUpdateSettings {const val PREFS_NAME="fixture";const val KEY_ENABLE="enable";const val DEFAULT_ENABLE=false}
''',
 }
 for name,s in stubs.items():(p/name).write_text(s)
 sources=[base/'hooks/core/DexInstallScheduler.kt',base/'hooks/api/model/WeChatVersionInfo.kt',base/'dexkit/DexMethodCache.kt',base/'hooks/items/tablet/WeChatTabletFeature.kt',base/'hooks/items/hotupdate/DisableHotUpdateFeature.kt',base/'hooks/items/monetgenerator/engine/MonetDexEvidenceCollector.kt',base/'hooks/items/monetgenerator/engine/MonetGenerationModels.kt']
 subprocess.run(compiler+['-classpath',f'{std}:{sdk}:{dex}','-d',str(p/'result.jar'),*map(str,p.glob('*.kt')),*map(str,sources)],check=True,timeout=55)
print('PASS complete early tablet/hot-update installers and Monet collector: SDK + real DexKit ABI compile')
