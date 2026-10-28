#!/usr/bin/env python3
"""Execute the production underlay controller against a minimal view model (not an Android UI test)."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
lib=Path('/tmp/pl-kotlin-libs')
files={
'Rect.kt': '''package android.graphics
data class Rect(var left:Int=0,var top:Int=0,var right:Int=0,var bottom:Int=0){fun set(l:Int,t:Int,r:Int,b:Int){left=l;top=t;right=r;bottom=b};fun width()=right-left;fun height()=bottom-top;companion object{fun intersects(a:Rect,b:Rect)=a.left<b.right&&b.left<a.right&&a.top<b.bottom&&b.top<a.bottom}}
''',
'Color.kt': '''package android.graphics.drawable
class ColorDrawable(val color:Int)
''',
'Log.kt': '''package t.mma.utils
object HLog{fun i(s:String){}}
''',
'View.kt': '''package android.view
interface ViewParent
open class View {
 var parent:ViewParent?=null; var visibility=0; var height=0; var width=0; var id=0; var top=0; var elevation=0f; var z=0f; var isShown=true; var alpha=1f
 var left=0; var measuredHeight=0; var measuredWidth=0;var isLayoutRequested=false;var measureCalls=0;var layoutCalls=0
 open fun measure(w:Int,h:Int){measureCalls++;measuredWidth=w;measuredHeight=h}
 open fun layout(l:Int,t:Int,r:Int,b:Int){layoutCalls++;left=l;top=t;width=r-l;height=b-t}
 object MeasureSpec {const val EXACTLY=0;fun makeMeasureSpec(s:Int,m:Int)=s}
 val bottom get()=top+height
 open fun canScrollVertically(direction:Int)=false
 open fun scrollBy(x:Int,y:Int){}
 var layoutParams:Any?=null
 var background:Any?=null
 var paddingLeft=0;var paddingTop=0;var paddingRight=0;var paddingBottom=0
 val resources=Resources();val context=Context()
 fun setPadding(l:Int,t:Int,r:Int,b:Int){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b}
 fun getLocationOnScreen(out:IntArray){out[0]=left;out[1]=top;var p=parent as? View;while(p!=null){out[0]+=p.left;out[1]+=p.top;p=p.parent as? View}}
 fun getGlobalVisibleRect(out:android.graphics.Rect)=true
 open fun <T:View> findViewById(id:Int):T? { @Suppress("UNCHECKED_CAST") return if(this.id==id)this as T else null }
 companion object {const val VISIBLE=0;const val GONE=8;const val NO_ID=-1}
}
open class ViewGroup:View(),ViewParent{var clipToPadding=true;var clipChildren=true;var foreground:Any?=null;val children=mutableListOf<View>();val childCount get()=children.size;fun getChildAt(i:Int)=children[i];fun add(v:View){children.add(v);v.parent=this}
 override fun <T:View> findViewById(id:Int):T? {super.findViewById<T>(id)?.let{return it};for(c in children)c.findViewById<T>(id)?.let{return it};return null}}
class Resources{fun getIdentifier(n:String,t:String,p:String)=n.hashCode().let{if(it==0)1 else it};fun getResourceEntryName(id:Int)=id.toString()}
class Context{val packageName="com.tencent.mm"}
''',
'Linear.kt':'''package android.widget
open class EditText:android.view.View()
open class LinearLayout:android.view.ViewGroup(){class LayoutParams{var bottomMargin=0}}
''',
'Native.kt':'''package com.tencent.mm.pluginsdk.ui.chat
class ChattingScrollLayout:android.widget.LinearLayout()
class ChattingContent:android.view.ViewGroup(){var measured:((android.view.View)->Unit)?=null;override fun measure(w:Int,h:Int){super.measure(w,h);measured?.invoke(this)};var resized:((Int)->Unit)?=null;override fun layout(l:Int,t:Int,r:Int,b:Int){super.layout(l,t,r,b);resized?.invoke(b-t)}}
class ChatFooter:android.view.ViewGroup()
class ChatFooterBottom:android.view.ViewGroup()
class AppPanel:android.view.ViewGroup()
''',
'Wrapper.kt':'''package com.tencent.mm.ui.chatting.view
class MMChattingListView(val list:android.view.ViewGroup):android.view.ViewGroup(){fun getListView()=list}
''',
'Reflect.kt':'''package t.mma.utils
object KavaReflector{fun findMethodRecursive(c:Class<*>,n:String)=c.methods.find{it.name==n};fun invokeOrThrow(m:java.lang.reflect.Method,o:Any)=m.invoke(o)}
''',
 'Main.kt':'''package t.mma.hooks.items.backgroundbeauty
import android.view.*
import android.widget.LinearLayout
import com.tencent.mm.pluginsdk.ui.chat.*
import com.tencent.mm.ui.chatting.view.MMChattingListView
class TestList:ViewGroup(){
 var scrollable=false;var history=false;var itemTotal=30;var last=29;var scrollCalls=0
 fun getCount()=itemTotal
 fun getLastVisiblePosition()=last
 override fun canScrollVertically(direction:Int)=if(direction<0)scrollable else history
 override fun scrollBy(x:Int,y:Int){scrollCalls++;for(row in children)row.top-=y}
 override fun layout(l:Int,t:Int,r:Int,b:Int){super.layout(l,t,r,b);if(scrollable&&!history)for(row in children)if(row.bottom>height-paddingBottom)row.top=height-paddingBottom-row.height}
}
fun main(){
 val scroll=ChattingScrollLayout()
 val content=ChattingContent();content.id="bki".hashCode()
 val lp=LinearLayout.LayoutParams();lp.bottomMargin=3;content.layoutParams=lp
 content.layout(0,0,1440,2637);content.measure(1440,2637)
 val footer=ChatFooter();footer.id="k52".hashCode();footer.layout(0,2637,1440,4141)
 val upper=ViewGroup();upper.id="blr".hashCode();upper.height=0;footer.add(upper)
 val host=ViewGroup();host.id="bl8".hashCode();footer.add(host)
 val row=ViewGroup();row.id="ssr".hashCode();row.height=171;host.add(row)
 val panel=ChatFooterBottom();panel.height=1333;host.add(panel)
 val list=TestList();list.setPadding(1,2,3,7)
 val wrapper=MMChattingListView(list);wrapper.id="bm6".hashCode();wrapper.add(list);content.add(wrapper)
 scroll.add(content);scroll.add(footer)
 val controller=ChatInputMessageUnderlay()
 content.measured={controller.onContentMeasured(it)}
 controller.apply(scroll)
 check(content.height==2808 && footer.top==2637 && footer.height==1504 && lp.bottomMargin==3)
 check(list.paddingBottom==178 && !list.clipToPadding)
 repeat(20){controller.apply(scroll)}
 check(content.height==2808 && footer.top==2637 && lp.bottomMargin==3)
 // Simulate LinearLayout placing footer after content: original measured height must be restored first.
 repeat(5){
  controller.beforeLayout(scroll)
  check(content.measuredHeight==2637)
  content.layout(0,0,1440,content.measuredHeight)
  footer.layout(0,content.measuredHeight,1440,content.measuredHeight+1504)
  controller.apply(scroll)
  check(content.height==2808 && footer.top==2637)
 }
 panel.height=2500;controller.apply(scroll);check(content.height==2808 && list.paddingBottom==178)
 row.height=250;controller.apply(scroll);check(content.height==2887 && list.paddingBottom==257)
 host.top=20;controller.apply(scroll);check(content.height==2907 && list.paddingBottom==277)
 host.visibility=View.GONE;controller.apply(scroll);check(content.height==2637 && list.paddingBottom==7)
 host.visibility=View.VISIBLE;host.top=0;row.height=171
 list.setPadding(5,9,6,11);controller.apply(scroll);check(list.paddingBottom==182 && list.paddingTop==9)
 controller.restoreAll();check(content.height==2637 && list.paddingBottom==11 && lp.bottomMargin==3 && list.clipToPadding)
 controller.apply(scroll);controller.restoreAll();controller.restoreAll();check(content.height==2637)
 // Real 11:52 geometry: ssr ends 26px before the clipped viewport bottom.
 scroll.layout(0,0,1440,2834)
 controller.apply(scroll)
 check(content.height==2834 && list.paddingBottom==208 && footer.top==2637)
 repeat(5){
  controller.beforeLayout(scroll)
  check(content.measuredHeight==2637)
  content.layout(0,0,1440,2637)
  controller.apply(scroll)
  check(content.height==2834 && footer.top==2637 && list.paddingBottom==208)
 }
 // Native relayout may change bounds without changing measured size.
 content.measure(1440,2834);content.layout(0,0,1440,2637)
 val measuredBefore=content.measureCalls;val laidBefore=content.layoutCalls
 controller.apply(scroll)
 check(content.height==2834 && content.measureCalls==measuredBefore && content.layoutCalls==laidBefore+1)
 val stableMeasures=content.measureCalls;val stableLayouts=content.layoutCalls
 repeat(10000){controller.apply(scroll)}
 check(content.measureCalls==stableMeasures && content.layoutCalls==stableLayouts)
 content.children.remove(wrapper);wrapper.parent=null
 val replacement=MMChattingListView(list);replacement.id="bm6".hashCode();replacement.add(list);content.add(replacement)
 controller.apply(scroll);check(content.height==2834)
 // A genuinely visible panel is not mistaken for the narrow bottom tail.
 scroll.height=3500;controller.apply(scroll)
 check(content.height==2808 && list.paddingBottom==182)
 scroll.height=2808;controller.apply(scroll)
 check(content.height==2808 && list.paddingBottom==182)
 controller.restoreAll();check(content.height==2637 && list.paddingBottom==11)
 scroll.height=0
 // A fresh native measurement (keyboard/window resize) takes priority over old expanded height.
 controller.apply(scroll);content.measure(1440,1700);controller.beforeLayout(scroll)
 check(content.measuredHeight==1700)
 content.layout(0,0,1440,1700);footer.layout(0,1700,1440,3204);controller.apply(scroll)
 check(content.height==1871 && footer.top==1700)
 controller.restoreAll();check(content.height==1700)
 // Same-width NEW native measurement equal to our old expanded height must win.
 controller.apply(scroll)
 val sameWidthNewHeight=content.height
 content.measure(content.width,sameWidthNewHeight)
 controller.beforeLayout(scroll)
 check(content.measuredHeight==sameWidthNewHeight)
 content.layout(0,0,content.width,sameWidthNewHeight)
 footer.layout(0,sameWidthNewHeight,content.width,sameWidthNewHeight+1504)
 controller.apply(scroll);controller.restoreAll()
 check(content.height==sameWidthNewHeight)
 // A new window width may share the previous expanded height.
 controller.apply(scroll)
 val oldExpanded=content.height
 content.measure(720,oldExpanded)
 controller.beforeLayout(scroll)
 check(content.measuredWidth==720 && content.measuredHeight==oldExpanded)
 content.layout(0,0,720,oldExpanded);footer.layout(0,oldExpanded,720,oldExpanded+1504)
 controller.apply(scroll)
 check(content.width==720 && content.height==oldExpanded+171)
 controller.restoreAll()
 check(content.width==720 && content.height==oldExpanded)
 controller.apply(scroll)
 content.layout(0,0,1440,content.height)
 controller.apply(scroll)
 check(content.width==1440)
 controller.restoreAll()
 // Floating acquisition must restore native padding BEFORE it snapshots it.
 val baseline=list.paddingBottom;val nativeHeight=content.height
 controller.apply(scroll);check(list.paddingBottom>baseline)
 ChatInputMessageUnderlay.releaseForFloatingFooter(footer)
 check(list.paddingBottom==baseline&&content.height==nativeHeight)
 controller.restoreAll();check(list.paddingBottom==baseline)
 // Model the real bottom-pinned list: native layout shrinks it first.
 scroll.height=0
 val rowItem=View();rowItem.height=120;list.add(rowItem)
 list.scrollable=true
 content.resized={h -> list.layout(0,0,content.width,h)}
 list.layout(0,0,content.width,content.height)
 rowItem.top=list.height-list.paddingBottom-rowItem.height
 controller.apply(scroll)
 check(rowItem.bottom==list.height-list.paddingBottom)
 val nativeBottom=footer.top
 repeat(4){
  controller.beforeLayout(scroll)
  content.layout(0,0,content.width,content.measuredHeight)
  controller.apply(scroll)
  check(rowItem.bottom==list.height-list.paddingBottom)
 }
 check(list.scrollCalls>0 && footer.top==nativeBottom)
 for(h in listOf(1100,2400,1100,2400)){
  controller.beforeLayout(scroll)
  content.measure(720,h);content.layout(0,0,720,h)
  footer.layout(0,h,720,h+1504)
  controller.apply(scroll)
  check(rowItem.bottom==list.height-list.paddingBottom)
 }
 list.history=true;list.last=15
 val callsBefore=list.scrollCalls;val topBefore=rowItem.top
 controller.beforeLayout(scroll);content.layout(0,0,720,content.measuredHeight);controller.apply(scroll)
 check(list.scrollCalls==callsBefore && rowItem.top==topBefore)
 list.history=false;list.last=29;list.scrollable=false
 controller.beforeLayout(scroll);content.layout(0,0,720,content.measuredHeight);controller.apply(scroll)
 check(list.scrollCalls==callsBefore)
 list.scrollable=true;rowItem.top=list.height-list.paddingBottom-rowItem.height
 controller.beforeLayout(scroll);list.itemTotal++
 content.layout(0,0,720,content.measuredHeight);controller.apply(scroll)
 check(list.scrollCalls==callsBefore)
 // Appends during this layout: only compensate if the new last row is visible.
 for(added in listOf(1,3)){
  list.history=false;list.last=list.itemTotal-1
  rowItem.top=list.height-list.paddingBottom-rowItem.height
  controller.beforeLayout(scroll);list.itemTotal+=added;list.last=list.itemTotal-1
  content.layout(0,0,720,content.measuredHeight);controller.apply(scroll)
  check(rowItem.bottom==list.height-list.paddingBottom)
  val stableScrolls=list.scrollCalls;val stableMeasures=content.measureCalls;val stableLayouts=content.layoutCalls
  repeat(10000){controller.apply(scroll)}
  check(list.scrollCalls==stableScrolls && content.measureCalls==stableMeasures && content.layoutCalls==stableLayouts)
 }
 // Offscreen appended rows and removed datasets must not be selected/scrolled.
 for(removed in listOf(false,true)){
  list.last=list.itemTotal-1;rowItem.top=list.height-list.paddingBottom-rowItem.height
  controller.beforeLayout(scroll)
  if(removed){list.itemTotal--;list.last=list.itemTotal-1}else{list.itemTotal++}
  val calls=list.scrollCalls
  content.layout(0,0,720,content.measuredHeight);controller.apply(scroll)
  check(list.scrollCalls==calls)
 }
 // Touch cancels an already captured transaction; MOVE has no controller path.
 list.last=list.itemTotal-1;rowItem.top=list.height-list.paddingBottom-rowItem.height
 controller.beforeLayout(scroll);controller.onTouch(scroll,0)
 val touchCalls=list.scrollCalls
 content.layout(0,0,720,content.measuredHeight);controller.apply(scroll)
 check(list.scrollCalls==touchCalls)
 controller.onTouch(scroll,1)
 controller.onTouch(scroll,0);controller.beforeLayout(scroll)
 content.layout(0,0,720,content.measuredHeight);controller.apply(scroll)
 check(list.scrollCalls==touchCalls)
 controller.onTouch(scroll,3)
 println("PASS: real measurement ownership, visible appends, offscreen/deletion protection, touch cancellation and stable 10000 refreshes")
 println("PASS: bottom anchor across repeated native layouts and window resize; history, short lists and changed data untouched")
 println("PASS: native footer position preserved, no negative margin, zero-height blr, repeated native layouts, panel exclusion, multiline, offsets, visibility, padding, restore, native resize")
}
'''
}
with tempfile.TemporaryDirectory() as d:
 d=Path(d)
 for n,s in files.items():(d/n).write_text(s)
 std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
 jar=d/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),*[str(d/n) for n in files],str(root/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/ChatInputMessageUnderlay.kt')],check=True,stdout=subprocess.PIPE)
 subprocess.run(['java','-cp',f'{jar}:{std}','t.mma.hooks.items.backgroundbeauty.MainKt'],check=True)
