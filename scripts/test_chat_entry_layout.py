"""Exercise production pre-draw transaction: no intermediate draw, bounded rollback."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatFooterFeature.kt').read_text()
a=s.index('    private fun scheduleFooterApply(');b=s.index('    private fun removeGlobalTracking(',a)
method=s[a:b]
assert 'return paddingReady' in s
assert 'val inputHeight = stableInputHeight' in s
bg=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a=bg.index('        if (slot == BackgroundBeautySettings.Slot.CHAT &&',bg.index('private fun applyNativeChrome'))
b=bg.index(' return',a)
predicate=bg[a:b].strip()[4:-1]
code='''
class ViewTreeObserver {
 interface OnPreDrawListener {fun onPreDraw():Boolean}
 var isAlive=true;val listeners=mutableListOf<OnPreDrawListener>()
 fun addOnPreDrawListener(l:OnPreDrawListener){listeners.add(l)}
 fun removeOnPreDrawListener(l:OnPreDrawListener){listeners.remove(l)}
 fun frame():Boolean {var draw=true;listeners.toList().forEach{if(!it.onPreDraw())draw=false};return draw}
}
class View {
 var isAttachedToWindow=true;var visibility=0;val rootView get()=this
 val viewTreeObserver=ViewTreeObserver();var invalidations=0;var layouts=0
 fun requestLayout(){layouts++};fun postInvalidateOnAnimation(){invalidations++}
 companion object{const val INVISIBLE=4}
}
class FooterTracker(val layout:View,val visibility:Int=0,var ready:Boolean=false,
 var preDraw:ViewTreeObserver.OnPreDrawListener?=null,var attempts:Int=0,var failureReported:Boolean=false)
object HLog {fun w(s:String){}}
object BackgroundBeautySettings {enum class Slot{CHAT,WECHAT}}
class Native(val javaClass:Name)
class Name(val name:String)
fun skip(view:Native,slot:BackgroundBeautySettings.Slot) = PREDICATE
class Runtime {
 val INITIAL_LAYOUT_RETRY_COUNT=3;var enabled=true;var restored=0;var untracked=0
 val outcomes=java.util.ArrayDeque<Boolean>()
 fun isReadyChatLayout(v:View)=v.isAttachedToWindow
 fun isEnabled()=enabled
 fun apply(v:View)=if(outcomes.isEmpty())false else outcomes.removeFirst()
 fun restore(v:View){restored++}
 fun removeGlobalTracking(t:FooterTracker){untracked++}
 val TAG="test"
 METHOD
 fun schedule(v:View,t:FooterTracker)=scheduleFooterApply(v,t)
}
fun main(){
 // Initial hierarchy pass, then padding pass, then settled native list layout.
 val r=Runtime();r.outcomes.addAll(listOf(false,false,true));val v=View();val t=FooterTracker(View())
 r.schedule(v,t);check(!v.viewTreeObserver.frame() && !t.ready)
 check(!v.viewTreeObserver.frame() && !t.ready)
 check(!v.viewTreeObserver.frame() && t.ready && r.untracked==1)
 check(v.viewTreeObserver.frame());check(v.viewTreeObserver.listeners.isEmpty())
 // No forever pre-draw loop when native structure never becomes ready.
 val fail=Runtime();val fv=View();val ft=FooterTracker(View());fail.schedule(fv,ft)
 repeat(3){check(!fv.viewTreeObserver.frame())};check(fv.viewTreeObserver.frame())
 check(fail.restored==1 && ft.failureReported && fv.viewTreeObserver.listeners.isEmpty())
 val off=Runtime();val ov=View();val ot=FooterTracker(View());off.schedule(ov,ot);off.enabled=false
 check(ov.viewTreeObserver.frame() && off.restored==1)
 val chat=BackgroundBeautySettings.Slot.CHAT
 check(!skip(Native(Name("com.tencent.mm.ui.FrostedContentView")),chat))
 check(!skip(Native(Name("androidx.appcompat.widget.ActionBarContainer")),chat))
 check(skip(Native(Name("com.tencent.mm.ui.widget.WeUIBounceViewV2")),chat))
 println("PASS production entry transaction: padding before reveal, bounded rollback, disabled cleanup, chat frosted scope")
}
'''.replace('METHOD',method).replace('PREDICATE',predicate)
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 d=Path(td);(d/'Test.kt').write_text(code)
 x=subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(d/'out.jar'),str(d/'Test.kt')],capture_output=True,text=True)
 assert x.returncode==0,x.stdout+x.stderr
 subprocess.run(['java','-cp',str(d/'out.jar')+':'+str(std),'TestKt'],check=True)
