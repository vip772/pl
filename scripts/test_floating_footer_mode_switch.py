"""Production hook predicate: only acquired floating footer modes 1/2 suppress crossfade."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatFooterFeature.kt').read_text();a=s.index('                        val footer = param.thisObject as? View ?: return',s.index('val verifiedInputSwitch'));b=s.index('\n                    }',a);block=s[a:b]
code='''class View
class State(val reparented:Boolean)
class Param(val thisObject:Any?,val args:Array<Any?>)
var enabled=true
fun isEnabled()=enabled
val states=mutableMapOf<View,State>()
fun hook(param:Param){
'''+block+'''
}
fun main(){
 val v=View();states[v]=State(true)
 for(mode in listOf(1,2)){val p=Param(v,arrayOf(mode,true));hook(p);check(p.args[1]==false)}
 val other=Param(v,arrayOf(3,true));hook(other);check(other.args[1]==true)
 enabled=false;val disabled=Param(v,arrayOf(2,true));hook(disabled);check(disabled.args[1]==true)
 enabled=true;states[v]=State(false);val native=Param(v,arrayOf(2,true));hook(native);check(native.args[1]==true)
 states.clear();val unowned=Param(v,arrayOf(1,true));hook(unowned);check(unowned.args[1]==true)
 println("PASS modes1/2, unrelated mode, disabled, not reparented and unowned footer")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 d=Path(td);(d/'Test.kt').write_text(code)
 x=subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(d/'out.jar'),str(d/'Test.kt')],capture_output=True,text=True);assert x.returncode==0,x.stdout+x.stderr
 subprocess.run(['java','-cp',str(d/'out.jar')+':'+str(std),'TestKt'],check=True)