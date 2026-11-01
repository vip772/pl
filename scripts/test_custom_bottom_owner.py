"""Extract production runtime gates and host queue; synthetic host, not device acceptance."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/custombottombar/CustomBottomBarFeature.kt').read_text()
def block(needle):
 a=s.index(needle);b=s.index('{',a);i=b+1;depth=1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
code='''import java.lang.reflect.Member
class Looper {companion object {val owner=Thread.currentThread();fun getMainLooper():Any=owner;fun myLooper():Any=Thread.currentThread()}}
class Handler(x:Any) {val tasks=java.util.concurrent.ConcurrentLinkedQueue<Runnable>();var reject=false;fun postDelayed(r:Runnable,d:Long):Boolean=if(reject)false else tasks.add(r);fun removeCallbacksAndMessages(x:Any?){tasks.clear()};fun drain(){while(true)(tasks.poll()?:return).run()}}
open class XC_MethodHook {class Unhook {var calls=0;fun unhook(){calls++;if(calls==1)error("native unhook fails")}}}
class Registry {var calls=0;fun hook(m:Member,c:XC_MethodHook):XC_MethodHook.Unhook {calls++;return XC_MethodHook.Unhook()};fun unhook(u:XC_MethodHook.Unhook){try {u.unhook()}finally {calls--}}}
object HookRegistry {val registry=Registry();fun get()=registry}
class Pref(val fail:Boolean) {var calls=0;fun unregisterOnSharedPreferenceChangeListener(x:Any){calls++;if(fail)error("pref unregister fails")}}
object CustomBottomBarFeature {val retired=mutableListOf<CustomBottomBarRuntime>();fun retire(o:CustomBottomBarRuntime){retired.add(o)};fun drainRetired(){val old=retired.toList();retired.clear();old.forEach{it.cleanups++}}}
class CustomBottomBarRuntime {
 @Volatile private var closed=false
 private val lifecycleLock=Any()
 val main=Handler(Looper.getMainLooper());private val pendingMain=linkedSetOf<MainTask>()
 val ownedHooks=linkedMapOf<Member,XC_MethodHook.Unhook>();var dexKitManager:Any?=Any()
 val customPrefs=Pref(true);val floatingPrefs=Pref(false);val customPreferenceListener=Any();val floatingPreferenceListener=Any()
 val logger:(String,Throwable?)->Unit={_,_->error("logger fails")}
 var cleanups=0;var executions=0
 fun pending()=synchronized(lifecycleLock){pendingMain.size}
 fun submit(){runOnMain{executions++}}
 fun hook(m:Member){hookOwned(m,object:XC_MethodHook(){})}
'''+block('    private inner class MainTask(')+block('    private fun runOnMain(')+block('    private fun safely(')+block('    private fun hookOwned(')+block('    fun destroy() {')+'''
}
'''+block('private class BottomBarHostTasks(')+'''
fun main(){
 Looper.getMainLooper()
 val method=String::class.java.getMethod("length")
 repeat(200){
  val old=CustomBottomBarRuntime();old.hook(method);old.hook(method);check(old.ownedHooks.size==1)
  val worker=Thread{repeat(100){old.submit()};check(old.pending()==64);old.destroy()};worker.start();worker.join(5000);check(!worker.isAlive)
  check(old.pending()==0&&old.main.tasks.isEmpty()&&old.ownedHooks.isEmpty())
  check(old.customPrefs.calls==1&&old.floatingPrefs.calls==1&&HookRegistry.get().calls==0)
  val fresh=CustomBottomBarRuntime();fresh.submit();check(fresh.executions==1&&old.cleanups==1)
  old.destroy();old.main.drain();old.hook(method);check(old.executions==0&&old.ownedHooks.isEmpty())
  fresh.destroy();CustomBottomBarFeature.drainRetired();check(fresh.cleanups==1)
 }
 val rejected=CustomBottomBarRuntime();rejected.main.reject=true;val t=Thread{rejected.submit()};t.start();t.join(5000);check(rejected.pending()==0);rejected.destroy();CustomBottomBarFeature.drainRetired()
 val tasks=BottomBarHostTasks {_,_->error("logger fails")};var count=0
 repeat(100){tasks.post{count++}}
 val f=tasks.javaClass.getDeclaredField("tasks").apply{isAccessible=true};check((f.get(tasks) as Set<*>).size==16)
 tasks.close();check((f.get(tasks) as Set<*>).isEmpty());tasks.post{count++};check(count==0)
 val h=tasks.javaClass.getDeclaredField("main").apply{isAccessible=true};(h.get(tasks) as Handler).drain();check(count==0)
 val q=BottomBarHostTasks {_,_->error("logger fails")};(h.get(q) as Handler).reject=true;q.post{count++};check((f.get(q) as Set<*>).isEmpty())
 q.safely{error("cleanup fails")};q.safely{count++};check(count==1);q.close()
 println("PASS 200 exact runtime owner sequences; hook dedup/unhook failure isolation; preferences; 64 runtime/16 host caps; rejected posts; no late execution")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=30)
