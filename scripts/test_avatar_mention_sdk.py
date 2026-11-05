"""SDK directional compilation of avatar mention runtime and shared avatar binding; no Gradle."""
from pathlib import Path
import subprocess
import tempfile

repo = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
abi = repo / 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
cp = [abi, repo / 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',
      Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
assert all(p.exists() for p in cp), 'Missing Android SDK/cached ABI; Gradle forbidden'
cp += list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))
cp += list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar'))
cp += list(libs.glob('*.jar'))
base = repo / 'app/src/main/java/t/mma'
sources = [base / 'hooks/core' / n for n in [
    'FeatureOwnership.kt', 'FeatureResourceScope.kt', 'PreferenceRuntimeOwner.kt',
    'BaseFeature.kt', 'Feature.kt', 'FeatureContext.kt', 'HookRegistry.kt', 'DexInstallScheduler.kt']]
sources += list((base / 'hooks/items/mentionextension').glob('AvatarMention*.kt'))
sources += list((base / 'hooks/items/hideavatar').glob('*.kt'))
sources += [base / 'hooks/items/antirecall/DynamicRecallGradient.kt',
            base / 'hooks/api/model/WeChatMessage.kt', base / 'dexkit/DexKitBridgeManager.java']
with tempfile.TemporaryDirectory(prefix='pl-avatar-mention-sdk-') as directory:
    toggle = Path(directory) / 'Toggle.kt'
    toggle.write_text('package t.mma.ui\nobject FeatureToggleRegistry { fun isEnabled(c:android.content.Context,id:String)=false }')
    sources.append(toggle)
    result = subprocess.run([
        'java', '-Xmx384m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-jvm-target', '17',
        '-Xfriend-paths=' + str(abi), '-nowarn', '-no-stdlib', '-no-reflect',
        '-classpath', ':'.join(map(str, cp)), '-d', directory, *map(str, sources)],
        cwd=repo, capture_output=True, text=True, timeout=120)
    print(result.stdout, end='')
    print(result.stderr, end='')
    if result.returncode:
        raise SystemExit(result.returncode)
print('PASS avatar mention production runtime + shared avatar binding SDK compilation (not Gradle/R8/device)')
