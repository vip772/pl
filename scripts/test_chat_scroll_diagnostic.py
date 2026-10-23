from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs')
files={
'Android.kt':'''package android.content
open class Context {val applicationContext:Context get()=this}
class Prefs {fun getBoolean(k:String,d:Boolean)=d}
''',
'Os.kt':'''package android.os
object SystemClock {var now=1000L;fun elapsedRealtime()=now;fun elapsedRealtimeNanos()=now*1000000}
class Looper {companion object {val main=Looper();fun myLooper()=main;fun getMainLooper()=main}}
''',
'Storage.kt':'''package t.mma.preferences
object HchatStorage {fun preferences(c:android.content.Context,n:String)=android.content.Prefs()}
''',
'Log.kt':'''package t.mma.utils
internal object ChatScrollTrace {fun start(){};fun stop(){}}
object RuntimeLogStore {var enabled=true;val logs=mutableListOf<String>();fun isEnabled(c:android.content.Context)=enabled;fun log(l:String,m:String){logs.add(m)}}
''',
'Test.kt':'''import t.mma.utils.*
import android.os.SystemClock
fun main(){
 val c=android.content.Context()
 check(!ChatScrollDiagnostic.isActive());check(ChatScrollDiagnostic.begin()==0L)
 ChatScrollDiagnostic.hit("disabled");check(RuntimeLogStore.logs.isEmpty())
 ChatScrollDiagnostic.start(c,"D");check(ChatScrollDiagnostic.isActive())
 repeat(2000){ChatScrollDiagnostic.query("private-group","private-member");ChatScrollDiagnostic.hit("nickname.total")}
 val t=ChatScrollDiagnostic.begin();SystemClock.now+=3;ChatScrollDiagnostic.end("timed",t)
 ChatScrollDiagnostic.geometry(100,130,30);ChatScrollDiagnostic.stop()
 val text=RuntimeLogStore.logs.joinToString();check(!text.contains("private-group"));check(!text.contains("private-member"))
 check(text.contains("repeatQueries=1999"));check(text.contains("count=2000"));check(text.contains("maxUs=3000"))
 check(text.contains("expanded=130"));check(!ChatScrollDiagnostic.isActive())
 ChatScrollDiagnostic.start(c,"A");SystemClock.now+=300001;check(!ChatScrollDiagnostic.isActive())
 val tick=ChatScrollDiagnostic.javaClass.getDeclaredMethod("tick");tick.isAccessible=true;tick.invoke(ChatScrollDiagnostic)
 val worker=ChatScrollDiagnostic.javaClass.getDeclaredField("worker");worker.isAccessible=true;check(worker.get(ChatScrollDiagnostic)==null)
 RuntimeLogStore.enabled=false;ChatScrollDiagnostic.start(c,"B");check(!ChatScrollDiagnostic.isActive())
 println("PASS default-off, timed stop, aggregation, privacy and worker release")
}
'''}
with tempfile.TemporaryDirectory() as td:
 d=Path(td)
 for n,s in files.items():(d/n).write_text(s)
 std=next(libs.glob('kotlin-stdlib-*.jar'))
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(d/'test.jar')]+list(map(str,d.glob('*.kt')))+[str(r/'app/src/main/java/t/mma/utils/ChatScrollDiagnostic.kt')]
 a=subprocess.run(cmd,capture_output=True,text=True);assert a.returncode==0,a.stdout+a.stderr
 a=subprocess.run(['java','-cp',str(d/'test.jar')+':'+str(std),'TestKt'],capture_output=True,text=True,timeout=15);print(a.stdout,a.stderr);assert a.returncode==0
