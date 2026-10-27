"""Production visibility gate and card signatures; JVM only, no Gradle."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/homeextension/HomeExtensionRuntime.kt').read_text()
a=s.index('        private fun contactsVisible()');b=s.index('        private fun observeContactVisibility()',a)
x=s.index('        private fun cardSignature(');y=s.index('        private fun refreshContactsIfNeeded()',x)
g=s[y:s.index('        private var compactCard',y)]
code='''class Rect
class Panel {var isAttachedToWindow=true;var isShown=true;var focus=true;var visible=true;fun hasWindowFocus()=focus;fun getGlobalVisibleRect(r:Rect)=visible}
class Configuration{var uiMode=0};class Resources{val configuration=Configuration()};class Context{val resources=Resources()}
class Prefs{val map=mutableMapOf<String,Any>();fun getBoolean(k:String,d:Boolean)=map[k] as? Boolean?:d;fun getString(k:String,d:String)=map[k] as? String?:d}
object HomeExtensionSettings{const val ENABLE="enable";const val FAVORITES="favorites";const val RECENT="recent";const val FAVORITE_IDS="favorite_ids"}
class Runtime {
 val ctx=Context();val prefs=Prefs();var contactsPanel:Panel?=Panel();val contactVisibleArea=Rect();var renderConfig:Map<String,*>=emptyMap<String,Any>();val musicDataKeys=setOf("music_favorites");val today=java.time.LocalDate.now()
 var loading=false;var loadedConfig="";var loadedAt=0L;var loads=0
 fun loadContacts(c:String){loads++}
'''+s[a:b]+s[x:y]+g+'''
 fun refresh()=refreshContactsIfNeeded()
 fun signature(id:String,compact:Boolean=false)=cardSignature(id,compact)
}
fun main(){val r=Runtime();r.prefs.map["enable"]=true
 r.contactsPanel!!.visible=false;r.refresh();check(r.loads==0)
 r.contactsPanel!!.visible=true;r.contactsPanel!!.focus=false;r.refresh();check(r.loads==0)
 r.contactsPanel!!.focus=true;r.refresh();check(r.loads==1)
 r.loading=true;r.refresh();check(r.loads==1);r.loading=false
 r.loadedConfig="false";r.loadedAt=System.currentTimeMillis();r.refresh();check(r.loads==1)
 r.renderConfig=mapOf("greeting" to "hello","weather_city" to "A","music_alpha" to 100)
 val calendar=r.signature("calendar");val weather=r.signature("weather");val music=r.signature("music")
 r.renderConfig=mapOf("greeting" to "hello","weather_city" to "B","music_alpha" to 100)
 check(r.signature("calendar")==calendar&&r.signature("music")==music&&r.signature("weather")!=weather)
 check(r.signature("calendar",true)!=calendar)
 r.ctx.resources.configuration.uiMode=32;check(r.signature("calendar")!=calendar)
 println("PASS hidden/focus/loading/cache gates; weather changes isolate calendar/music; compact and theme invalidate")
}
'''
code=code.replace('android.graphics.Rect','Rect').replace('Map<String,*>=','Map<String,*> =')
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)
