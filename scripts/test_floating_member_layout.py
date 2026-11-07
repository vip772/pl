"""Execute production capsule placement with geometry fixtures; not a visual/device test."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderCapsule.kt').read_text()
a=s.index('    private fun applyStatistics(');b=s.index('    private fun statisticsText(',a)
method=s[a:b]
code='''object Color {const val WHITE=-1}
class GradientDrawable {var color:Colors?=null;fun setColor(c:Int){color=Colors(c)}}
class Colors(val defaultColor:Int)
open class View {var top=0;var bottom=48;var height=48;var width=300;var visibility=VISIBLE;var isLayoutRequested=false
 companion object {const val VISIBLE=0;const val INVISIBLE=4;const val GONE=8}
 object MeasureSpec {const val AT_MOST=1;const val UNSPECIFIED=0;fun makeMeasureSpec(v:Int,m:Int)=v}}
class FrameLayout {class LayoutParams {var topMargin=0;var height=56}}
class ViewGroup:View(){var writes=0;var layoutParams=FrameLayout.LayoutParams();set(v){field=v;writes++}}
class Label:View(){var text:String?="";var currentTextColor=0;val background=GradientDrawable();var measures=0;val measuredHeight=24
 var layoutParams=FrameLayout.LayoutParams();fun setTextColor(c:Int){currentTextColor=c};fun measure(w:Int,h:Int){measures++;isLayoutRequested=false}}
class State {val statistics=Label();val row=View();val center=View();val title=View();val baseHeight=56;val originalHeight=56;var statisticsWidth=-1;var statisticsHeight=0}
class Runtime {fun statisticsText(h:ViewGroup,s:State,t:String)=t
fun dp(v:View,i:Int)=i
fun apply(h:ViewGroup,s:State,t:String?)=applyStatistics(h,s,t,0)
METHOD
}
fun main(){val r=Runtime();val h=ViewGroup();val s=State();s.center.top=4
 r.apply(h,s,"男 1");check(s.statistics.layoutParams.topMargin==55);check(h.layoutParams.height==79)
 val writes=h.writes;repeat(1000){r.apply(h,s,"男 1")};check(h.writes==writes&&s.statistics.measures==1)
 h.width=180;r.apply(h,s,"男 1");check(s.statistics.measures==2)
 r.apply(h,s,"男 12");check(s.statistics.measures==3)
 r.apply(h,s,null);check(h.layoutParams.height==56&&s.statistics.visibility==View.GONE)
 s.title.height=0;r.apply(h,s,"男 1");check(s.statistics.visibility==View.INVISIBLE&&h.layoutParams.height==56)
 println("PASS production capsule: measured title bottom +3, real occupied height, 1000 stable calls no writes/measure, width/text invalidation, disable height restore and unmeasured title hidden")}
'''.replace('METHOD',method)
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'out'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"out"}:{std}','TestKt'],check=True,timeout=15)
feature=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderFeature.kt').read_text()
apply=feature[feature.index('    private fun apply(sourceLayout:'):feature.index('    private fun visibleChatLayout(')]
assert apply.index('capsule.apply(')<apply.index('applyTrackedTipsGroup(layout, header)')
assert 'memberStatistics.close()' in feature[feature.index('    private fun restoreOwnedViews()'):]
settings=(r/'app/src/main/java/t/mma/ui/miuix/MiuixSettingsPagePart05.kt').read_text()
assert 'getBoolean(statisticsKey, false)' in settings and 'putBoolean(statisticsKey, it)' in settings
print('PASS production wiring: capsule before notice placement, cleanup, default-off settings read/write')