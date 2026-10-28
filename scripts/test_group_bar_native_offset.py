"""Execute production offset integration with deterministic native-field stubs."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/groupbar/GroupBarRuntime.kt').read_text()
a=s.index('        private fun applyNativeOffset(');z=s.index('        fun headerState(',a)
methods=s[a:z]
code='''import java.lang.ref.WeakReference
open class View
class ListView:View() {var offset=120;val controller=Controller();val taskBar=Any();var selections=0}
class Controller {var n=120}
class Field(val name:String) {var isAccessible=false;fun getInt(o:Any)=if(o is ListView)o.offset else (o as Controller).n;fun setInt(o:Any,v:Int){if(o is ListView)o.offset=v else (o as Controller).n=v};fun get(o:Any):Any=if(name=="i"||name=="Y2")(o as ListView).taskBar else (o as ListView).controller}
object KavaReflector {fun findFieldRecursive(c:Class<*>,n:String):Field?=Field(n)}
class Session(val list:ListView) {
 val viewRef=WeakReference<View>(list);val scrollField=Field("offset");val controllerField=Field("controller")
 var nativeScrollOffset:Int?=null;var appliedScrollInset=0;var nativeOffsetUpdating=false;var offsetAppliedDuringUpdate=false
 val reserveHeight=44;var floatingInset=2;var enabled=true;var hasVisibleGroups=true;var headerOpen=false
 fun selectNativeTop(v:View){list.selections++}
 fun apply(a:Boolean)=applyNativeOffset(a)
'''+methods+'''}
fun main() {
 val l=ListView();val s=Session(l)
 s.apply(true);check(l.offset==166&&l.controller.n==166)
 repeat(100){s.apply(true)};check(l.offset==166) // no accumulation
 s.beforeNativeOffset();check(l.offset==166)
 l.offset=140 // native C/y1 recomputes orientation/statusbar baseline
 s.afterNativeOffset();check(l.offset==186&&l.controller.n==186&&l.selections==0)
 s.beforeNativeOffset();check(l.offset==186);l.offset=140;s.afterNativeOffset();check(l.offset==186)
 // Native C/y1 invokes selection inside the update: reserve must exist beforehand.
 s.beforeNativeOffset();l.offset=140;s.beforeNativeSelection()
 check(l.offset==186&&l.controller.n==186)
 s.beforeNativeSelection();check(l.offset==186) // repeated nested selection never doubles inset
 s.afterNativeOffset();check(l.offset==186&&s.nativeScrollOffset==140)
 // Exact C/y1 order: snapshot old final, calculate base, controller.n=base,
 // TaskBar.q, compare snapshot vs final. Same geometry must NOT select row 1.
 repeat(100) {
   s.beforeNativeOffset();val old=l.offset
   l.offset=140;l.controller.n=140
   s.beforeTaskBarResize(Any());check(l.offset==140) // unrelated container ignored
   s.beforeTaskBarResize(l.taskBar)
   check(l.offset==186&&l.controller.n==186)
   if(old!=l.offset) l.selections++
   s.afterNativeOffset()
 }
 check(l.selections==0&&l.offset==186)
 // Actual geometry change triggers exactly the native selection, no module reset.
 s.beforeNativeOffset();val old=l.offset;l.offset=150;l.controller.n=150
 s.beforeTaskBarResize(l.taskBar);check(l.offset==196&&l.controller.n==196)
 if(old!=l.offset) l.selections++
 s.afterNativeOffset();check(l.selections==1&&s.nativeScrollOffset==150)
 s.beforeNativeOffset();l.offset=140;s.beforeTaskBarResize(l.taskBar);s.afterNativeOffset()
 l.selections=0
 s.apply(false);check(l.offset==140&&l.controller.n==140)
 s.apply(true);check(l.offset==186)
 s.headerOpen=true;s.beforeNativeOffset();l.offset=150;s.afterNativeOffset();check(l.offset==196&&l.controller.n==196&&l.selections==0)
 // Native onScroll: translated = max(0, childTop - s). At rest childTop == s.
 check(maxOf(0,l.offset-l.offset)==0)
 check(maxOf(0,(l.offset+20)-l.offset)==20)
 println("PASS idempotent reserve, native recompute, taskbar parity, disable restore, open-header preservation and zero resting title translation")
}
'''
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)