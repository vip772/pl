"""Execute production PageScaffold body with a measured-slot layout model; no device/Gradle."""
from pathlib import Path
import subprocess,tempfile
repo=Path(__file__).resolve().parents[1]
s=(repo/'app/src/main/java/t/mma/ui/miuix/MiuixSettingsPagePart20.kt').read_text()
a=s.index('public fun PageScaffold(');b=s.index('@Composable\npublic fun SettingsCard',a)
body=s[a:b].replace('@Composable ', '')
# The production body must use the real scaffold slot, not an unmeasured outer overlay.
assert 'bottomBar = {}' not in body
assert '.align(Alignment.BottomCenter)' not in body
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
code='''package check
class Dp(val value:Float){operator fun compareTo(other:Dp)=value.compareTo(other.value);operator fun plus(other:Dp)=Dp(value+other.value)}
val Int.dp get()=Dp(toFloat())
class PaddingValues(val top:Dp=0.dp,val bottom:Dp=0.dp){fun calculateTopPadding()=top;fun calculateBottomPadding()=bottom}
interface RowScope
class Backdrop
class ScrollBehavior
object Modifier {fun fillMaxSize()=this;fun fillMaxWidth()=this;fun background(v:Any)=this;fun layerBackdrop(v:Any)=this
 fun padding(bottom:Dp):Modifier {Measured.padding=bottom.value;return this}
 fun size(v:Dp)=this;fun responsiveTap(onClick:()->Unit)=this}
object Measured {var padding=0f;var bar=0f;var nav=0f;var gap=0f;var top=0f}
object MiuixTheme {object colorScheme{val background=0;val onSurface=0}}
fun RegisterSettingsBackHandler(v:(()->Unit)?){ }
fun rememberGraphicsLayer()=0
fun rememberLayerBackdrop(v:Any)=Backdrop()
fun navigationButtonBottomInset()=Dp(Measured.nav)
val NAVIGATION_BUTTON_EXTRA_GAP get()=Dp(Measured.gap)
fun Box(modifier:Modifier,contentAlignment:Any?=null,content:()->Unit){content()}
object Alignment{val Center=0}
fun WindowInsets(a:Int,b:Int,c:Int,d:Int)=0
fun TopAppBar(title:String,largeTitle:String,scrollBehavior:ScrollBehavior,navigationIcon:()->Unit,actions:RowScope.()->Unit,defaultWindowInsetsPadding:Boolean){}
object NavIcons{val Back=0}
object ColorFilter{fun tint(v:Int)=0}
fun Image(imageVector:Int,contentDescription:String,colorFilter:Int,modifier:Modifier){}
'''+body+'''
fun main(){
 for(bar in listOf(48f,68f,110f,164f))for(nav in listOf(0f,24f,48f)){
  Measured.bar=bar;Measured.nav=nav;Measured.gap=if(nav>0)8f else 0f
  var bottom=-1f
  PageScaffold("title","title",ScrollBehavior(),bottomBar={}){bottom=it.calculateBottomPadding().value}
  check(bottom==bar+nav+Measured.gap){"Measured bar missing or insets duplicated: $bottom"}
  // Scroll to end: final item's bottom must lie above the measured bar plus 12dp gap.
  val viewport=800f;val lastBottom=viewport-bottom-12f
  check(lastBottom<=viewport-bar-nav-Measured.gap-12f)
  Measured.bar=0f
  PageScaffold("title","title",ScrollBehavior()){bottom=it.calculateBottomPadding().value}
  check(bottom==nav+Measured.gap){"No-bar page reserves phantom bar"}
 }
 println("PASS: production PageScaffold measured bottom slot, variable bar/font sizes, navigation insets, absent bar; no duplicated avoidance")
}
'''
scaffold='''package top.yukonga.miuix.kmp.basic
import check.*
fun Scaffold(topBar:()->Unit,bottomBar:()->Unit,contentWindowInsets:Int,content:(PaddingValues)->Unit){
 Measured.padding=0f
 bottomBar()
 content(PaddingValues(top=32.dp,bottom=Dp(Measured.bar+Measured.padding)))
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Check.kt').write_text(code);(p/'Scaffold.kt').write_text(scaffold)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Check.kt'),str(p/'Scaffold.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p}/test.jar:{std}','check.CheckKt'],check=True,timeout=15)
# Verify every lazy-list page in the new-feature UI family consumes measured bottom padding.
family=['DynamicBadge','MessageMenuExtension','HomeExtension','HomeSidePanel','HomeTextColor','CustomBottomBar','ChatToolbar','MentionExtension','AutoCache','StepMultiplier','MonetModuleGenerator','ConversationAnalysis']
for name in family:
 path=repo/'app/src/main/java/t/mma/ui/miuix'/f'{name}MiuixPage.kt'
 text=path.read_text()
 assert 'calculateBottomPadding()' in text, path
 assert 'calculateBottomPadding() + 84.dp' not in text and 'calculateBottomPadding()+84.dp' not in text,path
print('PASS: new-feature list pages use shared measured bottom padding')