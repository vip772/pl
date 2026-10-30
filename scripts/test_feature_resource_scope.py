"""Compile production lifecycle classes with host fixtures, without Gradle."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
fixtures={
'Log.kt': '''package t.mma.utils
object HLog {fun d(s:String){};fun i(s:String){};fun w(s:String){};fun e(s:String,t:Throwable?){}}
object KavaReflector {fun findMethod(c:Class<*>,n:String)=c.getMethod(n);fun invoke(m:java.lang.reflect.Method,o:Any)=m.invoke(o)}''',
'Event.kt': '''package t.mma.event
fun interface EventHandler<E>{fun handleEvent(e:E)}
class EventBus {fun post(e:Any){};var count=0;class Subscription{companion object{fun empty()=Subscription()}};fun <E> subscribe(c:Class<E>,h:EventHandler<E>,p:Int):Subscription {count++;return Subscription()};fun unsubscribe(s:Subscription){count--}}''',
'UI.kt': '''package t.mma.ui
interface FeatureSettingsProvider {fun featureId():String}
object FeatureToggleRegistry{fun isEnabled(c:Any,id:String)=true}
class UIRegistry {var count=0;fun registerProvider(p:FeatureSettingsProvider,c:()->Boolean){count++};fun unregisterProvider(id:String){count--}}''',
'Settings.kt': 'package t.mma.hooks.items.settings\nobject SettingsFeature {const val ID="settings"}',
'Terms.kt': 'package t.mma.preferences\nobject TermsGate {fun isAccepted(c:Any)=true}',
'Core.kt': 'package t.mma.hooks.core\nobject DexInstallScheduler {enum class Stage{WARMUP};class Installation {var closed=false;fun schedule(task:()->Boolean){if(!closed)task()};fun close(){closed=true}};fun installation(k:String,l:String)=Installation();fun markDexBridgeReady(){};fun schedule(k:String,l:String,stage:Stage,installer:()->Boolean){}}\nclass HookRegistry {fun unhookAll(){};companion object {fun get()=HookRegistry()}}',
'Events.kt': 'package t.mma.event\nobject Events {class FeatureInstalled(s:String);class DexReady}',
'Context.kt': '''package t.mma.hooks.core
class Config {fun getGlobalBoolean(k:String,d:Boolean)=d;fun getBoolean(f:String,k:String,d:Boolean)=d;fun getInt(f:String,k:String,d:Int)=d;fun getString(f:String,k:String,d:String)=d}
class FeatureContext{val bus=t.mma.event.EventBus();val ui=t.mma.ui.UIRegistry();fun hostContext()=this;fun eventBus()=bus;fun uiRegistry()=ui;fun configStore()=Config()}''',
'SendButton.kt': 'package t.mma.hooks.items.script\nobject ScriptSendButtonHook{fun install(c:Any)=true}',
'Music.kt': '''package t.mma.hooks.items.musicorder
object State {var fail=false;var closed=0;var sub:Any?=null;var track:((Any)->Unit)?=null}
class QQMusicOrderSettingsProvider:t.mma.ui.FeatureSettingsProvider{override fun featureId()="qq_music_order"}
class QQMusicOrderRuntime(c:Any,log:(String,Throwable?)->Unit){
 fun install(track:(Any)->Unit){State.track=track;val sub=t.mma.hooks.core.Sub();State.sub=sub;track(sub);if(State.fail)error("music start")}
 fun destroy(){State.closed++}
}''',
'Scheduled.kt': '''package t.mma.hooks.items.scheduledtask
object State {var fail=false;var closed=0;var attached:ScheduledTaskRuntime?=null}
class ScheduledTaskSettingsProvider:t.mma.ui.FeatureSettingsProvider{override fun featureId()="scheduled_task"}
object ScheduledTaskRuntimeCoordinator{fun attach(r:ScheduledTaskRuntime){State.attached=r};fun detach(r:ScheduledTaskRuntime){if(State.attached===r)State.attached=null}}
class ScheduledTaskRuntime(c:Any,log:(String,Throwable?)->Unit){fun start(){if(State.fail)error("scheduled start")};fun shutdown(){State.closed++}}
''',
'Preferences.kt': '''package android.content
interface SharedPreferences{fun getBoolean(k:String,d:Boolean):Boolean;fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener);fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener);fun interface OnSharedPreferenceChangeListener{fun onSharedPreferenceChanged(p:SharedPreferences,k:String?)}}
''',
'Toolbar.kt': '''package t.mma.hooks.items.chattoolbar
class Prefs:android.content.SharedPreferences{var enabled=false;val listeners=mutableListOf<android.content.SharedPreferences.OnSharedPreferenceChangeListener>();override fun getBoolean(k:String,d:Boolean)=enabled;override fun registerOnSharedPreferenceChangeListener(l:android.content.SharedPreferences.OnSharedPreferenceChangeListener){listeners+=l};override fun unregisterOnSharedPreferenceChangeListener(l:android.content.SharedPreferences.OnSharedPreferenceChangeListener){listeners-=l};fun toggle(v:Boolean){enabled=v;listeners.toList().forEach{it.onSharedPreferenceChanged(this,"enable")}}}
object ChatToolbarSettings{const val KEY_ENABLE="enable";const val DEFAULT_ENABLE=false;val prefs=Prefs();fun preferences(c:Any)=prefs}
class ChatToolbarSettingsProvider:t.mma.ui.FeatureSettingsProvider{override fun featureId()="chat_toolbar"}
object ToolbarState{var created=0;var destroyed=0;var fail=false;var last:ChatToolbarRuntime?=null}
class ChatToolbarRuntime(c:Any,logger:(String,Throwable?)->Unit){var active=true;init{ToolbarState.created++;ToolbarState.last=this};fun destroy(){check(active);active=false;ToolbarState.destroyed++};fun installBaseHooks(){if(ToolbarState.fail)error("toolbar install")};fun installDexHooks():Boolean{check(active);return true};fun onActivityDestroyed(a:Any){check(active)}}
''',
'Apis.kt': '''package t.mma.hooks.api.core
class Lifecycle{class Event{val isDestroy=true;val activity=Any()};var subscriptions=0;inner class Subscription{fun unsubscribe(){subscriptions--}};fun subscribe(callback:(Event)->Unit):Subscription{subscriptions++;return Subscription()}}
object WeChatApis{val events=Lifecycle();fun lifecycle()=events}
''',
'Test.kt': '''package t.mma.hooks.core
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
class Sub {var removed=0;fun unsubscribe(){removed++}}
class F:BaseFeature(){var installs=0;var destroys=0;var fail=false;val sub=Sub()
 override fun featureId()="fixture"
 override fun onFeatureInit(c:FeatureContext){registerSettingsProvider(object:t.mma.ui.FeatureSettingsProvider{override fun featureId()="fixture"})}
 override fun onFeatureInstall(c:FeatureContext){installs++;subscribe(String::class.java){};trackSubscription(sub);if(fail)error("install")}
 override fun onFeatureDestroy(c:FeatureContext){destroys++}
 fun late(release:()->Unit)=trackResource(release)
 fun registrar()=subscriptionRegistrar()
 fun held()=context()!=null
}
fun main(){
 var errors=0;val order=mutableListOf<Int>();val scope=FeatureResourceScope{errors++}
 scope.register{order+=1};scope.register{error("cleanup")};scope.register{order+=2}
 scope.close();scope.close();scope.register{order+=3}
 check(order==listOf(2,1,3)&&errors==1)
 val released=AtomicInteger();val concurrent=FeatureResourceScope{throw it};val pool=Executors.newFixedThreadPool(4)
 repeat(1000){pool.submit{concurrent.register{released.incrementAndGet()}}};pool.submit{concurrent.close()}
 pool.shutdown();check(pool.awaitTermination(5,TimeUnit.SECONDS));concurrent.close();check(released.get()==1000)
 val c=FeatureContext();val f=F();f.onInit(c);f.onInit(c);f.install(c);f.install(c)
 check(c.ui.count==1&&c.bus.count==1&&f.installs==1)
 f.onDestroy(c);f.onDestroy(c);check(c.ui.count==0&&c.bus.count==0&&f.destroys==1&&!f.held()&&f.sub.removed==1)
 f.late{released.incrementAndGet()};check(released.get()==1001)
 f.onInit(c);f.install(c);f.onDestroy(c);check(f.installs==2&&f.destroys==2&&c.ui.count==0&&c.bus.count==0)
 val generations=F();generations.onInit(c);generations.install(c)
 val oldRegistrar=generations.registrar();generations.onDestroy(c)
 generations.onInit(c);generations.install(c)
 val oldSub=Sub();oldRegistrar(oldSub);check(oldSub.removed==1)
 val newSub=Sub();generations.registrar()(newSub);check(newSub.removed==0)
 generations.onDestroy(c);check(oldSub.removed==1&&newSub.removed==1&&c.ui.count==0&&c.bus.count==0)
 val failed=F();failed.fail=true;failed.onInit(c);try{failed.install(c);error("expected failure")}catch(_:IllegalStateException){}
 check(c.ui.count==0&&c.bus.count==0&&!failed.held()&&failed.sub.removed==1)
 val disabled=F();disabled.onInit(c);disabled.onDestroy(c);check(c.ui.count==0)
 val m=FeatureManager();val enabled=F();val disabledFeature=object:Feature{
 var inits=0;var ends=0
 override fun featureId()="disabled";override fun isEnabled(c:FeatureContext)=false
 override fun onInit(c:FeatureContext){inits++};override fun install(c:FeatureContext){error("disabled installed")}
 override fun onDestroy(c:FeatureContext){ends++}
 }
 m.register(enabled).register(disabledFeature);m.installAll(c);m.installAll(c)
 check(enabled.installs==1 && disabledFeature.inits==1 && c.ui.count==1)
 m.destroyAll(c);check(enabled.destroys==1&&disabledFeature.ends==1&&c.ui.count==0&&c.bus.count==0)
 m.installAll(c);check(enabled.installs==2);m.destroyAll(c)
 t.mma.hooks.items.musicorder.State.fail=true
 val music=t.mma.hooks.items.musicorder.QQMusicOrderFeature()
 try{music.install(c);error("expected music failure")}catch(_:IllegalStateException){}
 check(t.mma.hooks.items.musicorder.State.closed==1 && (t.mma.hooks.items.musicorder.State.sub as Sub).removed==1)
 val musicOldTrack=t.mma.hooks.items.musicorder.State.track!!
 t.mma.hooks.items.musicorder.State.fail=false;music.install(c)
 val lateSub=Sub();musicOldTrack(lateSub);check(lateSub.removed==1)
 music.onDestroy(c);music.onDestroy(c);check(t.mma.hooks.items.musicorder.State.closed==2)
 t.mma.hooks.items.scheduledtask.State.fail=true
 val scheduled=t.mma.hooks.items.scheduledtask.ScheduledTaskFeature()
 try{scheduled.install(c);error("expected scheduler failure")}catch(_:IllegalStateException){}
 check(t.mma.hooks.items.scheduledtask.State.attached==null && t.mma.hooks.items.scheduledtask.State.closed==1)
 t.mma.hooks.items.scheduledtask.State.fail=false;scheduled.install(c)
 check(t.mma.hooks.items.scheduledtask.State.attached!=null)
 scheduled.onDestroy(c);scheduled.onDestroy(c)
 check(t.mma.hooks.items.scheduledtask.State.attached==null && t.mma.hooks.items.scheduledtask.State.closed==2 && c.ui.count==0)
 val toolbar=t.mma.hooks.items.chattoolbar.ChatToolbarFeature()
 val prefs=t.mma.hooks.items.chattoolbar.ChatToolbarSettings.prefs
 toolbar.install(c);check(t.mma.hooks.items.chattoolbar.ToolbarState.created==0&&prefs.listeners.size==1)
 repeat(20){prefs.toggle(true);prefs.toggle(true);check(t.mma.hooks.api.core.WeChatApis.events.subscriptions==1);prefs.toggle(false);prefs.toggle(false);check(t.mma.hooks.api.core.WeChatApis.events.subscriptions==0)}
 check(t.mma.hooks.items.chattoolbar.ToolbarState.created==20&&t.mma.hooks.items.chattoolbar.ToolbarState.destroyed==20)
 t.mma.hooks.items.chattoolbar.ToolbarState.fail=true;prefs.toggle(true)
 check(t.mma.hooks.items.chattoolbar.ToolbarState.destroyed==21)
 t.mma.hooks.items.chattoolbar.ToolbarState.fail=false;prefs.toggle(false);prefs.toggle(true)
 val oldListener=prefs.listeners.single();toolbar.onDestroy(c)
 check(prefs.listeners.isEmpty()&&t.mma.hooks.items.chattoolbar.ToolbarState.destroyed==22&&t.mma.hooks.api.core.WeChatApis.events.subscriptions==0)
 toolbar.install(c);val before=t.mma.hooks.items.chattoolbar.ToolbarState.created;oldListener.onSharedPreferenceChanged(prefs,"enable");check(t.mma.hooks.items.chattoolbar.ToolbarState.created==before)
 toolbar.onDestroy(c);check(prefs.listeners.isEmpty()&&c.ui.count==0&&c.bus.count==0)
 println("PASS lifecycle: reverse cleanup, failure isolation, late/racing registration, duplicate install/destroy, restart, partial failure, initialized-only release")
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 music=(r/'app/src/main/java/t/mma/hooks/items/musicorder/QQMusicOrderFeature.kt').read_text()
 scheduled=(r/'app/src/main/java/t/mma/hooks/items/scheduledtask/ScheduledTaskFeature.kt').read_text().split('object ScheduledTaskRuntimeCoordinator')[0]
 scheduled='\n'.join(line for line in scheduled.splitlines() if not line.startswith('import '))
 scheduled=scheduled.replace('package t.mma.hooks.items.scheduledtask','package t.mma.hooks.items.scheduledtask\nimport t.mma.hooks.core.BaseFeature\nimport t.mma.hooks.core.FeatureContext')
 (p/'ToolbarFeature.kt').write_text((r/'app/src/main/java/t/mma/hooks/items/chattoolbar/ChatToolbarFeature.kt').read_text())
 (p/'MusicFeature.kt').write_text(music)
 (p/'ScheduledFeature.kt').write_text(scheduled)
 for name,body in fixtures.items():(p/name).write_text(body)
 sources=[r/'app/src/main/java/t/mma/hooks/core'/name for name in ['FeatureResourceScope.kt','BaseFeature.kt','Feature.kt','FeatureManager.kt']]
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,sources),*map(str,p.glob('*.kt'))],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)
