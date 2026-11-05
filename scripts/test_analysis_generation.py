"""Production sampling/normalization and extracted renderer on JVM stubs; no API or APK."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'app/src/main/java/t/mma/hooks/items/conversationanalysis'
view = (BASE / 'ConversationAnalysisAiReportView.kt').read_text()
ai = (BASE / 'ConversationAnalysisAi.kt').read_text()
assert 'db.query(' not in ai[ai.index('    private fun requestStreaming('):]
assert 'cursor.use' in ai and 'ConversationAnalysisInput.positions(it.count, limit)' in ai
assert 'ConversationAnalysisText.normalize(response)' in ai
assert 'onUpdate(ConversationAnalysisText.normalize(partial))' in ai
assert 'screenshotLayout(currentText' in view
assert 'body.removeAllViews()' not in view

# Execute the actual initialization, update and styling code with observable View stubs.
init = view[view.index('    private val body ='):view.index('fun updateMeta(')]
methods = view[view.index('    fun update(talker:'):]
probe = '''package t.mma.hooks.items.conversationanalysis
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.widget.*
import android.text.*
import android.text.style.*
class RenderProbe(context: Context):LinearLayout(context) {
 private var currentText=""
''' + init + methods
sources = {
'Context.kt': '''package android.content
class Context {val resources=Resources()}
class Resources {val displayMetrics=Metrics()}
class Metrics {val density=1f}
''',
'Graphics.kt': '''package android.graphics
object Color {const val TRANSPARENT=0;fun rgb(r:Int,g:Int,b:Int)=0}
object Typeface {const val BOLD=1}
''',
'Gravity.kt': '''package android.view
object Gravity {const val CENTER_HORIZONTAL=1;const val LEFT=2;const val TOP=4}
''',
'Widgets.kt': '''package android.widget
import android.content.Context
open class LinearLayout(val context:Context) {
 val resources get()=context.resources
 val children=mutableListOf<Any>();var layouts=0;var orientation=0;var gravity=0
 fun setPadding(a:Int,b:Int,c:Int,d:Int)=Unit
 fun setBackgroundColor(c:Int)=Unit
 fun addView(v:Any,p:LayoutParams) {children.add(v)}
 fun requestLayout() {layouts++}
 class LayoutParams(w:Int,h:Int) {companion object {const val WRAP_CONTENT=-2}}
 companion object {const val VERTICAL=1}
}
class TextView(c:Context) {
 var text:CharSequence="";var textSize=0f;var gravity=0
 fun setTextColor(c:Int)=Unit
 fun setLineSpacing(a:Float,b:Float)=Unit
 fun setPadding(a:Int,b:Int,c:Int,d:Int)=Unit
}
''',
'Text.kt': '''package android.text
class SpannableStringBuilder(val source:String):CharSequence by source {
 fun setSpan(o:Any,start:Int,end:Int,flags:Int) {check(start in 0..source.length && end in start..source.length)}
 override fun toString()=source
}
object Spanned {const val SPAN_EXCLUSIVE_EXCLUSIVE=1}
''',
'Style.kt': '''package android.text.style
class ForegroundColorSpan(c:Int)
class StyleSpan(s:Int)
''',
'Probe.kt': probe,
'Test.kt': r'''package t.mma.hooks.items.conversationanalysis
import android.widget.*
fun main() {
 val n=ConversationAnalysisText::normalize
 check(n("甲\r\n乙\r丙")=="甲\n乙\n丙")
 check(n("甲\\n乙\\r\\n丙")=="甲\n乙\n丙")
 check(n("甲\\\\n乙")=="甲\n乙")
 check(n("甲/n/n/n/n乙")=="甲\n\n乙")
 check(n("甲\n \n\n\n乙")=="甲\n\n乙")
 for (s in listOf("https://example.test/n/n/news", "www.test/n/n", "C:\\new\\notes", "/news", "/normal/name")) check(n(s)==s) {s}
 val raw="标题\\n正文/n/n/n末尾"
 val expected="标题\n正文\n\n末尾"
 check(n(raw)==expected && n(n(raw))==expected)
 // Accumulated snapshots repair tokens even when either spelling crosses SSE chunks.
 var combined=""
 listOf("标题\\", "n正文/", "n/n", "/n末尾").forEach {combined+=it;n(combined)}
 check(n(combined)==expected)
 for(total in listOf(0,1,2,3,301,1000000)) for(limit in listOf(0,1,2,300,50000)) {
  val p=ConversationAnalysisInput.positions(total,limit)
  check(p.size==minOf(total,limit))
  check(p.distinct().size==p.size && p.toList()==p.sorted())
  check(p.all {it in 0 until total})
  if(p.size>1) check(p.first()==0 && p.last()==total-1)
  if(p.size==1) check(p.single()==total-1)
 }
 check(listOf("128k","256k","512k","1m","2m").map(ConversationAnalysisInput::autoLimit)==listOf(300,600,1200,2500,5000))
 val render=RenderProbe(android.content.Context())
 val body=render.children.single() as LinearLayout
 val text=body.children.single() as TextView
 repeat(1000) {render.update("群聊",1,"群聊总结：标题\\n内容概览：第${it}段/n/n结论：完成")}
 check(body.children.size==1 && body.children.single()===text)
 check(text.text.toString().contains("\n内容概览"))
 val layouts=render.layouts
 render.update("群聊",1,"群聊总结：标题\\n内容概览：第999段/n/n结论：完成")
 check(render.layouts==layouts)
 render.update("群聊",1,"wxid_abc\\n群聊ID：g@chatroom")
 check(!text.text.contains("wxid_") && !text.text.contains("@chatroom"))
 println("PASS real text: escaped/double escapes, CRLF, repeated /n, cross-chunk, URL/path protection, idempotence; bounded uniform samples; real renderer 1000 updates reuse one TextView and skip identical content")
}
'''
}
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    for name, content in sources.items():
        (p / name).write_text(content)
    subprocess.run(['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect',
        '-classpath', str(std), '-d', str(p / 'test.jar'),
        *map(str, p.glob('*.kt')), str(BASE / 'ConversationAnalysisText.kt'),
        str(BASE / 'ConversationAnalysisInput.kt')], check=True, timeout=90)
    subprocess.run(['java', '-cp', f'{p / "test.jar"}:{std}',
        't.mma.hooks.items.conversationanalysis.TestKt'], check=True, timeout=20)
