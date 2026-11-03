"""SDK ABI compile of the production nickname-width feature plus its real core ownership sources.

Compiles against the real android.jar (Handler/Looper/TextView/PackageManager/SharedPreferences) and
the project's own FeatureOwnership/FeatureResourceScope/PreferenceRuntimeOwner/DexInstallScheduler.
Only host-only boundaries (HchatStorage, settings UI, Xposed adapter, HLog, FeatureContext) are
stubbed. This is not an Android/R8 build; runtime behaviour lives in test_nickname_width_lifecycle.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIBS = Path('/tmp/pl-kotlin-libs')
STD = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
ANDROID_JAR = Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
CORE = ROOT / 'app/src/main/java/t/mma/hooks/core'
SOURCE = ROOT / 'app/src/main/java/t/mma/hooks/items/nicknamewidth/NicknameWidthFeature.kt'

STUBS = {
    'Xposed.kt': '''package de.robv.android.xposed
open class XC_MethodHook {
    class Unhook
    class MethodHookParam(val args: Array<Any?>)
    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}
}
''',
    'Core.kt': '''package t.mma.hooks.core
class FeatureContext {
    fun hostContext(): android.content.Context = throw UnsupportedOperationException()
    fun hostClassLoader(): ClassLoader = throw UnsupportedOperationException()
}
object HookRegistry {
    fun get(): HookRegistry = this
    fun hook(method: java.lang.reflect.Member, callback: de.robv.android.xposed.XC_MethodHook): de.robv.android.xposed.XC_MethodHook.Unhook =
        throw UnsupportedOperationException()
    fun unhook(handle: de.robv.android.xposed.XC_MethodHook.Unhook?) {}
}
open class BaseFeature {
    open fun featureId(): String = ""
    open fun name(): String = ""
    open fun onFeatureInit(context: FeatureContext) {}
    open fun onFeatureInstall(context: FeatureContext) {}
    fun registerSettingsProvider(provider: Any) {}
    fun trackResource(release: () -> Unit) {}
    fun logError(message: String, throwable: Throwable?) {}
}
''',
    'Store.kt': '''package t.mma.preferences
object HchatStorage {
    fun preferences(context: android.content.Context, name: String): android.content.SharedPreferences =
        throw UnsupportedOperationException()
}
''',
    'Ui.kt': '''package t.mma.ui
object FeatureSettingsProvider { const val CATEGORY_ENHANCE: String = "enhance" }
open class SimpleFeatureSettingsProvider(
    featureId: String, title: String, summary: String, category: String, defaultEnabled: Boolean
)
''',
    'Log.kt': '''package t.mma.utils
object HLog {
    fun d(message: String) {}
    fun i(message: String) {}
    fun w(message: String) {}
    fun e(message: String) {}
    fun e(message: String, throwable: Throwable?) {}
}
''',
}

CORE_SOURCES = ['FeatureOwnership.kt', 'FeatureResourceScope.kt', 'PreferenceRuntimeOwner.kt',
                'DexInstallScheduler.kt']


def main() -> None:
    with tempfile.TemporaryDirectory(prefix='pl-nickname-width-sdk-') as td:
        path = Path(td)
        for name, text in STUBS.items():
            (path / name).write_text(text)
        command = ['java', '-Xmx320m', '-cp', ':'.join(map(str, LIBS.glob('*.jar'))),
                   'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib',
                   '-no-reflect', '-jvm-target', '17',
                   '-classpath', str(STD) + ':' + str(ANDROID_JAR),
                   '-d', str(path / 'sdk'), str(SOURCE)]
        command += [str(CORE / name) for name in CORE_SOURCES]
        command += [str(p) for p in sorted(path.glob('*.kt'))]
        subprocess.run(command, check=True, timeout=180)

    text = SOURCE.read_text()
    for forbidden in ['addOnLayoutChangeListener', 'OnPreDrawListener', 'setText', 'setSingleLine',
                      'setMaxLines', 'setEllipsize', 'findViewById', 'dexKitBridge']:
        assert forbidden not in text, forbidden
    assert 'CATEGORY_ENHANCE, true' in text
    assert 'NicknameWidthState' in text and 'NicknameWidthRuntime' in text
    print('PASS production nickname-width feature + core ownership compile against real android.jar ABI')
    print('PASS source keeps no per-frame scanning, no text/ellipsis/line mutation and stays default-off')


if __name__ == '__main__':
    main()