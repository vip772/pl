"""SDK directional compilation of forward sender and media APIs; no Gradle."""
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
sources = [base / 'hooks/api/media/VoiceMessageDurationResolver.kt', base / 'hooks/api/media/WeChatInternalServices.kt']
sources += [base / 'hooks/items/selectedmessages' / n for n in ['SelectedMessageModuleSender.kt', 'SelectedMessageMediaFiles.kt', 'SelectedMessageSnapshot.kt', 'SelectedSendBudget.kt', 'ForwardSendPolicy.kt', 'SelectedMessageSendHandle.kt']]
sources += [base / 'hooks/api/media/WeChatImageApi.java', base / 'hooks/api/media/WeChatVideoApi.java']
sources += [base / 'hooks/core' / n for n in ['FeatureOwnership.kt', 'FeatureResourceScope.kt', 'PreferenceRuntimeOwner.kt', 'BaseFeature.kt', 'Feature.kt', 'FeatureContext.kt', 'HookRegistry.kt', 'DexInstallScheduler.kt']]
sources += [base / 'dexkit/DexKitBridgeManager.java', base / 'hooks/api/message/WeChatRetransmitPayload.kt', base / 'hooks/api/model/WeChatMessage.kt']
sources += list((base / 'hooks/items/messageforward').glob('*.kt'))
sources += list((base / 'hooks/items/selectedmessages').glob('*.kt'))
sources += list((base / 'hooks/items/scheduledtask').glob('*.kt'))
sources += list((base / 'hooks/api/sns').glob('*.kt'))
sources += list((base / 'hooks/api/message').glob('*Locator.kt'))
sources += [base / 'hooks/api/message/MultiSelectMessageUi.kt']
sources += [base / p for p in ['hooks/api/media/FavoriteVoiceResolver.kt', 'hooks/api/media/FavoriteMenuLocator.kt', 'hooks/api/media/WeChatFavoriteApi.kt', 'hooks/api/net/WeChatNetworkApi.kt', 'hooks/api/runtime/WeChatTaskApi.kt', 'ui/miuix/ContactPickerNames.kt']]
sources = list(dict.fromkeys(sources))
with tempfile.TemporaryDirectory(prefix='pl-forward-sdk-') as directory:
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
    java_sources = [str(p) for p in sources if str(p).endswith('.java')]
    result = subprocess.run(['javac', '-J-Xmx256m', '-proc:none', '-source', '17', '-target', '17',
        '-classpath', directory + ':' + ':'.join(map(str, cp)), '-d', directory, *java_sources],
        cwd=repo, capture_output=True, text=True, timeout=120)
    print(result.stdout, end='')
    print(result.stderr, end='')
    if result.returncode: raise SystemExit(result.returncode)
print('PASS forward menu/hooks/sender Kotlin + media Java directional SDK compilation against cached dependencies; not Gradle/R8/device')
