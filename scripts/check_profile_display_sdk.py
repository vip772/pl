"""SDK/Compose targeted checks; no Gradle, APK, or device validation."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/t/mma'
LIBS = Path('/tmp/pl-kotlin-libs')
STDLIB = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
SDK = Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
PLUGIN = next((Path.home() / '.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compose-compiler-plugin-embeddable/2.4.0').glob('*/*.jar'))
STUBS = {
    'Shell.kt': '''package t.mma.ui.miuix
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import top.yukonga.miuix.kmp.basic.ScrollBehavior
@Composable fun PageScaffold(title:String,largeTitle:String,scrollBehavior:ScrollBehavior,bottomBar:@Composable (Any)->Unit,content:@Composable (PaddingValues)->Unit){}
@Composable fun BottomActionBar(primaryText:String,onPrimaryClick:()->Unit){}
@Composable fun SettingsCard(content:@Composable ()->Unit){content()}
@Composable fun InsetDivider(){}
@Composable fun InputRow(title:String,summary:String,value:String,minLines:Int=1,onValueChange:(String)->Unit){}
@Composable fun SwitchRow(sp:SharedPreferences,key:String,title:String,summary:String,defaultValue:Boolean){}
@Composable fun SwitchRow(checked:Boolean,title:String,summary:String,enabled:Boolean=true,onCheckedChange:(Boolean)->Unit){}
@Composable fun ColorPickerRow(title:String,summary:String,value:String,allowGradient:Boolean=true,onReset:(()->Unit)?=null,threeColorGradient:Boolean=false,showSummary:Boolean=true,onValueChange:(String)->Unit){}
''',
    'Provider.kt': '''package t.mma.ui
interface FeatureSettingsProvider {fun title():String}
''',
    'Storage.kt': '''package t.mma.preferences
object HchatStorage {fun preferences(context:android.content.Context,name:String):android.content.SharedPreferences=TODO()}
''',
}


def compile_sources(sources, output, classpath, plugin=False):
    command = ['java', '-Xmx384m', '-cp', ':'.join(map(str, LIBS.glob('*.jar'))),
               'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
               '-jvm-target', '17', '-classpath', ':'.join(map(str, classpath)), '-d', str(output)]
    if plugin:
        command.append('-Xplugin=' + str(PLUGIN))
    result = subprocess.run(command + list(map(str, sources)), capture_output=True, text=True, timeout=55)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)


with tempfile.TemporaryDirectory(prefix='profile-display-') as directory:
    temp = Path(directory)
    for name, source in STUBS.items():
        (temp / name).write_text(source)
    settings = [SRC / 'hooks/items/profileid' / name for name in
                ['ProfileIdSettings.kt', 'ProfileAddTimeSettings.kt', 'ProfileGroupInfoSettings.kt', 'ProfileRealNameTailSettings.kt']]
    cp = [STDLIB, SDK, *[p for p in Path('/tmp/favorite-compose-cp').glob('*.jar') if 'material' not in p.name.lower()]]
    compile_sources([SRC / 'ui/miuix/ProfileIdMiuixPage.kt', *settings, *temp.glob('*.kt')], temp / 'ui.jar', cp, True)
    print('PASS: production profile page and settings compiled against Android SDK + Compose/Miuix; project UI shells stubbed')
    header = (SRC / 'hooks/api/contact/WeContactHeaderApi.kt').read_text()
    start = header.index('    private fun applyColorSpec(')
    end = header.index('    private fun listenerInfo(', start)
    (temp / 'ColorCheck.kt').write_text('package t.mma.hooks.api.contact\nimport android.widget.TextView\nimport android.graphics.*\nobject ColorCheck {\n' + header[start:end] + '}')
    compile_sources([temp / 'ColorCheck.kt'], temp / 'color.jar', [STDLIB, SDK])
    print('PASS: exact production three-stop shader method compiled against Android SDK')
    (temp / 'Check.kt').write_text('''import t.mma.hooks.items.profileid.*
fun main() {
 check(ProfileRealNameTailSettings.headerText("**东", "＊＊") == "实名尾字：＊＊东")
 check(!ProfileRealNameTailSettings.DEFAULT_ENABLE)
 check(ProfileRealNameTailSettings.headerText("**东", "") == "实名尾字：可能是个东")
 check(ProfileRealNameTailSettings.headerText("**东", "小") == "实名尾字：小东")
 check(ProfileRealNameTailSettings.headerText("", "小") == "实名尾字：未知")
 check(ProfileRealNameTailSettings.headerText("＊＊", null) == "实名尾字：未知")
 check(ProfileRealNameTailSettings.headerText("**𠮷", null) == "实名尾字：可能是个𠮷")
 val values = listOf("#112233", "#112233,#445566", "#112233,#445566,#778899")
 for (value in values) {
  check(ProfileGroupJoinTimeSettings.cleanColorSpec(value) == value)
  check(ProfileGroupInviterSettings.cleanColorSpec(value) == value)
 }
 check(ProfileGroupJoinTimeSettings.cleanColorSpec("bad,#aabbcc") == "#AABBCC")
 check(ProfileGroupInviterSettings.cleanColorSpec("") == "")
 check(ProfileGroupJoinTimeSettings.DEFAULT_COLOR.split(',').size == 3)
 check(ProfileGroupInviterSettings.DEFAULT_COLOR.split(',').size == 3)
 println("PASS: single/two/three-stop persistence, invalid input, defaults")
}
''')
    compile_sources([*settings, temp / 'Check.kt'], temp / 'settings.jar', [STDLIB])
    subprocess.run(['java', '-cp', str(temp / 'settings.jar') + ':' + str(STDLIB), 'CheckKt'], check=True, timeout=10)

for path in (SRC).rglob('*.kt'):
    text = path.read_text()
    for obsolete in ['ProfileGroupInfoSettingsProvider', 'ProfileGroupInfoMiuixPage', '进入时间/邀请人', '"profile_group_info"']:
        assert obsolete not in text, (path, obsolete)
page = (SRC / 'ui/miuix/ProfileIdMiuixPage.kt').read_text()
assert page.count('ProfileGradientSetting(') == 4
assert 'if (enabled) {' in page and 'threeColorGradient = true' in page
registry = (SRC / 'ui/FeatureToggleRegistry.kt').read_text()
block = registry.split('"profile_id" ->', 1)[1].split('"moments_fake_interaction"', 1)[0]
assert all(name in block for name in ['ProfileIdSettings', 'ProfileAddTimeSettings', 'ProfileGroupJoinTimeSettings', 'ProfileGroupInviterSettings', 'ProfileRealNameTailSettings'])
groups = (SRC / 'ui/miuix/MiuixSettingsPagePart02.kt').read_text()
assert 'ProfileIdFeature.ID,' in groups
assert '"显示进群时间", "进群时间", "显示邀请人"' in groups
route = (SRC / 'ui/miuix/MiuixSettingsPagePart03.kt').read_text()
assert 'ProfileIdFeature.ID -> ProfileIdMiuixPage(context, provider, onBack)' in route
print('PASS: obsolete UI/registration/routes removed; consolidated enable state and search wired')
subprocess.run(['git', '-C', str(ROOT), 'diff', '--check'], check=True)
