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
'Core.kt': 'package t.mma.hooks.core\nobject DexInstallScheduler {fun markDexBridgeReady(){}}\nclass HookRegistry {fun unhookAll(){};companion object {fun get()=HookRegistry()}}',
'Events.kt': 'package t.mma.event\nobject Events {class FeatureInstalled(s:String)}',
'Context.kt': '''package t.mma.hooks.core
class Config {fun getGlobalBoolean(k:String,d:Boolean)=d;fun getBoolean(f:String,k:String,d:Boolean)=d;fun getInt(f:String,k:String,d:Int)=d;fun getString(f:String,k:String,d:String)=d}
class FeatureContext{val bus=t.mma.event.EventBus();val ui=t.mma.ui.UIRegistry();fun hostContext()=this;fun eventBus()=bus;fun uiRegistry()=ui;fun configStore()=Config()}''',
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
 println("PASS lifecycle: reverse cleanup, failure isolation, late/racing registration, duplicate install/destroy, restart, partial failure, initialized-only release")
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,body in fixtures.items():(p/name).write_text(body)
 sources=[r/'app/src/main/java/t/mma/hooks/core'/name for name in ['FeatureResourceScope.kt','BaseFeature.kt','Feature.kt','FeatureManager.kt']]
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,sources),*map(str,p.glob('*.kt'))],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)
