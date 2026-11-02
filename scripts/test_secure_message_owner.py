"""Whole production secure-message pair on a synthetic host; not device acceptance."""
from pathlib import Path
import subprocess, tempfile

r = Path(__file__).resolve().parents[1]
anti = r / 'app/src/main/java/t/mma/hooks/items/securemessage/AntiSecureMessageFeature.kt'
send = r / 'app/src/main/java/t/mma/hooks/items/securemessage/SendSecureMessageFeature.kt'
settings = r / 'app/src/main/java/t/mma/hooks/items/securemessage/SecureMessageSettings.kt'
source = r / 'app/src/main/java/t/mma/hooks/items/securemessage/SecureMessageSource.kt'
profile = r / 'app/src/main/java/t/mma/hooks/items/securemessage/EmojiSecureMessageProfile.kt'
locator = r / 'app/src/main/java/t/mma/hooks/api/message/SingleMessageMenuLocator.kt'
types = r / 'app/src/main/java/t/mma/hooks/api/model/WeChatMessageTypes.kt'
version = r / 'app/src/main/java/t/mma/hooks/api/model/WeChatVersionInfo.kt'
core = r / 'app/src/main/java/t/mma/hooks/core'

a = anti.read_text()
s = send.read_text()

# 1) Shared schedule and every feature-static installed flag are gone from both files.
for text in (a, s):
    assert 'DexInstallScheduler.schedule(' not in text
    assert 'DexInstallScheduler.scheduleTask(' not in text
for text in (a, s):
    assert 'PreferenceRuntimeOwner(' in text
    assert 'DexInstallScheduler.installation(' in text
    assert 'resources.register { owner.close() }' in text
    assert 'resources.register { installation.close() }' in text
    assert 'installation.schedule { owner.install() }' in text
    assert 'resources.register { context.eventBus().unsubscribe(subscription) }' in text
    assert 'trackResource { runtime.close() }' in text
    assert 'private val installedMembers = LinkedHashSet<String>()' in text
    assert 'fun hookOnce(method: Method, callback: XC_MethodHook): Boolean' in text
    assert 'pending.asReversed().forEach { unhook ->' in text

# 2) Anti side: check hook, insert strip and menu marker pairing stay intact.
assert '@Volatile private var closed = false' in a
nl = chr(10)
assert '// 关闭后不再拦截' in a
assert 'if (closed) return' in a
assert 'param.result = false' in a
assert 'SECURE_NODE.replace(current, "")' in a
assert 'states.addLast(if (closed) MenuMarkerState.None else hideMenuMarker(param))' in a
assert 'val state = if (states.isEmpty()) null else states.removeLast()' in a
assert 'if (states.isEmpty()) menuMarkerStates.remove()' in a
assert 'data class Restorable(val message: Any, val source: String) : MenuMarkerState' in a
assert 'SingleMessageMenuLocator.menuCreateMethods(context)' in a
assert 'SecureMessageSettings.CACHE_CHECK, candidates.first()' in a
assert 'const val SECURE_CHECK_ANCHOR = ".msgsource.sec_msg_node.sfn"' in a
assert 'val SOURCE_SETTERS = arrayOf("setMsgSource", "setMsgsource", "setSource")' in a
assert 'val SOURCE_FIELDS = arrayOf("field_msgSource", "msgSource", "G", "g")' in a
assert a.count('.findMethod(') == 2

# 3) Send side: all three verified paths, and no shared mutable statics left.
assert 'private var insertInstalled = false' in s
assert 'private var mergeInstalled = false' in s
assert 'private var emojiSourceInstalled = false' in s
assert 'private var emojiDispatchInstalled = false' in s
assert 'private var emojiCheckInstalled = false' in s
assert 'fun installInsertHook(insert: Method): Boolean' in s
assert 'fun installSourceMergeHooks(insert: Method): Boolean' in s
assert 'fun installEmojiHooks(): Boolean' in s
assert 'fun installEmojiSelfCheckHooks(runtimeKey: String): Boolean' in s
assert 'EmojiSecureMessageProfile.forVersion(version?.versionName, version?.versionCode ?: 0L)' in s
assert 'SecureMessageSource.addMarker(param.result as? String)' in s
assert 'KavaReflector.writeField(field, item, SecureMessageSource.addMarker(source))' in s
assert 'fun isStringSetter(method: Method): Boolean' in s
assert 'private fun looksLikeMsgSource(value: String): Boolean' in s
assert 'if (params.size == 2 && params[1] == Boolean::class.javaPrimitiveType) score += 8' in s
assert 'if (params.any { it.name.startsWith("com.tencent.mm.storage.") }) score += 6' in s
assert 'const val EMOJI_TYPE = 47' in s
assert 'const val EMOJI_MESSAGE_CLASS = "com.tencent.mm.storage.e9"' in s
assert s.count('.findMethod(') == 2

# 4) Settings boundary must stay byte-identical: ids, prefs names and the marker XML.
settings_text = settings.read_text()
assert 'const val SEND_ID = "send_secure_message"' in settings_text
assert 'const val ANTI_ID = "anti_secure_message"' in settings_text
assert 'const val SEND_PREFS = "Hchat_secure_message"' in settings_text
assert 'const val ANTI_PREFS = "Hchat_anti_secure_message"' in settings_text
assert 'const val CACHE_SCHEMA = "secure_message_v4"' in settings_text
assert '<sec_msg_node>' in settings_text
assert 'fun addMarker(value: String?): String' in source.read_text()
assert 'sourceOwner = "ft1.d1"' in profile.read_text()
assert 'emojiScene = "k52.y"' in profile.read_text()
assert '0x48435250' in locator.read_text()

# 5) Both features stay registered in the feature registry.
registry = (r / 'app/src/main/java/t/mma/hooks/core/FeatureRegistry.kt').read_text()
assert 'register(AntiSecureMessageFeature())' in registry
assert 'register(SendSecureMessageFeature())' in registry

libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
root = r / 'scripts/tests/securemessage'
hosts = sorted((root / 'host').glob('*.kt'))
with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    cmd = [
        'java', '-Xmx512m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-no-reflect', '-nowarn',
        '-classpath', str(std), '-d', str(p / 'test.jar'),
        str(root / 'RegressionMain.kt'), str(anti), str(send), str(settings),
        str(source), str(profile), str(locator), str(types), str(version),
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
         't.mma.hooks.items.securemessage.RegressionMainKt'],
        capture_output=True, text=True, timeout=300
    )
    print(ran.stdout)
    print(ran.stderr)
    raise SystemExit(ran.returncode)
