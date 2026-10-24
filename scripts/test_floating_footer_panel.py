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
assert 'applyChatListPadding(footer, occupiedHeight + gap, state)' in source
code='''
class Params(var height:Int)
class View(var visibility:Int=0,var layoutParams:Params?=null,var measuredHeight:Int=0){companion object{const val VISIBLE=0}}
class State(val panel:View)
fun top(state:State,inputHeight:Int,gap:Int):Int {
'''+calculation+''' return targetTop
}
fun main(){
 for(input in listOf(48,72,144))for(gap in listOf(0,8,24))for(height in listOf(180,265,600)){
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
