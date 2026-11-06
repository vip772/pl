"""Compile swipe production sources using existing project ABI; never invokes Gradle."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
abi = root / 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
classpath = [abi, root / 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes', Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
assert all(path.exists() for path in classpath), 'Existing SDK/ABI required; do not run Gradle'
classpath += list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))
classpath += list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar'))
classpath += list(libs.glob('*.jar'))
source = root / 'app/src/main/java/t/mma'
sources = list((source / 'hooks/items/swipequote').glob('*.kt'))
sources += [source / 'hooks/core' / name for name in ['BaseFeature.kt', 'Feature.kt', 'FeatureContext.kt', 'FeatureResourceScope.kt', 'DexInstallScheduler.kt', 'HookRegistry.kt', 'FeatureOwnership.kt']]
sources += [source / 'dexkit/DexKitBridgeManager.java', source / 'dexkit/DexMethodCache.kt']
sources += [source / name for name in ['hooks/api/media/VoiceMessageDurationResolver.kt', 'hooks/api/message/SingleMessageMenuLocator.kt', 'hooks/api/message/WeChatRetransmitPayload.kt']]
with tempfile.TemporaryDirectory(prefix='swipe-repeat-sdk-') as directory:
    output = Path(directory)
    registry = output / 'FeatureToggleRegistry.kt'
    registry.write_text('package t.mma.ui\nobject FeatureToggleRegistry { fun isEnabled(c:android.content.Context,id:String)=true }\n')
    sources.append(registry)
    command = ['java', '-Xmx384m', '-cp', ':'.join(map(str, libs.glob('*.jar'))), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-jvm-target', '17', '-Xfriend-paths=' + str(abi), '-nowarn', '-no-stdlib', '-no-reflect', '-classpath', ':'.join(map(str, classpath)), '-d', str(output / 'out'), *map(str, sources)]
    result = subprocess.run(command, capture_output=True, text=True, timeout=90)
    print(result.stdout, end='')
    print(result.stderr, end='')
    if result.returncode:
        raise SystemExit(result.returncode)
print('PASS swipe production package + current core SDK compile; existing ABI and UI registry fixture; no Gradle/APK/device')
