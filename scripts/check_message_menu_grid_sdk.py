"""Compile complete production menu runtime/settings against Android SDK; no Gradle/APK."""
from pathlib import Path
import subprocess, tempfile
r = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
sdk = Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
stubs = {
'X.kt': '''package de.robv.android.xposed
open class XC_MethodHook {
 open fun beforeHookedMethod(param:MethodHookParam){}
 open fun afterHookedMethod(param:MethodHookParam){}
 class Unhook
 class MethodHookParam {val thisObject:Any=Any();val args:Array<Any?> = emptyArray();val result:Any?=null;fun hasThrowable()=false;fun setObjectExtra(k:String,v:Any?){};fun getObjectExtra(k:String):Any?=null}
}
''',
'Core.kt': '''package t.mma.hooks.core
class FeatureContext {fun hostContext():android.content.Context=TODO();fun hostClassLoader():ClassLoader=TODO()}
class HookRegistry {fun hook(m:java.lang.reflect.Method,h:de.robv.android.xposed.XC_MethodHook)=de.robv.android.xposed.XC_MethodHook.Unhook();fun unhook(h:de.robv.android.xposed.XC_MethodHook.Unhook){};companion object{fun get()=HookRegistry()}}
''',
'Api.kt': '''package t.mma.hooks.api.message
object SingleMessageMenuLocator {fun menuCreateMethods(c:t.mma.hooks.core.FeatureContext,e:(String,Throwable)->Unit):List<java.lang.reflect.Method> = emptyList()}
''',
'Reflect.kt': '''package t.mma.utils
object HLog {fun i(s:String){};fun e(s:String,t:Throwable?=null){}}
object KavaReflector {
 fun loadClass(n:String,c:ClassLoader):Class<*>?=null
 fun findDeclaredField(c:Class<*>,n:String):java.lang.reflect.Field?=null
 fun findDeclaredMethod(c:Class<*>,n:String,vararg t:Class<*>):java.lang.reflect.Method?=null
 fun declaredMethods(c:Class<*>):List<java.lang.reflect.Method> = emptyList()
 fun readField(f:java.lang.reflect.Field,o:Any):Any?=null
 fun writeField(f:java.lang.reflect.Field,o:Any,v:Any?)=true
 fun invokeOrThrow(m:java.lang.reflect.Method,o:Any,vararg a:Any?):Any?=null
}
''',
'Storage.kt': '''package t.mma.preferences
object HchatStorage {fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()}
''',
'Ui.kt': '''package t.mma.ui
interface FeatureSettingsProvider {companion object{const val CATEGORY_ENHANCE=""}}
open class SimpleFeatureSettingsProvider(a:String,b:String,c:String,d:String,e:Boolean)
'''
}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in stubs.items(): (p/n).write_text(s)
 src = r/'app/src/main/java/t/mma/hooks/items/messagemenuextension'
 command=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',f'{std}:{sdk}','-d',str(p/'check.jar')]
 subprocess.run(command + [str(f) for f in p.glob('*.kt')] + [str(src/n) for n in ['MessageMenuExtensionSettings.kt','MessageMenuGridGeometry.kt','MessageMenuGridRuntime.kt']],check=True,timeout=90)
 print('PASS complete production MessageMenuGridRuntime + settings Android SDK compilation; dependency stubs only; not APK/R8/host execution')