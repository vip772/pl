"""Production parser and exact UI block regression; no full build or device claims."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]; b=r/'app/src/main/java/t/mma'
libs=Path('/tmp/pl-kotlin-libs'); cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
abi=r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
cp=[abi,r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',Path('/usr/lib/android-sdk/platforms/android-37.0/android.jar')]
cp+=list(cache.rglob('*.jar'))+list(Path('/tmp/favorite-compose-cp').glob('*.jar'))+list(libs.glob('*.jar'))
compiler=['java','-Xmx512m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-no-stdlib','-no-reflect']
ns={'compiler':compiler,'cp':cp}

s=(b/'hooks/items/floatingshortcut/FloatingShortcutRuntime.kt').read_text()
a=s.index('    private fun labelBackgroundColors(');z=s.index('    private fun applyLabelColors(',a);fn=s[a:z]
a=s.index('    private fun parseColor(value:');z=s.index('    private fun contrastColor',a);parse=s[a:z]
ui=(b/'ui/miuix/MiuixSettingsPagePart06.kt').read_text()
a=ui.index('                        if (displayMode != FloatingShortcutSettings.DISPLAY_ICON) {');z=ui.index('                        PopupChoiceRow(\n                            title = "展开方向"',a);block=ui[a:z]
assert ui.index('title = "快捷项样式"')<a<ui.index('title = "展开方向"',a)
assert 'labelBackgroundColors(context)' in s
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 (p/'Colors.kt').write_text('''package android.graphics
object Color {fun parseColor(s:String):Int {require(s.startsWith("#"));val h=s.drop(1);require(h.length==6||h.length==8);return (if(h.length==6) 0xff000000L or h.toLong(16) else h.toLong(16)).toInt()}}
''')
 (p/'Test.kt').write_text('''import android.graphics.Color
class Context(val dark:Boolean)
object FloatingShortcutSettings {const val KEY_LABEL_BACKGROUND_COLOR="label_background_color";const val DEFAULT_LABEL_BACKGROUND_COLOR=""}
class Prefs(var value:String=""){fun getString(k:String,d:String)=value}
class Runtime {val prefs:Prefs?=Prefs();fun isDark(c:Context)=c.dark
'''+fn+parse+'''
fun test(){val c=Context(false);check(labelBackgroundColors(c).contentEquals(intArrayOf(0xf5ffffff.toInt())));check(labelBackgroundColors(Context(true))[0]==0xf2383b40.toInt());for(v in listOf("#112233","#112233,#445566,#778899","#80112233,#90445566,#A0778899")){prefs!!.value=v;val colors=labelBackgroundColors(c);check(colors.contentEquals(v.split(',').map{Color.parseColor(it)}.toIntArray()))};prefs!!.value="bad,#112233,#445566,#778899";check(labelBackgroundColors(c).size==3);check(labelBackgroundColors(c)[0]==0xf5ffffff.toInt())}}
fun main(){Runtime().test();println("PASS production label background parser: defaults, dark/light, single, three, alpha, invalid fallback, bounded stops; JVM only")}
''')
 std=next(Path('/tmp/pl-kotlin-libs').glob('kotlin-stdlib-[0-9]*.jar'))
 subprocess.run(ns['compiler']+['-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(p/'Colors.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p}/test.jar:{std}','TestKt'],check=True,timeout=20)
 # Exact production UI block, real Android/Compose compiler; explicit shared UI boundaries.
 (p/'UI.kt').write_text('''package t.mma.ui.miuix
import androidx.compose.runtime.*
import android.content.SharedPreferences
object FloatingShortcutSettings {const val DISPLAY_ICON="icon";const val DEFAULT_LABEL_BACKGROUND_COLOR="";const val KEY_LABEL_BACKGROUND_COLOR="label_background_color"}
@Composable fun ColorPickerRow(title:String,summary:String,value:String,threeColorGradient:Boolean=false,onReset:(()->Unit)?=null,onValueChange:(String)->Unit){}
@Composable fun InsetDivider(){}
@Composable fun Setting(sp:SharedPreferences,displayMode:String){var labelBackgroundColor by remember{mutableStateOf("")}
'''+block+'\n}')
 plugin=next((Path('/root/.gradle/caches/modules-2/files-2.1')/'org.jetbrains.kotlin/kotlin-compose-compiler-plugin-embeddable/2.4.0').glob('*/*.jar'))
 subprocess.run(ns['compiler']+['-Xplugin='+str(plugin),'-classpath',':'.join(map(str,ns['cp'])),'-d',str(p/'ui'),str(p/'UI.kt')],check=True,timeout=70)
 print('PASS exact production label background Compose block SDK37 compile; explicit shared helper boundaries, NOT full page/app build')