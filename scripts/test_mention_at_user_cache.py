"""Compile the production bind-frame at-user cache with a counted model; no Gradle."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
feature = (root / 'app/src/main/java/t/mma/hooks/items/mentionhighlight/MentionHighlightFeature.kt').read_text()
start = feature.index('    private class BindState(')
end = feature.index('    private data class Gesture(')
state = feature[start:end].replace('private class BindState', 'class BindState')
assert 'fun effectiveAtUserList()' in state

main = '''import t.mma.hooks.api.model.*
import t.mma.hooks.items.mentionhighlight.MentionHighlightScope

'''+state+'''
fun message(source: String): WeChatMessage = WeChatMessage(
    1, 0, 1, 0, 0, 0, "room@chatroom", "hello", "", "", "", 0, source, ""
)

fun main() {
    val source = "<msgsource><atuserlist>alice</atuserlist></msgsource>"
    val frame = BindState(message(source))
    val before = MentionCacheCounter.effectiveReads
    repeat(3000) {
        check(frame.effectiveAtUserList() == listOf("alice"))
    }
    check(MentionCacheCounter.effectiveReads == before + 1)

    val emptyFrame = BindState(message(""))
    val emptyBefore = MentionCacheCounter.effectiveReads
    repeat(3000) {
        check(emptyFrame.effectiveAtUserList().isEmpty())
    }
    check(MentionCacheCounter.effectiveReads == emptyBefore + 1)

    val nested = BindState(message("<msgsource><atuserlist>nested</atuserlist></msgsource>"))
    nested.effectiveAtUserList()
    check(nested.effectiveAtUserList() == listOf("nested"))
    check(frame.effectiveAtUserList() == listOf("alice"))
    check(MentionCacheCounter.effectiveReads == emptyBefore + 2)

    val fresh = BindState(message(source))
    fresh.effectiveAtUserList()
    check(MentionCacheCounter.effectiveReads == emptyBefore + 3)
    println("PASS: bind-local at-user cache reuses non-empty and empty results, isolates nested and fresh binds")
}
'''

with tempfile.TemporaryDirectory() as temp:
    work = Path(temp)
    (work / 'Main.kt').write_text(main)
    (work / 'MentionCacheCounter.kt').write_text(
        'package t.mma.hooks.api.model\n'
        'object MentionCacheCounter { @JvmField var effectiveReads: Int = 0 }\n'
    )
    model_dir = root / 'app/src/main/java/t/mma/hooks/api/model'
    model_names = ['WeChatMessage.kt', 'WeChatMessageTypes.kt', 'WeChatParsedMessage.kt']
    model_files = []
    for name in model_names:
        text = (model_dir / name).read_text()
        if name == 'WeChatMessage.kt':
            marker = '    fun getEffectiveAtUserList(): List<String> {'
            assert text.count(marker) == 1
            text = text.replace(marker, marker + '\n        MentionCacheCounter.effectiveReads++', 1)
        out = work / name
        out.write_text(text)
        model_files.append(out)
    scope = root / 'app/src/main/java/t/mma/hooks/items/mentionhighlight/MentionHighlightScope.kt'
    jar = work / 'check.jar'
    compiler = [
        'java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
        '-classpath', str(stdlib), '-d', str(jar), str(work / 'Main.kt'),
        str(work / 'MentionCacheCounter.kt'), *map(str, model_files), str(scope),
    ]
    subprocess.run(compiler, check=True, timeout=60)
    subprocess.run(['java', '-cp', f'{jar}:{stdlib}', 'MainKt'], check=True, timeout=15)
