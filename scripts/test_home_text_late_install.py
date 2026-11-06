"""Execute production late-holder/catch-up methods with host views, then SDK type-check.
No Gradle, APK, R8 or device validation. Android/service fixtures are explicit.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/t/mma/hooks/items/hometextcolor/HomeTextColorFeature.kt'
LIB = Path('/tmp/pl-kotlin-libs')
STD = next(LIB.glob('kotlin-stdlib-[0-9]*.jar'))
s = SOURCE.read_text()
methods = s[s.index('    private fun modernConversationRow('):s.index('    private fun locateModernConversationMethods(')]
imports = '''import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
'''
body = '''
class Check {
 private val modernConversationRows = java.util.WeakHashMap<Any, WeakReference<View>>()
 private val modernHolderViewFields = ConcurrentHashMap<Class<*>, List<Field>>()
 private var destroyed = false
 private val config = Config(true)
 private class Config(val enabled:Boolean)
 private val applied = mutableListOf<View>()
 private enum class RowKind { CONVERSATION }
 private val ANCHOR_MASK_CONVERSATION = 7
 private fun isLauncherPage(a:Any)=true
 private fun recyclerAnchorMask(v:View)=if(v.tag != null) 7 else 0
 private fun restoreRow(v:View) {}
 private fun applyBoundRow(v:View,k:RowKind) { applied.add(v) }
 METHODS
 fun resolve(holder:Any)=modernConversationRow(holder)
 fun scan(root:View,budget:Int)=refreshVisibleConversationTree(root,budget)
 fun appliedCount()=applied.size
}
object KavaReflector {
 fun declaredFields(c:Class<*>)=c.declaredFields.toList().onEach { it.isAccessible=true }
 fun readField(f:Field,o:Any):Any?=f.get(o)
}
object WeChatApis {fun currentActivity():ActivityApi?=null}
class ActivityApi {fun currentActivity():android.app.Activity?=null}
'''.replace('METHODS', methods)
view = '''package android.view
open class View {var tag:Any?=null;var parent:View?=null;var visibility=0;companion object {const val VISIBLE=0}}
open class ViewGroup:View() {val children=mutableListOf<View>();val childCount get()=children.size;fun getChildAt(i:Int)=children[i];fun add(v:View){children.add(v);v.parent=this}}
class Window {val decorView:View=ViewGroup()}
'''
main = '''
open class BaseHolder(val child:View)
class Holder(child:View):BaseHolder(child)
fun main() {
 val c=Check(); val list=android.widget.AdapterView<Any>();val row=ViewGroup();val child=View()
 row.add(child);list.add(row);val holder=Holder(child);row.tag=holder
 check(c.resolve(holder)===row) // holder was created before hooks; inherited fields
 check(c.resolve(holder)===row) // cached path
 row.tag=Any();check(c.resolve(holder)==null) // stale mapping must not color another row
 row.tag=holder
 check(c.scan(list,20)>=0);check(c.appliedCount()==1) // already visible at hook install
 row.visibility=8;c.scan(list,20);check(c.appliedCount()==1)
 val other=ViewGroup();other.tag=Any();other.add(View());val root=ViewGroup();root.add(other)
 c.scan(root,20);check(c.appliedCount()==1) // unrelated page subtree excluded
 val huge=ViewGroup();repeat(3000){huge.add(View())};check(c.scan(huge,2048)==0)
 println("PASS late holder recovery, inherited field, stale identity, visible catch-up, hidden/unrelated exclusion, traversal budget")
}
'''
compiler = ['java','-Xmx256m','-cp',':'.join(map(str,LIB.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17']
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 files={'Check.kt':imports+body+main,'View.kt':view,'Widget.kt':'package android.widget\nopen class AdapterView<T>:android.view.ViewGroup()','Activity.kt':'package android.app\nclass Activity {val window:android.view.Window?=android.view.Window()}'}
 for name,text in files.items():(p/name).write_text(text)
 subprocess.run(compiler+['-classpath',str(STD),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt'))],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p}/test.jar:{STD}','CheckKt'],check=True,timeout=10)
 (p/'Check.kt').write_text(imports+body)
 subprocess.run(compiler+['-classpath',f'{STD}:/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(p/'Check.kt')],check=True,timeout=45)
 print('PASS production late-install methods type-check against Android SDK')
assert s.count('val row = modernConversationRow(holder) ?: return') == 2
assert 'mainHandler.removeCallbacks(refreshVisibleConversations)' in s
assert 'installedRowHookCount != previousRowHookCount' in s
print('PASS before/after bind integration, catch-up scheduling and cleanup guards')
