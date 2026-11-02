"""Whole production multi-recall feature on a synthetic host; not device acceptance."""
from pathlib import Path
import subprocess, tempfile

r = Path(__file__).resolve().parents[1]
feature = r / 'app/src/main/java/t/mma/hooks/items/multirecall/MultiRecallFeature.kt'
settings = r / 'app/src/main/java/t/mma/hooks/items/multirecall/MultiRecallSettings.kt'
provider = r / 'app/src/main/java/t/mma/hooks/items/multirecall/MultiRecallSettingsProvider.kt'
locator = r / 'app/src/main/java/t/mma/hooks/api/message/MultiSelectMessageMenuLocator.kt'
resolver = r / 'app/src/main/java/t/mma/hooks/api/message/MultiSelectMessageResolver.kt'
ui = r / 'app/src/main/java/t/mma/hooks/api/message/MultiSelectMessageUi.kt'
core = r / 'app/src/main/java/t/mma/hooks/core'

text = feature.read_text()

# 1) The shared schedule and the mutable hooker field are gone; ownership is per interval.
assert 'DexInstallScheduler.schedule(' not in text
assert 'DexInstallScheduler.scheduleTask(' not in text
assert 'private var hooker' not in text
assert 'PreferenceRuntimeOwner(' in text
assert 'setOf(MultiRecallSettings.KEY_ENABLE)' in text
assert 'DexInstallScheduler.installation(' in text
assert 'stage = DexInstallScheduler.Stage.BRIDGE' in text
assert 'resources.register { owner.close() }' in text
assert 'resources.register { installation.close() }' in text
assert 'installation.schedule { owner.install() }' in text
assert 'if (!resources.isClosed()) installation.schedule { owner.install() }' in text
assert 'resources.register { context.eventBus().unsubscribe(subscription) }' in text
assert 'trackResource { runtime.close() }' in text
assert 'runtime.start()' in text

# 2) The runtime owns handles and dedupes per member with a close-race rollback.
assert 'private class MultiRecallRuntime(' in text
assert 'private val installedMembers = LinkedHashSet<String>()' in text
assert 'private val hooks = ArrayList<XC_MethodHook.Unhook>()' in text
assert '@Volatile private var closed = false' in text
assert 'fun hookOnce(method: Method, callback: XC_MethodHook): Boolean' in text
assert 'val memberKey = method.toGenericString()' in text
assert 'pending.asReversed().forEach { unhook ->' in text
assert 'synchronized(lock) { installedMembers.clear() }' in text
assert 'hookedMethods' not in text

# 3) Both entry points stay gated by the stored preference and by the instance close flag.
assert 'private fun addRecallMenu(param: XC_MethodHook.MethodHookParam)' in text
assert 'private fun handleRecallMenuClick(param: XC_MethodHook.MethodHookParam, exitMethod: Method)' in text
assert text.count('if (closed) return') >= 4
assert 'if (!enabled()) {' in text
assert 'private fun enabled(): Boolean = MultiRecallFeature.isEnabled(context.hostContext())' in text
assert '@JvmStatic' in text
assert 'fun isEnabled(context: Context?): Boolean' in text

# 4) The revoke loop keeps its native semantics and stops on close.
assert 'if (menuItem.itemId != MENU_ITEM_ID) return' in text
assert 'param.result = null' in text
assert 'for (message in messages) {' in text
assert 'if (closed) break' in text
assert 'sender?.revokeNative(message) == true' in text
assert 'if (closed) return' in text
assert 'if (closed) return@runOnUiThread' in text
assert 'MultiSelectMessageUi.resolveExitTarget(' in text
assert 'exitTarget.exit { message, error -> report(message, error) }' in text
assert 'MultiSelectMessageResolver.resolve(param.thisObject)' in text
assert 'private const val MENU_ITEM_ID = 0x48435243' in text
assert '"批量撤回[H]"' in text

# 5) The locator, resolver and UI helper files keep their verified matching rules.
loc = locator.read_text()
assert 'const val CLICK_METHOD_ANCHOR = "FinalShareCountByType"' in loc
assert 'name("onCreateMMMenu")' in loc
assert 'name("onMMMenuItemSelected")' in loc
assert 'paramTypes("android.view.MenuItem", "int")' in loc
assert 'declaredClass(CHATTING_COMPONENT_PREFIX, StringMatchType.StartsWith)' in loc
assert 'addCall(MethodMatcher(menuClickMethod))' in loc
assert loc.count('.findMethod(') == 3
assert 'private const val COMPONENT_SEARCH_DEPTH = 4' in resolver.read_text()
assert 'private const val COMPONENT_SEARCH_DEPTH = 4' in ui.read_text()

# 6) Settings and the feature id stay byte-identical, and the feature stays registered.
assert 'const val PREFS_NAME = "Hchat_multi_recall_config"' in settings.read_text()
assert 'const val KEY_ENABLE = "multi_recall_enable"' in settings.read_text()
assert 'const val DEFAULT_ENABLE = false' in settings.read_text()
assert 'MultiRecallFeature.ID' in provider.read_text()
registry = (r / 'app/src/main/java/t/mma/hooks/core/FeatureRegistry.kt').read_text()
assert 'register(MultiRecallFeature())' in registry

libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
root = r / 'scripts/tests/multirecall'
hosts = sorted((root / 'host').glob('*.kt'))
with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    cmd = [
        'java', '-Xmx512m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-no-reflect', '-nowarn',
        '-classpath', str(std), '-d', str(p / 'test.jar'),
        str(root / 'RegressionMain.kt'), str(feature), str(settings), str(provider),
        str(locator), str(resolver), str(ui),
        str(core / 'PreferenceRuntimeOwner.kt'), str(core / 'FeatureResourceScope.kt'),
    ]
    cmd += [str(h) for h in hosts]
    compiled = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    if compiled.returncode:
        print(compiled.stdout)
        print(compiled.stderr)
        raise SystemExit(compiled.returncode)
    ran = subprocess.run(
        ['java', '-cp', str(p / 'test.jar') + ':' + str(std),
         't.mma.hooks.items.multirecall.RegressionMainKt'],
        capture_output=True, text=True, timeout=300
    )
    print(ran.stdout)
    print(ran.stderr)
    raise SystemExit(ran.returncode)
