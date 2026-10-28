"""Compile group-bar production classes against Android SDK; project service stubs only."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
sources={
'Core.kt':'''package t.mma.hooks.core
open class BaseFeature {open fun featureId()="";open fun name()="";open fun onFeatureInit(context:FeatureContext){};open fun onFeatureInstall(context:FeatureContext){};open fun onFeatureDestroy(context:FeatureContext){};fun registerSettingsProvider(p:Any){}}
class FeatureContext {fun hostContext():android.content.Context=TODO();fun hostClassLoader():ClassLoader=TODO()}
object HookRegistry {fun get()=this;fun hook(m:java.lang.reflect.Member,h:de.robv.android.xposed.XC_MethodHook):de.robv.android.xposed.XC_MethodHook.Unhook=TODO();fun unhook(h:de.robv.android.xposed.XC_MethodHook.Unhook?){}}
''',
'Xposed.kt':'''package de.robv.android.xposed
open class XC_MethodHook {class Unhook;class MethodHookParam(val thisObject:Any,val args:Array<Any?>){var result:Any?=null};open fun beforeHookedMethod(param:MethodHookParam){};open fun afterHookedMethod(param:MethodHookParam){}}
object XposedBridge {fun invokeOriginalMethod(m:java.lang.reflect.Member,o:Any,a:Array<Any?>):Any?=null}
''',
'Store.kt':'''package t.mma.preferences
object HchatStorage {fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()}
''',
'Ui.kt':'''package t.mma.ui
object FeatureSettingsProvider {const val CATEGORY_ENHANCE="enhance"}
open class SimpleFeatureSettingsProvider(a:String,b:String,c:String,d:String,e:Boolean)
''',
'Utils.kt':'''package t.mma.utils
object HLog {fun w(s:String){};fun d(s:String){};fun i(s:String){};fun e(s:String,t:Throwable?=null){}}
object KavaReflector {fun findFieldRecursive(c:Class<*>,n:String):java.lang.reflect.Field?=null;fun findMethodRecursive(c:Class<*>,n:String,vararg t:Class<*>):java.lang.reflect.Method?=null}
''',
'Api.kt':'''package t.mma.hooks.api.core
object WeChatApis {fun currentActivity():ActivityApi?=null;fun contact()=Contact();fun databaseChanges():t.mma.hooks.api.runtime.WeChatDatabaseListenerApi?=null;fun conversationChanges():t.mma.hooks.api.conversation.WeChatConversationChangeApi?=null}
class ActivityApi {fun currentActivity():android.app.Activity?=null}
class Contact {fun contacts():Contacts?=null}
class Contacts {fun getPickerContacts()=listOf<User>();fun getPickerOfficialAccounts()=listOf<User>()}
class User(val wxId:String)
'''}

sources['Database.kt']='package t.mma.hooks.api.runtime\nclass WeChatDatabaseListenerApi {class QuerySubscription {fun unsubscribe(){}};fun install(){};fun subscribeQuery(callback:(String)->String):QuerySubscription?=null}\n'
sources['Change.kt']='package t.mma.hooks.api.conversation\nclass WeChatConversationChangeApi {class Subscription {fun unsubscribe(){}};fun subscribe(callback:(Any?)->Unit):Subscription?=null}\n'
sources['Group.kt']='package t.mma.hooks.items.conversationgroup\nobject ConversationGroupRuntime {fun afterDisabledRestore(context:android.content.Context,complete:(Boolean)->Unit){}}\n'
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,text in sources.items():(p/name).write_text(text)
 cmd=['java','-Xmx384m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',f'{std}:/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'check.jar'),*map(str,p.glob('*.kt')),*map(str,(r/'app/src/main/java/t/mma/hooks/items/groupbar').glob('*.kt')),str(r/'app/src/main/java/t/mma/hooks/core/DexInstallScheduler.kt'),str(r/'app/src/main/java/t/mma/hooks/items/homeextension/HomeExtensionSurface.kt')]
 subprocess.run(cmd,check=True,timeout=45)
print('PASS production group bar store, feature and runtime Android SDK compile')