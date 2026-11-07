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
assert 'parent.timeLayoutParams(label, details.isSelf, params.topMargin,' in source
assert source.count('avatarHidden && parent !is MessageDetailsFinderColumn') == 2
assert 'MessageDetailsFinderColumn.restore(parent)' in source
assert 'MessageDetailsFinderColumn.restore(previousParent)' in source
assert 'releaseSafely("视频号时间布局")' in source

files = {
    'Gravity.kt': 'package android.view\nobject Gravity {const val END=8388613;const val START=8388611}',
    'TextUtils.kt': 'package android.text\nobject TextUtils {enum class TruncateAt { END }}',
    'Text.kt': '''package android.widget
class TextView:android.view.View() {
 var maxLines=99;var ellipsize:android.text.TextUtils.TruncateAt?=null
 fun setSingleLine(single:Boolean){maxLines=if(single) 1 else 99}
 override fun onMeasure(w:Int,h:Int){measuredWidth=if(MeasureSpec.getMode(w)==MeasureSpec.EXACTLY) MeasureSpec.getSize(w) else minOf(desiredWidth,MeasureSpec.getSize(w));measuredHeight=desiredHeight}
}''',
    'Context.kt': 'package android.content\nclass Context',
    'View.kt': '''package android.view
open class View(val context:android.content.Context=android.content.Context()) {
 var parent:ViewGroup?=null;var visibility=0;var width=200;var height=300
 var gravity=0;var textAlignment=0;var paddingTop=2;var paddingBottom=2;var paddingLeft=4;var paddingRight=4
 fun setPadding(l:Int,t:Int,r:Int,b:Int){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b}
 var layoutParams:ViewGroup.LayoutParams=ViewGroup.LayoutParams(-2,-2)
 var measuredWidth=200;var measuredHeight=300
 var desiredWidth=200;var desiredHeight=300
 open fun onMeasure(w:Int,h:Int){measuredWidth=if(MeasureSpec.getMode(w)==MeasureSpec.EXACTLY) MeasureSpec.getSize(w) else minOf(desiredWidth,MeasureSpec.getSize(w));measuredHeight=desiredHeight}
 fun measure(w:Int,h:Int)=onMeasure(w,h)
 fun setMeasuredDimension(w:Int,h:Int){measuredWidth=w;measuredHeight=h}
 object MeasureSpec {
  const val EXACTLY=1073741824;const val AT_MOST=-2147483648;const val UNSPECIFIED=0
  fun makeMeasureSpec(s:Int,m:Int)=(s and 0x3fffffff) or m
  fun getSize(s:Int)=s and 0x3fffffff
  fun getMode(s:Int)=s and -1073741824
 }
 companion object {const val VISIBLE=0;const val GONE=8;const val TEXT_ALIGNMENT_TEXT_END=3;const val TEXT_ALIGNMENT_TEXT_START=2}
}
open class ViewGroup(c:android.content.Context=android.content.Context()):View(c) {
 open class LayoutParams(var width:Int,var height:Int){companion object {const val WRAP_CONTENT=-2;const val MATCH_PARENT=-1}}
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
 override fun onMeasure(w:Int,h:Int){
  val limit=View.MeasureSpec.getSize(w);val exact=View.MeasureSpec.getMode(w)==View.MeasureSpec.EXACTLY
  var widest=0;var totalHeight=0
  for(v in children){
   val fixed=v.layoutParams.width.takeIf{it>0}
   val childWidth=fixed ?: if(v.layoutParams.width==ViewGroup.LayoutParams.MATCH_PARENT) limit else minOf(v.desiredWidth,limit)
   v.measure(View.MeasureSpec.makeMeasureSpec(childWidth,View.MeasureSpec.EXACTLY),h)
   widest=maxOf(widest,v.measuredWidth)
   totalHeight+=if(fixed==null && v !is TextView) ((v.desiredWidth+childWidth-1)/childWidth)*v.desiredHeight else v.measuredHeight
  }
  setMeasuredDimension(if(exact) limit else minOf(widest,limit),totalHeight)
 }
 var orientation=HORIZONTAL
 class LayoutParams(w:Int,h:Int):ViewGroup.LayoutParams(w,h) {
  var leftMargin=0;var rightMargin=0;var topMargin=0;var gravity=0;var weight=0f
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
 fun isViewWithinRoot(v:View,r:View):Boolean {var x:View?=v;repeat(32){if(x===r)return true;x=x?.parent as? View};return false}
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
  check(a.parent is MessageDetailsFinderColumn)
  check(a.parent.layoutParams.width==240)
  val label=android.widget.TextView()
  val timeParams=a.parent.timeLayoutParams(label,self,2,1000)
  check(timeParams.width==ViewGroup.LayoutParams.MATCH_PARENT && timeParams.topMargin==2)
  check(timeParams.leftMargin==0 && timeParams.rightMargin==0)
  check(label.paddingLeft==0 && label.paddingRight==0 && label.paddingTop==2 && label.paddingBottom==2)
  check(label.gravity==if(self) Gravity.END else Gravity.START)
  check(label.textAlignment==if(self) View.TEXT_ALIGNMENT_TEXT_END else View.TEXT_ALIGNMENT_TEXT_START)
  a.parent.addView(label,timeParams);check(a.parent.indexOfChild(label)>a.parent.indexOfChild(card))
  // Time may expand the column, but never changes native card size or line count.
  for(nativeWidth in listOf(180,240,360,540,720)){
   card.layoutParams.width=nativeWidth
   for(textWidth in listOf(100,400,1200,2400)){
    label.desiredWidth=textWidth;label.desiredHeight=24
    a.parent.measure(View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.AT_MOST),0)
    check(a.parent.measuredWidth==maxOf(nativeWidth,minOf(textWidth,1000))){"single-line time width"}
    check(card.measuredWidth==nativeWidth && label.measuredWidth==a.parent.measuredWidth)
    check(label.maxLines==1 && label.ellipsize==android.text.TextUtils.TruncateAt.END)
    check((card.layoutParams as LinearLayout.LayoutParams).gravity==if(self) Gravity.END else Gravity.START)
    check(a.parent.measuredHeight==card.measuredHeight+24){"time must remain one line"}
   }
  }
  card.layoutParams.width=240
  repeat(5000){check(r.anchor(row,holder)?.parent===a.parent)};check(r.lookups==1)
  MessageDetailsFinderColumn.restore(a.parent);check(card.parent===a.parent)
  a.parent.removeView(label)
  card.layoutParams.width=260
  MessageDetailsFinderColumn.restore(a.parent)
  check(card.parent===host && host.indexOfChild(card)==if(self) 1 else 0)
  check(row.getChildAt(0)===neighbor)
  check(card.layoutParams===params && params.width==260 && params.leftMargin==9 && params.rightMargin==7 && params.gravity==5)
  // Repeated enable/recycle cycles preserve the native slot and never nest columns.
  repeat(50){val next=r.anchor(row,holder)!!;MessageDetailsFinderColumn.restore(next.parent);check(card.parent===host)}
  host.removeView(card);check(r.anchor(row,holder)==null)
  val replacement=View();host.addView(replacement,LinearLayout.LayoutParams(240,-2));holder.r=replacement
  val next=r.anchor(row,holder)!!;check(next.layoutView===replacement);MessageDetailsFinderColumn.restore(next.parent)
  holder.r=null;check(r.anchor(row,holder)==null)
  println("PASS Finder self=$self: single-line row width with native card size, edge alignment and zero duplicate margins/padding across five display widths and four text widths, below-card anchor, zero/hidden/foreign/detached/null checks, 5000 warm binds, 50 cleanup cycles, original slot/margins and recycled holder")
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

    sdk = Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
    subprocess.run(['java', '-Xmx256m', '-cp', str(LIB / '*'), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect', '-classpath', str(STD)+':'+str(sdk), '-d', str(out/'sdk.jar'), str(out/'Resolver.kt'), str(out/'Reflect.kt'), str(BASE / 'MessageDetailsFinderColumn.kt')], check=True, timeout=60)
    print('PASS exact Finder resolver and full container SDK compile')
