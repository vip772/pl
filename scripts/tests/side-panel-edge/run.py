from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[3]
s=(r/'app/src/main/java/t/mma/hooks/items/homesidepanel/HomeSidePanelDrawer.kt').read_text()
a=s.index('    private fun onEdgeTouch');b=s.index('    private fun onPanelTouch',a)
lib=Path('/tmp/pl-kotlin-libs');stdlib=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
code='''class MotionEvent(val actionMasked:Int,val rawX:Float) {companion object {const val ACTION_DOWN=0;const val ACTION_MOVE=2;const val ACTION_UP=1;const val ACTION_CANCEL=3}}
class Animation {fun withEndAction(a:Any?)=this;fun cancel(){}}
class View {var visibility=0;var translationX=0f;var alpha=0f;fun animate()=Animation();companion object {const val VISIBLE=0;const val GONE=8}}
class Drawer {
 var enabled=true;var opened=false;var dragging=false;var downX=0f;var lastX=0f
 val panelWidth=300;val root=View();val panel=View();val edge=View();val scrim=View();var width=72
 fun dp(v:Int)=v
 fun resizeRoot(open:Boolean){width=if(open)1000 else 72}
 fun settleOpen(){opened=true;panel.translationX=0f}
 fun close(a:Boolean){opened=false;panel.visibility=8;width=72}
'''+s[a:b]+'''
 fun touch(a:Int,x:Float)=onEdgeTouch(MotionEvent(a,x))
}
fun main(){
 val d=Drawer();check(d.touch(0,20f));d.touch(2,24f);check(!d.opened)
 d.touch(2,220f);check(d.opened);check(d.width==1000);check(d.panel.visibility==0)
 d.touch(1,220f);check(d.opened);check(d.panel.translationX==0f)
 d.close(false);check(d.panel.visibility==8);d.touch(0,20f);d.touch(2,230f);d.touch(1,230f);check(d.opened);check(d.panel.visibility==0)
 val short=Drawer();short.touch(0,20f);short.touch(2,40f);short.touch(1,40f);check(!short.opened);check(short.width==72)
 val off=Drawer();off.enabled=false;check(!off.touch(0,20f));check(!off.opened)
 println("PASS: 历史边缘跟手拖动、展开阈值、短划关闭、关闭后重开面板可见、禁用不打开")
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(stdlib),'TestKt'],check=True,timeout=10)
runtime=(r/'app/src/main/java/t/mma/hooks/items/homesidepanel/HomeSidePanelRuntime.kt').read_text()
assert 'dispatchTouchEvent' not in runtime and 'setOnLongClickListener' not in runtime
assert 'setSystemGestureExclusionRects' in runtime
assert 'root.addView(edge, FrameLayout.LayoutParams(dp(72)' in s
feature=(r/'app/src/main/java/t/mma/hooks/items/homesidepanel/HomeSidePanelFeature.kt').read_text()
assert 'CATEGORY_PRACTICAL' in feature and 'CATEGORY_ENHANCE' not in feature
print('PASS: 恢复边缘View/系统排除策略、移除Activity触摸Hook/长按入口、移出增强分类')