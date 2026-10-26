"""Compare cached background field reads with original hierarchy lookup."""
from pathlib import Path
import ast,subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
tree=ast.parse((r/'scripts/test_swipe_message_id.py').read_text())
stub=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stub' for t in n.targets))
main='''package t.mma.hooks.items.backgroundbeauty
import t.mma.utils.KavaReflector
open class Parent {@JvmField var inherited:Any?=1}
class Child:Parent(){@JvmField var own:Any?=2}
class Other {@JvmField var own:Any?=3}
fun main(){
 val reader=NativeFieldAccessors();val child=Child();var count=0
 for(o in listOf(child,Other())) for(n in listOf("own","inherited","missing")){
  repeat(100){check(reader.read(o,n)==KavaReflector.readField(o,n));count++}
 }
 reader.read(child,"own");val before=KavaReflector.searches
 repeat(1000){child.own=it;check(reader.read(child,"own")==it)}
 check(KavaReflector.searches==before)
 child.own=null;check(reader.read(child,"own")==null)
 check(reader.read(null,"own")==null)
 println("PASS $count field comparisons; inheritance, nulls, class isolation; 1000 live reads without lookup")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Main.kt').write_text(main);(p/'Reflector.kt').write_text(stub);jar=p/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),str(p/'Main.kt'),str(p/'Reflector.kt'),str(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/NativeFieldAccessors.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{std}','t.mma.hooks.items.backgroundbeauty.MainKt'],check=True,timeout=30)