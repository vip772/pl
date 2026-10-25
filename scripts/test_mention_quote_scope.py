"""Execute production quote scope with actual message model, without Gradle."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
feature=(r/'app/src/main/java/t/mma/hooks/items/mentionhighlight/MentionHighlightFeature.kt').read_text()
state=feature[feature.index('    private class BindState('):feature.index('    private data class Gesture(')].replace('private class BindState', 'class BindState').replace('MentionHighlightScope', 'Scope')
assert '!state.accepts(input)' in feature
code='''import t.mma.hooks.api.model.*
import t.mma.hooks.items.mentionhighlight.MentionHighlightScope as Scope
fun msg(type:Int,body:String,source:String="")=WeChatMessage(1,0,type,0,0,0,"room@chatroom",body,"","","",0,source,"")
'''+state+'''
fun main(){
 val own="<msgsource><atuserlist>new</atuserlist></msgsource>"
 val quote="<refermsg><type>1</type><content>old @old</content><msgsource><atuserlist>old</atuserlist></msgsource></refermsg>"
 for(type in listOf(49,0x31000031)){
 val m=msg(type,"sender:\\n<msg><appmsg><title><![CDATA[@new 对的]]></title><type>57</type>$quote</appmsg></msg>",own)
 val frame=BindState(m);val before=Scope.preparations
 repeat(3000){check(frame.accepts("@new 对的"));check(!frame.accepts("old @old"))}
 check(Scope.preparations==before+1)
 val nested=BindState(msg(1,"other"));check(nested.accepts("other"));check(frame.accepts("@new 对的"))
 check(Scope.preparations==before+2)
 check(!BindState(null).accepts("@new 对的"))
 val fresh=BindState(m);check(fresh.accepts("@new 对的"));check(Scope.preparations==before+3)
 check(Scope.supports(m));check(Scope.accepts(m,"@new 对的"));check(!Scope.accepts(m,"old @old"));check(!Scope.accepts(m,"@new"))
 check(m.getEffectiveAtUserList()==listOf("new"))
 val noOwn=msg(type,m.content);check(noOwn.getEffectiveAtUserList().isEmpty())
 val link=msg(type,"<appmsg><title>@new</title><type>5</type>$quote</appmsg>",own)
 check(!Scope.supports(link))
 val missing=msg(type,"<appmsg><type>57</type>$quote</appmsg>",own);check(!Scope.supports(missing))
 val same=msg(type,"<appmsg><title>@old</title><type>57</type><refermsg><content>@old</content></refermsg></appmsg>",own)
 check(!Scope.accepts(same,"@old"))
 val escaped=msg(type,"<appmsg><title>@new &amp; &#x1F600;</title><type>57</type>$quote</appmsg>",own)
 check(Scope.accepts(escaped,"@new & 😀"))
 }
 check(Scope.accepts(msg(1,"@new"),"@new"))
 check(!Scope.supports(msg(3,"@new")))
 println("PASS: normal text, quote types49/high-bit49, title/CDATА/entities/emoji, quote-source isolation, previews/partial/equal ambiguity/link/missing-title rejection")
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'Main.kt').write_text(code);jar=p/'x.jar'
 files=[r/'app/src/main/java/t/mma/hooks/api/model'/n for n in ['WeChatMessage.kt','WeChatMessageTypes.kt','WeChatParsedMessage.kt']]
 scope=(r/'app/src/main/java/t/mma/hooks/items/mentionhighlight/MentionHighlightScope.kt').read_text().replace('internal object MentionHighlightScope {','internal object MentionHighlightScope {\n var preparations=0').replace('fun prepare(message: WeChatMessage): Prepared {','fun prepare(message: WeChatMessage): Prepared {\n preparations++')
 (p/'Scope.kt').write_text(scope);files.append(p/'Scope.kt')
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),str(p/'Main.kt'),*map(str,files)],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{std}','MainKt'],check=True,timeout=15)
