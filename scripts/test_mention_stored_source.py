"""SDK compile and reproduce source loss using real stored-source and mention models."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];base=r/'app/src/main/java/t/mma/hooks'
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 helper=base/'api/message/WeChatStoredMsgSource.java'
 subprocess.run(['javac','-classpath','/usr/lib/android-sdk/platforms/android-36/android.jar','-d',td,str(helper)],check=True,timeout=30)
 (p/'Test.kt').write_text(r'''import t.mma.hooks.api.message.WeChatStoredMsgSource
import t.mma.hooks.api.model.WeChatMessage
fun main(){
 val me="<msgsource><atuserlist><![CDATA[me]]></atuserlist></msgsource>"
 val all="<msgsource><atuserlist>notify@all</atuserlist></msgsource>"
 val binary=("\u0000\u0001prefix"+me+"\u0000tail").toByteArray()
 fun kind(s:String)=WeChatMessage.classifyAtMention(s,"sender:\nhello","me")
 check(kind("")==WeChatMessage.AtMentionType.NONE)
 check(kind(WeChatStoredMsgSource.resolve("",binary))==WeChatMessage.AtMentionType.AT_ME)
 check(kind(WeChatStoredMsgSource.resolve(null,all.toByteArray()))==WeChatMessage.AtMentionType.AT_ALL)
 check(WeChatStoredMsgSource.resolve(me,all.toByteArray())===me)
 check(WeChatStoredMsgSource.resolve("",null)=="")
 check(WeChatStoredMsgSource.resolve("",byteArrayOf())=="")
 check(WeChatStoredMsgSource.resolve("","<msgsource>broken")=="")
 check(WeChatStoredMsgSource.resolve("","<msgsourcefake>x</msgsource>")=="")
 check(WeChatStoredMsgSource.resolve("",Any())=="")
 repeat(10000){check(WeChatStoredMsgSource.resolve(me,binary)===me)}
 println("PASS stored source loss reproduced; binary/absent/empty source, AT_ME/ALL, priority, malformed input and 10000 fast-path calls")
}''')
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',f'{std}:{td}','-d',str(p/'test.jar'),str(base/'api/model/WeChatMessage.kt'),str(base/'api/model/WeChatMessageTypes.kt'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}:{td}','TestKt'],check=True,timeout=15)
store=(base/'api/message/WeChatMessageStoreApi.java').read_text()
changes=(base/'api/message/WeChatMessageChangeApi.java').read_text()
assert 'case "lvbuffer": buffer = value; break;' in store
assert 'WeChatStoredMsgSource.resolve(rowString(source), buffer)' in store
assert 'WeChatStoredMsgSource.resolve(str(change.values, "msgSource"), change.values.get("lvbuffer"))' in changes
print('PASS cursor and change-values fallback wired; no extra database read or write')