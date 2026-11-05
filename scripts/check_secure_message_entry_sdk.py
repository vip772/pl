"""Check standalone entry removal and compile affected sources with SDK/dependency ABI stubs, without Gradle."""
from pathlib import Path
import sys
import subprocess
import tempfile

r = Path(__file__).resolve().parents[1]
base = r / 'app/src/main/java/t/mma'
send = base / 'hooks/items/securemessage/SendSecureMessageFeature.kt'
anti = base / 'hooks/items/securemessage/AntiSecureMessageFeature.kt'
router = base / 'ui/miuix/MiuixSettingsPagePart03.kt'
page = base / 'ui/miuix/MiuixSettingsPagePart23.kt'
for path in (send, anti):
    assert 'registerSettingsProvider(' not in path.read_text(), path
for token in ('SendSecureMessageSettingsProvider', 'AntiSecureMessageSettingsProvider', 'SecureMessageMiuixPage'):
    assert not any(token in p.read_text() for p in (r / 'app/src').rglob('*.kt')), token
assert not (send.parent / 'SecureMessageSettingsProviders.kt').exists()
assert 'SpecialMessageFeature.ID -> SpecialMessageMiuixPage(context, onBack)' in router.read_text()
assert 'SecureMessageSettings.SEND_ID' not in router.read_text()
assert 'SecureMessageSettings.ANTI_ID' not in router.read_text()
page_text = page.read_text().split('internal fun SpecialMessageMiuixPage', 1)[1]
assert 'SettingsRouteTransition' not in page_text
assert 'ActionRow(' not in page_text
assert 'var route' not in page_text
assert 'sendPrefs, SecureMessageSettings.KEY_ENABLE, "安全消息"' in page_text
assert 'antiPrefs, SecureMessageSettings.KEY_ENABLE, "反安全消息"' in page_text
registry = (base / 'hooks/core/FeatureRegistry.kt').read_text()
for name in ('SendSecureMessageFeature', 'AntiSecureMessageFeature', 'SpecialMessageFeature'):
    assert f'.register({name}())' in registry
for name in ('SecureMessageSettings.kt', 'AntiSecureMessageFeature.kt', 'EmojiSecureMessageProfile.kt'):
    path = send.parent / name
    assert path.read_bytes() == subprocess.check_output(['git', 'show', '9366061:' + str(path.relative_to(r))], cwd=r), name
# Verified media mappings cover both size calculation and encoding.
send_text = send.read_text()
for token in ('version?.versionName != "8.0.78" || version.versionCode != 3180L',
              '"pc5.ot" to "i"', '"pc5.h37" to "t"', '"pc5.q76" to "p"', '"pc5.kk0" to "p"',
              'operation != 0 && operation != 1', '"pc5.v27"', '"computeSize"', '"writeFields"',
              'request.javaClass != imageClass || closed || !enabled()',
              'KavaReflector.invoke(getter, request, 9)',
              'KavaReflector.invokeSuccessfully(setter, request, 9,',
              'val mediaReady = installMediaEncodingHooks()', 'mediaReady && emojiReady'):
    assert token in send_text, token
assert send_text.count('mediaInstalled = false') == 2
print('PASS static removal, sole special-message route, direct switches, preserved preferences/anti/legacy emoji profile')
print('PASS version-gated media mappings, PB3 type gate, encoding/size coverage and lifecycle reset (static only)')
if '--static-only' in sys.argv:
    raise SystemExit(0)
libs = Path('/tmp/pl-kotlin-libs')
sdk = Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
assert sdk.exists(), 'Android SDK required; never invoke Gradle'
host = r / 'scripts/tests/securemessage/host'
with tempfile.TemporaryDirectory(prefix='pl-secure-sdk-') as td:
    out = Path(td)
    # Dependency-only ABI stubs; Android classes come exclusively from real SDK.
    core_stub = (host / 'CoreStubs.kt').read_text().replace(
        'FeatureContext(android.content.Context(), BaseFeature::class.java.classLoader)', 'TODO()')
    (out / 'CoreStubs.kt').write_text(core_stub)
    (out / 'Storage.kt').write_text('package t.mma.preferences' + chr(10) +
        'object HchatStorage {' + chr(10) +
        'fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()' + chr(10) +
        'fun store(n:String):android.content.SharedPreferences=TODO()' + chr(10) + '}')
    sources = [p for p in host.glob('*.kt') if p.name not in
               ('CoreStubs.kt', 'StorageStub.kt', 'AndroidStubs.kt', 'AndroidViewStubs.kt')]
    sources += list(out.glob('*.kt'))
    sources += list(send.parent.glob('*.kt'))
    sources += [base / 'hooks/core/PreferenceRuntimeOwner.kt', base / 'hooks/core/FeatureResourceScope.kt',
                base / 'hooks/api/message/SingleMessageMenuLocator.kt',
                base / 'hooks/api/model/WeChatMessageTypes.kt', base / 'hooks/api/model/WeChatVersionInfo.kt']
    cmd = ['java', '-Xmx384m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
           'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-jvm-target', '17', '-nowarn',
           '-no-stdlib', '-no-reflect', '-classpath', str(std) + ':' + str(sdk),
           '-d', str(out / 'check.jar'), *map(str, sources)]
    result = subprocess.run(cmd, cwd=r, capture_output=True, text=True, timeout=120)
    print(result.stdout, end='')
    print(result.stderr, end='')
    if result.returncode:
        raise SystemExit(result.returncode)
print('PASS complete secure-message production files SDK compilation; dependency ABI stubs only; no APK/R8/device execution')
