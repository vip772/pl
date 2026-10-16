from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[3];src=(r/'app/src/main/java/t/mma/hooks/items/homesidepanel/HomeSidePanelRuntime.kt').read_text();a=src.index('        private val edgeGesture');b=src.index('        private var header:',a);block=src[a:b]
lib=Path('/tmp/pl-kotlin-libs');stdlib=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
code='''package t.mma.hooks.items.homesidepanel
class MotionEvent(var actionMasked:Int,val x:Float=0f,val y:Float=0f,val pointerCount:Int=1) {
 var action:Int get()=actionMasked;set(v){actionMasked=v}
 fun recycle(){}
 companion object {const val ACTION_DOWN=0;const val ACTION_MOVE=2;const val ACTION_UP=1;const val ACTION_CANCEL=3;const val ACTION_POINTER_DOWN=5;fun obtain(e:MotionEvent)=MotionEvent(e.actionMasked,e.x,e.y,e.pointerCount)}
}
class ViewConfiguration {val scaledTouchSlop=8;companion object {fun get(a:FakeActivity)=ViewConfiguration()}}
object HomeSidePanelSettings {var enabled=true;fun enabled(a:FakeActivity)=enabled}
class FakeActivity {var cancelled=0;lateinit var callback:(MotionEvent)->Boolean;fun dispatchTouchEvent(e:MotionEvent):Boolean {check(e.actionMasked==3);cancelled++;return callback(e)}}
class Drawer {var opened=false;var shows=0;fun isOpen()=opened;fun show(a:Boolean){opened=true;shows++}}
class Session {
 val activity=FakeActivity();val drawer=Drawer();var attached=true;var chatVisible=false
 fun dp(v:Int)=v
 init {activity.callback=::onEdgeTouch}
'''+block+'''
}
fun main(){
 val s=Session();s.onEdgeTouch(MotionEvent(0,3f,100f));check(!s.onEdgeTouch(MotionEvent(2,8f,100f)))
 check(s.onEdgeTouch(MotionEvent(2,60f,102f)));check(s.activity.cancelled==1);check(s.drawer.shows==1)
 check(s.onEdgeTouch(MotionEvent(2,80f,102f)));check(s.activity.cancelled==1)
 check(s.onEdgeTouch(MotionEvent(1,80f,102f)));check(!s.onEdgeTouch(MotionEvent(1)))
 s.onEdgeTouch(MotionEvent(0,3f,100f));check(!s.onEdgeTouch(MotionEvent(2,60f,102f)));check(s.drawer.shows==1)
 val chat=Session();chat.chatVisible=true;chat.onEdgeTouch(MotionEvent(0,3f,100f));check(!chat.onEdgeTouch(MotionEvent(2,60f,102f)))
 val multi=Session();multi.onEdgeTouch(MotionEvent(0,3f,100f));multi.onEdgeTouch(MotionEvent(5,3f,100f,2));check(!multi.onEdgeTouch(MotionEvent(2,60f,100f)))
 println("PASS: 原页面仅取消一次、接管到抬手并重置、抽屉开启不抢触摸、聊天页放行、多指取消候选")
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/items/homesidepanel/SidePanelEdgeGesture.kt')],check=True)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(stdlib),'t.mma.hooks.items.homesidepanel.TestKt'],check=True,timeout=10)
assert 'setOnLongClickListener' not in src
print('PASS: 已删除侧边栏长按监听安装/恢复代码')