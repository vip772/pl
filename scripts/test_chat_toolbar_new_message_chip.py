# Updated fixtures are intentionally NOT executed in the 2026-11-04 fix: user forbids local builds.
"""Execute the complete production coordinator with deterministic native-container fixtures."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
src=r/'app/src/main/java/t/mma/hooks/items/chattoolbar/ChatToolbarNewMessagePosition.kt'
runtime=(src.parent/'ChatToolbarRuntime.kt').read_text()
header=(src.parent.parent/'floatingchat/FloatingChatHeaderFeature.kt').read_text()
assert 'applyBottomNewMessageChip' not in header and 'newMessageChipMargins' not in header
assert 'binding.chipPosition.close()' in runtime
assert runtime.count('chipPosition.attach()')==4 and 'chipPosition.close()' in runtime
stubs={
'Package.kt':'''package android.content.pm
class Info {var versionName="8.0.78";var longVersionCode=3180L;val versionCode=3180}
class PackageManager {val info=Info();fun getPackageInfo(n:String,f:Int)=info}
''',
'Os.kt':'''package android.os
object Build {object VERSION {const val SDK_INT=36}}
''',
'Context.kt':'''package android.content
class Context {val packageName="com.tencent.mm";val packageManager=android.content.pm.PackageManager()}
''',
'Resources.kt':'''package android.content.res
class Metrics {var density=1f}
class Resources {val displayMetrics=Metrics();fun getIdentifier(n:String,t:String,p:String)=when(n){"bki"->1;"bm2"->2;"bm4"->3;"ssr"->4;else->0}}
''',
'View.kt':'''package android.view
import android.content.Context
import android.content.res.Resources
open class View {
 var parent:View?=null;val context=Context();val resources=Resources();var id=0
 var isShown=true;var height=20;var width=80;var top=0;var left=0
 var writes=0;var translationY=0f;set(v){field=v;writes++}
 var layoutParams:Any=android.widget.FrameLayout.LayoutParams()
 val viewTreeObserver=ViewTreeObserver()
 open fun <T:View> findViewById(id:Int):T?=if(this.id==id)this as T else null
 fun getLocationOnScreen(out:IntArray){out[0]=left;out[1]=kotlin.math.round(top+translationY).toInt()}
}
open class ViewGroup:View(){val children=mutableListOf<View>();val childCount get()=children.size;fun add(v:View){children+=v;v.parent=this};override fun <T:View> findViewById(id:Int):T? {if(this.id==id)return this as T;for(v in children){v.findViewById<T>(id)?.let{return it}};return null}}
object Gravity {const val BOTTOM=80;const val TOP=48;const val VERTICAL_GRAVITY_MASK=112}
class ViewTreeObserver {var isAlive=true;val listeners=mutableListOf<OnPreDrawListener>();fun interface OnPreDrawListener{fun onPreDraw():Boolean};fun addOnPreDrawListener(l:OnPreDrawListener){listeners+=l};fun removeOnPreDrawListener(l:OnPreDrawListener){listeners-=l};fun frame(){listeners.toList().forEach{check(it.onPreDraw())}}}
''',
'Frame.kt':'''package android.widget
class FrameLayout {class LayoutParams(var gravity:Int=80,var bottomMargin:Int=24)}
''',
'Native.kt':'''package com.tencent.mm.pluginsdk.ui.chat
class ChattingUILayout:android.view.ViewGroup()
class ChattingContent:android.view.ViewGroup()
''',
'Test.kt':'''package t.mma.hooks.items.chattoolbar
import android.view.*
import android.widget.FrameLayout
import com.tencent.mm.pluginsdk.ui.chat.*
fun main(){
 val root=ChattingUILayout();val content=ChattingContent().apply{id=1;height=900;width=1000};val footer=ViewGroup().apply{top=700;height=500;width=1000};val toolbar=View().apply{top=700;width=900;height=55};val chip=View().apply{id=2;left=700;top=765;height=40;width=180}
 val input=View().apply{id=4;top=755;height=60;width=900};val history=View().apply{id=3;left=700;top=780;height=40;width=180;isShown=false}
 root.add(content);root.add(footer);footer.add(input);footer.add(toolbar);content.add(chip);content.add(history)
 var errors=0;val owner=ChatToolbarNewMessagePosition(footer,toolbar){errors++}
 owner.attach();owner.attach();check(footer.viewTreeObserver.listeners.size==1)
 fun frame()=footer.viewTreeObserver.frame()
 frame();check(chip.top+chip.translationY+chip.height==692f) // entirely BELOW toolbar, not overlapping
 val first=chip.translationY;val writes=chip.writes
 repeat(5000){frame();check(chip.translationY==first)};check(chip.writes==writes)
 chip.top=735;frame();check(chip.top+chip.translationY+chip.height==692f) // native margin/position rewrite
 toolbar.top=600;frame();check(chip.top+chip.translationY+chip.height==592f)
 footer.resources.displayMetrics.density=2f;frame();check(chip.top+chip.translationY+chip.height==584f)
 chip.top=400;frame();check(chip.translationY==0f) // native already above
 chip.top=765;frame();toolbar.isShown=false;frame();check(chip.top+chip.translationY+chip.height==684f) // input fallback outside footer hit area
 toolbar.isShown=true;frame();check(chip.translationY<0f)
 (chip.layoutParams as FrameLayout.LayoutParams).gravity=Gravity.TOP;frame();check(chip.translationY==0f)
 (chip.layoutParams as FrameLayout.LayoutParams).gravity=Gravity.BOTTOM;frame()
 chip.translationY=7f;owner.close();check(chip.translationY==7f) // native owns unexpected translation
 check(footer.viewTreeObserver.listeners.isEmpty())
 chip.translationY=0f;owner.attach();frame();owner.close();check(chip.translationY==0f)
 owner.close();check(errors==0)
 chip.translationY=0.25f;owner.attach();frame();val fractional=chip.translationY;val fractionalWrites=chip.writes
 repeat(5000){frame();check(chip.translationY==fractional)};check(chip.writes==fractionalWrites);owner.close();check(chip.translationY==0.25f)
 chip.translationY=0f;chip.left=1200;owner.attach();frame();check(chip.translationY<0f);owner.close();chip.left=700
 content.children.remove(chip);chip.parent=null;owner.attach();frame();check(chip.translationY==0f)
 content.add(chip);frame();check(chip.translationY<0f);owner.close();check(chip.translationY==0f)
 // Actual new-message target: top/history stays native; bottom/new-message moves.
 owner.attach();history.isShown=true;(history.layoutParams as FrameLayout.LayoutParams).gravity=Gravity.TOP
 frame();check(history.translationY==0f);(history.layoutParams as FrameLayout.LayoutParams).gravity=Gravity.BOTTOM
 frame();check(history.top+history.translationY+history.height==584f)
 toolbar.isShown=false;frame();check(history.top+history.translationY+history.height==684f)
 footer.top=500;input.top=500;frame();check(history.top+history.translationY+history.height==484f) // keyboard
 footer.isShown=false;frame();check(history.translationY==0f);footer.isShown=true
 owner.close();check(history.translationY==0f);toolbar.isShown=true;footer.top=700;input.top=755
 val fake=ViewGroup().apply{id=1};root.children.remove(content);content.parent=null;root.add(fake)
 owner.attach();check(footer.viewTreeObserver.listeners.isEmpty());owner.close()
 root.children.remove(fake);root.add(content)
 footer.context.packageManager.info.versionName="8.0.77";owner.attach();check(footer.viewTreeObserver.listeners.isEmpty())
 footer.context.packageManager.info.versionName="8.0.78";footer.context.packageManager.info.longVersionCode=3181;owner.attach();check(footer.viewTreeObserver.listeners.isEmpty())

 check((chip.layoutParams as FrameLayout.LayoutParams).bottomMargin==24)
 println("PASS complete production footer coordinator: bm4 actual new-message vs bm2 quote, top-gravity preserved, toolbar-off fallback, keyboard/footer hit bounds, 10000 stable frames, native translation ownership and cleanup; synthetic containers only")
}
'''
}
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in stubs.items():(p/n).write_text(s)
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar')]
 subprocess.run(cmd+[str(x) for x in p.glob('*.kt')]+[str(src)],check=True,timeout=90)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.chattoolbar.TestKt'],check=True,timeout=15)
