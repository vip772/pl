"""Real calendar/repository code on JVM, fake message DB; no network or Android build."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
b=r/'app/src/main/java/t/mma/hooks/items/conversationanalysis'
report=(b/'ConversationAnalysisReportDialog.kt').read_text()
ai=(b/'ConversationAnalysisAi.kt').read_text()
assert 'talker, selection.days, logger, reportHost, screenshot' in report
assert 'selection.select(pair.second)' in report
assert 'val reloadRanking: (Int) -> Unit = { selection.select(it) }' in report
assert 'selection.observe { reloadDeep(it) }' in report
assert 'renderDeepData(activity, bodies, details, reloadRanking, periodDays)' in report
assert 'epoch != rankingEpoch' in report
assert 'reportRequests.remove(dialog)?.close()' in report
assert 'generatedReportViews.remove(dialog)?.clear()' in report
assert 'ConversationAnalysisPeriod.recentDays(nextDays)' in report
for name in ['高频语义特征','情绪指纹探测','废话程度鉴定','多维属性探测','全天活跃频次']:
    assert f'bodies["{name}"]' in report
assert 'private fun aiPeriodRange(days: Int): LongRange = ConversationAnalysisPeriod.range(days)' in ai
assert ai.index('val period = aiPeriodRange(days)') < ai.index('val sampledRows =') < ai.index('buildPrompt(records))')
assert ai.index('require(records.isNotEmpty())') < ai.index('val conn = URL(join(cfg.address, cfg.path))',ai.index('private fun requestStreaming'))
# Compile the real AI read/filter/sample prefix; stop before HTTP/JSON construction.
prefix=ai[ai.index('        val db = WeChatApis.database()',ai.index('private fun requestStreaming')):ai.index('        val body = JSONObject()',ai.index('private fun requestStreaming'))]
normalize=ai[ai.index('    private fun normalizeRecord('):ai.index('    private fun buildPrompt(')]
ai_probe='package t.mma.hooks.items.conversationanalysis\nimport t.mma.hooks.api.core.WeChatApis\nclass Config { fun sampleLimit()=1;fun contextWindow()="128k" }\nclass AiReadProbe {\n var closed=false; val requestToken=java.util.concurrent.atomic.AtomicLong(1); val token=1L; fun postResult(f:()->Unit)=f();fun onUpdate(s:String)=Unit\n val configStore=Config(); var sampleCount=0\n fun autoMessageLimit(s:String)=3000\n fun aiPeriodRange(days:Int)=ConversationAnalysisPeriod.range(days)\n fun read(talker:String,days:Int):List<String> {\n' + prefix + "\nreturn records\n}\n" + normalize + "\n}"
details=(b/'ConversationAnalysisDetails.kt').read_text()
details='package t.mma.hooks.items.conversationanalysis\nimport t.mma.hooks.api.core.WeChatApis\nimport java.util.Calendar\nimport java.util.Locale\n'+details[details.index('internal data class ConversationRankingRow'):details.index('internal object ConversationAnalysisDetailsDialog')]
api='''package t.mma.hooks.api.core
object WeChatApis {
 val db = t.mma.hooks.api.runtime.WeChatDatabaseApi()
 fun database() = db
 fun account() = Account()
 fun contacts() = Contacts()
}
class Account { fun selfWxId() = "self" }
class Contacts {
 fun getGroupMemberIds(t:String) = listOf("a","b")
 fun getGroupMemberDisplayName(t:String,id:String) = id
}
'''
db='''package t.mma.hooks.api.runtime
import java.util.Calendar
class WeChatDatabaseApi {
 data class Message(val time:Long,val content:String,val sender:String="a")
 var messages = listOf<Message>()
 val calls = mutableListOf<Pair<String,List<String>>>()
 var lastCursor: MessageCursor? = null
 var failQuery=false
 var cancelOnRead: (() -> Unit)? = null
 fun rawQuery(sql:String,args:Array<String>):MessageCursor? {
  if(failQuery) return null
  return MessageCursor(query(sql,args)) { cancelOnRead?.invoke() }.also {lastCursor=it}
 }
 class MessageCursor(val data:List<Map<String,Any?>>,val onRead:()->Unit):java.io.Closeable {
  var closed=false;var reads=0;var position=0
  val count get()=data.size
  fun getColumnIndexOrThrow(s:String)=if(s=="content") 0 else 1
  fun moveToPosition(p:Int):Boolean {position=p;return p in data.indices}
  fun getString(c:Int):String {reads++;onRead();return data[position]["content"].toString()}
  fun getInt(c:Int)=0
  override fun close() {closed=true}
 }
 fun prepare() = true
 fun query(sql:String,args:Array<String>):List<Map<String,Any?>> {
  calls.add(sql to args.toList())
  if (!sql.contains("createTime >=")) return listOf(mapOf("total" to messages.size))
  val start=args[args.size-2].toLong();val end=args.last().toLong()
  val rows=messages.filter { it.time in start..end }
  if (sql.contains("GROUP BY sender")) return rows.groupBy { it.sender }.map { mapOf("sender" to it.key,"cnt" to it.value.size) }
  if (sql.contains("SELECT DISTINCT")) return rows.map { mapOf("sender" to it.sender) }.distinct()
  if (sql.contains("SELECT content,isSend,type")) return rows.sortedBy{it.time}.map { mapOf("content" to it.content,"isSend" to 0,"type" to 1) }
  if (sql.contains("SELECT content, type")) return rows.map { mapOf("content" to it.content,"type" to 1,"createTime" to it.time) }
  return listOf(mapOf("total" to rows.size,"speakers" to rows.map{it.sender}.distinct().size,"textCount" to rows.size,"questionCount" to rows.count{it.content.contains('?')},"laughCount" to rows.count{it.content.contains("哈哈")}))
 }
}
'''
test='''package t.mma.hooks.items.conversationanalysis
import java.util.*
import java.time.*
import t.mma.hooks.api.core.WeChatApis
fun time(s:String):Long = LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun expect(code:Int,now:String,start:String,end:String) {
 val actual=ConversationAnalysisPeriod.range(code,time(now))
 check(actual.first==time(start)) { "$code start $actual expected $start" }
 check(actual.last==time(end)) { "$code end $actual expected $end" }
}
fun main() {
 TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
 val now="2026-11-05T14:30:00"
 expect(1,now,"2026-11-05T00:00:00",now)
 expect(-1,now,"2026-11-04T00:00:00","2026-11-04T23:59:59.999")
 expect(7,now,"2026-11-02T00:00:00",now)
 expect(-7,now,"2026-10-26T00:00:00","2026-11-01T23:59:59.999")
 expect(30,now,"2026-11-01T00:00:00",now)
 expect(-30,now,"2026-10-01T00:00:00","2026-10-31T23:59:59.999")
 expect(-30,"2024-03-31T12:00:00","2024-02-01T00:00:00","2024-02-29T23:59:59.999")
 expect(-7,"2026-01-01T00:00:00","2025-12-22T00:00:00","2025-12-28T23:59:59.999")
 expect(7,"2026-11-01T12:00:00","2026-10-26T00:00:00","2026-11-01T12:00:00")
 expect(-1,"2026-01-01T00:00:00","2025-12-31T00:00:00","2025-12-31T23:59:59.999")
 check(ConversationAnalysisPeriod.range(365,time(now))==0L..time(now))
 check(ConversationAnalysisPeriod.recentDays(60,time(now)).first==time("2026-09-07T00:00:00"))
 TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
 expect(-1,"2026-03-09T12:00:00","2026-03-08T00:00:00","2026-03-08T23:59:59.999")
 expect(-1,"2026-11-02T12:00:00","2026-11-01T00:00:00","2026-11-01T23:59:59.999")
 TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
 val selected=ConversationAnalysisPeriodSelection();val changes=mutableListOf<Int>()
 selected.observe { changes.add(it) };selected.select(-1);selected.select(-1);selected.select(-30);selected.select(1)
 check(changes==listOf(-1,-30,1)&&selected.days==1)
 // Actual repositories must pass identical SQL boundaries and exclude sentinel rows outside them.
 val db=WeChatApis.db
 for (code in listOf(1,-1,7,-7,30,-30,365)) {
  val bounds=ConversationAnalysisPeriod.range(code)
  db.messages=listOf(
   t.mma.hooks.api.runtime.WeChatDatabaseApi.Message(bounds.first-1,"OUTSIDE BEFORE"),
   t.mma.hooks.api.runtime.WeChatDatabaseApi.Message(bounds.first,"a:\\n哈哈?"),
   t.mma.hooks.api.runtime.WeChatDatabaseApi.Message(bounds.last-1000,"b:\\ninside","b"),
   t.mma.hooks.api.runtime.WeChatDatabaseApi.Message(bounds.last+100000,"OUTSIDE AFTER"))
  db.calls.clear()
  val summary=ConversationAnalysisRepository().loadSummary("test@chatroom",code)
  val deep=ConversationAnalysisDetailsRepository().load("test@chatroom",code)
  check(summary.periodTotal==2&&summary.periodSpeakers==2)
  check(deep.deep.hourly.sum()==2&&deep.deep.questionCount==1&&deep.deep.laughCount==1)
  check(deep.ranking.sumOf{it.count}==2)
  check(db.calls.filter{it.first.contains("createTime >=")}.all { (_,a)->
    a[a.size-2].toLong()==bounds.first && kotlin.math.abs(a.last().toLong()-bounds.last)<2000
  })
  val probe=AiReadProbe()
  val records=probe.read("test@chatroom",code)
  check(records.size==1&&!records.single().contains("OUTSIDE")&&probe.sampleCount==1)
   check(db.lastCursor!!.closed && db.lastCursor!!.reads==1)
   db.failQuery=true
   check(runCatching {probe.read("g",code)}.exceptionOrNull()?.message=="消息查询失败，请检查数据库状态")
   db.failQuery=false
   db.cancelOnRead={probe.closed=true}
   check(runCatching {probe.read("g",code)}.exceptionOrNull()?.message=="生成已取消")
   check(db.lastCursor!!.closed)
   db.cancelOnRead=null;probe.closed=false
  db.messages=emptyList()
  check(runCatching { probe.read("test@chatroom",code) }.exceptionOrNull()?.message=="所选时段暂无文本消息")
  check(ConversationAnalysisDetailsRepository().load("test@chatroom",code).deep.hourly.sum()==0)
 }
 println("PASS calendar day/week/month/all, year/leap/DST edges, 60-day slider; shared selection; real summary/five-chart queries and production AI read/sample/empty guard")
}
'''
libs=Path('/tmp/pl-kotlin-libs'); std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,content in [('Api.kt',api),('Db.kt',db),('Details.kt',details),('AiProbe.kt',ai_probe),('Test.kt',test)]: (p/name).write_text(content)
 sources=[*p.glob('*.kt'),b/'ConversationAnalysisPeriod.kt',b/'ConversationAnalysisRepository.kt',b/'ConversationAnalysisInput.kt']
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,sources)],check=True,timeout=90)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.conversationanalysis.TestKt'],check=True,timeout=20)
print('PASS UI selection to generation and all deep-chart refresh routing; AI filters before sampling and refuses empty records before HTTP')
