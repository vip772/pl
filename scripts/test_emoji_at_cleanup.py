"""Execute production emoji-at install/stop/destroy with failing resource endpoints.
Extracted methods and actual choice queue; not full Android linkage or device acceptance.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIBS = Path('/tmp/pl-kotlin-libs')
SOURCE = ROOT / 'app/src/main/java/t/mma/hooks/items/emojiat/EmojiAtRuntime.kt'

def method(source, marker):
    start = source.index(marker)
    opening = source.index('{', start)
    depth = 0
    for i in range(opening, len(source)):
        if source[i] == '{':
            depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0:
                return source[start:i + 1]
    raise AssertionError(marker)

def main():
    source = SOURCE.read_text()
    bodies = [method(source, marker) for marker in [
        '    fun install()', '    @Synchronized private fun stop()',
        '    fun destroy()' if '    @Synchronized fun destroy()' not in source else '    @Synchronized fun destroy()',
    ]]
    for marker in ['    private fun releaseSafely(', '    private fun releaseHooks(']:
        if marker in source:
            bodies.append(method(source, marker))
    listener = source[source.index('    private val listener ='):source.index('    private val cleanup =')]
    code = '''package t.mma.hooks.items.emojiat
import android.app.Application
import android.content.SharedPreferences
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
object EmojiAtSettings {const val ENABLE="enable"}
class Preferences: SharedPreferences {
 var fail=false;var removes=0;var adds=0;var reads=0
 override fun getBoolean(key:String,default:Boolean):Boolean {reads++;return true}
 fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){adds++}
 fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){removes++;if(fail)error("prefs remove")}
}
class Main {
 val queued=mutableListOf<()->Unit>();var failRemove=false;var failPost=false
 fun removeCallbacks(r:Any){if(failRemove)error("handler remove")}
 fun post(r:()->Unit):Boolean {if(failPost)error("handler post");queued.add(r);return true}
 fun drain(){val copy=queued.toList();queued.clear();copy.forEach{it()}}
}
class App:Application(){var removes=0;var adds=0;var fail=false
 override fun registerActivityLifecycleCallbacks(l:Any){adds++}
 override fun unregisterActivityLifecycleCallbacks(l:Any){removes++;if(fail)error("lifecycle remove")}
}
class Context(val app:App=App()){fun hostContext()=this;val applicationContext:Any get()=app}
class Handle(val fail:Boolean=false):java.io.Closeable {var calls=0;override fun close(){calls++;if(fail)error("close")}}
class Hook(val fail:Boolean=false){var calls=0}
object HookRegistry {fun get()=this;fun unhook(h:Hook){h.calls++;if(h.fail)error("unhook")}}
class Choice(val epoch:Int,val handle:Handle?)
internal class Owner {
 val context=Context();val prefs=Preferences();val main=Main()
 val lifecycle=Any();val cleanup=Any()
 val worker=Executors.newSingleThreadExecutor() as java.util.concurrent.ThreadPoolExecutor
 val scope=ThreadLocal<Any>();val pending=mutableListOf<Any>();val requests=mutableListOf<Any>()
 val handles=mutableListOf<Hook>();var storageHandle:java.io.Closeable?=null
 var installation:Handle?=null;var targets:Any?=Any()
 val choices=EmojiAtChoiceQueue<Choice>();var shown:Choice?=null
 var enabled=true;var alive=true;var generation=0;var installed=false
 var scheduled=0;var reports=0;var reporterFails=false
 fun schedule(){scheduled++}
 fun report(stage:String,e:Throwable){reports++;if(reporterFails)error("reporter")}
 fun drainChoices(){}
''' + listener + chr(10).join(bodies) + '''
 fun latePreference(){listener.onSharedPreferenceChanged(prefs,EmojiAtSettings.ENABLE)}
 fun invokeStop(){stop()}
}
fun main(){
 var failed=0
 fun verify(name:String,ok:Boolean){println((if(ok)"PASS " else "FAIL ")+name);if(!ok)failed++}
 for(mode in 0..3){
  val owner=Owner();owner.install();owner.install()
  verify("duplicate installation is idempotent mode=$mode",owner.prefs.adds==1&&owner.context.app.adds==1)
  val first=Hook(true);val second=Hook();owner.handles.add(first);owner.handles.add(second)
  val storage=Handle(true);owner.storageHandle=storage
  val ticket=Handle(true);owner.installation=ticket
  owner.pending.add(Any());owner.requests.add(Any())
  val dialog=Handle();val choice=Choice(0,dialog);owner.shown=choice;owner.choices.offer(choice);owner.choices.acquire()
  owner.prefs.fail=mode==0||mode==3;owner.context.app.fail=mode==1||mode==3
  owner.main.failRemove=mode==2||mode==3;owner.reporterFails=mode==3
  owner.destroy();owner.destroy();owner.main.drain()
  verify("cleanup endpoints and idempotence mode=$mode",!owner.alive&&!owner.enabled&&owner.worker.isShutdown&&first.calls==1&&second.calls==1&&storage.calls==1&&ticket.calls==1&&owner.handles.isEmpty()&&owner.storageHandle==null&&owner.installation==null&&owner.pending.isEmpty()&&owner.requests.isEmpty()&&dialog.calls==1&&owner.shown==null)
  val schedules=owner.scheduled;val epoch=owner.generation;owner.latePreference();owner.install()
  verify("destroyed owner refuses stale listener and reinstall mode=$mode",owner.scheduled==schedules&&owner.generation==epoch&&!owner.enabled&&owner.prefs.adds==1)
  check(owner.worker.awaitTermination(3,TimeUnit.SECONDS))
 }
 val owner=Owner();val bad=Hook(true);val good=Hook();owner.handles.addAll(listOf(bad,good));owner.invokeStop()
 verify("disable releases remaining hooks after native failure",bad.calls==1&&good.calls==1&&owner.handles.isEmpty())
 owner.destroy();owner.main.drain()
 check(failed==0){"emoji cleanup failures=$failed"}
 println("EMOJI-CLEANUP-OK")
}
'''
    with tempfile.TemporaryDirectory() as temp:
        p = Path(temp)
        (p/'Main.kt').write_text(code.replace('Executors.newSingleThreadExecutor() as java.util.concurrent.ThreadPoolExecutor', 'java.util.concurrent.ThreadPoolExecutor(1,1,1L,TimeUnit.SECONDS,java.util.concurrent.LinkedBlockingQueue<Runnable>())'))
        (p/'Application.kt').write_text('package android.app\nopen class Application {open fun registerActivityLifecycleCallbacks(l:Any){};open fun unregisterActivityLifecycleCallbacks(l:Any){}}'.replace('\n',chr(10)))
        (p/'Preferences.kt').write_text('package android.content\ninterface SharedPreferences {fun getBoolean(key:String,default:Boolean):Boolean;fun interface OnSharedPreferenceChangeListener {fun onSharedPreferenceChanged(p:SharedPreferences,key:String?)}}'.replace('\n',chr(10)))
        std=next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
        jar=p/'test.jar'
        subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,LIBS.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(jar),*map(str,p.glob('*.kt')),str(ROOT/'app/src/main/java/t/mma/hooks/items/emojiat/EmojiAtChoiceQueue.kt')],check=True,timeout=120)
        subprocess.run(['java','-cp',f'{jar}:{std}','t.mma.hooks.items.emojiat.MainKt'],check=True,timeout=30)

if __name__=='__main__':
    main()
