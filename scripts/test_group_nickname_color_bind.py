"""Execute production nickname-color bind helpers; no native visibility mutation."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/realtail/RealNameTailRenderer.kt').read_text()
a=s.index('    private fun applyNicknameColorAfterBind(');b=s.index('    private fun hideNicknameColor(',a)
code='''import java.util.WeakHashMap
class TextView {var text:CharSequence="name";var visibility=8;var isAttachedToWindow=true;var writes=0
 val tasks=mutableListOf<Runnable>();fun post(r:Runnable){tasks+=r};fun removeCallbacks(r:Runnable){tasks.remove(r)}
 fun drain(){val q=tasks.toList();tasks.clear();q.forEach{it.run()}}}
class Store {var enabled=true;fun isEnabled()=enabled;fun color()=1;fun weight()=400}
object ChatNameDecorationLayout {fun ensure(v:TextView)=v;fun showBaseNameStyle(v:TextView,c:Int,w:Int){v.writes++;v.text=String(v.text.toString().toCharArray())}}
class Runtime {
 val colorRebinds=WeakHashMap<TextView,Runnable>();val boundViews=WeakHashMap<TextView,String>();val nicknameColorStore=Store()
 fun bind(v:TextView,id:String){boundViews[v]=id;applyNicknameColorAfterBind(v,id)}
METHOD
}
fun main(){
 val r=Runtime();val v=TextView();r.bind(v,"a");check(v.visibility==8&&v.writes==1)
 v.drain();check(v.writes==1&&v.tasks.isEmpty()) // no idle duplicate compose
 r.bind(v,"a");v.text=String("name".toCharArray());v.drain();check(v.writes==3) // native same-string replacement
 repeat(20){r.bind(v,"a")};check(v.tasks.size==1);val before=v.writes;v.drain();check(v.writes==before)
 r.bind(v,"a");r.boundViews[v]="b";v.text="new";val recycled=v.writes;v.drain();check(v.writes==recycled)
 val empty=TextView();empty.text="";r.bind(empty,"a");empty.text="late";empty.drain();check(empty.writes==1)
 r.bind(v,"a");r.nicknameColorStore.enabled=false;v.text="new";val disabled=v.writes;v.drain();check(v.writes==disabled)
 r.nicknameColorStore.enabled=true;r.bind(v,"a");v.isAttachedToWindow=false;v.text="new";val detached=v.writes;v.drain();check(v.writes==detached)
 println("PASS hidden name styling without reveal; late text, same-string native overwrite, single post-bind pass, coalescing, sender recycle, disabled/detached guards")
}
'''.replace('METHOD',s[a:b])
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)
assert 'colorRebinds.remove(tv)?.let(tv::removeCallbacks)' in s[s.index('    private fun clearBoundDecorations'):]
print('PASS invalid/private/outgoing binding cleanup cancels pending color work')