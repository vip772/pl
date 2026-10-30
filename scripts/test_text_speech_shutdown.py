"""Exercise production speech shutdown on main/background paths without Android services."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/textspeech/TextSpeechRuntime.kt').read_text()
a=s.index('    @Synchronized\n    fun shutdown()');b=s.index('    private fun settings()',a);shutdown=s[a:b]
code='''object Looper{var main=true;fun myLooper()=if(main)1 else 2;fun getMainLooper()=1}
class Handler{val pending=mutableListOf<()->Unit>();fun post(r:()->Unit){pending+=r};fun removeCallbacksAndMessages(v:Any?){pending.clear()};fun drain(){pending.toList().also{pending.clear()}.forEach{it()}}}
class Prefs{var removed=0;fun unregisterOnSharedPreferenceChangeListener(l:Any){removed++}}
class Runtime{var destroyed=false;val prefs=Prefs();val preferenceListener=Any();val mainHandler=Handler();var stopped=0;var closed=0
 fun stopAndClear(){stopped++};fun safeShutdownTts(){closed++}
'''+shutdown+'''}
fun main(){
 val main=Runtime();main.mainHandler.post{error("old work ran")};main.shutdown();main.shutdown();main.mainHandler.drain();check(main.destroyed&&main.stopped==1&&main.closed==1&&main.prefs.removed==1)
 Looper.main=false;val background=Runtime();background.mainHandler.post{error("old background work ran")};background.shutdown();background.shutdown();check(background.destroyed&&background.closed==0&&background.mainHandler.pending.size==1)
 background.mainHandler.drain();check(background.stopped==1&&background.closed==1&&background.prefs.removed==1)
 println("PASS speech shutdown: immediate main cleanup, posted background cleanup, old callbacks removed, duplicate shutdown ignored")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
f=(r/'app/src/main/java/t/mma/hooks/items/textspeech/TextSpeechFeature.kt').read_text()
assert f.index('trackResource { installed.shutdown()') < f.index('installVolumeKeyHook(installed)')
assert 'trackResource { HookRegistry.get().unhook(unhook) }' in f
assert 'if (destroyed) return false' in s[s.index('    fun handleVolumeKey'):s.index('    @Synchronized\n    fun shutdown')]

assert "installed.handleMessage(message)" in f and "installed.handleVolumeKey(event)" in f
assert "observing.compareAndSet(false, true)" in f and "val registerSubscription = subscriptionRegistrar()" in f
