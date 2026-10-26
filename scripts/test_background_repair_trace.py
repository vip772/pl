"""Compile and run the production repair block with normal/error/disabled cases."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text();a=s.index('                            surfaceMutationDepth++',s.index('val applied = snapshot.appliedBackground'));b=s.index('                        if (applied == null)',a);block=s[a:b].rstrip();block=block[:block.rfind('}')].rstrip()
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
 repair(v,snap,applied);check(v.background===applied);check(surfaceMutationDepth==0)
 fail=true;check(runCatching{repair(v,snap,applied)}.isFailure);check(surfaceMutationDepth==0)
 fail=false;repair(v,snap,applied);check(v.background===applied);check(surfaceMutationDepth==0)
 println("PASS production repair block: background preserved, depth cleanup on success and exception")
}
'''}
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 d=Path(td)
 for n,t in files.items():(d/n).write_text(t)
 x=subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(d/'out.jar')]+list(map(str,d.glob('*.kt'))),capture_output=True,text=True)
 assert x.returncode==0,x.stdout+x.stderr
 subprocess.run(['java','-cp',str(d/'out.jar')+':'+str(std),'TestKt'],check=True,timeout=15)
