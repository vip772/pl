"""Execute production inset calculation with platform geometry stubs, no Gradle."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderFeature.kt').read_text();a=s.index('    private fun applyChatListTopInset');z=s.index('    private fun collect(',a)
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
code="""import java.util.WeakHashMap
open class View(var y:Int=0,var height:Int=0){var isLayoutRequested=false;fun getLocationOnScreen(a:IntArray){a[1]=y}}
class ViewGroup(y:Int=0,height:Int=0):View(y,height){var paddingLeft=1;var paddingTop=9;var paddingRight=2;var paddingBottom=3;var writes=0;fun setPadding(l:Int,t:Int,r:Int,b:Int){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b;writes++}}
class Runtime {
 val chatListPaddings=WeakHashMap<ViewGroup,Pair<ViewGroup,Int>>()
 val list=ViewGroup(0,100);val CHATTING_RECYCLER_CLASS="ViewGroup";val CARD_GAP_DP=8;var searches=0
 fun dp(i:Int)=i
 fun findVisibleChatContent(l:ViewGroup,h:View):ViewGroup?=l
 fun find(c:ViewGroup,p:(View)->Boolean):View?{searches++;return list}
 fun apply(l:ViewGroup,h:View)=applyChatListTopInset(l,h)
 fun restore(l:ViewGroup)=restoreChatListTopInset(l)
"""+s[a:z]+"""}
fun main(){val r=Runtime();val layout=ViewGroup();val header=View(20,40)
 r.apply(layout,header);check(r.list.paddingTop==68);repeat(1000){r.apply(layout,header)};check(r.list.writes==1&&r.searches==1)
 r.list.paddingBottom=55;r.restore(layout);check(r.list.paddingTop==9&&r.list.paddingBottom==55)
 layout.isLayoutRequested=true;r.apply(layout,header);check(r.list.paddingTop==9)
 println("PASS header overlap/nonaccumulation/1000 idle calls cached; preserve footer padding; restore and pending-layout guard")}
"""
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Main.kt').write_text(code);jar=p/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),str(p/'Main.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{std}','MainKt'],check=True,timeout=20)
