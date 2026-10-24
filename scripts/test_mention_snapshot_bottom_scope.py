from pathlib import Path
import subprocess,tempfile
import os
r=Path(__file__).resolve().parents[1];lib=Path(os.environ.get('KOTLIN_LIB_DIR','/tmp/pl-kotlin-libs'));std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
s=(r/'app/src/main/java/t/mma/hooks/items/hchatextra/HchatExtraFeature.kt').read_text();a=s.index('    private fun atMentionType(');e=s.index('    private fun parseAtUserIds(',a);method=s[a:e].replace('private fun','fun',1)
a=s.rindex('private data class MessageDetails(');data=s[a:].replace('private data class','data class',1)
main='''import t.mma.hooks.api.model.WeChatMessage
object WeChatApis {var self="me";fun account()=this;fun selfWxId()=self}
'''+data+'''\nclass Check {\n'''+method+'''}
fun main(){
 val check=Check()
 val d=MessageDetails(1,1,1,"g@chatroom","sender","","","<msgsource><atuserlist>me</atuserlist></msgsource>","me","",1,false)
 check(check.atMentionType(d)==WeChatMessage.AtMentionType.AT_ME)
 val cached=d.mentionClassification
 repeat(100){check(check.atMentionType(d)==WeChatMessage.AtMentionType.AT_ME);check(d.mentionClassification===cached)}
 WeChatApis.self="other";check(check.atMentionType(d)==WeChatMessage.AtMentionType.OTHERS)
 check(d.mentionClassification?.first=="other")
 val fresh=d.copy(msgSource="<msgsource><atuserlist>notify@all</atuserlist></msgsource>")
 check(fresh.mentionClassification==null);check(check.atMentionType(fresh)==WeChatMessage.AtMentionType.AT_ALL)
 println("PASS production snapshot cache: repeated reads, account switch invalidation, fresh message isolation")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Check.kt').write_text(main);jar=p/'check.jar'
 cmd=['java','-Xmx384m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),str(p/'Check.kt'),str(r/'app/src/main/java/t/mma/hooks/api/model/WeChatMessage.kt'),str(r/'app/src/main/java/t/mma/hooks/api/model/WeChatMessageTypes.kt')]
 subprocess.run(cmd,check=True,timeout=60);subprocess.run(['java','-cp',f'{jar}:{std}','CheckKt'],check=True,timeout=15)
# Guarded updates and actual chat gate must be present together.
bottom=(r/'app/src/main/java/t/mma/hooks/items/custombottombar/CustomBottomBarFeature.kt').read_text()
assert 'if (!applied.get() || !shouldShowOverlay()) return' in bottom
assert 'if (overlayActive.value) CustomBottomBarRoot(' in bottom
assert 'WeChatApis.chatPage()?.isInChatPage() != true' in bottom
print('PASS source checks: chat-page gate, hidden sync guard, conditional composition')