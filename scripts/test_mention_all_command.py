"""Compile production mention-all policy and test command/member boundaries; no Gradle/device sends."""
from pathlib import Path
import subprocess, tempfile

root = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
fixtures = {
    'Android.kt': 'package android.content\nopen class Context\n',
    'Storage.kt': 'package t.mma.preferences\nobject HchatStorage { fun preferences(c: android.content.Context, n: String): Any = Any() }\n',
    'Test.kt': r'''package t.mma.hooks.items.mentionall
fun main() {
    check(MentionAllCommand.normalize("  #notify  ") == "#notify")
    check(MentionAllCommand.normalize("  ") == "#aa")
    check(MentionAllCommand.normalize("invalid command") == "#aa")
    check(MentionAllCommand.normalize("x".repeat(65)) == "#aa")
    check(MentionAllCommand.body("#aa", "#aa") == "\u200B")
    check(MentionAllCommand.body("#aa hello", "#aa") == "hello")
    check(MentionAllCommand.body("#aabb", "#aa") == null)
    check(MentionAllCommand.body("prefix #aa", "#aa") == null)
    check(MentionAllCommand.body("#AA", "#aa") == null)
    val members = listOf("self", "alice", "alice", "notify@all", "weixin", "filehelper", "bob")
    check(MentionAllCommand.recipients(members, "self") == listOf("alice", "bob"))
    val capped = MentionAllCommand.recipients((1..700).map { "user$it" } + "self", "self")
    check(capped.size == 499 && capped.last() == "user499" && "self" !in capped)
    check(runCatching { MentionAllCommand.recipients(listOf("alice,bob"), "self") }.isFailure)
    check(runCatching { MentionAllCommand.recipients(listOf("alice"), "") }.isFailure)
    println("PASS mention-all command matching, normalization, self/exclusion, deduplication, invalid IDs, and 499-member cap")
}''',
}
with tempfile.TemporaryDirectory() as td:
    tmp = Path(td)
    for name, text in fixtures.items():
        (tmp / name).write_text(text)
    jar = tmp / 'test.jar'
    command = root / 'app/src/main/java/t/mma/hooks/items/mentionall/MentionAllCommand.kt'
    settings = root / 'app/src/main/java/t/mma/hooks/items/mentionall/MentionAllSettings.kt'
    subprocess.run(['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect',
                    '-classpath', str(stdlib), '-d', str(jar), str(settings), str(command),
                    str(tmp/'Android.kt'), str(tmp/'Storage.kt'), str(tmp/'Test.kt')], check=True, timeout=60)
    subprocess.run(['java', '-cp', f'{jar}:{stdlib}', 't.mma.hooks.items.mentionall.TestKt'], check=True, timeout=20)
