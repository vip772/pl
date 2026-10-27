"""Compile and exercise the production seek binder with lifecycle/player stubs."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/homeextension/HomeMusic.kt').read_text();a=s.index('    private fun bindSeek(');b=s.index('    private fun time(',a)
code='''class Context
open class View {var isAttachedToWindow=true;interface OnAttachStateChangeListener {fun onViewAttachedToWindow(v:View);fun onViewDetachedFromWindow(v:View)}}
class SeekBar:View(){var max=0;var progress=0;var tag:Any?=null;var progressBackgroundTintList:Any?=null;lateinit var listener:OnSeekBarChangeListener;lateinit var attach:OnAttachStateChangeListener
 interface OnSeekBarChangeListener{fun onProgressChanged(v:SeekBar?,p:Int,user:Boolean);fun onStartTrackingTouch(v:SeekBar?);fun onStopTrackingTouch(v:SeekBar?)}
 fun setOnSeekBarChangeListener(l:OnSeekBarChangeListener){listener=l};fun addOnAttachStateChangeListener(l:OnAttachStateChangeListener){attach=l}}
class Main {val tasks=mutableListOf<Runnable>();fun post(r:Runnable){tasks.add(r)};fun postDelayed(r:Runnable,d:Long){tasks.add(r)};fun removeCallbacks(r:Runnable){tasks.removeAll{it===r}};fun tick(){val copy=tasks.toList();tasks.clear();copy.forEach{it.run()}}}
class Player{var duration=10000;var currentPosition=2000;fun seekTo(p:Int){currentPosition=p}}
class Runtime {val main=Main();var player:Player?=Player();var preparing=false;var changes=0;fun changed(){changes++}
'''+s[a:b].replace('android.content.res.ColorStateList.valueOf(0x55FFFFFF)','0x55FFFFFF')+'''
 fun bind(s:SeekBar,f:(Int,Int)->Unit)=bindSeek(s,null,f)
}
fun main(){val r=Runtime();val seek=SeekBar();var position=-1;var calls=0;r.bind(seek){p,d->position=p;calls++;check(d==10000)}
 seek.attach.onViewAttachedToWindow(seek);r.main.tick();check(position==2000&&seek.progress==200&&calls==1&&r.main.tasks.size==1)
 seek.listener.onStartTrackingTouch(seek);r.main.tick();check(calls==1)
 seek.progress=700;seek.listener.onStopTrackingTouch(seek);r.main.tick();check(position==7000&&r.changes==1)
 r.preparing=true;r.main.tick();check(calls==2)
 seek.isAttachedToWindow=false;seek.attach.onViewDetachedFromWindow(seek);check(r.main.tasks.isEmpty())
 println("PASS production binder: shared progress/time sample, single ticker, dragging protected, seek/resume refresh, preparing skip, detach cleanup")}
'''
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)
