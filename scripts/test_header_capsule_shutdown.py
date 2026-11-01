"""Execute actual Capsule restore/destroy with failing Android/UI cleanup stubs."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderCapsule.kt').read_text()
a=s.index('    private fun releaseSafely(');b=s.index('    private fun collectMenuActions',a)
code="""
object HLog {var calls=0;fun e(s:String,t:Throwable){calls++;error("logger")}}
open class View {var alpha=1f;var importantForAccessibility=0;var cleared=false;fun setOnClickListener(x:Any?){cleared=true}}
class Observer {val isAlive=true;fun removeOnWindowFocusChangeListener(x:Any){error("observer")}}
class ViewGroup(val fail:Boolean=false):View(){val viewTreeObserver=Observer();var background:Any?=null;var removed=false;fun removeView(v:Any){removed=true;if(fail)error("remove")}}
class Image:View(){var clearedImage=false;fun setImageDrawable(x:Any?){clearedImage=true}}
class ChildState(val alpha:Float,val accessibility:Int)
class State {var generation=0;var focusListener:Any?=Any();val row=View();val back=View();val more=View();val center=View();val avatar=Image();val background:Any=Any();val hidden=linkedMapOf<View,ChildState>();var backTarget:Any?=Any();var moreTarget:Any?=Any();var titleTarget:Any?=Any()}
object FloatingChatCardVisuals{var calls=0;fun releasePresentation(v:View){calls++;if(calls==1)error("visual")}}
class Worker{var calls=0;fun shutdownNow(){calls++;error("worker")}}
class Handler{var calls=0;fun removeCallbacksAndMessages(x:Any?){calls++;error("handler")}}
class Cache{var calls=0;fun evictAll(){calls++}}
class Capsule{val states=linkedMapOf<ViewGroup,State>();var closed=false;var workerStarted=true;val worker=Worker();val main=Handler();val cache=Cache();var hostVersionName:String?="v";val resourceIds=mutableMapOf(1 to 1)
"""+s[a:b]+"""
}
fun main(){val c=Capsule();val h1=ViewGroup(true);val h2=ViewGroup();val s1=State();val s2=State();val child=View();s1.hidden[child]=ChildState(.3f,4);c.states[h1]=s1;c.states[h2]=s2;c.destroy()
check(c.closed&&c.states.isEmpty()&&s1.generation==1&&s2.generation==1)
check(h1.removed&&h2.removed&&h1.background===s1.background&&h2.background===s2.background)
check(child.alpha==.3f&&child.importantForAccessibility==4&&s1.hidden.isEmpty())
check(s1.focusListener==null&&s2.focusListener==null&&s1.backTarget==null&&s1.moreTarget==null&&s1.titleTarget==null)
check(s1.back.cleared&&s1.more.cleared&&s1.center.cleared&&s1.avatar.clearedImage)
check(c.worker.calls==1&&c.main.calls==1&&c.cache.calls==1&&c.hostVersionName==null&&c.resourceIds.isEmpty())
check(FloatingChatCardVisuals.calls==12);c.destroy();check(c.worker.calls==1&&c.cache.calls==1)
println("PASS production Capsule restore/destroy: observer/visual/view/worker/handler/logger failures isolated; state cleared; idempotent")}
"""
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
assert 'ArrayBlockingQueue<Runnable>(16)' in s and 'catch (_: RejectedExecutionException)' in s
print('PASS source guards: bounded avatar queue and explicit rejection handling (structural only)')
