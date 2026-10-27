"""Target compile settings page with real Compose/Miuix/Android APIs; stub project shells."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs')
std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
plugin=next((Path.home()/'.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compose-compiler-plugin-embeddable/2.4.0').glob('*/*.jar'))
cp=[std,Path('/usr/lib/android-sdk/platforms/android-36/android.jar'),*Path('/tmp/favorite-compose-cp').glob('*.jar')]
stubs={
'Shell.kt':'''package t.mma.ui.miuix
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import android.content.SharedPreferences
import top.yukonga.miuix.kmp.basic.ScrollBehavior
@Composable fun PageScaffold(title:String,largeTitle:String,scrollBehavior:ScrollBehavior,bottomBar:@Composable (Any)->Unit,content:@Composable (PaddingValues)->Unit){content(PaddingValues(0.dp))}
@Composable fun BottomActionBar(primaryText:String,onPrimaryClick:()->Unit){}
@Composable fun SettingsCard(content:@Composable ()->Unit){content()}
@Composable fun SwitchRow(sp:SharedPreferences,key:String,title:String,summary:String,defaultValue:Boolean){}
@Composable fun SmallTitle(text:String){}
''',
'Ui.kt':'''package t.mma.ui
interface FeatureSettingsProvider {fun title():String}
''',
'Store.kt':'''package t.mma.preferences
object HchatStorage {fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()}
''',
'Settings.kt':'''package t.mma.hooks.items.nicknamewidth
object NicknameWidthSettings {const val PREFS="Hchat_group_nickname_width_config";const val ENABLE="enable";const val TITLE="解除群成员昵称长度限制"}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in stubs.items():(p/n).write_text(s)
 cmd=['java','-Xmx384m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-Xplugin='+str(plugin),'-classpath',':'.join(map(str,cp)),'-d',str(p/'ui.jar'),str(r/'app/src/main/java/t/mma/ui/miuix/NicknameWidthMiuixPage.kt'),*map(str,p.glob('*.kt'))]
 subprocess.run(cmd,check=True,timeout=45)
print('PASS settings page compiled with Compose plugin, real Android/Compose/Miuix APIs')
for path,needle in [('hooks/core/FeatureRegistry.kt','register(t.mma.hooks.items.nicknamewidth.NicknameWidthFeature())'),('ui/FeatureToggleRegistry.kt','"group_nickname_width" to Toggle("Hchat_group_nickname_width_config", "enable")'),('ui/miuix/MiuixSettingsPagePart03.kt','NicknameWidthSettings.ID -> NicknameWidthMiuixPage'),('ui/miuix/MiuixSettingsPagePart02.kt','"group_nickname_width" -> listOf')]:
 assert needle in (r/'app/src/main/java/t/mma'/path).read_text(),path
print('PASS feature registration, default-off toggle registry, route and search wiring')