"""Exercise production Activity resolution on JVM Context stubs; no host/device acceptance."""
from pathlib import Path
import subprocess
import tempfile
r = Path(__file__).resolve().parents[1]
b = r / 'app/src/main/java/t/mma/hooks/items/conversationanalysis'
s = (b / 'ConversationAnalysisMessageMenuRuntime.kt').read_text()
assert not (b / 'ConversationAnalysisRuntime.kt').exists()
assert 'ConversationAnalysisRuntime' not in (b / 'ConversationAnalysisFeature.kt').read_text()
assert 'ConversationMenuExtension' not in ''.join(p.read_text() for p in b.glob('*.kt'))
assert 'Binding(talker, WeakReference(findActivity(view.context)))' in s
assert 'val nativeMessage: Any' not in s
helper = s[s.index('    private fun findActivity('):s.index('    private fun addMenuItem(')]
selection = s[s.index('        val activity = binding.activity.get()'):s.index('        activity.runOnUiThread')]
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory(prefix='analysis-entry-test-') as td:
    p = Path(td)
    (p / 'Context.kt').write_text('''package android.content
open class Context
open class ContextWrapper(var baseContext: Context): Context()
''')
    (p / 'Activity.kt').write_text('''package android.app
class Activity: android.content.Context() { var isFinishing=false;var isDestroyed=false }
''')
    (p / 'Test.kt').write_text('''import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.lang.ref.WeakReference
import java.util.Collections
object WeChatApis {
 var current: Activity? = null
 fun currentActivity() = Tracker
 object Tracker { fun currentActivity() = current }
}
class Resolver {
 data class Binding(val activity:WeakReference<Activity>)
 var chosen:Activity?=null
 fun resolve(c:Context)=findActivity(c)
 fun click(a:Activity?) { val binding=Binding(WeakReference(a))
''' + selection + '''
 chosen=activity
 }
''' + helper + '''
}
fun main() {
 val r=Resolver();val owner=Activity();val other=Activity()
 check(r.resolve(ContextWrapper(ContextWrapper(owner)))===owner)
 check(r.resolve(Context())==null)
 val cycle=ContextWrapper(Context());cycle.baseContext=cycle;check(r.resolve(cycle)==null)
 val a=ContextWrapper(Context());val b=ContextWrapper(a);a.baseContext=b;check(r.resolve(a)==null)
 WeChatApis.current=null;r.click(owner);check(r.chosen===owner)
 WeChatApis.current=other;r.click(owner);check(r.chosen===owner)
 owner.isDestroyed=true;check(r.resolve(owner)==null);r.click(owner);check(r.chosen===other)
 owner.isDestroyed=false;owner.isFinishing=true;check(r.resolve(owner)==null)
 r.chosen=null;other.isDestroyed=true;r.click(null);check(r.chosen==null)
 other.isDestroyed=false;r.click(null);check(r.chosen===other)
 println("PASS production owner resolution: nested wrappers, cycles, missing global API, owner priority, destroyed/finishing Activity and fallback; removed group registration")
}
''')
    subprocess.run(['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                    '-classpath', str(stdlib), '-d', str(p / 'out'),
                    *map(str, p.glob('*.kt'))], check=True, timeout=45)
    subprocess.run(['java', '-cp', f'{p / "out"}:{stdlib}', 'TestKt'], check=True, timeout=15)
