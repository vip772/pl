"""Compare text-only scope against the pre-change model/scope without Gradle."""
from pathlib import Path
import subprocess
import tempfile

r = Path(__file__).resolve().parents[1]
lib = Path('/tmp/pl-kotlin-libs')
std = next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
model_path = 'app/src/main/java/t/mma/hooks/api/model/WeChatMessage.kt'
scope_path = 'app/src/main/java/t/mma/hooks/items/mentionhighlight/MentionHighlightScope.kt'
def old(path):
    return subprocess.check_output(['git', 'show', '75a572c:' + path], cwd=r, text=True)

code = '''import t.mma.hooks.api.model.*
import legacy.LegacyMessage
import t.mma.hooks.items.mentionhighlight.MentionHighlightScope as Scope
import t.mma.hooks.items.mentionhighlight.LegacyScope
fun main() {
 val titles=listOf("@new", "", " ", "<![CDATA[ @new &amp; &#128512; ]]>", "@new &amp;amp;", "@new &#x1F600;", "@new &#99999999;", "@new:\\nextra")
 val refs=listOf("", "<refermsg/>", "<refermsg></refermsg>", "<refermsg><content>@old</content></refermsg>", "<refermsg><content>@new suffix</content></refermsg>", "<refermsg><content><![CDATA[@new]]></content></refermsg>", "<REFERMSG a='1'><content> &amp;amp; &#128512; </content></REFERMSG>", "<refermsg><title>@old</title><type>57</type><refermsg><content>@new</content></refermsg></refermsg>", "<refermsg><content>@new", "<content>@new</content>")
 var cases=0
 for(type in listOf(1,3,49,0x31000031)) for(talker in listOf("room@chatroom","friend"))
 for(prefix in listOf("","sender:\\n")) for(title in titles) for(ref in refs)
 for(sub in listOf("57","5",""," 57 ")) for(fallback in listOf("","<refermsgcontent>@new</refermsgcontent>")) {
  val body=prefix+"<appmsg><title>$title</title><type>$sub</type>$ref$fallback</appmsg>"
  val m=WeChatMessage(1,0,type,0,0,0,talker,body,"","","",0)
  val old=LegacyMessage(1,0,type,0,0,0,talker,body,"","","",0)
  val full=old.getQuoteMsg()
  check(m.quoteTitle()==full?.title) { "title case $cases" }
  check(m.quoteContent()==full?.content) { "content case $cases" }
  val prepared=Scope.prepare(m)
  for(text in listOf("@new","@old",full?.title.orEmpty(),"@new & 😀","@new:\\nextra")) {
   check(prepared.accepts(text)==LegacyScope.prepare(m).accepts(text)) { "scope case $cases text=$text" }
  }
  cases++
 }
 val sample=WeChatMessage(1,0,49,0,0,0,"room@chatroom","<appmsg><title>@new</title><type>57</type><refermsg><content>@old</content></refermsg></appmsg>","","","",0)
 WeChatMessage.fullQuoteReads=0
 val prepared=Scope.prepare(sample)
 repeat(3000) { check(prepared.accepts("@new"));check(!prepared.accepts("@old")) }
 check(WeChatMessage.fullQuoteReads==0)
 check(LegacyScope.prepare(sample).accepts("@new"))
 check(WeChatMessage.fullQuoteReads==2)
 println("PASS $cases legacy/new model and scope cases; 3000 matches use zero full quote reads, legacy uses two")
}
'''
with tempfile.TemporaryDirectory() as d:
    p=Path(d)
    model=(r/model_path).read_text().replace('fun getQuoteMsg(): WeChatQuoteMsg? {', 'fun getQuoteMsg(): WeChatQuoteMsg? {\n fullQuoteReads++').replace('companion object {', 'companion object {\n var fullQuoteReads=0')
    (p/'Model.kt').write_text(model)
    (p/'Legacy.kt').write_text(old(model_path).replace('package t.mma.hooks.api.model', 'package legacy\nimport t.mma.hooks.api.model.*').replace('WeChatMessage', 'LegacyMessage').replace('LegacyMessageTypes', 'WeChatMessageTypes'))
    (p/'LegacyScope.kt').write_text(old(scope_path).replace('MentionHighlightScope', 'LegacyScope'))
    (p/'Main.kt').write_text(code)
    files=[p/'Model.kt',p/'Legacy.kt',p/'LegacyScope.kt',p/'Main.kt',r/scope_path]
    files += [r/'app/src/main/java/t/mma/hooks/api/model'/n for n in ['WeChatMessageTypes.kt','WeChatParsedMessage.kt']]
    jar=p/'test.jar'
    subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),*map(str,files)],check=True,timeout=60)
    subprocess.run(['java','-Xmx256m','-cp',f'{jar}:{std}','MainKt'],check=True,timeout=60)
