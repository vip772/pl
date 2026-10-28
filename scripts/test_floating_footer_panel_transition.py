"""Execute production occupied-height refresh; panel open/close must reanchor ready footers."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatFooterFeature.kt').read_text()
a=s.index('    private fun refreshMeasuredInputHeight(');b=s.index('    private fun applyChatListPadding(',a)
method=s[a:b].replace('private fun ','fun ')
code='''class Params(var height:Int)
open class View {var visibility=0;var measuredHeight=0;var layoutParams:Params?=null;companion object{const val VISIBLE=0}}
class FooterState(val panel:View,val inputColumn:View,var listExtra:Int)
class Runtime {
 val BOTTOM_GAP_DP=8;var inputHeight=60;var applies=0
 fun dp(v:Int)=v
 fun visibleHeightExcludingPanel(input:View,panel:View)=inputHeight
 fun apply(footer:View){applies++}
 METHOD
}
fun main(){
 val r=Runtime();val footer=View();val panel=View();panel.visibility=8;panel.layoutParams=Params(300)
 val state=FooterState(panel,View(),68)
 repeat(1000){r.refreshMeasuredInputHeight(footer,state)};check(r.applies==0)
 panel.visibility=0;r.refreshMeasuredInputHeight(footer,state);check(r.applies==1)
 state.listExtra=368;repeat(1000){r.refreshMeasuredInputHeight(footer,state)};check(r.applies==1)
 panel.visibility=8;r.refreshMeasuredInputHeight(footer,state);check(r.applies==2)
 state.listExtra=68;r.inputHeight=108;r.refreshMeasuredInputHeight(footer,state);check(r.applies==3)
 println("PASS production refresh: panel open/close and late toolbar reanchor; 2000 stable calls do not reapply")
}
'''.replace('METHOD',method)
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=40)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)
assert 'private var panelState =' not in s
assert 'panelStates.remove(footer)' in s
assert 'if (states[footer]?.reparented == true) apply(footer)' in s
print('PASS per-footer panel ownership, detach cleanup and panel-switch wiring')