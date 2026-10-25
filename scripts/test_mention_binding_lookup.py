"""Compile extracted production binding conversion with actual message model; no Gradle."""
from pathlib import Path
import tempfile, subprocess
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/mentionhighlight/MentionHighlightFeature.kt').read_text()
metadata=s[s.index('    private class BindingMembers'):s.index('    private val reentry')]
methods=s[s.index('    private fun messageFromNative'):s.index('    private fun locateBindMethod')]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
files={'Main.kt':'''import java.lang.reflect.Field
import java.lang.reflect.Method
import t.mma.hooks.api.model.*
import t.mma.hooks.items.mentionhighlight.MentionHighlightScope
import t.mma.hooks.api.core.WeChatApis
import t.mma.utils.KavaReflector
class Check {
val TAG="test"
fun debugLog(m:()->String){}
'''+metadata+methods+'''
fun convert(v:Any)=messageFromNative(v)
fun size()=bindingMembers.size
fun touch(c:Class<*>)=bindingMethod(c,"missing")
}
open class Native {
 @JvmField var field_msgId=1L
 @JvmField var field_type=1
 @JvmField var field_talker="room@chatroom"
 @JvmField var field_content="hello"
 @JvmField var field_msgSource=""
 @JvmField var field_reserved=""
}
class Child:Native()
class Unknown{@JvmField var field_msgId=2L}
class Getter:Native(){var kind=1;fun getType()=kind;fun getContent()="getter"}
class Broken:Native(){fun getType():Int=error("unavailable")}

fun main(){
 val c=Check();val n=Child()
 fun stored()=WeChatMessage(1,0,1,0,0,0,"room@chatroom","db text","","<reserved/>","",0,"<msgsource><atuserlist>alice</atuserlist></msgsource>","")
 WeChatApis.store.message=stored()
 val a=c.convert(n)!!;check(a.getEffectiveAtUserList()==listOf("alice"));check(a.reserved=="<reserved/>")
 val warm=KavaReflector.lookups
 repeat(3000){n.field_content="new$it";check(c.convert(n)!!.content==n.field_content)}
 check(KavaReflector.lookups==warm)
 val q=WeChatApis.store.queries
 n.field_type=3;check(c.convert(n)==null);check(WeChatApis.store.queries==q)
 n.field_type=1;n.field_talker="friend";check(c.convert(n)==null);check(WeChatApis.store.queries==q)
 n.field_talker="";check(c.convert(n)!!.talker=="room@chatroom");check(WeChatApis.store.queries==q+1)
 n.field_msgId=0;check(c.convert(n)==null);check(WeChatApis.store.queries==q+1)
 check(c.convert(Unknown())!!.content=="db text")
 n.field_msgId=1;n.field_talker="room@chatroom";n.field_type=0x31000031
 n.field_content="<appmsg><title><![CDATA[@alice 对的]]></title><type>57</type><refermsg><content>old</content></refermsg></appmsg>"
 val reply=c.convert(n)!!;check(MentionHighlightScope.accepts(reply,"@alice 对的"))
 check(reply.getEffectiveAtUserList()==listOf("alice"))
 val beforeLink=WeChatApis.store.queries
 n.field_content="<appmsg><title>@alice link</title><type>5</type></appmsg>"
 check(c.convert(n)==null);check(WeChatApis.store.queries==beforeLink)
 n.field_content="";c.convert(n);check(WeChatApis.store.queries==beforeLink+1)
 n.field_type=1;n.field_content="hello"
 WeChatApis.store.message=null;n.field_msgId=1;n.field_talker="room@chatroom"
 check(c.convert(n)!!.content==n.field_content)
 val getter=Getter();check(c.convert(getter)!!.content=="getter")
 getter.kind=3;check(c.convert(getter)==null)
 check(c.convert(Broken())!=null)
 val bytes=Native::class.java.getResourceAsStream("Native.class")!!.readBytes()
 fun isolated():Class<*> = object:ClassLoader(null){fun make()=defineClass("Native",bytes,0,bytes.size)}.make()
 val one=isolated();val two=isolated();check(one!==two)
 check(c.convert(one.getConstructor().newInstance())!=null)
 check(c.convert(two.getConstructor().newInstance())!=null)
 repeat(140){c.touch(isolated())};check(c.size()==128)
 WeChatApis.store.fail=true;check(c.convert(n)!=null)
 println("PASS: 3000 warm conversions no lookup; mutable/inherited fields, blank metadata DB enrichment, ineligible skip, unknown fallback, unavailable/failing store")
}
''','Api.kt':'''package t.mma.hooks.api.core
import t.mma.hooks.api.model.WeChatMessage
object WeChatApis {
 val store=Store()
 fun messageStore()=store
 fun chatPage()=Page()
 class Page{fun currentTalker()="fallback@chatroom"}
 class Store{var queries=0;var message:WeChatMessage?=null;var fail=false
 fun getMessageById(id:Long):WeChatMessage?{queries++;if(fail)error("db");return message}}
}
''','Reflect.kt':'''package t.mma.utils
import java.lang.reflect.*
object KavaReflector {
 var lookups=0
 fun findMethod(c:Class<*>,n:String):Method?{lookups++;return c.declaredMethods.firstOrNull{it.name==n&&it.parameterCount==0}?:c.methods.firstOrNull{it.name==n&&it.parameterCount==0}}
 fun findDeclaredField(c:Class<*>,n:String):Field?{lookups++;return c.declaredFields.firstOrNull{it.name==n}}
 fun invoke(m:Method?,v:Any):Any?=runCatching{m?.isAccessible=true;m?.invoke(v)}.getOrNull()
 fun readField(f:Field,v:Any):Any?=runCatching{f.isAccessible=true;f.get(v)}.getOrNull()
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,v in files.items():(p/n).write_text(v)
 model=[r/'app/src/main/java/t/mma/hooks/api/model'/n for n in ['WeChatMessage.kt','WeChatMessageTypes.kt','WeChatParsedMessage.kt']]
 jar=p/'check.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),*[str(p/n) for n in files],*map(str,model),str(r/"app/src/main/java/t/mma/hooks/items/mentionhighlight/MentionHighlightScope.kt")],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{std}','MainKt'],check=True,timeout=15)
