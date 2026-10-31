"""Production dual-toggle refresh/stop methods on JVM, no database or native refresh."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/moments/MomentsKeywordBlockFeature.kt').read_text()
refresh=s[s.index('        fun refresh() {'):s.index('        val listener =',s.index('        fun refresh() {'))]
stop=s[s.index('    private fun stopRuntime() {'):s.index('    private fun scheduleInstall() {')]
assert 'refreshVisibleTimelineLocally()' in s[s.index('    @Synchronized fun destroy()'):s.index('    private fun prepareViewBinding(')]
code='''class Main {
 val q=java.util.ArrayDeque<()->Unit>()
 fun post(f:()->Unit){q.add(f)}
 fun drain(){while(q.isNotEmpty())q.removeFirst()()}
}
class Prefs {var enabled=false;fun getBoolean(k:String,d:Boolean)=enabled}
object MomentsKeywordBlockSettings {const val KEY_ENABLE="enable";const val DEFAULT_ENABLE=false}
object MomentsContactFilterSettings {const val KEY_ENABLE="enable";const val DEFAULT_ENABLE=false}
class MomentsKeywordBlockRuntime(c:Any,l:(String,Throwable?)->Unit) {
 var dead=false
 fun start(){starts++}
 fun destroy(){check(!dead);dead=true;stops++}
 companion object{var starts=0;var stops=0}
}
class Ticket {var closed=false;fun close(){closed=true}}
class Owner {
 var featureClosed=false;val lifecycleMain=Main();val keywords=Prefs();val contacts=Prefs();val context=Any()
 var runtime:MomentsKeywordBlockRuntime?=null;val tickets=linkedMapOf<String,Ticket>()
 fun logFeatureError(s:String,e:Throwable?){}
 fun scheduleInstall(){tickets["private"]=Ticket()}
''' + refresh + stop + '''
}
fun main(){
 val o=Owner();o.refresh();o.lifecycleMain.drain();check(o.runtime==null)
 o.keywords.enabled=true;o.refresh();o.lifecycleMain.drain();val first=o.runtime!!;val ticket=o.tickets.values.single()
 o.contacts.enabled=true;o.refresh();o.lifecycleMain.drain();check(o.runtime===first)
 o.keywords.enabled=false;o.refresh();o.lifecycleMain.drain();check(o.runtime===first&&!first.dead)
 o.contacts.enabled=false;o.refresh();o.lifecycleMain.drain();check(o.runtime==null&&first.dead&&ticket.closed)
 o.keywords.enabled=true;o.refresh();o.lifecycleMain.drain();check(o.runtime!==first&&MomentsKeywordBlockRuntime.starts==2)
 o.keywords.enabled=false;o.refresh();o.lifecycleMain.drain();check(MomentsKeywordBlockRuntime.stops==2)
 o.keywords.enabled=true;o.refresh();o.featureClosed=true;o.lifecycleMain.drain();check(o.runtime==null)
 println("PASS production dual-toggle owner: either consumer keeps runtime, last disable releases ticket/runtime, reopen and stale callback")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=20)