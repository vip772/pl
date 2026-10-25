"""Compare lazy source fallback to the former eager implementation; no Gradle."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
model_dir = root / 'app/src/main/java/t/mma/hooks/api/model'
model = (model_dir / 'WeChatMessage.kt').read_text()
legacy = '''    fun legacyMsgSource(): String {
        val raw = firstNotBlank(
            msgSource,
            xmlSection(withoutQuotedMessage(content), "msgsource"),
            xmlSection(withoutQuotedMessage(bodyContent()), "msgsource"),
            xmlSection(withoutQuotedMessage(reserved), "msgsource")
        )
        return unescapeXml(raw)
    }
'''
model = model.replace('    fun getAtUserList()', legacy + '\n    fun getAtUserList()', 1)
for marker, counter in [
    ('private fun withoutQuotedMessage(raw: String): String {', 'strips'),
    ('fun xmlSection(xml: String?, tag: String): String {', 'sections'),
]:
    assert model.count(marker) == 1
    model = model.replace(marker, marker + '\n            SourceCounts.' + counter + '++', 1)

main = r'''import t.mma.hooks.api.model.*
fun msg(source:String, body:String, reserved:String, talker:String="room@chatroom") =
    WeChatMessage(1,0,49,0,0,0,talker,body,"",reserved,"",0,source,"")
fun main() {
    val a="<msgsource><atuserlist>alice</atuserlist></msgsource>"
    val b="<msgsource><atuserlist>bob</atuserlist></msgsource>"
    val quote="<refermsg>$a</refermsg>"
    val values=listOf("", " ", "\t\n", a, b,
        "<msgsource> </msgsource>", "<MSGSOURCE x='1'>own&amp;value</MSGSOURCE>",
        "<msgsource><![CDATA[&lt;value&gt;]]></msgsource>",
        "&lt;msgsource&gt;encoded&lt;/msgsource&gt;", quote,
        "<refermsg>$a", "$quote$b", "<!-- $quote -->$b",
        "<![CDATA[$quote]]>$b", "<refermsg><refermsg>$a</refermsg></refermsg>$b")
    var cases=0
    for(source in values) for(body in values) for(reserved in values) {
        for(talker in listOf("friend", "room@chatroom")) {
            val m=msg(source, "sender:\n$body", reserved, talker)
            check(m.getMsgSource()==m.legacyMsgSource()) { "source fallback mismatch" }
            cases++
        }
    }
    fun counted(m:WeChatMessage, expected:String, calls:Int) {
        SourceCounts.strips=0; SourceCounts.sections=0
        check(m.getMsgSource()==expected)
        check(SourceCounts.strips==calls && SourceCounts.sections==calls)
    }
    counted(msg(a, quote+b, b), a, 0)
    counted(msg("", quote+b, a), "<atuserlist>bob</atuserlist>", 1)
    counted(msg(" ", quote, b), "<atuserlist>bob</atuserlist>", 3)
    counted(msg("", quote, ""), "", 3)
    val stable=msg(a,quote+b,b)
    SourceCounts.strips=0;SourceCounts.sections=0
    repeat(3000) { check(stable.getMsgSource()==a) }
    check(SourceCounts.strips==0 && SourceCounts.sections==0)
    println("PASS: $cases legacy-equivalence cases; direct source zero XML work, ordered fallback and 3000 direct reads")
}
'''
with tempfile.TemporaryDirectory() as td:
    work = Path(td)
    (work / 'WeChatMessage.kt').write_text(model)
    (work / 'Main.kt').write_text(main)
    (work / 'Counts.kt').write_text('package t.mma.hooks.api.model\nobject SourceCounts { var strips=0; var sections=0 }')
    files = [work / 'WeChatMessage.kt', work / 'Main.kt', work / 'Counts.kt',
             model_dir / 'WeChatMessageTypes.kt', model_dir / 'WeChatParsedMessage.kt']
    jar = work / 'test.jar'
    subprocess.run(['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
        '-classpath', str(std), '-d', str(jar), *map(str, files)], check=True, timeout=60)
    subprocess.run(['java', '-cp', f'{jar}:{std}', 'MainKt'], check=True, timeout=60)
