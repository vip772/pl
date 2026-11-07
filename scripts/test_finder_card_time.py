"""Run production Finder resolver/column against view fixtures; no Gradle or APK."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIB = Path('/tmp/pl-kotlin-libs')
STD = next(LIB.glob('kotlin-stdlib-[0-9]*.jar'))
BASE = ROOT / 'app/src/main/java/t/mma/hooks/items/hchatextra'
source = (BASE / 'HchatExtraFeature.kt').read_text()
start = source.index('    private fun finderFeedBottomAnchor(')
end = source.index('    private fun cardBottomAnchor(', start)
resolver = source[start:end]
assert source.index('return finderFeedBottomAnchor(root, holder)', end) < source.index('val row = root as? ViewGroup', end)
assert 'MessageDetailsFinderColumn.restore(parent)' in source
assert 'MessageDetailsFinderColumn.restore(previousParent)' in source
assert 'releaseSafely("视频号时间布局")' in source

files = {
    'Context.kt': 'package android.content\nclass Context',
    'View.kt': '''package android.view
open class View(val context:android.content.Context=android.content.Context()) {
 var parent:ViewGroup?=null;var visibility=0;var width=200;var height=300
 var layoutParams:ViewGroup.LayoutParams=ViewGroup.LayoutParams(-2,-2)
 companion object {const val VISIBLE=0;const val GONE=8}
}
open class ViewGroup(c:android.content.Context=android.content.Context()):View(c) {
 open class LayoutParams(var width:Int,var height:Int){companion object {const val WRAP_CONTENT=-2}}
 val children=mutableListOf<View>();val childCount get()=children.size
 fun indexOfChild(v:View)=children.indexOf(v)
 fun getChildAt(i:Int)=children[i]
 fun removeView(v:View){children.remove(v);v.parent=null}
 fun removeViewAt(i:Int){removeView(children[i])}
 fun addView(v:View,p:LayoutParams){addView(v,children.size,p)}
 fun addView(v:View,i:Int,p:LayoutParams){check(v.parent==null);children.add(i,v);v.parent=this;v.layoutParams=p}
}
''',
    'Linear.kt': '''package android.widget
import android.view.*
open class LinearLayout(c:android.content.Context=android.content.Context()):ViewGroup(c) {
 var orientation=HORIZONTAL
 class LayoutParams(w:Int,h:Int):ViewGroup.LayoutParams(w,h) {
  var leftMargin=0;var rightMargin=0;var gravity=0;var weight=0f
  constructor(p:LayoutParams):this(p.width,p.height){leftMargin=p.leftMargin;rightMargin=p.rightMargin;gravity=p.gravity;weight=p.weight}
 }
 companion object {const val HORIZONTAL=0;const val VERTICAL=1}
}
''',
    'Holder.kt': '''package com.tencent.mm.ui.chatting.viewitems
class p7(@JvmField var r:android.view.View?)
''',
    'Reflect.kt': '''package t.mma.utils
object KavaReflector {fun readField(f:java.lang.reflect.Field,h:Any)=f.get(h)}
''',
    'Resolver.kt': '''package t.mma.hooks.items.hchatextra
import android.view.*
import android.widget.LinearLayout
import t.mma.utils.KavaReflector
class Resolver {
 data class BottomDetailsAnchor(val parent:ViewGroup,val layoutView:View,val alignmentView:View)
 var lookups=0
 private val fields=mutableMapOf<Class<*>,List<java.lang.reflect.Field>>()
 fun messageContentFields(h:Any)=fields.getOrPut(h.javaClass){lookups++;h.javaClass.declaredFields.toList()}
 fun isViewWithinRoot(v:View,r:View):Boolean {var x:View?=v;repeat(32){if(x===r)return true;x=x?.parent};return false}
''' + resolver + '''
 fun anchor(r:View,h:Any)=finderFeedBottomAnchor(r,h)
}
''',
    'Main.kt': '''package t.mma.hooks.items.hchatextra
import android.view.*
import android.widget.LinearLayout
import com.tencent.mm.ui.chatting.viewitems.p7
fun main(){
 for(self in listOf(false,true)) {
  val row=LinearLayout();val neighbor=View();row.addView(neighbor,LinearLayout.LayoutParams(30,50))
  val host=if(self) row else LinearLayout().also {it.orientation=LinearLayout.VERTICAL;row.addView(it,LinearLayout.LayoutParams(-2,-2))}
  val card=View();val params=LinearLayout.LayoutParams(240,-2).apply{leftMargin=9;rightMargin=7;gravity=5}
  host.addView(card,params);val holder=p7(card);val r=Resolver()
  card.width=0;check(r.anchor(row,holder)==null);check(card.parent===host);card.width=240
  card.visibility=View.GONE;check(r.anchor(row,holder)==null);card.visibility=View.VISIBLE
  check(r.anchor(LinearLayout(),holder)==null)
  val a=r.anchor(row,holder)!!;check(a.parent is LinearLayout && a.parent.orientation==LinearLayout.VERTICAL)
  check(a.layoutView===card && a.alignmentView===card)
  check((a.parent is MessageDetailsFinderColumn)==self)
  val label=View();a.parent.addView(label,LinearLayout.LayoutParams(-2,-2));check(a.parent.indexOfChild(label)>a.parent.indexOfChild(card))
  repeat(5000){check(r.anchor(row,holder)?.parent===a.parent)};check(r.lookups==1)
  MessageDetailsFinderColumn.restore(a.parent);check(card.parent===a.parent)
  a.parent.removeView(label)
  card.layoutParams.width=260
  MessageDetailsFinderColumn.restore(a.parent)
  check(card.parent===host && host.indexOfChild(card)==if(self) 1 else 0)
  check(row.getChildAt(0)===neighbor)
  if(self){check(card.layoutParams===params && params.width==260 && params.leftMargin==9 && params.rightMargin==7 && params.gravity==5)}
  // Repeated enable/recycle cycles preserve the native slot and never nest columns.
  repeat(50){val next=r.anchor(row,holder)!!;MessageDetailsFinderColumn.restore(next.parent);check(card.parent===host)}
  host.removeView(card);check(r.anchor(row,holder)==null)
  val replacement=View();host.addView(replacement,LinearLayout.LayoutParams(240,-2));holder.r=replacement
  val next=r.anchor(row,holder)!!;check(next.layoutView===replacement);MessageDetailsFinderColumn.restore(next.parent)
  holder.r=null;check(r.anchor(row,holder)==null)
  println("PASS Finder self=$self: horizontal native row, below-card anchor, zero/hidden/foreign/detached/null checks, 5000 warm binds, 50 cleanup cycles, original slot/margins and recycled holder")
 }
}
''',
}
with tempfile.TemporaryDirectory(prefix='finder-time-regression-') as directory:
    out = Path(directory)
    for name, text in files.items():
        (out / name).write_text(text)
    jar = out / 'test.jar'
    subprocess.run(['java', '-Xmx256m', '-cp', str(LIB / '*'),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn',
                    '-no-stdlib', '-no-reflect', '-classpath', str(STD), '-d', str(jar),
                    *map(str, out.glob('*.kt')), str(BASE / 'MessageDetailsFinderColumn.kt')],
                   check=True, timeout=60)
    subprocess.run(['java', '-ea', '-cp', str(jar) + ':' + str(STD),
                    't.mma.hooks.items.hchatextra.MainKt'], check=True, timeout=20)
