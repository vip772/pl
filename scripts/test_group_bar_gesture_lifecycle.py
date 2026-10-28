"""Execute production gesture/reset/reload gate bodies with deterministic scheduling."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/groupbar/GroupBarRuntime.kt').read_text()
a=s.index('        fun userTouchStarted()');z=s.index('        private fun selectNativeTop',a)
methods=s[a:z]
a=s.index('        private val resetAfterData = Runnable');z=s.index('        private val switchObserver',a)
reset=s[a:z]
code='''import java.lang.ref.WeakReference
class View
class Field {var open=false;fun getBoolean(v:View)=open}
class Main {val tasks=mutableListOf<Runnable>();fun removeCallbacks(r:Runnable){tasks.removeAll{it===r}};fun post(r:Runnable){tasks+=r};fun drain(){val q=tasks.toList();tasks.clear();q.forEach{it.run()}}}
class Runtime {var disposed=false;var enabled=true;var pendingSwitchReset=false;var userInteracting=false;var headerOpen=false
 val view=View();val viewRef=WeakReference(view);val openField=Field();val main=Main();var selects=0;var reloads=0
 val drainReload=Runnable{}
 fun traceState(s:String){};fun selectNativeTop(v:View){selects++};fun reload(){reloads++}
'''+reset+methods+'''
 fun notifyData(){resetAfterData.run()}
}
fun main(){
 val r=Runtime();r.switchGroup();check(r.selects==0&&r.reloads==1)
 r.userTouchStarted();r.notifyData();check(r.selects==0&&!r.pendingSwitchReset)
 r.userTouchEnded();r.main.drain();check(r.selects==0)
 r.switchGroup();r.notifyData();check(r.selects==1&&!r.pendingSwitchReset)
 r.notifyData();check(r.selects==1)
 r.switchGroup();r.openField.open=true;r.notifyData();check(r.selects==1)
 r.userTouchStarted();r.openField.open=false;r.userTouchEnded();r.main.drain();check(r.selects==1)
 r.switchGroup();r.disposed=true;r.notifyData();check(r.selects==1)
 println("PASS no old-result reset; synchronous single data-completion selection; gesture/open/dispose cancel")
}
'''
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)
assert 'val open = nativeHeaderOpen()' in s
assert 'copy.offsetLocation(' in s and 'forward(start)' in s
assert 'cancel.action = android.view.MotionEvent.ACTION_CANCEL' in s
assert 'if (userInteracting || nativeHeaderOpen()) { reloadDeferred = true; return }' in s
print('PASS open-only visibility, vertical sequence handoff and all reload-source gesture gate integration')