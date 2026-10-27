"""Compile production group bar UI against actual Compose/Miuix/Android APIs."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
plugin=next((Path.home()/'.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compose-compiler-plugin-embeddable/2.4.0').glob('*/*.jar'))
cp=[std,Path('/usr/lib/android-sdk/platforms/android-36/android.jar'),*Path('/tmp/favorite-compose-cp').glob('*.jar')]
stubs={
'Shell.kt':'''package t.mma.ui.miuix
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import android.content.Context
import android.app.Activity
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import t.mma.hooks.items.conversationgroup.ConversationGroup
@Composable fun PageScaffold(title:String,largeTitle:String,scrollBehavior:ScrollBehavior,onBack:()->Unit,bottomBar:@Composable (Any)->Unit,content:@Composable (PaddingValues)->Unit){content(PaddingValues(0.dp))}
@Composable fun BottomActionBar(primaryText:String,onPrimaryClick:()->Unit){}
@Composable fun SettingsCard(content:@Composable ()->Unit){content()}
@Composable fun SwitchRow(checked:Boolean,title:String,summary:String,enabled:Boolean=true,onCheckedChange:(Boolean)->Unit){}
@Composable fun ActionRow(title:String,summary:String,onClick:()->Unit){}
data class PopupChoice(val title:String,val value:String)
@Composable fun PopupChoiceRow(title:String,summary:String,choices:List<PopupChoice>,value:String,onSelected:(String)->Unit){}
@Composable fun ConversationGroupConversationPickerPage(context:Context,group:ConversationGroup,groups:List<ConversationGroup>,onBack:()->Unit,onConfirm:(List<String>)->Unit){}
object VoiceForwardMiuixDialog {
fun showNumberInput(activity:Activity,title:String,initialValue:Int,minValue:Int,maxValue:Int?=null,onConfirm:(Int)->Unit,onDismiss:()->Unit){}
fun showTextInput(activity:Activity,title:String,summary:String,initialValue:String="",placeholder:String="",maxLength:Int=100,singleLine:Boolean=true,allowEmpty:Boolean=false,onConfirm:(String)->Unit,onDismiss:()->Unit){}
}
''',
'Ui.kt':'''package t.mma.ui
interface FeatureSettingsProvider {fun title():String}
''',
'Store.kt':'''package t.mma.preferences
object HchatStorage {fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,text in stubs.items():(p/name).write_text(text)
 production=[r/'app/src/main/java/t/mma/ui/miuix/GroupBarMiuixPage.kt',r/'app/src/main/java/t/mma/hooks/items/groupbar/GroupBarStore.kt',r/'app/src/main/java/t/mma/hooks/items/conversationgroup/ConversationGroup.kt']
 cmd=['java','-Xmx384m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-Xplugin='+str(plugin),'-classpath',':'.join(map(str,cp)),'-d',str(p/'ui.jar'),*map(str,production),*map(str,p.glob('*.kt'))]
 subprocess.run(cmd,check=True,timeout=45)
print('PASS group bar settings page + actual model compiled with real Android/Compose/Miuix APIs')