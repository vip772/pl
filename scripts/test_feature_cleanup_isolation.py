"""Execute production feature cleanup closures with unregister failure; no Gradle/device."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIBS = Path('/tmp/pl-kotlin-libs')
FEATURES = [
    'chattoolbar/ChatToolbarFeature.kt',
    'homesidepanel/HomeSidePanelFeature.kt',
    'musicorder/QQMusicOrderFeature.kt',
    'autoreply/AutoReplyFeature.kt',
    'autocache/AutoCacheFeature.kt',
    'textspeech/TextSpeechFeature.kt',
]

def cleanup_block(source):
    needle = 'unregisterOnSharedPreferenceChangeListener(listener)'
    target = source.index(needle)
    start = source.rfind('trackResource {', 0, target)
    assert start >= 0
    opening = source.index('{', start)
    depth = 0
    for i in range(opening, len(source)):
        if source[i] == '{':
            depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0:
                assert opening < target < i
                return source[opening + 1:i]
    raise AssertionError('unterminated cleanup block')

def main():
    std = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
    cases = []
    for index, relative in enumerate(FEATURES):
        source = (ROOT / 'app/src/main/java/t/mma/hooks/items' / relative).read_text()
        body = cleanup_block(source)
        cases.append(f'''
class Case{index} {{
    val preferences = Preferences()
    val prefs = preferences
    val listener = Any()
    var active = true
    var stopped = 0
    private fun stopRuntime() {{ stopped++; if (failStop) error("stop failed") }}
    var failStop = false
    fun cleanup() {{ {body} }}
}}
''')
    checks = []
    for index, relative in enumerate(FEATURES):
        checks.append(f'''
    run {{
        val feature = Case{index}()
        val errors = java.util.concurrent.atomic.AtomicInteger()
        var otherRelease = 0
        val scope = FeatureResourceScope {{ errors.incrementAndGet() }}
        scope.register {{ otherRelease++ }}
        scope.register {{ feature.cleanup() }}
        scope.close(); scope.close()
        val ok = !feature.active && feature.stopped == 1 &&
            feature.preferences.attempts == 1 && errors.get() == 1 && otherRelease == 1
        println((if (ok) "PASS " else "FAIL ") + "{relative}: unregister failure still stops runtime; independent cleanup continues")
        if (!ok) failed++
        val both = Case{index}()
        both.failStop = true
        val bothScope = FeatureResourceScope {{ }}
        bothScope.register {{ both.cleanup() }}
        bothScope.close()
        if (both.active || both.stopped != 1) failed++
    }}
''')
    code = '''package t.mma.hooks.core
class Preferences {
    var attempts = 0
    fun unregisterOnSharedPreferenceChangeListener(listener: Any) {
        attempts++; error("injected unregister failure")
    }
}
''' + ''.join(cases) + '''
fun main() {
    var failed = 0
''' + ''.join(checks) + '''
    check(failed == 0) { "cleanup regressions failed=$failed" }
    println("CLEANUP-ISOLATION-OK: 6 production closures, single and double failures")
}
'''
    with tempfile.TemporaryDirectory() as temp:
        path = Path(temp)
        (path / 'Main.kt').write_text(code)
        jar = path / 'tests.jar'
        subprocess.run([
            'java', '-Xmx256m', '-cp', ':'.join(map(str, LIBS.glob('*.jar'))),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib',
            '-no-reflect', '-classpath', str(std), '-d', str(jar),
            str(ROOT / 'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt'),
            str(path / 'Main.kt'),
        ], check=True, timeout=120)
        subprocess.run(['java', '-cp', f'{jar}:{std}', 't.mma.hooks.core.MainKt'],
                       check=True, timeout=30)

if __name__ == '__main__':
    main()
