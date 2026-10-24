"""Execute production touch-branch search and title handoff against small view trees."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
items = root / 'app/src/main/java/t/mma/hooks/items'
quick = (items / 'quickread/QuickMarkReadFeature.kt').read_text()
home = (items / 'homesidepanel/HomeSidePanelRuntime.kt').read_text()
start = quick.index('    private fun findBottomBarAt(')
end = quick.index('    private fun findFloatingHomeRegion(', start)
search = quick[start:end].replace('private fun', 'fun', 1)
start = home.index('            fun isHomeText(')
end = home.index('            return isHomeText(title)', start)
text_check = home[start:end].replace('            fun isHomeText(', 'fun isHomeText(', 1)
regex = next(line.strip() for line in home.splitlines() if 'val HOME_TITLE = Regex' in line).replace('private ', '')
predicate = next(line.strip() for line in home.splitlines() if 'val headerVisible = enabled' in line).split(' = ', 1)[1]
assert 'findBottomNavContainer' not in quick
assert 'root.findViewWithTag' not in quick
assert 'removeOnPreDrawListener(titleHandoffListener)' in home
assert 'if (headerVisible && !chatVisible) ensureHeader()' in home

code = '''
open class View(var tag:Any?=null,var z:Float=0f,var isShown:Boolean=true,var visibility:Int=0,var alpha:Float=1f,var isClickable:Boolean=false,var isLongClickable:Boolean=false,val box:android.graphics.Rect=android.graphics.Rect(0,0,100,100)) {
 companion object { const val VISIBLE=0 }
 var reads=0
 fun getGlobalVisibleRect(out:android.graphics.Rect):Boolean { reads++;out.left=box.left;out.top=box.top;out.right=box.right;out.bottom=box.bottom;return box.right>box.left&&box.bottom>box.top }
}
open class ViewGroup:View(){val children=mutableListOf<View>();val childCount get()=children.size;fun getChildAt(i:Int)=children[i]}
class TextView(val text:String):View()
object CustomBottomBarFeature {const val FLOATING_BOTTOM_BAR_TAG="floating"}
const val HCHAT_SETTINGS_PAGE_TAG="settings"
''' + regex + '\n' + search + '\n' + text_check + '''
fun main(){
 val root=ViewGroup(); val home=ViewGroup(); val bar=ViewGroup().apply {tag="floating";box.top=80}
 home.children+=bar;root.children+=home
 check(findBottomBarAt(root,10f,90f)===bar)
 check(findBottomBarAt(root,10f,20f)==null)
 // A foreground chat/dialog covers home. Never descend into the underlying home tree.
 val chat=com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout(); val message=ViewGroup();chat.children+=message;root.children+=chat
 repeat(10000){message.children+=TextView("message")}
 bar.reads=0
 check(findBottomBarAt(root,10f,90f)==null);check(bar.reads==0)
 // Invisible and alpha-zero overlays must not mask the real bar.
 chat.visibility=8;check(findBottomBarAt(root,10f,90f)===bar)
 chat.visibility=0;chat.alpha=0f;check(findBottomBarAt(root,10f,90f)===bar)
 chat.alpha=1f;home.z=10f;check(findBottomBarAt(root,10f,90f)===bar)
 home.z=0f;chat.tag="settings";check(findBottomBarAt(root,10f,90f)==null)
 root.children.remove(chat)
 // Full-window passive decoration must not block either bottom bar.
 val decoration=ViewGroup();decoration.children+=View();root.children+=decoration
 check(findBottomBarAt(root,10f,90f)===bar)
 val button=View().apply{isClickable=true};decoration.children+=button
 check(findBottomBarAt(root,10f,90f)==null)
 button.isClickable=false;button.isLongClickable=true
 check(findBottomBarAt(root,10f,90f)==null)
 decoration.children.remove(button)
 val native=com.tencent.mm.ui.LauncherUIBottomTabView();native.box.top=80;home.children.clear();home.children+=native
 check(findBottomBarAt(root,10f,90f)===native)
 // Retain home on early ENTER, release when native title changes, and disable immediately.
 fun visible(enabled:Boolean,chatVisible:Boolean,homeText:Boolean,tab:Boolean):Boolean {
  fun isWechatTab()=tab
  fun retainsHomeTitle()=homeText
  return ''' + predicate + '''
 }
 for(title in listOf("微信","微信(255)","微信（99+）"))check(isHomeText(TextView(title)))
 for(title in listOf("威朗车友吃播群(208)","通讯录","微信运动",""))check(!isHomeText(TextView(title)))
 check(visible(true,false,true,true));check(visible(true,true,true,true))
 check(!visible(true,true,false,true));check(!visible(false,true,true,true));check(!visible(true,true,true,false))
 println("PASS: production search, native/floating bars, 10000-node chat exclusion, hidden overlays, Z order, settings, early ENTER and native title handoff")
}
'''
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as temp:
    directory = Path(temp)
    (directory / 'Test.kt').write_text(code)
    (directory / 'Chat.kt').write_text('package com.tencent.mm.pluginsdk.ui.chat\nimport ViewGroup\nclass ChattingUILayout:ViewGroup()')
    (directory / 'Rect.kt').write_text('package android.graphics\nclass Rect(var left:Int=0,var top:Int=0,var right:Int=0,var bottom:Int=0)')
    # Kotlin default-package types can be explicitly imported by packaged fixtures.
    (directory / 'Native.kt').write_text('package com.tencent.mm.ui\nimport ViewGroup\nclass LauncherUIBottomTabView: ViewGroup()')
    command = ['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
               'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
               '-classpath', str(stdlib), '-d', str(directory / 'out.jar')]
    result = subprocess.run(command + list(map(str, directory.glob('*.kt'))), capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    subprocess.run(['java', '-cp', str(directory / 'out.jar') + ':' + str(stdlib), 'TestKt'], check=True)
