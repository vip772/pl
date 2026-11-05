"""Exercise production mention labels and real message parser without Gradle."""
from pathlib import Path
import subprocess
import tempfile

repo = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory(prefix='pl-avatar-label-') as directory:
    main = Path(directory) / 'Test.kt'
    main.write_text('''package t.mma.hooks.items.mentionextension
import t.mma.hooks.api.model.WeChatMessage
fun main() {
 var cases = 0
 fun expect(users:String, expected:String, outgoing:Boolean=false, body:String="hello", room:String="room@chatroom", self:String="me") {
  val source = if(users.isEmpty()) "" else "<msgsource><atuserlist><![CDATA[$users]]></atuserlist></msgsource>"
  val m = WeChatMessage(1, 1, 1, 0, if(outgoing) 1 else 0, 1, room, body, "", "", "", 0, source, self)
  check(AvatarMentionLabel.text(m)==expected) { "$users / $outgoing: ${AvatarMentionLabel.text(m)} != $expected" }
  cases++
 }
 expect("me", "@我")
 expect("other", "@1人")
 expect("me,other", "@2人")
 expect("other,a,b", "@3人")
 expect("me,me,other", "@2人")
 expect("other", "@1人", true)
 expect("other,a,b", "@3人", true)
 expect("notify@all", "@所有人")
 expect("notify@all,me,other", "@所有人")
 expect("notify@all", "@所有人", true)
 expect("", "", body="@所有人 @me")
 expect("me", "", room="friend")
 expect("", "", body="<refermsg><msgsource><atuserlist>me</atuserlist></msgsource></refermsg>")
 expect("announcement@all", "")
 expect("me", "@1人", self="")
 expect("me", "@所有人", body="@所有人\\u2005hello")
 println("PASS $cases avatar mention label cases: incoming/outgoing/count/all/dedup/quote/private/announcement")
}
''')
    jar = Path(directory) / 'test.jar'
    subprocess.run(['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
        '-classpath', str(stdlib), '-d', str(jar), str(main),
        str(repo / 'app/src/main/java/t/mma/hooks/api/model/WeChatMessage.kt'),
        str(repo / 'app/src/main/java/t/mma/hooks/api/model/WeChatMessageTypes.kt'),
        str(repo / 'app/src/main/java/t/mma/hooks/items/mentionextension/AvatarMentionLabel.kt')], check=True, timeout=90)
    subprocess.run(['java', '-cp', f'{jar}:{stdlib}', 't.mma.hooks.items.mentionextension.TestKt'], check=True, timeout=15)
