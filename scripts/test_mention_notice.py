"""Compile the real mention model and exercise mention classification without Gradle."""
from pathlib import Path
import os, subprocess, tempfile
root = Path(__file__).resolve().parents[1]
libs = Path(os.environ.get('KOTLIN_LIB_DIR', '/tmp/pl-kotlin-libs'))
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    tmp = Path(td)
    main = tmp / 'MentionTest.kt'
    main.write_text(r'''import t.mma.hooks.api.model.WeChatMessage
fun main() {
 fun kind(source:String,body:String="hello",self:String="me") = WeChatMessage.classifyAtMention(source,body,self)
 val me="<msgsource><atuserlist><![CDATA[me]]></atuserlist></msgsource>"
 val all="<msgsource><atuserlist><![CDATA[notify@all,me]]></atuserlist></msgsource>"
 check(kind(me)==WeChatMessage.AtMentionType.AT_ME)
 check(kind(all)==WeChatMessage.AtMentionType.AT_ALL)
 check(kind("", "@me @所有人")==WeChatMessage.AtMentionType.NONE)
 check(kind("<msgsource><atuserlist>other</atuserlist></msgsource>")==WeChatMessage.AtMentionType.OTHERS)
 check(kind(me,self="")==WeChatMessage.AtMentionType.OTHERS)
 check(kind("<msgsource><atuserlist>announcement@all</atuserlist></msgsource>")==WeChatMessage.AtMentionType.ANNOUNCEMENT_ALL)
 check(kind(me, "@所有人\u2005hello")==WeChatMessage.AtMentionType.AT_ALL)
 val quoted="<refermsg><content><![CDATA[@所有人\u2005old]]></content><msgsource><atuserlist>me</atuserlist></msgsource></refermsg>"
 val reply="<msg><appmsg><title>reply only</title><type>57</type>$quoted</appmsg></msg>"
 val model=WeChatMessage.transient("group@chatroom","B",reply,1L,false)
 check(model.getMsgSource().isEmpty())
 check(kind(model.getMsgSource(),model.bodyContent())==WeChatMessage.AtMentionType.NONE)
 check(kind(quoted)==WeChatMessage.AtMentionType.NONE)
 check(kind(me,reply)==WeChatMessage.AtMentionType.AT_ME)
 check(kind(all,reply)==WeChatMessage.AtMentionType.AT_ALL)
 check(kind(me,"<msg><appmsg><title>@所有人\u2005new</title>$quoted</appmsg></msg>")==WeChatMessage.AtMentionType.AT_ALL)
 val ownSource=reply.replace("</msg>","$me</msg>")
 val own=WeChatMessage.transient("group@chatroom","B",ownSource,1L,false)
 check(kind(own.getMsgSource(),own.bodyContent())==WeChatMessage.AtMentionType.AT_ME)
 check(kind("<refermsg><msgsource><atuserlist>notify@all</atuserlist></msgsource></refermsg>")==WeChatMessage.AtMentionType.NONE)
 check(kind("<refermsg><msgsource><atuserlist>me</atuserlist></msgsource>")==WeChatMessage.AtMentionType.NONE)
 println("PASS real model: direct mention, at-all, combined mention, plain text rejection, other user, missing identity, announcement distinction")
}
''')
    jar = tmp / 'mention.jar'
    command = ['java', '-Xmx384m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
        '-classpath', str(stdlib), '-d', str(jar),
        str(root/'app/src/main/java/t/mma/hooks/api/model/WeChatMessage.kt'),
        str(root/'app/src/main/java/t/mma/hooks/api/model/WeChatMessageTypes.kt'), str(main)]
    subprocess.run(command, check=True, timeout=60)
    subprocess.run(['java', '-cp', str(jar)+':'+str(stdlib), 'MentionTestKt'], check=True, timeout=15)
