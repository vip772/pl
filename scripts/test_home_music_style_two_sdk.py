"""Compile actual music view/background/gesture against SDK, without Gradle."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];base=r/'app/src/main/java/t/mma/hooks/items/homeextension';lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
files={
'Storage.kt':'''package t.mma.preferences
object HchatStorage { fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO();fun resourceDirectory(c:android.content.Context,f:String)=java.io.File("/tmp",f) }
''',
'Log.kt':'package t.mma.utils\nobject HLog {fun e(s:String,t:Throwable){}}',
'Stubs.kt':'''package t.mma.hooks.items.homeextension
object HomeMusic {fun setListener(f:()->Unit){};fun removeListener(f:()->Unit){}}
object HomeExtensionSettings {const val MUSIC_AVATAR_SLOT="home_music_avatar";const val MUSIC_THREE_AVATAR_SLOT="home_music_three_avatar";const val MUSIC_ALPHA="music_alpha";const val MUSIC_STYLE_TWO_HEIGHT_DP=100;fun preferences(c:android.content.Context):android.content.SharedPreferences=TODO()}
class MusicIcon(c:android.content.Context,s:String):android.widget.TextView(c)
''',
'Images.kt':'''package t.mma.hooks.items.homesidepanel
object SidePanelImages {fun hasImage(c:android.content.Context,s:String)=false;fun load(c:android.content.Context,s:String):android.graphics.Bitmap?=null}
'''
}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,v in files.items():(p/n).write_text(v)
 sources=[base/n for n in ['HomeMusicPaintedView.kt','HomeMusicStyleTwoBackground.kt','HomeMusicLyrics.kt','CalendarBackground.kt','PhotoTransformController.kt']]
 # Calendar store settings are not part of the view under test; compile only its production drawable.
 text=(base/'CalendarBackground.kt').read_text();(p/'Drawable.kt').write_text('package t.mma.hooks.items.homeextension\nimport android.graphics.*\nimport android.graphics.drawable.Drawable\n'+text[text.index('internal class CalendarBackgroundDrawable'):]);sources.remove(base/'CalendarBackground.kt')
 result=subprocess.run(['java','-Xmx384m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',f'{std}:/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'test.jar'),*map(str,sources),*map(str,p.glob('*.kt'))],capture_output=True,text=True,timeout=60)
 if result.returncode:print(result.stderr)
 result.check_returncode()
print('PASS complete music canvas, importer, shared drawable/gesture SDK compile (host/player stubs)')