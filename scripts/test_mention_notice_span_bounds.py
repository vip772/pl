"""Run production styling against a strict span model; no Gradle or APK build."""
from pathlib import Path
import os, subprocess, tempfile
root = Path(__file__).resolve().parents[1]
source = (root/'app/src/main/java/t/mma/hooks/items/mentionextension/MentionNoticeFeature.kt').read_text()
start = source.index('    private fun style(')
end = source.index('    fun bind(', start)
style = source[start:end].replace('private fun style(', 'fun style(', 1)
assert 'MentionNoticeSettings.highlight' not in (root/'app/src/main/java/t/mma/hooks/items/groupleave/GroupLeaveMonitorHighlighter.kt').read_text()
assert 'verticalArrangement = Arrangement.spacedBy(10.dp)' in (root/'app/src/main/java/t/mma/ui/miuix/MentionExtensionMiuixPage.kt').read_text()
code = '''
class Context
class Color {companion object {fun parseColor(s:String)=0}}
object Spanned {const val SPAN_EXCLUSIVE_EXCLUSIVE=33;const val SPAN_PRIORITY_SHIFT=16}
class HostSpan
class HiddenPrefixSpan
class NoticeGradientSpan(val colors:IntArray,val text:String)
data class Entry(val span:Any,val start:Int,val end:Int)
class SpannableStringBuilder(val input:CharSequence):CharSequence {
 val entries=mutableListOf<Entry>()
 init { if(input is SpannableStringBuilder)entries.addAll(input.entries) }
 override val length get()=input.length
 override fun get(index:Int)=input[index]
 override fun subSequence(start:Int,end:Int)=input.subSequence(start,end)
 override fun toString()=input.toString()
 fun <T:Any> getSpans(start:Int,end:Int,c:Class<T>):List<T> = entries.filter{c.isInstance(it.span)}.map{c.cast(it.span)}
 fun removeSpan(s:Any){entries.removeAll{it.span===s}}
 fun setSpan(s:Any,start:Int,end:Int,flags:Int){check(start>=0&&end<=length&&end>=start);entries+=Entry(s,start,end)}
}
class Check {
 val MARKER="\\u2063\\u2060\\u2063"
 val PREFIX="[艾特提醒] "
 fun colors(c:Context)=listOf("#FF0000","#00FF00","#0000FF")
''' + style + '''
}
fun main(){
 val check=Check();val c=Context()
 val original=SpannableStringBuilder(check.MARKER+"x".repeat(42))
 val host=HostSpan();original.setSpan(host,0,45,0)
 val styled=check.style(c,original) as SpannableStringBuilder
 check(styled.length==45);check(styled.toString()==original.toString())
 check(styled.entries.any{it.span===host&&it.end==45})
 // Reproduce host applying its original full-length span after our rendering pass.
 styled.setSpan(HostSpan(),0,45,0)
 val twice=check.style(c,styled) as SpannableStringBuilder
 check(twice.entries.count{it.span is NoticeGradientSpan}==1)
 check(twice.entries.count{it.span is HiddenPrefixSpan}==1)
 val legacy=check.PREFIX+"张三 艾特了你"
 val old=check.style(c,legacy) as SpannableStringBuilder
 check(old.length==legacy.length);check(old.toString()==legacy)
 check(old.entries.any{it.span is HiddenPrefixSpan&&it.end==check.PREFIX.length})
 val normalized="张三 艾特了你"
 check(check.style(c,normalized).length==normalized.length)
 for(text in listOf("",check.MARKER,check.PREFIX)) check(check.style(c,text).length==text.length)
 println("PASS production styling: 45-character original offsets preserved, host spans retained, repeated styling deduplicated, legacy prefix hidden without deletion, normalized/empty text safe")
}
'''
libs=Path(os.environ.get('KOTLIN_LIB_DIR','/tmp/pl-kotlin-libs'))
stdlib=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    d=Path(td);(d/'Check.kt').write_text(code);jar=d/'check.jar'
    subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect',
        '-classpath',str(stdlib),'-d',str(jar),str(d/'Check.kt')],check=True,timeout=60)
    subprocess.run(['java','-cp',f'{jar}:{stdlib}','CheckKt'],check=True,timeout=15)
