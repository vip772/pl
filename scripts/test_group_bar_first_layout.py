"""Execute production first-frame visibility gate across delayed data/layout events."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/groupbar/GroupBarRuntime.kt').read_text()
a=s.index('        fun syncHeaderVisibility()');b=s.index('        private fun restoreAvoidance()',a)
code='''import java.lang.ref.WeakReference
open class View {var isLayoutRequested=false;var visibility=4;var isShown=true;var height=44;companion object {const val VISIBLE=0;const val INVISIBLE=4}}
class ViewGroup:View(){var childCount=0}
class Session {
 val list=ViewGroup();val viewRef=WeakReference<View>(list);val bar=View();val titleAnchor:View?=View()
 var enabled=true;var hasVisibleGroups=true;var userInteracting=false;var initialAlignmentPending=true
 var stableTitleTop:Int?=50;var positionDirty=false;var open=false;var selections=0
 fun nativeHeaderOpen()=open;fun titleOpaque()=true;fun safePosition(){}
 fun selectNativeTop(v:View){selections++;v.isLayoutRequested=true}
METHOD
}
fun main(){
 val r=Session()
 repeat(100){r.syncHeaderVisibility()};check(r.initialAlignmentPending&&r.selections==0&&r.bar.visibility==4)
 r.list.childCount=8;r.list.isLayoutRequested=true;r.syncHeaderVisibility();check(r.initialAlignmentPending&&r.selections==0)
 r.list.isLayoutRequested=false;r.syncHeaderVisibility();check(!r.initialAlignmentPending&&r.selections==1&&r.bar.visibility==4)
 r.list.isLayoutRequested=false;r.syncHeaderVisibility();check(r.bar.visibility==0)
 repeat(2000){r.syncHeaderVisibility()};check(r.selections==1)
 r.open=true;r.syncHeaderVisibility();check(r.bar.visibility==4)
 r.open=false;r.syncHeaderVisibility();check(r.bar.visibility==0&&r.selections==1)
 val touched=Session();touched.list.childCount=8;touched.userInteracting=true;touched.syncHeaderVisibility();check(touched.selections==0)
 println("PASS cold-start delayed rows, pending layout, selection settlement, 2000 stable frames and gesture/open gates")
}
'''.replace('METHOD',s[a:b])
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)
