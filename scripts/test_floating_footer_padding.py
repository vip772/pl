"""Execute the production list-padding method; verify it never scrolls messages."""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatFooterFeature.kt').read_text()
start = source.index('    private fun applyChatListPadding(')
end = source.index('    private fun ensurePreDrawTracking(', start)
method = source[start:end].replace('private fun ', 'fun ')
fixture = '''
class View {
 var isAttachedToWindow=true
 var paddingLeft=3; var paddingTop=5; var paddingRight=7; var paddingBottom=11
 var writes=0; var scrolls=0
 fun setPadding(l:Int,t:Int,r:Int,b:Int) {
  paddingLeft=l; paddingTop=t; paddingRight=r; paddingBottom=b; writes++
 }
 fun canScrollVertically(d:Int)=false
 fun scrollBy(x:Int,y:Int){scrolls++}
}
class FooterState(var chatRecycler:View?=null,var recyclerPaddingBottom:Int?=null)
class Runtime(val recycler:View) {
 fun findChatRecycler(footer:View):View?=recycler
 fun liftNewMessageBubble(footer:View){}
METHOD
}
fun main() {
 val list=View(); val footer=View(); val runtime=Runtime(list); val state=FooterState()
 runtime.applyChatListPadding(footer,171,state)
 check(list.paddingBottom==182 && list.scrolls==0)
 repeat(1000) {runtime.applyChatListPadding(footer,171,state)}
 check(list.writes==1 && list.scrolls==0)
 runtime.applyChatListPadding(footer,217,state)
 check(list.paddingBottom==228 && list.writes==2 && list.scrolls==0)
 check(list.paddingLeft==3 && list.paddingTop==5 && list.paddingRight==7)
 check(state.recyclerPaddingBottom==11)
 println("PASS: production footer padding: initial entry has no explicit scroll, stable updates are no-ops, resize does not accumulate, side/top preserved")
}
'''.replace('METHOD', method)
libs=Path('/tmp/pl-kotlin-libs')
stdlib=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as tmp:
 folder=Path(tmp); kt=folder/'Test.kt'; kt.write_text(fixture); jar=folder/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),
  'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),
  '-d',str(jar),str(kt)],check=True)
 subprocess.run(['java','-cp',f'{jar}:{stdlib}','TestKt'],check=True)
