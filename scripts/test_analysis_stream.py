"""Production SSE framing and nickname normalizer on JVM; no external requests."""
from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[1]; b=r/'app/src/main/java/t/mma/hooks/items/conversationanalysis'
ai=(b/'ConversationAnalysisAi.kt').read_text()
assert '.put("stream", true)' in ai
assert 'text/event-stream, application/json' in ai
assert 'if (isJson)' in ai and 'json.optJSONArray("choices")' in ai
assert 'postResult { if (token == requestToken.get()) onUpdate(partial) }' in ai
assert 'val names = mutableMapOf<String, String>()' in ai
normal=ai[ai.index('    private fun normalizeRecord('):ai.index('    private fun buildPrompt(')].replace('private fun normalizeRecord','fun normalizeRecord')
api='''package t.mma.hooks.api.core
object WeChatApis { val api=Contacts();fun contacts()=api }
class Contacts { var calls=0;fun getGroupMemberDisplayName(t:String,id:String):String {calls++;return if(id=="unknown") id else "昵称"} }
'''
test=r'''package t.mma.hooks.items.conversationanalysis
import java.io.*
import org.json.JSONObject
import java.util.concurrent.*
import t.mma.hooks.api.core.WeChatApis
class Normalizer {
NORMALIZER
}
fun main() {
 check(ConversationAnalysisStream.bodyText(null)=="")
 check(ConversationAnalysisStream.bodyText(object {override fun toString()="null"})=="")
 for(value in listOf(1,true,listOf("text"),mapOf("text" to "bad"))) check(ConversationAnalysisStream.bodyText(value)=="")
 check(ConversationAnalysisStream.bodyText("null")=="null")
 check(ConversationAnalysisStream.bodyText("正文")=="正文")
 val absent=JSONObject("""{"content":null}""").opt("content")
 check(ConversationAnalysisStream.bodyText(absent)=="")
 check(ConversationAnalysisStream.bodyText(JSONObject("{}").opt("content"))=="")
 check(ConversationAnalysisStream.bodyText(JSONObject("""{"content":42}""").opt("content"))=="")
 check(ConversationAnalysisStream.bodyText(JSONObject("""{"content":"正文含null"}""").opt("content"))=="正文含null")
 fun decode(s:String)=ConversationAnalysisStream.Piece(if(s=="finish") "" else s,s=="finish")
 fun read(s:String):String=ConversationAnalysisStream.read(s.reader().buffered(), {}, ::decode) {}
 check(read(":heartbeat\r\ndata: 你好\r\n\r\ndata: 世界\r\n\r\ndata: [DONE]\r\n\r\n")=="你好世界")
 check(read("data: 第一行\ndata: 第二行\n\ndata: finish")=="第一行\n第二行")
 check(read("data: [DONE]\n\n")=="")
 check(runCatching{read("data: 半篇\n\n")}.exceptionOrNull()?.message?.contains("中断")==true)
 check(runCatching{ConversationAnalysisStream.read("data: bad\n\n".reader().buffered(), {}, {error("格式错误")}){}}.isFailure)
 val updates=mutableListOf<String>();var clock=0L
 val events=(1..1000).joinToString(""){"data: x\n\n"}+"data: [DONE]\n\n"
 val result=ConversationAnalysisStream.read(events.reader().buffered(),{},::decode,{clock+=1_000_000L;clock}){updates.add(it)}
 check(result.length==1000&&updates.size==4&&updates.first()=="x")
 // The first partial must arrive while the server stream remains open.
 val pipe=PipedWriter();val input=PipedReader(pipe).buffered();val partial=CountDownLatch(1)
 val done=CountDownLatch(1);var text="";var failure:Throwable?=null
 val thread=Thread {try{text=ConversationAnalysisStream.read(input,{},::decode){partial.countDown()}}catch(e:Throwable){failure=e}finally{done.countDown()}}
 thread.start();pipe.write("data: 先到正文\n\n");pipe.flush()
 check(partial.await(2,TimeUnit.SECONDS));check(done.count==1L)
 pipe.write("data: 后到正文\n\ndata: [DONE]\n\n");pipe.flush()
 check(done.await(2,TimeUnit.SECONDS));check(failure==null&&text=="先到正文后到正文");pipe.close();input.close()
 // A finish_reason is sufficient; the provider may keep HTTP open without [DONE].
 val finishPipe=PipedWriter();val finishInput=PipedReader(finishPipe).buffered()
 val finishDone=CountDownLatch(1);var finishText="";var finishError:Throwable?=null
 val finishThread=Thread {try{finishText=ConversationAnalysisStream.read(finishInput,{},::decode){}}catch(e:Throwable){finishError=e}finally{finishDone.countDown()}}
 finishThread.start();finishPipe.write("data: 完成正文\n\ndata: finish\n\n");finishPipe.flush()
 val finishedBeforeEof=finishDone.await(2,TimeUnit.SECONDS)
 finishPipe.close();finishThread.join(2000);finishInput.close()
 check(finishedBeforeEof && finishError==null && finishText=="完成正文")
 var active=true
 check(runCatching {ConversationAnalysisStream.read("data: a\n\ndata: b\n\n".reader().buffered(),{check(active){"取消"}},::decode){active=false}}.exceptionOrNull()?.message=="取消")
 val normal=Normalizer();val names=mutableMapOf<String,String>()
 repeat(3000){check(normal.normalizeRecord("g", "sender:\n哈哈",false,names)=="[昵称]: 哈哈")}
 check(WeChatApis.api.calls==1)
 repeat(10){normal.normalizeRecord("g","unknown:\n内容",false,names)}
 check(WeChatApis.api.calls==2)
 normal.normalizeRecord("g","sender:\n自己",true,names);check(WeChatApis.api.calls==2)
 normal.normalizeRecord("g","sender:\n新请求",false,mutableMapOf());check(WeChatApis.api.calls==3)
 println("PASS real SSE first partial before EOF; CRLF/multiline/heartbeat/DONE/finish/interrupt/cancel; 1000 chunks -> 4 snapshots; 3000 nickname lookups -> 1 per request")
}
'''.replace('NORMALIZER',normal)
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(test);(p/'Api.kt').write_text(api)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',f'{std}:/tmp/backup-json.jar','-d',str(p/'test.jar'),str(b/'ConversationAnalysisStream.kt'),str(p/'Test.kt'),str(p/'Api.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}:/tmp/backup-json.jar','t.mma.hooks.items.conversationanalysis.TestKt'],check=True,timeout=15)
