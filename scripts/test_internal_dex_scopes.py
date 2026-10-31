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
    'typingreport/TypingReportBlockFeature.kt',
    'transparentavatar/UploadTransparentAvatarFeature.kt',
    'quickcontactedit/SnsAvatarMenuLocator.kt',
    'quickread/QuickMarkReadRuntime.kt',
    'roundavatar/RoundAvatarFeature.kt',
    'quoteclear/QuoteDeleteClearFeature.kt',
    'quickcontactedit/ConversationMenuLocator.kt',
    'shortvideo/FinderFeedDetailResolver.kt',
    'realtail/BeforeTransferNameQuery.kt',
    'antirecall/AntiRecallFeature.kt',
    'customnotify/CustomNotificationRuntime.kt',
    'moments/SnsAntiRecallFeature.kt',
    'moments/OriginalMomentsUploadFeature.kt',
    'realtail/RealNameTailRenderer.kt',
    'shortvideo/FinderMediaDownloadFeature.kt',
    'messagetextcolor/MessageTextColorFeature.kt',
    'dynamicbadge/DynamicBadgeFeature.kt',
    'conversationgroup/ConversationGroupRuntime.kt',
    'hometextcolor/HomeTextColorFeature.kt',
    'chattime/ChatTimeStyleFeature.kt',
    'membertitle/MemberTitleRenderer.kt',
    'messagebubble/MessageBubbleFeature.kt',
    'mentionhighlight/MentionHighlightFeature.kt',
    'patblock/PatBlockFeature.kt',
    'payment/fakebalance/FakeWalletBalanceFeature.kt',
    'fakevoiceduration/FakeVoiceDurationFeature.kt',
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
        assert any(token in scope for token in ['getMethodInstance', 'getConstructorInstance', 'R.loadClass(it.name, loader)', 'KavaReflector.loadClass(it.name, context.hostClassLoader())', 'map { it.name }']), relative
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
import org.luckypray.dexkit.query.enums.StringMatchType
import t.mma.dexkit.DexKitBridgeManager
object KavaReflector {
 fun declaredMethods(c:Class<*>?): List<Method> = c?.declaredMethods?.toList().orEmpty()
 fun loadClass(n:String,l:ClassLoader):Class<*>? = null
}
class Spec(val anchor:String = "anchor")
val spec = Spec()
fun isConversationAdapterGetView(m:Method,s:Spec) = true
fun isModernConversationBind(m:Method) = true
fun isLayoutInflaterFactoryMethod(m:Method) = true
fun methodDescriptor(m:Method) = m.toGenericString()
data class RevokeEntry(val method: Method, val invokedMethods: List<Method>)
class Context {
 fun dexKitManager() = DexKitBridgeManager()
 fun hostClassLoader() = ClassLoader.getSystemClassLoader()
}
val context = Context()
val strings = arrayOf("a")
val tag = "a"
val anchor = "a"
val methodName: String? = "method"
val predicate: (Method) -> Boolean = { true }
val query = FindMethod()
val loader = context.hostClassLoader()
val matcher: ClassMatcher = ClassMatcher()
object R {fun loadClass(n:String,l:ClassLoader):Class<*>? = null}
fun isLaunchConstructor(c: Constructor<*>) = true
fun isPrivateOpenUrlMethod(m: Method) = true
fun isSendTypingMethod(m: Method) = true
fun isSupportOnKeyCandidate(m: Method) = true
fun isQuoteClearCandidate(m: Method) = true
fun isLongClickMethod(m: Method) = true
fun isRequestConstructor(c: Constructor<*>) = true
fun isMsgProcessingClearByMessage(m: Method) = true
fun isMsgProcessingClearByKey(m: Method) = true
fun isLegacySelfRecallMediaCleanupMethod(m: Method) = true
fun isRevokeEntry(m: Method) = true
fun isNativeNotificationBuilderCandidate(m: Method) = true
fun isEnableFlutterSnsPageMethod(m: Method) = true
fun isModernBind(m: Method) = true
fun isLegacyConversationBind(m: Method) = true
fun isNativeGroupMenuCreateMethod(m: Method) = true
fun isNativeGroupMenuClickMethod(m: Method) = true
fun isAvatarDoubleClickMethod(m: Method) = true
fun isTickerTextMethod(m: Method) = true
fun isRecorderDurationMethod(m: Method) = true
'''
    names = set(re.findall(r'\b[A-Z][A-Z_]+\b', '\n'.join(scopes))) - {'R', 'ERR', 'MODERN_BIND_ANCHORS', 'LEGACY_BIND_ANCHORS', 'MODERN_CONVERSATION_BIND_ANCHORS'}
    prelude += 'val MODERN_CONVERSATION_BIND_ANCHORS = listOf("anchor")\n'
    prelude += 'val MODERN_BIND_ANCHORS = listOf("anchor")\nval LEGACY_BIND_ANCHORS = listOf("anchor")\n'
    prelude += '\n'.join(f'const val {name} = "anchor"' for name in sorted(names))
    types = ['List<Constructor<*>>', 'List<Method>', 'List<Method>', 'List<Member>',
             'List<Method>', 'List<Method>', 'List<Class<*>>', 'List<Method>', 'List<Class<*>>',
             'List<Method>', 'List<String>', 'List<Method>',
             'List<Method>', 'List<Member>', 'Method?', 'Method?',
             'Method?', 'List<Constructor<*>>', 'List<String>',
             'List<Method>', 'List<Method>', 'List<RevokeEntry>', 'List<Method>', 'List<Method>',
             'Method?', 'List<Method>', 'List<Method>', 'List<Method>', 'List<Method>', 'List<Method>',
             'List<Method>', 'List<Method>', 'List<Method>', 'List<Method>', 'Method?',
             'List<Method>', 'List<Method>', 'List<Method>', 'List<Method>', 'List<Method>']
    types += ['List<Method>', 'List<Method>', 'List<Method>', 'Method?', 'List<Method>', 'List<Method>', 'List<Method>']
    assert len(types) == len(scopes)
    body = '\n'.join(f'fun query{i}(): {t} {{ val matcher = {"MethodMatcher" if i == 11 else "ClassMatcher"}(); return {s} }}'
                     for i, (s, t) in enumerate(zip(scopes, types)))
    (p / 'Scopes.kt').write_text(prelude + '\n' + body)
    subprocess.run(compiler + ['-classpath', f'{classes}:{std}:{dex}', '-d', str(p / 'scopes.jar'),
                              str(p / 'Scopes.kt')], check=True, timeout=60)
print('PASS extracted production query scopes: real manager/DexKit ABI and detached return types')
anti = (base / 'antirecall/AntiRecallFeature.kt').read_text()
assert 'MethodData' not in anti and 'entry.data' not in anti
assert 'val invokedMethods: List<Method>' in anti
assert 'RevokeEntry(it, emptyList())' in anti
assert 'locateLegacyMessageStorageUpdateMethod(context, revokeEntries.map { it.method })' in anti
record = (base / 'hchatextra/ForwardedRecordTypeLabels.kt').read_text()
assert 'context.dexKitBridge()' not in record
assert 'withCurrentBridge { bridge ->\n            locateFresh(bridge, loader, key, prefs)' in record
assert record.count('locateFresh(') == 2
assert 'data class Targets(val bind: Method, val detail: Class<*>, val dataType: Field)' in record
fresh = record[record.index('    private fun locateFresh('):record.index('    private fun validBind(')]
assert 'bridge.getMethodData(viewType)?.usingFields' in fresh
assert 'bindData.usingFields' in fresh
assert 'typeFieldData.getFieldInstance(loader)' in fresh
print('PASS forwarded record field-data consumption: private fresh resolver owned by manager scope')
monet = (base / 'monetgenerator/MonetModuleGeneratorRuntime.kt').read_text()
assert 'context.dexKitBridge()' not in monet
assert 'val manager: DexKitBridgeManager' in monet
assert 'current.owner.get() === context' in monet
assert 'val owner: WeakReference<FeatureContext>' in monet
assert 'return manager.withCurrentBridge { bridge ->\n            MonetDexEvidenceCollector.collect(bridge, candidates)' in monet
