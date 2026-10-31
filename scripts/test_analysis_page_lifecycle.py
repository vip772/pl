"""Production page owner + extracted AI lifecycle on JVM Handler stubs; no network/sends."""
from pathlib import Path
import subprocess, tempfile
r = Path(__file__).resolve().parents[1]
base = r/'app/src/main/java/t/mma/hooks/items/conversationanalysis'
s = (base/'ConversationAnalysisReportDialog.kt').read_text()
for anchor in ['generatedReportViews[reportDialog]?.get()', 'generatedReportViews.remove(dialog)?.clear()',
               'reportRequests.put(reportDialog, ai)?.close()', 'reportRequests.remove(dialog)?.close()',
               'pageTasks.remove(dialog)?.close()', 'configDialog.setOnDismissListener { ai.close() }',
               'loading.setOnDismissListener { tasks.close() }', 'if (!loading.isShowing) return@post',
               'epoch != activityEpoch', 'epoch != rankingEpoch']:
    assert anchor in s, anchor
send = s[s.index('    private fun captureAndSend('):s.index('    private fun loadDetails(')]
assert 'executor.execute' in send and 'tasks.execute' not in send
ai = (base/'ConversationAnalysisAi.kt').read_text()
life = ai[ai.index('    fun generate('):ai.index('    fun save(')]
assert 'postResult { onResult(result.first, result.second) }' in ai
handler = '''package android.os
import java.util.concurrent.ConcurrentLinkedQueue
class Looper { companion object { fun getMainLooper() = Looper() } }
class Handler(looper: Looper) {
 private val q = ConcurrentLinkedQueue<Runnable>()
 init { all.add(this) }
 fun post(r: Runnable): Boolean { q.add(r); return true }
 fun removeCallbacksAndMessages(token: Any?) { q.clear() }
 companion object {
  val all = java.util.concurrent.CopyOnWriteArrayList<Handler>()
  fun drain() { all.forEach { h -> while(true) { val r=h.q.poll() ?: break; r.run() } } }
  fun size() = all.sumOf { it.q.size }
 }
}
'''
code = '''package t.mma.hooks.items.conversationanalysis
import android.os.Handler
import android.os.Looper
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.net.HttpURLConnection
class ConversationAnalysisAi {
 private val executor = t.mma.hooks.core.IdleSerialExecutor.create("fixture-ai")
 @Volatile private var closed = false
 private val main = Handler(Looper.getMainLooper())
 private val requestToken = AtomicLong(0)
 @Volatile private var activeConnection: HttpURLConnection? = null
 val started = CountDownLatch(1)
 val finish = CountDownLatch(1)
 val returned = CountDownLatch(1)
 private fun requestStreaming(talker: String, days: Int, token: Long, update: (String)->Unit): String {
  started.countDown(); check(finish.await(3,TimeUnit.SECONDS)); returned.countDown(); return talker
 }
 fun idle() { executor.shutdown(); check(executor.awaitTermination(3,TimeUnit.SECONDS)) }
''' + life + '''
}
class Dialog
class ConversationAnalysisAiReportView
object Reports {
''' + '\n'.join(line for line in s.splitlines() if 'private val generatedReportViews =' in line or 'private val reportRequests =' in line) + '''
 fun replace(d:Dialog, ai:ConversationAnalysisAi, v:ConversationAnalysisAiReportView) {
  reportRequests.put(d,ai)?.close()
  generatedReportViews[d]=java.lang.ref.WeakReference(v)
 }
 fun close(d:Dialog) { reportRequests.remove(d)?.close(); generatedReportViews.remove(d)?.clear() }
 fun view(d:Dialog)=generatedReportViews[d]?.get()
 fun current(d:Dialog, ai:ConversationAnalysisAi)=reportRequests[d]===ai
}
fun main() {
 val p=ConversationAnalysisPageTasks();val start=CountDownLatch(1);val done=CountDownLatch(1);val release=CountDownLatch(1)
 val count=AtomicInteger();val interrupted=AtomicBoolean()
 p.execute { start.countDown();try { release.await() } catch(e:InterruptedException) { interrupted.set(true) };p.post { count.incrementAndGet() };done.countDown() }
 check(start.await(2,TimeUnit.SECONDS));p.execute { count.incrementAndGet() };p.post { count.incrementAndGet() }
 p.close();p.close();p.execute { count.incrementAndGet() };p.post { count.incrementAndGet() };release.countDown()
 check(done.await(2,TimeUnit.SECONDS));Handler.drain();check(count.get()==0&&!interrupted.get());check(Handler.size()==0)
 val next=ConversationAnalysisPageTasks();val ready=CountDownLatch(1)
 next.execute { next.post { count.incrementAndGet() };ready.countDown() };check(ready.await(2,TimeUnit.SECONDS));Handler.drain();check(count.get()==1);next.close()
 val a=Dialog();val b=Dialog();val old=ConversationAnalysisAi();val other=ConversationAnalysisAi();val fresh=ConversationAnalysisAi()
 val va=ConversationAnalysisAiReportView();val vb=ConversationAnalysisAiReportView();val vf=ConversationAnalysisAiReportView()
 Reports.replace(a,old,va);Reports.replace(b,other,vb)
 old.generate("old",1) { error("old completion escaped replacement") };check(old.started.await(2,TimeUnit.SECONDS))
 Reports.replace(a,fresh,vf);old.finish.countDown();old.idle();Handler.drain()
 check(Reports.view(a)===vf&&Reports.view(b)===vb&&!Reports.current(a,old))
 Reports.close(a);check(Reports.view(a)==null&&Reports.view(b)===vb&&Reports.current(b,other))
 Reports.close(b);fresh.idle();other.idle()
 val canceled=ConversationAnalysisAi();canceled.generate("cancel",1) { error("cancel callback escaped") }
 check(canceled.started.await(2,TimeUnit.SECONDS));canceled.cancel();canceled.finish.countDown();canceled.idle();Handler.drain();canceled.close()
 val live=ConversationAnalysisAi();var result="";live.generate("live",1) { result=it };check(live.started.await(2,TimeUnit.SECONDS));live.finish.countDown();live.idle();Handler.drain();check(result=="live");live.close()
 println("PASS production page close/queue removal/non-interruption/reopen; extracted AI replace/cancel/close; dialog isolation")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Handler.kt').write_text(handler);(p/'Test.kt').write_text(code)
 sources=[p/'Handler.kt',p/'Test.kt',base/'ConversationAnalysisPageTasks.kt',r/'app/src/main/java/t/mma/hooks/core/IdleSerialExecutor.kt']
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,sources)],check=True,timeout=90)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.conversationanalysis.TestKt'],check=True,timeout=20)