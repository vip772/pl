"""Source-boundary checks for the next internal DexKit migration batch."""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
base = root / 'app/src/main/java/t/mma/hooks/items'
files = [
    'miniprogrambaselib/FakeMiniProgramBaseLibFeature.kt',
    'hideavatar/HideChatAvatarFeature.kt',
    'customfriendavatar/CustomFriendAvatarFeature.kt',
    'groupleave/GroupLeaveMonitorHighlighter.kt',
    'autocache/NativeMediaCache.kt',
    'emojiat/EmojiAtLocator.kt',
]
total = 0
scopes = []
for relative in files:
    source = (base / relative).read_text()
    assert 'context.dexKitBridge()' not in source, relative
    matches = list(re.finditer(r'context\.dexKitManager\(\)\.withCurrentBridge \{ bridge ->', source))
    assert matches, relative
    for match in matches:
        opening = source.index('{', match.start())
        depth, end = 1, opening + 1
        while depth:
            depth += (source[end] == '{') - (source[end] == '}')
            end += 1
        scope = source[match.start():end]
        assert 'bridge.findMethod(' in scope or 'bridge.findClass(' in scope, relative
        assert any(token in scope for token in ['getMethodInstance', 'getConstructorInstance', 'R.loadClass(it.name, loader)']), relative
        total += 1
        scopes.append(scope)
print(f'PASS internal DexKit source boundaries: {total} manager-protected query scopes; no raw bridge calls in migrated files')

# Compile the unchanged query expressions with the real Java manager and DexKit ABI.
# Android context, cache and predicate implementations remain Actions/device coverage.
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
dex = Path('/tmp/pl-dexkit.jar')
compiler = ['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect']
with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    classes = p / 'classes'
    classes.mkdir()
    (p / 'DexInstallScheduler.java').write_text('''package t.mma.hooks.core;
public class DexInstallScheduler {
 public static <T> T queryDexKitTask(java.util.concurrent.Callable<T> t) throws Exception {return t.call();}
}''')
    subprocess.run(['javac', '-cp', str(dex), '-d', str(classes), str(p / 'DexInstallScheduler.java'),
                    str(root / 'app/src/main/java/t/mma/dexkit/DexKitBridgeManager.java')], check=True, timeout=30)
    prelude = '''import java.lang.reflect.*
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import t.mma.dexkit.DexKitBridgeManager
class Context {
 fun dexKitManager() = DexKitBridgeManager()
 fun hostClassLoader() = ClassLoader.getSystemClassLoader()
}
val context = Context()
val strings = arrayOf("a")
val tag = "a"
val loader = context.hostClassLoader()
val matcher: ClassMatcher = ClassMatcher()
object R {fun loadClass(n:String,l:ClassLoader):Class<*>? = null}
fun isLaunchConstructor(c: Constructor<*>) = true
fun isPrivateOpenUrlMethod(m: Method) = true
'''
    names = set(re.findall(r'\b[A-Z][A-Z_]+\b', '\n'.join(scopes))) - {'R', 'ERR'}
    prelude += '\n'.join(f'const val {name} = "anchor"' for name in sorted(names))
    types = ['List<Constructor<*>>', 'List<Method>', 'List<Method>', 'List<Member>',
             'List<Method>', 'List<Method>', 'List<Class<*>>', 'List<Method>', 'List<Class<*>>']
    assert len(types) == len(scopes)
    body = '\n'.join(f'fun query{i}(): {t} {{ val matcher = ClassMatcher(); return {s} }}'
                     for i, (s, t) in enumerate(zip(scopes, types)))
    (p / 'Scopes.kt').write_text(prelude + '\n' + body)
    subprocess.run(compiler + ['-classpath', f'{classes}:{std}:{dex}', '-d', str(p / 'scopes.jar'),
                              str(p / 'Scopes.kt')], check=True, timeout=60)
print('PASS extracted production query scopes: real manager/DexKit ABI and detached return types')
