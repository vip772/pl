"""Execute production low-frequency send settlement, with deterministic scheduler stubs."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
files={'View.kt':'''package android.view
open class View {var isAttachedToWindow=true;var visibility=0;var isShown=true;var alpha=1f;val tasks=mutableListOf<Runnable>();fun postOnAnimation(r:Runnable){tasks.add(r)};fun removeCallbacks(r:Runnable):Boolean=tasks.remove(r);fun flush(){val copy=tasks.toList();tasks.clear();copy.forEach{it.run()}};companion object{const val VISIBLE=0}}
''','Button.kt':'package android.widget\nclass Button:android.view.View()','Test.kt':'''package t.mma.hooks.items.floatingchat
import android.view.View
import android.widget.Button
fun main(){
 val f=View();val b=Button();val a=Any();var allowed=true;var reads=0
 val current={reads++;if(allowed)b else null}
 b.alpha=0f;SendButtonSettlement.started(f,a);SendButtonSettlement.schedule(f,current);f.flush();check(b.alpha==0f&&reads==0)
 SendButtonSettlement.finished(f,a);repeat(10){SendButtonSettlement.schedule(f,current)};check(f.tasks.size==1);f.flush();check(b.alpha==1f&&reads==1)
 b.alpha=Float.NaN;SendButtonSettlement.schedule(f,current);f.flush();check(b.alpha==1f)
 b.alpha=0f;allowed=false;SendButtonSettlement.schedule(f,current);f.flush();check(b.alpha==0f)
 allowed=true;b.visibility=8;SendButtonSettlement.schedule(f,current);f.flush();check(b.visibility==8&&b.alpha==0f)
 b.visibility=0;b.isShown=false;SendButtonSettlement.schedule(f,current);f.flush();check(b.alpha==0f)
 b.isShown=true;SendButtonSettlement.schedule(f,current);val newer=Any();SendButtonSettlement.started(f,newer);f.flush();check(b.alpha==0f)
 SendButtonSettlement.finished(f,newer);SendButtonSettlement.schedule(f,current);SendButtonSettlement.cancel(f);check(f.tasks.isEmpty())
 repeat(1000){check(f.tasks.isEmpty())}
 println("PASS active/new animation guard, current can-send, hidden parent/button, NaN alpha, coalescing, cleanup and zero idle scheduling")
}'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,v in files.items():(p/n).write_text(v)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(r/'app/src/main/java/t/mma/hooks/items/floatingchat/SendButtonSettlement.kt'),*map(str,p.glob('*.kt'))],check=True,timeout=40)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.floatingchat.TestKt'],check=True,timeout=10)
s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatFooterFeature.kt').read_text()
assert 'SendButtonSettlement.cancel(footer)' in s
assert 'installSendAnimationSettlement(footerClass)' in s
assert 'SendButtonSettlement' not in s[s.index('    private fun ensurePreDrawTracking'):s.index('    private fun liftNewMessageBubble')]
print('PASS hook installation/cleanup and no pre-draw settlement wiring')