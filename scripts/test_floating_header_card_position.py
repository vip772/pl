"""Run production header-zone card placement with distinct screen/local origins."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderFeature.kt').read_text()
a=s.index('    private fun applyHeaderZoneCards(');b=s.index('    private fun applyTrackedTipsGroup(',a)
code='''import java.util.WeakHashMap
import kotlin.math.roundToInt
open class View {var y=0;var height=50;var visibility=0;var layoutParams:Any?=ViewGroup.MarginLayoutParams();var writes=0
 fun getLocationOnScreen(a:IntArray){a[1]=y};companion object{const val VISIBLE=0}}
class Metrics {val density=1f};class Resources{val displayMetrics=Metrics()}
class ViewGroup:View(){val children=mutableListOf<View>();val childCount get()=children.size;val resources=Resources();var paddingTop=0
 fun getChildAt(i:Int)=children[i]
 class MarginLayoutParams {var leftMargin=0;var rightMargin=0;var topMargin=0;var bottomMargin=0}}
class HeaderZoneState{val originalMargins=WeakHashMap<View,IntArray>()}
object FloatingChatCardVisuals{fun apply(v:View,r:Int,transparency:Int){}}
class Runtime {
 val headerZoneStates=WeakHashMap<ViewGroup,HeaderZoneState>();val SIDE_MARGIN_DP=8;val CARD_GAP_DP=8;val CARD_CORNER_RADIUS_DP=12;val backgroundTransparency=0
 val host=View();fun dp(v:Int)=v
 fun find(v:View,p:(View)->Boolean):View?=if(v===host) View() else null
 fun apply(l:ViewGroup,h:View)=applyHeaderZoneCards(l,h)
METHOD
}
fun main(){
 for(origin in listOf(0,80,150))for(status in listOf(0,24,42)){
 val r=Runtime();val layout=ViewGroup();layout.y=origin;layout.paddingTop=7
 val header=View();header.y=origin+status+8;header.height=56
 val card=View();layout.children.add(card);layout.children.add(r.host)
 r.apply(layout,header);val p=card.layoutParams as ViewGroup.MarginLayoutParams
 check(layout.y+layout.paddingTop+p.topMargin==header.y+header.height+8)
 val top=p.topMargin;repeat(1000){r.apply(layout,header)};check(p.topMargin==top)
 check(r.headerZoneStates[layout]!!.originalMargins[card]!![1]==0)
 }
 println("PASS production card margin: status bar, differing screen origins, original restore values and stable repeated placement")
}
'''.replace('METHOD',s[a:b])
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)
assert 'findDeclaredMethod(clazz, "setVisibility"' in s
print('PASS native tips visibility entry is wired to relayout')