"""Compile production background request/refresh code and exercise cancellation races."""
from pathlib import Path
import subprocess, tempfile
root = Path(__file__).resolve().parents[1]
s = (root/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
def section(a,b): return s[s.index(a):s.index(b,s.index(a))]
request = section('    private class LoadRequest(', '    private data class LoadedBitmap(')
refresh = section('    private fun scheduleRefresh(', '    private fun refreshAll()')
loading = section('        val existing = synchronized(pendingLoads)', '    private fun ensureState(')
loader = section('    private val loader = ThreadPoolExecutor(', '    private val observedActivities')
code = '''import java.lang.ref.WeakReference
import java.util.concurrent.*
import java.util.WeakHashMap
object BackgroundBeautySettings {
 enum class Slot { WECHAT, CONTACTS }
 const val KEY_REVISION="rev"; const val KEY_ENABLE="enabled"; const val DEFAULT_ENABLE=true
}
class View { var isFinishing=false; val callbacks=mutableListOf<Runnable>()
 fun postOnAnimation(r:Runnable){callbacks+=r};fun removeCallbacks(r:Runnable){callbacks.remove(r)} }
open class ViewGroup:ViewBase(){val childCount=0;fun getChildAt(i:Int)=Any()}
open class ViewBase()
class Window {val decorView=View()}
class Activity {val window=Window();var isFinishing=false;var isDestroyed=false}
class Handler {val callbacks=mutableListOf<Runnable>();fun post(r:Runnable){synchronized(callbacks){callbacks+=r}}
 fun postDelayed(r:Runnable,d:Long){post(r)}
 fun drain(){val batch=synchronized(callbacks){callbacks.toList().also{callbacks.clear()}};batch.forEach{it.run()}} }
class Bitmap
class LoadedBitmap(val revision:Int,val bitmap:Bitmap)
class State {val loadedBitmaps=mutableMapOf<BackgroundBeautySettings.Slot,LoadedBitmap>()}
class Prefs {var revision=1;fun getInt(k:String,d:Int)=revision;fun getBoolean(k:String,d:Boolean)=true}
class Context {fun hostContext()=this}
object HLog {fun e(s:String,t:Throwable){throw AssertionError(s,t)}}
object BackgroundBeautyStore {var calls=0;fun loadBitmap(c:Context,s:BackgroundBeautySettings.Slot):Bitmap {calls++;return Bitmap()}}
class Fixture {
 val TAG="test";val prefs=Prefs();val context=Context();val main=Handler();var destroyed=false
 private val pendingLoads=WeakHashMap<Activity,LoadRequest>();private val pendingRefreshes=WeakHashMap<Activity,Runnable>()
 val observedActivities=WeakHashMap<Activity,Boolean>();val homeLists=mutableMapOf<ViewBase,BackgroundBeautySettings.Slot>()
 val dirtyBoundRows=mutableMapOf<Any,Boolean>();val state=State();var applied=0;var refreshes=0
 fun findActivity(v:ViewBase):Activity?=null
 fun applyForActivity(a:Activity){refreshes++}
 fun resolveTarget(a:Activity)=BackgroundBeautySettings.Slot.WECHAT
 fun hasBackgroundImage(s:BackgroundBeautySettings.Slot)=true
 fun ensureState(a:Activity)=state
 fun trimLauncherBitmaps(s:State,slot:BackgroundBeautySettings.Slot){}
 fun applyBitmap(s:State,slot:BackgroundBeautySettings.Slot,b:Bitmap){applied++}
'''+request+loader+refresh+'''
 fun load(activity:Activity,slot:BackgroundBeautySettings.Slot=BackgroundBeautySettings.Slot.WECHAT,revision:Int=prefs.revision){
'''+loading+'''
 fun queueRefresh(a:Activity)=scheduleRefresh(a)
 fun clear(a:Activity){ synchronized(pendingLoads){pendingLoads.remove(a)?.let(::cancelLoad)}
 synchronized(pendingRefreshes){pendingRefreshes.remove(a)}?.let{a.window.decorView.removeCallbacks(it)} }
 fun block():CountDownLatch {val entered=CountDownLatch(1);val release=CountDownLatch(1)
 loader.execute{entered.countDown();check(release.await(3,TimeUnit.SECONDS))};check(entered.await(3,TimeUnit.SECONDS));return release}
 fun settled(){loader.submit{}.get(3,TimeUnit.SECONDS)}
 fun queued()=loader.queue.size
 fun close(){loader.shutdownNow()}
}
fun main(){
 val f=Fixture();val a=Activity()
 try {
 // Old queued request is physically removed; replacement with same config has a new identity.
 val gate=f.block();f.load(a);check(f.queued()==1);f.clear(a);check(f.queued()==0)
 f.load(a);f.load(a);check(f.queued()==1);gate.countDown();f.settled();f.main.drain()
 check(BackgroundBeautyStore.calls==1 && f.applied==1)
 // Decode completed but its main callback is late: A -> clear -> A must reject the first result.
 f.load(a);f.settled();f.clear(a);f.load(a);f.settled();f.main.drain();check(f.applied==2)
 // Revision changes during decode-to-apply handoff: discard and request a refresh.
 f.load(a);f.settled();f.prefs.revision++;f.main.drain();check(f.applied==2)
 check(a.window.decorView.callbacks.size==1)
 val stale=a.window.decorView.callbacks.single();f.clear(a);check(a.window.decorView.callbacks.isEmpty())
 f.queueRefresh(a);stale.run();check(f.refreshes==0)
 val latest=a.window.decorView.callbacks.single();latest.run();check(f.refreshes==1)
 f.clear(a);a.window.decorView.callbacks.clear()
 // Host destruction between decode and UI callback never applies a bitmap.
 f.load(a);f.settled();a.isDestroyed=true;f.main.drain();check(f.applied==2)
 println("PASS production background lifecycle: queued cancellation/removal, dedup, A-clear-A late-result rejection, revision rejection, refresh identity/removal, destroyed host")
 }finally{f.close()}
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
