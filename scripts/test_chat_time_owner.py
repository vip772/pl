"""Whole production chat-time feature on a synthetic host; not device acceptance."""
from pathlib import Path
import subprocess, tempfile

r = Path(__file__).resolve().parents[1]
feature = r / 'app/src/main/java/t/mma/hooks/items/chattime/ChatTimeStyleFeature.kt'
settings = r / 'app/src/main/java/t/mma/hooks/items/chattime/ChatTimeStyleSettings.kt'
provider = r / 'app/src/main/java/t/mma/hooks/items/chattime/ChatTimeStyleSettingsProvider.kt'
formatter = r / 'app/src/main/java/t/mma/hooks/items/chattime/ChatTimeFormatter.kt'
core = r / 'app/src/main/java/t/mma/hooks/core'

text = feature.read_text()
# 1) Owner: one runtime per enabled interval, no shared schedule and no mutable runtime capture.
assert 'DexInstallScheduler.schedule(' not in text
assert 'DexInstallScheduler.scheduleTask(' not in text
assert 'private var runtime' not in text
assert 'PreferenceRuntimeOwner(' in text
assert 'setOf(ChatTimeStyleSettings.KEY_MODE, ChatTimeStyleSettings.KEY_TIME_FORMAT)' in text
assert 'enabled = { ChatTimeStyleSettings.isConfigured(prefs) }' in text
assert 'DexInstallScheduler.installation(' in text
assert 'resources.register { owner.close() }' in text
assert 'resources.register { installation.close() }' in text
assert 'if (!resources.isClosed()) installation.schedule { owner.install() }' in text
assert 'resources.register { context.eventBus().unsubscribe(subscription) }' in text
assert 'trackResource { runtime.close() }' in text
assert 'runtime.start()' in text

# 2) Runtime owns handles, rollback on close race, and releases them on close().
assert 'private class ChatTimeStyleRuntime(' in text
assert 'private val hooks = ArrayList<XC_MethodHook.Unhook>()' in text
assert '@Volatile private var closed = false' in text
assert '@Volatile private var installed = false' in text
assert 'if (closed) return false' in text
assert 'pending.asReversed().forEach { unhook ->' in text
assert 'HookRegistry.get().unhook(unhook)' in text
assert 'prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)' in text
assert 'if (closed) return\n                    bindTime(param.args)' in text

# 3) Restore only touches views this generation wrote, retries, and falls back to its own handler.
assert 'private fun restoreOriginal(view: TextView, bound: BoundTime, attempt: Int)' in text
assert 'private fun stillOwned(view: TextView, bound: BoundTime): Boolean' in text
assert 'RESTORE_RETRY_LIMIT' in text
assert 'val posted = runCatching {' in text
assert 'if (!fallback)' in text
assert 'bound.styledText = view.text?.toString().orEmpty()' in text
assert 'private val mainHandler = Handler(Looper.getMainLooper())' in text
assert 'mainHandler.post { if (!closed) applyStyle(view, bound, mode) }' in text

# 4) Every reflection cache is bounded or keyed weakly, and cleared on close.
assert 'BoundedLruMap<Class<*>, Field>(FIELD_CACHE_LIMIT)' in text
assert 'BoundedLruSet<Class<*>>(FIELD_CACHE_LIMIT)' in text
assert 'size > 128' in text
assert 'rootFieldCache.clear()' in text
assert 'timeFieldCache.clear()' in text
assert 'unsupportedTimeHolders.clear()' in text
assert 'synchronized(resolutionMetadata) { resolutionMetadata.clear() }' in text
assert 'WeakHashMap<TextView, BoundTime>()' in text

# 5) Resolution keeps its verified rules: identity visited set, depth 4, id+time validation.
assert 'Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())' in text
assert 'depth > 4' in text
assert 'it > 0L && messageId(value) > 0L' in text
assert 'arrayOf("getMsgId", "getMsgID")' in text
assert 'arrayOf("field_createTime", "createTime")' in text
assert 'CACHE_SCHEMA = "chat_time_style_v1"' in text

# 6) Settings and identity stay byte-stable, and the feature stays registered.
st = settings.read_text()
assert 'const val PREFS_NAME = "Hchat_chat_time_style_config"' in st
assert 'const val KEY_MODE = "chat_time_mode"' in st
assert 'const val KEY_TIME_FORMAT = "chat_time_format"' in st
assert 'fun isConfigured(prefs: SharedPreferences): Boolean' in st
assert 'ChatTimeStyleFeature.ID' in provider.read_text()
registry = (r / 'app/src/main/java/t/mma/hooks/core/FeatureRegistry.kt').read_text()
assert 'register(ChatTimeStyleFeature())' in registry

toggle = (r / 'app/src/main/java/t/mma/ui/FeatureToggleRegistry.kt').read_text()
assert '"chat_time_style" -> string(context, "Hchat_chat_time_style_config", "chat_time_mode") in setOf("custom", "hidden")' in toggle

# 7) Compile and run the production files against the synthetic host.
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
root = r / 'scripts/tests/chattime'
hosts = sorted((root / 'host').glob('*.kt'))
with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    cmd = [
        'java', '-Xmx512m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-no-reflect', '-nowarn',
        '-classpath', str(std), '-d', str(p / 'test.jar'),
        str(root / 'RegressionMain.kt'), str(root / 'HostFixtures.kt'), str(root / 'HolderFixtures.kt'),
        str(feature), str(settings), str(provider), str(formatter),
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
         't.mma.hooks.items.chattime.RegressionMainKt'],
        capture_output=True, text=True, timeout=300
    )
    print(ran.stdout)
    print(ran.stderr)
    raise SystemExit(ran.returncode)
