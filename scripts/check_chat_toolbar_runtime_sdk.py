"""Compile full production toolbar (including coordinator) and header; dependency fixtures only.
No Gradle, APK/R8, native Hook or device execution.
"""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];base=r/'app/src/main/java/t/mma'
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'));sdk=Path('/usr/lib/android-sdk/platforms/android-36/android.jar');dex=Path('/tmp/pl-dexkit.jar')
stubs={
'Core.kt':'''package t.mma.hooks.core
import android.content.Context
open class BaseFeature {open fun isEnabled(c:FeatureContext)=true;open fun onConfigChanged(c:FeatureContext,k:String?){};open fun featureId()="";open fun name()="";open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){};fun registerSettingsProvider(p:Any){};fun trackResource(f:()->Unit){}}
class FeatureContext {fun hostContext():Context=TODO();fun hostClassLoader():ClassLoader=TODO();fun configStore()=Config();fun dexKitManager()=Manager()}
class Config {fun getGlobalBoolean(k:String,b:Boolean)=b}
class Manager {fun <T> withCurrentBridge(f:(org.luckypray.dexkit.DexKitBridge)->T):T=TODO()}
object DexInstallScheduler {fun installation(k:String,n:String)=Ticket()}
class Ticket {fun close(){};fun schedule(f:()->Boolean){}}
class HookRegistry {fun hook(m:java.lang.reflect.Member,h:de.robv.android.xposed.XC_MethodHook)=de.robv.android.xposed.XC_MethodHook.Unhook();fun unhook(h:de.robv.android.xposed.XC_MethodHook.Unhook){};companion object{fun get()=HookRegistry()}}
''',
'X.kt':'''package de.robv.android.xposed
open class XC_MethodHook {open fun beforeHookedMethod(p:MethodHookParam){};open fun afterHookedMethod(p:MethodHookParam){};class Unhook;class MethodHookParam{var thisObject:Any=Any();var args:Array<Any?> = emptyArray();var result:Any?=null}}
''',
'Reflect.kt':'''package t.mma.utils
object HLog {fun i(s:String){};fun e(s:String,t:Throwable?=null){}}
object ChatScrollDiagnostic {fun begin()=0L;fun end(n:String,t:Long){}}
object PerformanceSample {fun start()=0L;fun finish(n:String,t:Long){}}
object KavaReflector {
fun loadClass(n:String,c:ClassLoader):Class<*>?=null
fun declaredConstructors(c:Class<*>):List<java.lang.reflect.Constructor<*>> = emptyList()
fun findFieldRecursive(c:Class<*>,n:String):java.lang.reflect.Field?=null
fun declaredFields(c:Class<*>):List<java.lang.reflect.Field> = emptyList()
fun readField(f:java.lang.reflect.Field,o:Any?):Any?=null
fun invokeOrThrow(m:java.lang.reflect.Method,o:Any?,vararg a:Any?):Any?=null
fun newInstanceByArgs(c:Class<*>,a:Array<Any?>):Any?=null
fun findMethodRecursive(c:Class<*>?,n:String,vararg p:Class<*>):java.lang.reflect.Method?=null
fun findDeclaredMethod(c:Class<*>,n:String,vararg p:Class<*>):java.lang.reflect.Method?=null
}
''',
'Cache.kt':'''package t.mma.dexkit
object DexMethodCache {
fun prefs(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()
fun runtimeKey(c:android.content.Context,l:ClassLoader)=""
fun load(p:android.content.SharedPreferences,k:String,l:ClassLoader,n:String):java.lang.reflect.Method?=null
fun save(p:android.content.SharedPreferences,k:String,n:String,m:java.lang.reflect.Method){}
fun clear(p:android.content.SharedPreferences,k:String,n:String){}
}
''',
'Storage.kt':'''package t.mma.preferences
object HchatStorage {fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()}
''',
'Api.kt':'''package t.mma.hooks.api.core
object WeChatApis {fun runtime()=Runtime();fun currentActivity():Current?=null;fun version():Version?=null;fun chatPage():Chat?=null;fun contact()=Contact();fun message()=Message()}
class Message {fun sender():Sender?=null};class Sender {fun sendText(t:String,s:String)=true}
class Runtime {fun database():Database?=null};class Database {fun rawQuery(s:String,a:Array<String>):android.database.Cursor?=null}
class Current {fun currentActivity():android.app.Activity?=null}
class Version {fun versionName()="8.0.78"}
class Chat {fun currentTalker():String?=null}
class Contact {fun contacts():Contacts?=null}
class Contacts {fun getAvatarUrl(talker:String,high:Boolean):String?=null}
''',
'Settings.kt':'package t.mma.hooks.items.floatingchat\nclass FloatingChatHeaderSettingsProvider'
}
production=['hooks/core/FeatureResourceScope.kt']+[f'hooks/items/chattoolbar/{n}.kt' for n in ['ChatToolbarRuntime','ChatToolbarSettings','ChatToolbarGlyph','ChatToolbarNewMessagePosition']]+[f'hooks/items/floatingchat/{n}.kt' for n in ['FloatingChatHeaderFeature','FloatingChatHeaderCapsule','FloatingChatCardVisuals','FloatingChatOpacity','FloatingChatActionBarBackgrounds']]
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in stubs.items():(p/n).write_text(s)
 cmd=['java','-Xmx384m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',f'{std}:{sdk}:{dex}','-d',str(p/'check.jar')]
 subprocess.run(cmd+[str(x) for x in p.glob('*.kt')]+[str(base/n) for n in production],check=True,timeout=110)
print('PASS full production toolbar/runtime/settings/glyph/coordinator + header/capsule/visuals/resource scope SDK compilation; real DexKit types, project dependency fixtures; no APK/R8/device claim')
