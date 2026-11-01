"""Execute production shared-card restore and capture logic using JVM property stubs."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatCardVisuals.kt').read_text()
a=s.index('    private fun restoreSafely(')
methods=s[a:s.rindex('}')]
capture=next(line.strip() for line in s.splitlines() if 'if (!originals.containsKey(view)) originals[view]' in line)
code="""
class View(val fail:Boolean=false){var attempts=0;var background:Any?=null;set(v){attempts++;if(fail)error("background");field=v};var backgroundTintList:Any?=null;var backgroundTintMode:Any?=null}
class Original(val background:Any?,val tint:Any?,val tintMode:Any?)
object Cards{val originals=linkedMapOf<View,Original>();val applied=linkedMapOf<View,Any>()
fun claim(view:View){
"""+capture+"""
}
"""+methods+"""
}
fun main(){val v=View();val bg=Any();val tint=Any();val mode=Any();v.background=bg;v.backgroundTintList=tint;v.backgroundTintMode=mode;Cards.claim(v);v.background=Any();v.backgroundTintList=null;v.backgroundTintMode=null;Cards.claim(v);Cards.applied[v]=Any();Cards.restore(v);check(v.background===bg&&v.backgroundTintList===tint&&v.backgroundTintMode===mode&&Cards.originals.isEmpty()&&Cards.applied.isEmpty());val count=v.attempts;Cards.restore(v);check(v.attempts==count)
val failing=View(true);Cards.originals[failing]=Original(bg,tint,mode);Cards.applied[failing]=Any();Cards.restore(failing);check(failing.backgroundTintList===tint&&failing.backgroundTintMode===mode&&Cards.originals.isEmpty()&&Cards.applied.isEmpty())
val nulls=View();Cards.claim(nulls);nulls.background=Any();Cards.restore(nulls);check(nulls.background==null)
println("PASS production card capture/restore: original nullable background/tint/mode, repeated apply, throwing property/logger isolation, ownership cleanup and idempotence")}
"""
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code);(p/'Log.kt').write_text('package t.mma.utils;object HLog{fun e(s:String,t:Throwable){error("logger")}}')
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt'))],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
