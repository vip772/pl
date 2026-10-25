"""Compile the new calendar settings controls with the actual Compose compiler and the whole picker against SDK."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
ui=(r/'app/src/main/java/t/mma/ui/miuix/HomeExtensionMiuixPage.kt').read_text();a=ui.index('                        ActionRow(if (calendarPicking)');b=ui.index('                        HomeExtensionSlider(context, "背景透明度"',a)
code='''package t.mma.ui.miuix
import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.*
import t.mma.hooks.items.homeextension.CalendarBackground
import t.mma.hooks.items.homesidepanel.*
@Composable fun ActionRow(title:String,summary:String,onClick:()->Unit){}
@Composable fun Controls(context:Context,activity:Activity?) {
var calendarBackground by remember { mutableStateOf(CalendarBackground.hasImage(context)) }
var calendarPicking by remember { mutableStateOf(false) }
'''+ui[a:b]+'}'
files={'Controls.kt':code,'Calendar.kt':'''package t.mma.hooks.items.homeextension
object CalendarBackground {
const val SLOT="home_calendar_background"
fun hasImage(c:android.content.Context)=false
fun clear(c:android.content.Context)=true
fun save(c:android.content.Context,u:android.net.Uri)=true
}
''','Images.kt':'''package t.mma.hooks.items.homesidepanel
object SidePanelAppearance {fun isPhoto(s:String)=false}
object SidePanelImages {fun saveFromUri(c:android.content.Context,s:String,u:android.net.Uri)=true}
''','Xposed.kt':'''package de.robv.android.xposed
open class XC_MethodHook {
class MethodHookParam(val thisObject:Any,val args:Array<Any?>)
open fun afterHookedMethod(param:MethodHookParam){}
}
object XposedBridge {fun hookAllMethods(c:Class<*>,n:String,h:XC_MethodHook){}}
'''}
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
plugin=next((cache/'org.jetbrains.kotlin/kotlin-compose-compiler-plugin-embeddable/2.4.0').glob('*/*.jar'))
jars=list(Path('/tmp/favorite-compose-cp').glob('*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in files.items():(p/n).write_text(s)
 cp=':'.join(map(str,[std,Path('/usr/lib/android-sdk/platforms/android-36/android.jar'),*jars]))
 cmd=['java','-Xmx384m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17',f'-Xplugin={plugin}','-classpath',cp,'-d',str(p/'check.jar'),*[str(p/n) for n in files],str(r/'app/src/main/java/t/mma/hooks/items/homesidepanel/SidePanelImagePicker.kt')]
 result=subprocess.run(cmd,capture_output=True,text=True,timeout=90)
 if result.returncode:print(result.stderr)
 result.check_returncode()
print('PASS: actual Compose compiler for new calendar controls; complete production picker SDK compile (host dependencies stubbed)')