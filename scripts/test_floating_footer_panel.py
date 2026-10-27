"""Execute production occupied-height calculation and verify panel anchoring/rollback wiring."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatFooterFeature.kt').read_text()
a=source.index('        val panelHeight = if (state.panel.visibility')
b=source.index('        var layoutChanged',a)
calculation=source[a:b]
a=source.index('    private fun reparentPanel(');b=source.index('    private fun applyCompactPanelHeight(',a)
reparent=source[a:b]
assert 'addRule(RelativeLayout.BELOW, state.inputColumn.id)' in reparent
assert 'addRule(RelativeLayout.BELOW, panel.id)' not in reparent
assert 'state.changedRules[state.inputColumn] = columnParams.rules.copyOf()' in reparent
assert 'panel.visibility = if (isPanelState(panelState))' in reparent
assert 'state.listExtra = occupiedHeight + gap' in source
assert 'applyChatListPadding(footer, state.listExtra, state)' in source
assert '.coerceAtLeast(dp(MIN_PANEL_HEIGHT_DP)) + dp(PANEL_EXTRA_HEIGHT_DP)' in source
assert 'private const val PANEL_EXTRA_HEIGHT_DP = 50' in source
assert source.count('applyCompactPanelHeight(footer,') == 2
assert 'restorePanelHeight(state.panel)' in source
a=source.index('    private fun applyCompactPanelHeight(');b=source.index('    private fun restorePanelHeight(',a)
sizing=source[a:b]
code="""
import kotlin.math.roundToInt
class Params(var height:Int)
class Metrics(val heightPixels:Int=2400)
class Resources(val displayMetrics:Metrics=Metrics())
open class View(var visibility:Int=0,var layoutParams:Params?=null,var measuredHeight:Int=0){
 var height=measuredHeight;var parent:Any?=null;val resources=Resources()
 companion object{const val VISIBLE=0}
}
class ViewGroup:View(){val children=mutableListOf<View>();val childCount get()=children.size;fun getChildAt(i:Int)=children[i]}
class Sizer(val density:Float){
 val panelOriginalHeights=java.util.WeakHashMap<View,Int>();val panelBodyOriginalHeights=java.util.WeakHashMap<View,Int>()
 fun dp(v:Int)=kotlin.math.round(v*density).toInt()
 val MAX_PANEL_HEIGHT_DP=265;val MIN_PANEL_HEIGHT_DP=180;val MAX_PANEL_HEIGHT_RATIO=0.30f;val PANEL_EXTRA_HEIGHT_DP=50
"""+sizing+"""
 fun test(footer:View,panel:View)=applyCompactPanelHeight(footer,panel)
}
"""+'''

class State(val panel:View)
fun top(state:State,inputHeight:Int,gap:Int):Int {
'''+calculation+''' return targetTop
}
fun main(){
 for(density in listOf(1f,2f,3f))for(naturalDp in listOf(160,240,300,500))for(hostDp in listOf(400,800,1200)){
  val sizing=Sizer(density);val natural=sizing.dp(naturalDp)
  val panel=ViewGroup();panel.height=natural;panel.layoutParams=Params(natural)
  val child=View(0,Params(natural),natural);panel.children.add(child)
  val footer=View();val host=View();host.height=sizing.dp(hostDp);footer.parent=host
  val base=minOf(natural,sizing.dp(265),kotlin.math.round(host.height*0.30f).toInt()).coerceAtLeast(sizing.dp(180))
  repeat(100){sizing.test(footer,panel);check(panel.layoutParams!!.height==base+sizing.dp(50));check(child.layoutParams!!.height==base+sizing.dp(50))}
 }

 for(input in listOf(48,72,144))for(gap in listOf(0,8,24))for(height in listOf(120,180,265,600,960,1200)){
  val panel=View(0,Params(height),height);val state=State(panel)
  val opened=top(state,input,gap)
  check(opened+input == -height-gap) // input bottom == panel top
  check(opened+input+height == -gap) // stack bottom stays at bottom gap
  panel.visibility=8;check(top(state,input,gap)==-input-gap)
  panel.visibility=0;panel.layoutParams=Params(-2)
  check(top(state,input,gap)==opened) // measured-height fallback
 }
 println("PASS panel below input; open/close, multiline, gap/height changes, measured fallback; anchoring and rollback wiring")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 d=Path(td);(d/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(d/'out.jar'),str(d/'Test.kt')],check=True)
 subprocess.run(['java','-cp',str(d/'out.jar')+':'+str(std),'TestKt'],check=True)
