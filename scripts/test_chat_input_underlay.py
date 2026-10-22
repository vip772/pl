#!/usr/bin/env python3
"""Execute the production underlay controller against a minimal view model (not an Android UI test)."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
lib=Path('/tmp/pl-kotlin-libs')
files={
'Rect.kt': '''package android.graphics
data class Rect(var left:Int,var top:Int,var right:Int,var bottom:Int){fun width()=right-left;fun height()=bottom-top;companion object{fun intersects(a:Rect,b:Rect)=a.left<b.right&&b.left<a.right&&a.top<b.bottom&&b.top<a.bottom}}
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
 var left=0; var measuredHeight=0; var measuredWidth=0
 open fun measure(w:Int,h:Int){measuredWidth=w;measuredHeight=h}
 open fun layout(l:Int,t:Int,r:Int,b:Int){left=l;top=t;width=r-l;height=b-t}
 object MeasureSpec {const val EXACTLY=0;fun makeMeasureSpec(s:Int,m:Int)=s}
 var layoutParams:Any?=null
 var background:Any?=null
 var paddingLeft=0;var paddingTop=0;var paddingRight=0;var paddingBottom=0
 val resources=Resources();val context=Context()
 fun setPadding(l:Int,t:Int,r:Int,b:Int){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b}
 fun getLocationOnScreen(out:IntArray){out[0]=0;out[1]=top}
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
class ChattingContent:android.view.ViewGroup()
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
 val list=ViewGroup();list.setPadding(1,2,3,7)
 val wrapper=MMChattingListView(list);wrapper.id="bm6".hashCode();wrapper.add(list);content.add(wrapper)
 scroll.add(content);scroll.add(footer)
 val controller=ChatInputMessageUnderlay()
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
 // A fresh native measurement (keyboard/window resize) takes priority over old expanded height.
 controller.apply(scroll);content.measure(1440,1700);controller.beforeLayout(scroll)
 check(content.measuredHeight==1700)
 content.layout(0,0,1440,1700);footer.layout(0,1700,1440,3204);controller.apply(scroll)
 check(content.height==1871 && footer.top==1700)
 controller.restoreAll();check(content.height==1700)
 println("PASS: native footer position preserved, no negative margin, zero-height blr, repeated native layouts, panel exclusion, multiline, offsets, visibility, padding, restore, native resize")
}
'''
}
with tempfile.TemporaryDirectory() as d:
 d=Path(d)
 for n,s in files.items():(d/n).write_text(s)
 std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
 jar=d/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),*[str(d/n) for n in files],str(root/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/ChatInputMessageUnderlay.kt')],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 subprocess.run(['java','-cp',f'{jar}:{std}','t.mma.hooks.items.backgroundbeauty.MainKt'],check=True)
