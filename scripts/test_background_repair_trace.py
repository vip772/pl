"""Compile and run the production repair block with normal/error/disabled cases."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text();a=s.index('                            val repairStarted =');b=s.index('                        if (applied == null)',a);block=s[a:b].rstrip();block=block[:block.rfind('}')].rstrip()
files={'Test.kt':'''class View{var backgroundTintList:Any?=null;var backgroundTintMode:Any?=null;var background:Any?=null}
enum class Slot{CHAT};class Snapshot{val slot:Slot?=Slot.CHAT}
var surfaceMutationDepth=0
var fail=false
fun restoreSurfaceGeometry(v:View,s:Snapshot){if(fail)error("test")}
fun repair(view:View,snapshot:Snapshot,applied:Any){
'''+block+'''
}
fun main(){
 val v=View();val snap=Snapshot();val applied=Any()
 repair(v,snap,applied);check(v.background===applied);check(surfaceMutationDepth==0);check(t.mma.utils.ChatScrollTrace.phase=="outside");check(t.mma.utils.ChatScrollDiagnostic.ends==1)
 fail=true;check(runCatching{repair(v,snap,applied)}.isFailure);check(surfaceMutationDepth==0);check(t.mma.utils.ChatScrollTrace.phase=="outside");check(t.mma.utils.ChatScrollDiagnostic.ends==2)
 fail=false;t.mma.utils.ChatScrollDiagnostic.active=false;repair(v,snap,applied);check(v.background===applied);check(t.mma.utils.ChatScrollDiagnostic.ends==2);check(surfaceMutationDepth==0)
 println("PASS production repair block: background preserved, disabled no sample, phase/depth cleanup on success and exception")
}
''','Utils.kt':'''package t.mma.utils
object ChatScrollDiagnostic{var active=true;var ends=0;fun begin()=if(active)1L else 0L;fun detail(n:String,v:String){};fun end(n:String,t:Long){if(t!=0L)ends++}}
object ChatScrollTrace{var phase:String?="outside";fun enter(n:String):String?{val old=phase;phase=n;return old};fun leave(n:String?){phase=n}}
''','Os.kt':'package android.os\nobject SystemClock{fun elapsedRealtime()=1L}'}
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 d=Path(td)
 for n,t in files.items():(d/n).write_text(t)
 x=subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(d/'out.jar')]+list(map(str,d.glob('*.kt'))),capture_output=True,text=True)
 assert x.returncode==0,x.stdout+x.stderr
 subprocess.run(['java','-cp',str(d/'out.jar')+':'+str(std),'TestKt'],check=True,timeout=15)
