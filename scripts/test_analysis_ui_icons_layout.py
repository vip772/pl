"""Compile real icon code and extracted dialog sizing against JVM stubs; no APK/device."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'app/src/main/java/t/mma/hooks/items/conversationanalysis'
report = (BASE / 'ConversationAnalysisReportDialog.kt').read_text()
menu = (BASE / 'ConversationAnalysisMessageMenuRuntime.kt').read_text()
painter = (BASE / 'ConversationAnalysisIconPainter.kt').read_text()
for glyph in ('▤', '⚙', '⌃', '⌄'):
    assert glyph not in report, glyph
assert 'drawText' not in painter
assert 'val topSampling = ConversationAnalysisIconView' in report
assert 'contentDescription = "采样设置"' in report
assert 'contentDescription = "模型容量与采样"' in report
assert 'contentDescription = "AI接口设置"' in report
assert 'store.setSampling(window, seek.progress + 1); dialog.dismiss()' in report
for start, end, width in [('showTopSamplingDialog', 'showSamplingDialog', '.90f'),
                          ('showSamplingDialog', 'showContentDialog', '.92f')]:
    body = report[report.index('    private fun ' + start + '('):report.index('    private fun ' + end + '(')]
    assert f'showContentDialog(activity, root, {width})' in body
    assert 'heightPixels *' not in body and 'setLayout(' not in body
helper = report[report.index('    private fun showContentDialog('):report.index('    private fun addSettingSlider(')]
assert 'ViewGroup.LayoutParams.WRAP_CONTENT' in helper
assert 'View.MeasureSpec.AT_MOST' in helper and 'isFillViewport = false' in helper
assert 'getWindowVisibleDisplayFrame' in helper
print('PASS icon routing, accessibility labels, unchanged sampling save and compact-dialog contracts')

sources = {
'Context.kt': '''package android.content
open class Context { val resources=android.content.res.Resources() }
''',
'Resources.kt': '''package android.content.res
class Metrics { var density=1f;var widthPixels=1080;var heightPixels=1920 }
class Resources {val displayMetrics=Metrics()}
''',
'Graphics.kt': '''package android.graphics
class ColorFilter
object Color {const val WHITE:Int=-1}
object PixelFormat {const val TRANSLUCENT=-3}
class Rect(var left:Int=0,var top:Int=0,var right:Int=0,var bottom:Int=0) {
 fun width()=right-left;fun height()=bottom-top
 fun exactCenterX()=(left+right)/2f;fun exactCenterY()=(top+bottom)/2f
}
class Paint(flags:Int) {
 enum class Style {STROKE};enum class Cap {ROUND};enum class Join {ROUND}
 var style=Style.STROKE;var strokeCap=Cap.ROUND;var strokeJoin=Join.ROUND
 var strokeWidth=0f;var color=0;var alpha=255;var colorFilter:ColorFilter?=null
 companion object {const val ANTI_ALIAS_FLAG=1}
}
class Path {
 var points=0
 fun reset(){points=0};fun moveTo(x:Float,y:Float){points++};fun lineTo(x:Float,y:Float){points++}
 fun cubicTo(a:Float,b:Float,c:Float,d:Float,e:Float,f:Float){points+=3};fun close(){}
}
class Canvas {
 var depth=0;var marks=0;var lastAlpha=0;var lastFilter:ColorFilter?=null
 fun save():Int {val old=depth;depth++;return old}
 fun restoreToCount(n:Int){check(n<=depth);depth=n}
 fun translate(x:Float,y:Float){};fun scale(x:Float,y:Float){};fun rotate(a:Float,x:Float,y:Float){}
 private fun mark(p:Paint){marks++;lastAlpha=p.alpha;lastFilter=p.colorFilter}
 fun drawCircle(x:Float,y:Float,r:Float,p:Paint){check(r>0);mark(p)}
 fun drawLine(x:Float,y:Float,a:Float,b:Float,p:Paint){mark(p)}
 fun drawRoundRect(a:Float,b:Float,c:Float,d:Float,e:Float,f:Float,p:Paint){mark(p)}
 fun drawArc(a:Float,b:Float,c:Float,d:Float,e:Float,f:Float,g:Boolean,p:Paint){mark(p)}
 fun drawPath(path:Path,p:Paint){if(path.points>0)mark(p)}
}
''',
'View.kt': '''package android.view
import android.content.Context
import android.graphics.*
open class View(val context:Context) {
 val resources get()=context.resources
 var width=48;var height=48;var desiredHeight=100;var measuredHeight=0;var measuredWidth=0
 var visibleHeight=900
 open fun onDraw(canvas:Canvas){}
 open fun onMeasure(w:Int,h:Int){measuredWidth=MeasureSpec.getSize(w);measuredHeight=desiredHeight}
 fun measure(w:Int,h:Int)=onMeasure(w,h)
 fun getWindowVisibleDisplayFrame(r:Rect){r.bottom=visibleHeight;r.right=1080}
 object MeasureSpec {
  const val UNSPECIFIED=0;const val EXACTLY=1073741824;const val AT_MOST:Int=-2147483648
  fun getMode(v:Int)=v and -1073741824
  fun getSize(v:Int)=v and 1073741823
  fun makeMeasureSpec(size:Int,mode:Int)=(size and 1073741823) or mode
 }
}
open class ViewGroup(c:Context):View(c) {
 class LayoutParams(val width:Int,val height:Int){companion object{const val WRAP_CONTENT=-2}}
}
class Window(c:Context) {
 val decorView=View(c);var layoutWidth=0;var layoutHeight=0
 fun setLayout(w:Int,h:Int){layoutWidth=w;layoutHeight=h}
 fun setBackgroundDrawableResource(v:Int){};fun setWindowAnimations(v:Int){}
}
''',
'Drawable.kt': '''package android.graphics.drawable
import android.graphics.*
abstract class Drawable {
 val bounds=Rect(0,0,48,48);var invalidations=0
 abstract fun draw(c:Canvas);abstract fun setAlpha(a:Int);abstract fun setColorFilter(f:ColorFilter?)
 abstract fun getOpacity():Int
 open fun getIntrinsicWidth()=0;open fun getIntrinsicHeight()=0
 fun invalidateSelf(){invalidations++}
}
''',
'App.kt': '''package android.app
import android.content.Context
import android.view.*
class Activity:Context(){val window=Window(this)}
class Dialog(val activity:Activity) {
 val window:Window?=Window(activity);var content:View?=null;var shown=false
 private var listener:((Dialog)->Unit)?=null
 fun setContentView(v:View){content=v}
 fun setOnShowListener(l:(Dialog)->Unit){listener=l}
 fun show(){shown=true;listener?.invoke(this)}
}
''',
'Widgets.kt': '''package android.widget
import android.content.Context
import android.view.*
open class ScrollView(c:Context):ViewGroup(c) {
 var isFillViewport=true;var child:View?=null
 fun addView(v:View,p:ViewGroup.LayoutParams){child=v}
 override fun onMeasure(w:Int,h:Int){
  measuredWidth=View.MeasureSpec.getSize(w)
  measuredHeight=minOf(child!!.desiredHeight,View.MeasureSpec.getSize(h))
 }
}
''',
'R.kt': '''package android
object R {object color{const val transparent=0}}
''',
'Menu.kt': 'package t.mma.hooks.items.conversationanalysis\nimport android.graphics.*\nimport android.graphics.drawable.Drawable\n' + menu[menu.index('private class AnalysisMenuDrawable('):].replace('private class AnalysisMenuDrawable', 'internal class AnalysisMenuDrawable'),
'Sizing.kt': '''package t.mma.hooks.items.conversationanalysis
import android.app.*
import android.view.*
import android.widget.ScrollView
object Sizing {
 private fun dp(a:Activity,n:Int)=(a.resources.displayMetrics.density*n).toInt()
''' + helper.replace('private fun showContentDialog', 'fun showContentDialog') + '\n}\n',
'Test.kt': '''package t.mma.hooks.items.conversationanalysis
import android.app.*
import android.graphics.*
import android.view.*
import android.widget.ScrollView
fun main(){
 val context=android.content.Context()
 for (density in listOf(1f,2f,3f)) for (kind in ConversationAnalysisIconView.Kind.values()) {
  context.resources.displayMetrics.density=density
  val view=ConversationAnalysisIconView(context,kind);val canvas=Canvas()
  view.onDraw(canvas);check(canvas.marks>0 && canvas.depth==0)
  val painter=ConversationAnalysisIconPainter(kind);val empty=Canvas()
  painter.draw(empty,0f,0f,0f,24f,0);check(empty.marks==0 && empty.depth==0)
 }
 val drawable=AnalysisMenuDrawable(2f);check(drawable.getIntrinsicWidth()==56)
 val filter=ColorFilter();drawable.setAlpha(90);drawable.setColorFilter(filter)
 val c=Canvas();drawable.draw(c);check(c.marks>0 && c.depth==0 && c.lastAlpha==90 && c.lastFilter===filter)
 check(drawable.invalidations==2)
 println("PASS real icon View/painter and menu Drawable compile; all nine icon kinds draw; density, bounds, alpha/filter and canvas restoration")
 val activity=Activity()
 for (visible in listOf(360,900,1600)) for (contentHeight in listOf(180,450,1800)) for(width in listOf(.90f,.92f)) {
  activity.window.decorView.visibleHeight=visible
  val body=View(activity);body.desiredHeight=contentHeight
  val d=Sizing.showContentDialog(activity,body,width)
  check(d.shown && d.window!!.layoutHeight==-2 && d.window!!.layoutWidth==(1080*width).toInt())
  val scroll=d.content as ScrollView;check(!scroll.isFillViewport && scroll.child===body)
  scroll.measure(View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.AT_MOST))
  check(scroll.measuredHeight==minOf(contentHeight,visible-32))
  scroll.measure(0,View.MeasureSpec.makeMeasureSpec(220,View.MeasureSpec.AT_MOST))
  check(scroll.measuredHeight==minOf(contentHeight,visible-32,220))
  scroll.measure(0,View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
  check(scroll.measuredHeight==minOf(contentHeight,visible-32))
 }
 println("PASS extracted production dialog helper: short content wraps, overflow bounded, parent limits and unspecified measurement; 54 combinations")
}
'''
}
libs=Path('/tmp/pl-kotlin-libs')
std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    p=Path(td)
    for name,text in sources.items(): (p/name).write_text(text)
    files=[p/n for n in sources]+[BASE/'ConversationAnalysisIconPainter.kt',BASE/'ConversationAnalysisIconView.kt']
    subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect',
                    '-classpath',str(std),'-d',str(p/'test.jar'),*map(str,files)],check=True,timeout=90)
    subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.conversationanalysis.TestKt'],check=True,timeout=30)
