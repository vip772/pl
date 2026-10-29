"""Exercise production pre-draw transaction: no intermediate draw, bounded rollback."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatFooterFeature.kt').read_text()
a=s.index('    private fun scheduleFooterApply(');b=s.index('    private fun removeGlobalTracking(',a)
method=s[a:b]
assert 'return paddingReady' not in s
assert 'val inputHeight = stableInputHeight' in s
assert 'if (tracker?.failed != true)' in s
assert 'refreshMeasuredInputHeight(footer, state)' in s
bg=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a=bg.index('        if (slot == BackgroundBeautySettings.Slot.CHAT &&',bg.index('private fun applyNativeChrome'))
b=bg.index(' return',a)
predicate=bg[a:b].strip()[4:-1]
code='''
object ChatBottomTrace {fun event(v:View,p:String,d:()->String){};fun listGeometry(v:View)=""}
class ViewTreeObserver {
 interface OnPreDrawListener {fun onPreDraw():Boolean}
 var isAlive=true;val listeners=mutableListOf<OnPreDrawListener>()
 fun addOnPreDrawListener(l:OnPreDrawListener){listeners.add(l)}
 fun removeOnPreDrawListener(l:OnPreDrawListener){listeners.remove(l)}
 fun frame():Boolean {var draw=true;listeners.toList().forEach{if(!it.onPreDraw())draw=false};return draw}
}
class View {
 var isAttachedToWindow=true;var visibility=0;val rootView get()=this
 val viewTreeObserver=ViewTreeObserver();var invalidations=0;var layouts=0;var isLayoutRequested=false
 fun requestLayout(){layouts++;isLayoutRequested=true};fun postInvalidateOnAnimation(){invalidations++}
 companion object{const val INVISIBLE=4}
}
class FooterTracker(val layout:View,val visibility:Int=0,var ready:Boolean=false,var failed:Boolean=false,var settlePasses:Int=0,
 var preDraw:ViewTreeObserver.OnPreDrawListener?=null,var attempts:Int=0,var failureReported:Boolean=false)
object HLog {fun w(s:String){}}
object BackgroundBeautySettings {enum class Slot{CHAT,WECHAT}}
class Native(val javaClass:Name)
class Name(val name:String)
fun skip(view:Native,slot:BackgroundBeautySettings.Slot) = PREDICATE
class Runtime {
 val INITIAL_LAYOUT_RETRY_COUNT=3;var enabled=true;var restored=0;var untracked=0
 val states=mutableMapOf<View,State>();var paddingCalls=0;var paddingStable=true
 class State(val listExtra:Int=223,val chatRecycler:View?=null)
 fun refreshMeasuredInputHeight(v:View,s:State){}
 fun applyChatListPadding(v:View,e:Int,s:State):Boolean {paddingCalls++;return paddingStable}
 val outcomes=java.util.ArrayDeque<Boolean>()
 fun isReadyChatLayout(v:View)=v.isAttachedToWindow
 fun isEnabled()=enabled
 var layoutAware=false;var passes=0
 fun apply(v:View):Boolean {
  if(!layoutAware)return if(outcomes.isEmpty())false else outcomes.removeFirst()
  passes++
  if(passes==1){v.requestLayout();return false}
  if(v.isLayoutRequested)return false
  if(passes==2){v.requestLayout();return false}
  return true
 }
 fun restore(v:View){restored++}
 fun removeGlobalTracking(t:FooterTracker){untracked++}
 fun panelState(v:View)=0
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
 r.states[v]=Runtime.State()
 check(v.viewTreeObserver.frame());check(v.viewTreeObserver.listeners.isEmpty())
 // Native layout clears the flag before pre-draw; retries must not set it again.
 val live=Runtime();live.layoutAware=true;val lv=View();val lt=FooterTracker(View())
 live.schedule(lv,lt);lv.isLayoutRequested=false
 check(!lv.viewTreeObserver.frame());lv.isLayoutRequested=false
 check(!lv.viewTreeObserver.frame());lv.isLayoutRequested=false
 check(!lv.viewTreeObserver.frame() && lt.ready && live.restored==0)
 check(lv.layouts==2)
 live.states[lv]=Runtime.State()
 check(lv.viewTreeObserver.frame() && lv.viewTreeObserver.listeners.isEmpty())
 // No forever pre-draw loop when native structure never becomes ready.
 val fail=Runtime();val fv=View();val ft=FooterTracker(View());fail.schedule(fv,ft)
 repeat(3){check(!fv.viewTreeObserver.frame())};check(fv.viewTreeObserver.frame())
 check(fail.restored==1 && ft.failureReported && fv.viewTreeObserver.listeners.isEmpty())
 // Repeated layout events cannot restart failed structural work.
 repeat(1000){fail.schedule(fv,ft);check(fv.viewTreeObserver.frame())}
 check(fail.restored==1 && ft.attempts==4)
 // Permanently pending padding never rolls back a successfully attached footer.
 val pending=Runtime();pending.paddingStable=false;pending.outcomes.add(true)
 val pv=View();val pt=FooterTracker(View());pending.states[pv]=Runtime.State()
 pending.schedule(pv,pt);check(!pv.viewTreeObserver.frame() && pt.ready)
 repeat(2){check(!pv.viewTreeObserver.frame())};check(pv.viewTreeObserver.frame())
 check(pending.restored==0 && pending.paddingCalls==3 && pv.viewTreeObserver.listeners.isEmpty())
 val off=Runtime();val ov=View();val ot=FooterTracker(View());off.schedule(ov,ot);off.enabled=false
 check(ov.viewTreeObserver.frame() && off.restored==1)
 val chat=BackgroundBeautySettings.Slot.CHAT
 check(skip(Native(Name("com.tencent.mm.ui.FrostedContentView")),chat))
 check(!skip(Native(Name("androidx.appcompat.widget.ActionBarContainer")),chat))
 check(skip(Native(Name("com.tencent.mm.ui.widget.WeUIBounceViewV2")),chat))
 println("PASS production entry transaction: padding before reveal, bounded rollback, disabled cleanup, failed callback circuit breaker, pending padding independent of structural success")
}
'''.replace('METHOD',method).replace('PREDICATE',predicate)
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 d=Path(td);(d/'Test.kt').write_text(code)
 x=subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(d/'out.jar'),str(d/'Test.kt')],capture_output=True,text=True)
 assert x.returncode==0,x.stdout+x.stderr
 subprocess.run(['java','-cp',str(d/'out.jar')+':'+str(std),'TestKt'],check=True)
