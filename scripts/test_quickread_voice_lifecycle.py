"""Exercise production controller/runtime lifecycle with JVM hook doubles."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
q=(r/'app/src/main/java/t/mma/hooks/items/quickread/QuickMarkReadFeature.kt').read_text();v=(r/'app/src/main/java/t/mma/hooks/items/fakevoiceduration/FakeVoiceDurationFeature.kt').read_text()
qb=q[q.index('    @Volatile private var installed'):q.index('    private fun collectDispatchMethods')]
vb=v[v.index('    @Volatile private var installed'):v.index('    private fun locateRecorderDurationMethod')]
code='''package t.mma.hooks.core
import java.lang.reflect.Method
open class Activity
class MotionEvent
class Host {fun dispatch(){};fun duration():Long=1}
open class XC_MethodHook {class MethodHookParam(val thisObject:Any, val args:Array<Any?> = emptyArray()){var result:Any?=null};open fun beforeHookedMethod(param:MethodHookParam){}}
object HookRegistry {class Handle {var active=true};val handles=mutableListOf<Handle>();val callbacks=mutableListOf<XC_MethodHook>();var fail=false
 fun get()=this
 fun hook(m:Method,c:XC_MethodHook):Handle {if(fail)error("hook failure");callbacks+=c;return Handle().also{handles+=it}}
 fun unhook(h:Handle){h.active=false}
}
class Context {fun hostContext()=this}
object FakeVoiceDurationFeature {fun isEnabled(c:Context)=true;fun durationMillis(c:Context)=9000}
class PendingDragSession
class DragSession {var resetCount=0;fun reset(){resetCount++;error("restore failure")}}
class Quick {val logger:(String,Throwable?)->Unit={_,_->};var touches=0
'''+qb+'''
 fun collectDispatchMethods()=setOf(Host::class.java.getDeclaredMethod("dispatch"))
 fun handleTouch(a:Activity,e:MotionEvent):Boolean {touches++;return true}
 fun seed(s:DragSession){session=s;pending=PendingDragSession()}
 fun empty()=session==null&&pending==null
}
class Voice {val logger:(String,Throwable?)->Unit={_,_->};val context=Context();var ready=false
'''+vb+'''
 fun locateRecorderDurationMethod():Method?=if(ready)Host::class.java.getDeclaredMethod("duration") else null
}
fun main(){
 HookRegistry.fail=true;val q=Quick();check(!q.install());HookRegistry.fail=false;check(q.install());check(q.install());check(HookRegistry.handles.size==1)
 val cb=HookRegistry.callbacks.last();val s=DragSession();q.seed(s);q.destroy();q.destroy();check(s.resetCount==1&&q.empty());check(!HookRegistry.handles.last().active);check(!q.install())
 cb.beforeHookedMethod(XC_MethodHook.MethodHookParam(Activity(),arrayOf(MotionEvent())));check(q.touches==0)
 val fresh=Quick();check(fresh.install());fresh.destroy()
 val voice=Voice();check(!voice.install());voice.ready=true;HookRegistry.fail=true;check(!voice.install());HookRegistry.fail=false;check(voice.install());val vc=HookRegistry.callbacks.last()
 val active=XC_MethodHook.MethodHookParam(Host());vc.beforeHookedMethod(active);check(active.result==9000L)
 voice.destroy();voice.destroy();check(!HookRegistry.handles.last().active);check(!voice.install());val stale=XC_MethodHook.MethodHookParam(Host());vc.beforeHookedMethod(stale);check(stale.result==null)
 val next=Voice();next.ready=true;check(next.install());next.destroy()
 check(HookRegistry.handles.none{it.active});println("PASS quickread/voice failure retry, idempotence, cleanup exception isolation, stale callbacks, release and fresh installation")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)
