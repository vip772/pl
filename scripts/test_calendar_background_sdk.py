"""Compile production calendar image storage/drawable and rendering against Android SDK; no Gradle."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
base=r/'app/src/main/java/t/mma/hooks/items/homeextension'
s=(base/'HomeExtensionRuntime.kt').read_text();a=s.index('        private fun renderCalendar()');b=s.index('        private fun renderRecent()',a)
files={
'Storage.kt':'''package t.mma.preferences
object HchatStorage { fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO();fun resourceDirectory(c:android.content.Context,f:String)=java.io.File("/tmp",f) }
''',
'Log.kt':'''package t.mma.utils
object HLog {fun e(s:String,t:Throwable){}}
''',
'Settings.kt':'''package t.mma.hooks.items.homeextension
object HomeExtensionSettings {
const val CALENDAR_ALPHA="calendar_alpha";const val CALENDAR_EFFECT="calendar_effect";const val CALENDAR_EFFECT_STYLE="style";const val GREETING="greeting";const val DEFAULT_GREETING="hello"
fun preferences(c:android.content.Context):android.content.SharedPreferences=TODO()
}
class CalendarTodayView(c:android.content.Context,e:Boolean,s:Int):android.widget.TextView(c)
''',
'Render.kt':'''package t.mma.hooks.items.homeextension
import android.view.*
import android.widget.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
class Session {
val ctx:android.content.Context=TODO();val prefs:android.content.SharedPreferences=TODO()
val week=LocalDate.now();val today=LocalDate.now();val compactCard=false;val destroyed=false
val worker=java.util.concurrent.Executors.newSingleThreadExecutor();val main=android.os.Handler(android.os.Looper.getMainLooper())
val root=LinearLayout(ctx)
fun dp(v:Int)=v
fun card(s:String,calendar:Boolean=false):LinearLayout=if(calendar)AdjustableCalendarPanel(ctx)else LinearLayout(ctx)
fun text(s:String,f:Float=14f)=TextView(ctx)
'''+s[a:b]+'}'
}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,v in files.items():(p/n).write_text(v)
 cmd=['java','-Xmx384m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',f'{std}:/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'test.jar'),*[str(base/n) for n in ['CalendarBackground.kt','PhotoTransformController.kt','AdjustablePhotoView.kt','AdjustableCalendarPanel.kt']],*[str(p/n) for n in files]]
 result=subprocess.run(cmd,capture_output=True,text=True,timeout=60)
 if result.returncode:print(result.stderr)
 result.check_returncode()
print('PASS: production calendar importer/cache/rounded drawable and exact renderCalendar compile against SDK (host dependencies stubbed)')
# Wiring checks complement SDK compile; they do not execute the Android picker.
ui=(r/'app/src/main/java/t/mma/ui/miuix/HomeExtensionMiuixPage.kt').read_text()
picker=(r/'app/src/main/java/t/mma/hooks/items/homesidepanel/SidePanelImagePicker.kt').read_text()
assert 'SidePanelImagePicker.launch(activity, CalendarBackground.SLOT)' in ui
assert 'CalendarBackground.clear(context)' in ui and 'if (calendarBackground && !calendarPicking)' in ui
assert 'CalendarBackground.save(appContext, uri)' in picker
assert 'backgroundRevision == prefs.getLong(CalendarBackground.REVISION, 0L)' in s
print('PASS: picker/clear wiring and stale panel revision guard present')