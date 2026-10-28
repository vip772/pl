"""Run production anchor handling against RecyclerView layout scenarios and Android SDK."""
from pathlib import Path
import subprocess, tempfile
root = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
source = root/'app/src/main/java/t/mma/hooks/items/floatingchat/ChatListBottomInset.kt'
compiler = ['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect']
fixture = '''package android.view
open class View {
 var paddingBottom=20;var height=1000;var bottom=980;var up=true;var down=false
 var scrolls=0;var distance=0
 fun canScrollVertically(d:Int)=if(d<0)up else down
 fun scrollBy(x:Int,y:Int){scrolls++;distance+=y;bottom-=y}
}
open class ViewGroup:View(){
 val childCount=1;fun getChildAt(i:Int):View?=if(i==0)this else null
 var size=20;var last=19;var motion=0
 fun getCount()=size;fun getLastVisiblePosition()=last;fun getScrollState()=motion
}
'''
main = '''package t.mma.hooks.items.floatingchat
import android.view.ViewGroup
fun main(){
 fun list()=ViewGroup()
 val inset=ChatListBottomInset();val v=list()
 inset.beforePadding(v,400);v.paddingBottom=400;inset.afterLayout(v)
 check(v.distance==380);repeat(1000){inset.afterLayout(v)};check(v.scrolls==1)
 val shrinking=list();shrinking.paddingBottom=400;shrinking.bottom=600
 inset.beforePadding(shrinking,20);shrinking.paddingBottom=20;inset.afterLayout(shrinking)
 check(shrinking.distance == -380)
 val repeated=list();inset.beforePadding(repeated,400);repeated.paddingBottom=400
 inset.beforePadding(repeated,400);inset.afterLayout(repeated);check(repeated.distance==380)
 val native=list();inset.beforePadding(native,400);native.paddingBottom=400;native.bottom=600
 inset.afterLayout(native);check(native.scrolls==0)
 val history=list();history.down=true;inset.beforePadding(history,400);history.paddingBottom=400
 inset.afterLayout(history);check(history.scrolls==0)
 val dragging=list();dragging.motion=1;inset.beforePadding(dragging,400);dragging.paddingBottom=400
 inset.afterLayout(dragging);check(dragging.scrolls==0)
 val append=list();inset.beforePadding(append,400);append.paddingBottom=400;append.size++;append.last++
 inset.afterLayout(append);check(append.scrolls==0)
 val removed=list();inset.beforePadding(removed,400);removed.paddingBottom=400;removed.size--
 inset.afterLayout(removed);check(removed.scrolls==0)
 val short=list();short.up=false;inset.beforePadding(short,400);short.paddingBottom=400
 inset.afterLayout(short);check(short.scrolls==0)
 val moving=list();inset.beforePadding(moving,400);moving.paddingBottom=400;moving.motion=2
 inset.afterLayout(moving);check(moving.scrolls==0)
 println("PASS: bottom correction once; native correction, history, drag, fling, append, removal and short list preserved")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 subprocess.run(compiler+['-classpath',f'{std}:/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(source)],check=True,timeout=50)
 (p/'Views.kt').write_text(fixture);(p/'Test.kt').write_text(main)
 subprocess.run(compiler+['-classpath',str(std),'-d',str(p/'test.jar'),str(source),str(p/'Views.kt'),str(p/'Test.kt')],check=True,timeout=50)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.floatingchat.TestKt'],check=True)
