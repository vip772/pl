"""Execute production manual-refresh gate and SDK-compile the full weather source; no network/device writes."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
base = root / 'app/src/main/java/t/mma'
path = base / 'hooks/items/homeextension/HomeWeather.kt'
source = path.read_text()
start = source.index('        manualRefresh = manual@{')
end = source.index('            val fine = context.checkSelfPermission', start)
gate = source[start:end] + '            locationCalls++\n        }\n'
assert 'locateOnRefresh: Boolean = true' in source
assert 'HomeWeather.card(ctx, compactCard)' in (base / 'hooks/items/homeextension/HomeExtensionRuntime.kt').read_text()
assert 'HomeWeather.card(activity, settings = prefs, locateOnRefresh = false)' in (base / 'hooks/items/homesidepanel/HomeSidePanelDrawer.kt').read_text()
# The entire existing permission/location/cleanup path is byte-identical to HEAD.
original = subprocess.check_output(['git', 'show', 'HEAD:app/src/main/java/t/mma/hooks/items/homeextension/HomeWeather.kt'], cwd=root, text=True)
marker = '            val fine = context.checkSelfPermission'
assert source[source.index(marker):source.index('        panel.addOnAttachStateChangeListener')] == original[original.index(marker):original.index('        panel.addOnAttachStateChangeListener')]
assert 'val cachePrefix = if (locateOnRefresh) "" else "sidebar:"' in source
assert 'var key = cachePrefix + "$lat,$lon" + if (showWeek) ":week7" else ""' in source
print('PASS sidebar opt-in, unchanged homepage call/default/location path, isolated cache/request/listener key')

fixture = '''
class Panel { var isAttachedToWindow = true }
class TestCard(val locateOnRefresh: Boolean) {
 val panel = Panel(); val context = Any(); val key = "test"
 val inFlight = mutableSetOf<String>()
 var locating = false; var lat = "30.0"; var lon = "120.0"
 var manualRefresh: () -> Unit = {}; var fetches = 0; var locationCalls = 0
 fun fetch(force: Boolean = false) { check(force); fetches++ }
 init {
GATE
 }
}
fun main() {
 val sidebar = TestCard(false)
 sidebar.manualRefresh(); check(sidebar.fetches == 1 && sidebar.locationCalls == 0)
 for (pair in listOf("" to "120", "bad" to "120", "NaN" to "120", "91" to "120", "30" to "181", "30" to "Infinity")) {
  sidebar.lat = pair.first; sidebar.lon = pair.second
  sidebar.manualRefresh(); check(sidebar.fetches == 1 && sidebar.locationCalls == 0)
 }
 check(android.widget.Toast.messages == 6)
 sidebar.lat = "-90"; sidebar.lon = "180"; sidebar.manualRefresh(); check(sidebar.fetches == 2)
 sidebar.inFlight.add("test"); sidebar.manualRefresh(); check(sidebar.fetches == 2)
 sidebar.inFlight.clear(); sidebar.locating = true; sidebar.manualRefresh(); check(sidebar.fetches == 2)
 sidebar.locating = false; sidebar.panel.isAttachedToWindow = false; sidebar.manualRefresh(); check(sidebar.fetches == 2)
 check(sidebar.locationCalls == 0)
 val home = TestCard(true); home.manualRefresh(); check(home.locationCalls == 1 && home.fetches == 0)
 println("PASS production click gate: saved coordinates, invalid/missing/boundary inputs, busy/detach guards; sidebar never enters location, homepage still does")
}
'''.replace('GATE', gate)
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
compiler = ['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect']
with tempfile.TemporaryDirectory() as directory:
    p = Path(directory)
    (p / 'Test.kt').write_text(fixture)
    (p / 'Toast.kt').write_text('package android.widget\nclass Toast { fun show() { messages++ }; companion object { var messages = 0; const val LENGTH_SHORT=0; fun makeText(c:Any,s:String,d:Int)=Toast() } }')
    subprocess.run(compiler + ['-classpath', str(std), '-d', str(p / 'test.jar'), str(p / 'Test.kt'), str(p / 'Toast.kt')], check=True, timeout=45)
    subprocess.run(['java', '-cp', f'{p / "test.jar"}:{std}', 'TestKt'], check=True, timeout=10)
    (p / 'Storage.kt').write_text('package t.mma.preferences\nobject HchatStorage { fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO() }')
    (p / 'Settings.kt').write_text('package t.mma.hooks.items.homeextension\nobject HomeExtensionSettings { fun preferences(c:android.content.Context):android.content.SharedPreferences=TODO() }')
    (p / 'Executor.kt').write_text('package t.mma.hooks.core\nobject IdleSerialExecutor { fun create(n:String):java.util.concurrent.Executor=TODO() }')
    subprocess.run(compiler + ['-classpath', f'{std}:/usr/lib/android-sdk/platforms/android-36/android.jar', '-d', str(p / 'sdk.jar'), str(path), str(p / 'Storage.kt'), str(p / 'Settings.kt'), str(p / 'Executor.kt')], check=True, timeout=45)
print('PASS full production HomeWeather.kt Android SDK compile (project services stubbed); no Gradle/APK/device execution')
