"""Execute extracted production chip methods with synthetic Views; no APK/device claim."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderFeature.kt').read_text()
a=s.index('    private fun applyBottomNewMessageChip(');b=s.index('    private fun isNewMessageChip(',a)
methods=s[a:b]
assert 'setOnClickListener' not in methods
fixture='''
import java.util.WeakHashMap
open class View {
 var tag:Any?="toolbar";var parent:View?=null;var visibility=VISIBLE;var isShown=true
 var height=20;var width=40;var left=200;var nativeTop=500;var isLayoutRequested=false
 var layoutParams:Any=FrameLayout.LayoutParams()
 fun getLocationOnScreen(out:IntArray){out[0]=left;out[1]=nativeTop-(layoutParams as FrameLayout.LayoutParams).bottomMargin}
 companion object {const val VISIBLE=0}
}
open class ViewGroup:View(){val children=mutableMapOf<Int,View>();fun <T:View> findViewById(id:Int):T?=children[id] as? T}
class FrameLayout {class LayoutParams(var bottomMargin:Int=24,var gravity:Int=android.view.Gravity.BOTTOM or android.view.Gravity.RIGHT)}
class Prefs {var enabled=true;fun getBoolean(k:String,d:Boolean)=enabled}
class Host {
 class ChipMarginState(var bottom:Int,var appliedBottom:Int=bottom)
 val newMessageChipMargins=WeakHashMap<View,ChipMarginState>()
 val toolbarPrefs=Prefs();val newMessageChipLocation=IntArray(2)
 val toolbar=View();val chip=View();val layout=ViewGroup();val content=ViewGroup();val footer=ViewGroup()
 init {layout.children[1]=content;layout.children[3]=footer;content.children[2]=chip;content.parent=layout;footer.parent=layout;chip.parent=content;toolbar.parent=footer;toolbar.nativeTop=504;toolbar.height=50}
 fun resourceId(v:View,n:String)=when(n){"bki"->1;"bm2"->2;"k52"->3;else->0}
 fun find(v:View,p:(View)->Boolean):View?=toolbar
 fun dp(v:Int)=v
 val CHAT_TOOLBAR_CHIP_GAP_DP=8
 METHODS
 fun apply()=applyBottomNewMessageChip(layout)
 fun restore()=restoreBottomNewMessageChip(layout)
 fun margin()=(chip.layoutParams as FrameLayout.LayoutParams).bottomMargin
}
fun main(){
 val h=Host();h.apply();check(h.margin()==48)
 repeat(5000){h.apply();check(h.margin()==48)}
 h.toolbar.nativeTop=494;h.apply();check(h.margin()==58)
 h.toolbar.nativeTop=504;h.apply();check(h.margin()==48)
 h.toolbar.left=500;h.apply();check(h.margin()==24)
 h.toolbar.left=200;h.apply();check(h.margin()==48)
 h.toolbar.nativeTop=424;h.toolbar.height=20;h.apply();check(h.margin()==24)
 h.toolbar.nativeTop=504;h.toolbar.height=50;h.apply();check(h.margin()==48)
 h.toolbarPrefs.enabled=false;h.apply();check(h.margin()==24&&h.newMessageChipMargins.isEmpty())
 h.toolbarPrefs.enabled=true;h.apply();h.chip.isLayoutRequested=true;h.apply();check(h.margin()==48)
 h.chip.isLayoutRequested=false;h.restore();check(h.margin()==24)
 h.apply();(h.chip.layoutParams as FrameLayout.LayoutParams).bottomMargin=71;h.restore();check(h.margin()==71)
 (h.chip.layoutParams as FrameLayout.LayoutParams).bottomMargin=24;h.chip.parent=null;h.apply();check(h.margin()==24)
 println("PASS actual production chip methods: 5000 stable frames, toolbar moves, full rectangles, disable, pending layout, native override/restore; synthetic Views only")
}
'''.replace('METHODS',methods)
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(fixture)
 (p/'Gravity.kt').write_text('package android.view\nobject Gravity {const val BOTTOM=80;const val TOP=48;const val RIGHT=5;const val VERTICAL_GRAVITY_MASK=112;const val HORIZONTAL_GRAVITY_MASK=7}')
 (p/'Toolbar.kt').write_text('package t.mma.hooks.items.chattoolbar\nobject ChatToolbarSettings {const val KEY_ENABLE="enable";const val DEFAULT_ENABLE=false;internal const val TOOLBAR_TAG="toolbar"}')
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar')]+[str(x) for x in p.glob('*.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)