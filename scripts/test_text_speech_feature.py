"""Compile production speech feature and verify event/Hook ownership across reinstall."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 files={
 'Core.kt':'''package t.mma.hooks.core
class FeatureContext{fun hostContext()=Any()}
open class BaseFeature{
 private var scope=FeatureResourceScope{}
 val callbacks=mutableListOf<()->Unit>()
 open fun featureId()="";open fun name()="";open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){}
 fun registerSettingsProvider(p:Any){}
 fun trackResource(r:()->Unit){scope.register(r)}
 fun subscriptionRegistrar():(Any)->Unit{val original=scope;return{s->original.register{(s as t.mma.hooks.api.core.Subscription).unsubscribe()}}}
 fun <E> subscribe(c:Class<E>,f:()->Unit){callbacks.add(f)}
 fun close(){scope.close()};fun reopen(){scope=FeatureResourceScope{}}
}
object HookRegistry{val callbacks=mutableListOf<de.robv.android.xposed.XC_MethodHook>();fun get()=this;fun hook(m:Any,c:de.robv.android.xposed.XC_MethodHook):Any{callbacks.add(c);return c};fun unhook(h:Any){}}
''',
 'Events.kt':'''package t.mma.event
object Events{class DexReady}
''',
 'Activity.kt':'''package android.app
class Activity
''',
 'Key.kt':'''package android.view
class KeyEvent
''',
 'Xposed.kt':'''package de.robv.android.xposed
open class XC_MethodHook{class MethodHookParam{var args=arrayOf<Any>();var result:Any?=null};open fun beforeHookedMethod(param:MethodHookParam){}}
''',
 'Utils.kt':'''package t.mma.utils
object HLog{fun e(s:String,t:Throwable?=null){}}
object KavaReflector{fun findDeclaredMethod(c:Class<*>,n:String,vararg p:Class<*>):Any?=Any()}
''',
 'API.kt':'''package t.mma.hooks.api.core
class Subscription(val f:(Any)->Unit){var active=true;fun unsubscribe(){active=false}}
class Observer{val subscriptions=mutableListOf<Subscription>();var fail=false;var onSubscribe:(()->Unit)?=null
 fun subscribe(f:(Any)->Unit):Subscription{if(fail)error("injected subscribe failure");return Subscription(f).also{subscriptions.add(it);onSubscribe?.invoke()}}}
object WeChatApis{val observer=Observer();fun message()=this;fun observe():Observer?=observer}
''',
 'Runtime.kt':'''package t.mma.hooks.items.textspeech
class TextSpeechSettingsProvider
class TextSpeechRuntime(c:Any){var closed=false;var messages=0;var keys=0;init{instances.add(this)};fun shutdown(){closed=true};fun handleMessage(m:Any){if(!closed)messages++};fun handleVolumeKey(e:android.view.KeyEvent):Boolean{if(closed)return false;keys++;return true};companion object{val instances=mutableListOf<TextSpeechRuntime>()}}
''',
 'Test.kt':'''package t.mma.hooks.items.textspeech
import t.mma.hooks.core.*
import t.mma.hooks.api.core.WeChatApis
fun key(c:de.robv.android.xposed.XC_MethodHook){val p=de.robv.android.xposed.XC_MethodHook.MethodHookParam();p.args=arrayOf(android.view.KeyEvent());c.beforeHookedMethod(p)}
fun main(){
 val feature=TextSpeechFeature();val c=FeatureContext();val observer=WeChatApis.observer
 feature.onFeatureInstall(c);val first=TextSpeechRuntime.instances.last();val event=feature.callbacks.last();val hook=HookRegistry.callbacks.last()
 event();event();check(observer.subscriptions.size==1)
 val old=observer.subscriptions.last();old.f(Any());key(hook);check(first.messages==1&&first.keys==1)
 feature.close();check(first.closed&&!old.active);feature.reopen();feature.onFeatureInstall(c)
 val second=TextSpeechRuntime.instances.last();old.f(Any());key(hook);check(second.messages==0&&second.keys==0)
 observer.fail=true;val fresh=feature.callbacks.last();try{fresh()}catch(_:IllegalStateException){};observer.fail=false
 fresh();fresh();check(observer.subscriptions.size==2);observer.subscriptions.last().f(Any());check(second.messages==1)
 feature.close();feature.reopen();feature.onFeatureInstall(c);observer.onSubscribe={feature.close()}
 feature.callbacks.last()();check(!observer.subscriptions.last().active)
 println("PASS full speech feature: repeated readiness deduplication, subscription failure recovery, old message/key isolation, close-during-subscribe immediate release")
}
'''
 }
 for n,s in files.items():(p/n).write_text(s)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt')),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt'),str(r/'app/src/main/java/t/mma/hooks/items/textspeech/TextSpeechFeature.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.textspeech.TestKt'],check=True,timeout=15)
