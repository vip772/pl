"""Production FakeLocation owners with synthetic host/Xposed; not device acceptance."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/fakelocation/FakeLocationFeature.kt').read_text()
def block(needle):
 a=s.index(needle);b=s.index('{',a);i=b+1;depth=1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
assert 'DexInstallScheduler.schedule(' not in s
assert 'resources.register { owner.close() }' in s
assert 'resources.register { installation.close() }' in s
assert 'installation.schedule { owner.install() }' in s
assert 'FakeLocationFeature.destroyProcessHooks()' in (r/'app/src/main/java/t/mma/hooks/core/FeatureManager.kt').read_text()
assert 'trackResource { runtime.close() }' in s
assert 'PreferenceRuntimeOwner(' in s
assert 'resources.register { context.eventBus().unsubscribe(subscription) }' in s
entry=(r/'app/src/main/java/t/mma/ModuleEntry.java').read_text()
assert 'FakeLocationFeature.destroyProcessHooks();' in entry
code='''import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
open class Context { val applicationContext:Context get()=this }
class FeatureContext {fun hostContext()=Context()}
open class XC_MethodHook {
 open fun beforeHookedMethod(param:MethodHookParam){}
 class MethodHookParam(var args:Array<Any?>?=null){var result:Any?=null}
 class Unhook(val method:Method,val callback:XC_MethodHook){var calls=0}
}
class Registry {
 val handles=java.util.concurrent.CopyOnWriteArrayList<XC_MethodHook.Unhook>()
 var failHook=false;var failUnhook=false
 @Volatile var entered:CountDownLatch?=null;@Volatile var release:CountDownLatch?=null
 fun hook(m:Method,c:XC_MethodHook):XC_MethodHook.Unhook {
  if(failHook)error("hook rejected")
  entered?.countDown();release?.let{check(it.await(5,TimeUnit.SECONDS))}
  return XC_MethodHook.Unhook(m,c).also{handles.add(it)}
 }
 fun unhook(h:XC_MethodHook.Unhook){try{h.calls++;if(failUnhook)error("native unhook failed")}finally{handles.remove(h)}}
}
object HookRegistry {val registry=Registry();fun get()=registry}
object KavaReflector {fun findMethodRecursive(c:Class<*>,n:String):Method?=runCatching{c.getMethod(n)}.getOrNull()}
object FakeLocationSettings {fun latitude(c:Context)=31.0;fun longitude(c:Context)=121.0}
object HLog {fun e(s:String,t:Throwable?){}}
object DexMethodCache {
 var callbacks=listOf<Method>();var fail=false
 fun prefs(c:Context,n:String)=Any()
 fun runtimeKey(c:Context,l:ClassLoader)=l
 fun loadListCrossProcess(p:Any,k:Any,l:ClassLoader,n:String):List<Method>{if(fail)error("cache failure");return callbacks}
}
object FakeLocationFeature {
 const val CACHE_PREFS_NAME="cache";const val CACHE_LOCATION_CALLBACKS="callbacks"
 var enabled=true;fun isEnabled(c:Context)=enabled
 private var appBrandOwner:FakeLocationProcessHooker?=null
 private var appBrandLoader:ClassLoader?=null
'''+block('        fun destroyProcessHooks()').replace('fun destroyProcessHooks()','@Synchronized fun destroyProcessHooks()')+block('        fun installAppBrandProcessHook(').replace('fun installAppBrandProcessHook(','@Synchronized fun installAppBrandProcessHook(')+'''
}
'''+block('private class FakeLocationProcessHooker(')+block('private fun isLocationCallback(')+'''
class MainOwner(private val context:FeatureContext,private val logger:(String,Throwable?)->Unit){
 private val owner=FakeLocationProcessHooker(context.hostContext(),logger)
 private var closed=false;private var installed=false
 var discoveries=0
 private fun locateLocationCallbacks():List<Method>{discoveries++;return DexMethodCache.callbacks}
'''+block('    fun close() {').replace('fun close()','@Synchronized fun close()')+block('    fun install(): Boolean {').replace('fun install()','@Synchronized fun install()')+'''
}
class TencentLocation {fun getLatitude():Double=1.0;fun getLongitude():Double=2.0}
class Rejected0
class Rejected1
class Rejected2
class Rejected3
class Rejected4
class Rejected5
class Rejected6
class Rejected7
class Rejected8
class Rejected9
class Rejected10
class Rejected11
class Rejected12
class Rejected13
class Rejected14
class Rejected15
class Rejected16
class Rejected17
class Rejected18
class Rejected19
class Rejected20
class Rejected21
class Rejected22
class Rejected23
class Rejected24
class Rejected25
class Rejected26
class Rejected27
class Rejected28
class Rejected29
class Rejected30
class Rejected31
class Rejected32
class Rejected33
class Rejected34
class Rejected35
class Rejected36
class Rejected37
class Rejected38
class Rejected39
class Rejected40
class Rejected41
class Rejected42
class Rejected43
class Rejected44
class Rejected45
class Rejected46
class Rejected47
class Rejected48
class Rejected49
class Rejected50
class Rejected51
class Rejected52
class Rejected53
class Rejected54
class Rejected55
class Rejected56
class Rejected57
class Rejected58
class Rejected59
class Rejected60
class Rejected61
class Rejected62
class Rejected63
class Rejected64
class Rejected65
class Rejected66
class Rejected67
class Rejected68
class Rejected69
class Callback {fun onLocationChanged(l:TencentLocation,n:Int,s:String){}}
fun field(o:Any,n:String):Any?=o.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(o)
fun fire(h:XC_MethodHook.Unhook,args:Array<Any?>?=null):XC_MethodHook.MethodHookParam {
 val p=XC_MethodHook.MethodHookParam(args);h.callback.beforeHookedMethod(p);return p
}
fun main(){
 val registry=HookRegistry.get();val c=Context()
 val method=Callback::class.java.getDeclaredMethod("onLocationChanged",TencentLocation::class.java,Integer.TYPE,String::class.java)
 DexMethodCache.callbacks=listOf(method)
 repeat(200){
  val a=FakeLocationProcessHooker(c){_,_->error("logger fails")}
  val b=FakeLocationProcessHooker(c){_,_->error("logger fails")}
  check(a.install(listOf(method,method)));check(a.install(listOf(method)));check(registry.handles.size==1)
  val oldCallback=registry.handles.single();fire(oldCallback,arrayOf(TencentLocation(),0,""));check(registry.handles.size==3)
  val getter=registry.handles.first{it.method.name=="getLatitude"};check(fire(getter).result==31.0)
  FakeLocationFeature.enabled=false;check(fire(getter).result==null);FakeLocationFeature.enabled=true
  check(b.install(listOf(method)));val fresh=registry.handles.last()
  registry.failUnhook=true;a.close();registry.failUnhook=false
  check(registry.handles.size==1&&registry.handles.single()===fresh)
  check((field(a,"hookedCallbacks") as Map<*,*>).isEmpty());check((field(a,"hookedGetters") as Map<*,*>).isEmpty())
  check(!a.install(listOf(method)));fire(oldCallback,arrayOf(TencentLocation(),0,""));check(fire(getter).result==null)
  a.close();b.close();check(registry.handles.isEmpty())
 }
 val failure=FakeLocationProcessHooker(c){_,_->};registry.failHook=true;check(!failure.install(listOf(method)));registry.failHook=false
 check(failure.install(listOf(method)));failure.close()
 val all=(String::class.java.declaredMethods.toList()+Class::class.java.declaredMethods.toList()).distinct()
 check(all.size>256)
 val bounded=FakeLocationProcessHooker(c){_,_->};check(!bounded.install(all));check((field(bounded,"hookedCallbacks") as Map<*,*>).size==128)
 val hookGetter=bounded.javaClass.declaredMethods.single{it.name=="hookGetter"}.apply{isAccessible=true}
 all.forEach{hookGetter.invoke(bounded,it,{1.0})};check((field(bounded,"hookedGetters") as Map<*,*>).size==256)
 bounded.close();check(registry.handles.isEmpty())
 val reject=FakeLocationProcessHooker(c){_,_->};check(reject.install(listOf(method)));val cb=registry.handles.single()
 fire(cb,arrayOf("not location",0,""));check((field(reject,"rejectedLocationClasses") as Set<*>).size==1)
 fire(cb,arrayOf("not location",0,""));check((field(reject,"rejectedLocationClasses") as Set<*>).size==1)
 repeat(70){i->fire(cb,arrayOf(Class.forName("Rejected$i").getDeclaredConstructor().newInstance(),0,""))}
 check((field(reject,"rejectedLocationClasses") as Set<*>).size==64)
 reject.close();check((field(reject,"rejectedLocationClasses") as Set<*>).isEmpty())
 val race=FakeLocationProcessHooker(c){_,_->};registry.entered=CountDownLatch(1);registry.release=CountDownLatch(1)
 val install=Thread{check(race.install(listOf(method)))};install.start();check(registry.entered!!.await(5,TimeUnit.SECONDS))
 val close=Thread{race.close()};close.start();registry.release!!.countDown();install.join(5000);close.join(5000)
 check(!install.isAlive&&!close.isAlive&&registry.handles.isEmpty());registry.entered=null;registry.release=null
 val main=MainOwner(FeatureContext()){_,_->};check(main.install());main.close();check(!main.install()&&main.discoveries==1)
 val l1=object:ClassLoader(){};val l2=object:ClassLoader(){}
 check(FakeLocationFeature.installAppBrandProcessHook(c,l1));val first=registry.handles.single()
 check(FakeLocationFeature.installAppBrandProcessHook(c,l1));check(registry.handles.size==1)
 check(FakeLocationFeature.installAppBrandProcessHook(c,l2));check(first.calls==1&&registry.handles.size==1)
 DexMethodCache.callbacks=emptyList();check(!FakeLocationFeature.installAppBrandProcessHook(c,l1));check(registry.handles.isEmpty())
 DexMethodCache.callbacks=listOf(method);check(FakeLocationFeature.installAppBrandProcessHook(c,l1))
 FakeLocationFeature.enabled=false;check(!FakeLocationFeature.installAppBrandProcessHook(c,l1));check(registry.handles.isEmpty())
 FakeLocationFeature.enabled=true;check(FakeLocationFeature.installAppBrandProcessHook(c,l1));FakeLocationFeature.destroyProcessHooks()
 check(registry.handles.isEmpty()&&field(FakeLocationFeature,"appBrandOwner")==null&&field(FakeLocationFeature,"appBrandLoader")==null)
 println("PASS 200 process owner sequences; exact hooks; close/install race; late callbacks; failure isolation; 128/256 budgets; 64-class rejected cache cap/cleanup; main owner and child loader replacement")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=30)