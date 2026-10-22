#!/usr/bin/env python3
"""Execute the production underlay controller against a minimal view model (not an Android UI test)."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
lib=Path('/tmp/pl-kotlin-libs')
files={
'View.kt': '''package android.view
open class View {
 var parent:View?=null; var visibility=0; var height=0; var id=0; var top=0
 var layoutParams:Any?=null
 var background:Any?=null
 var paddingLeft=0;var paddingTop=0;var paddingRight=0;var paddingBottom=0
 val resources=Resources();val context=Context()
 fun setPadding(l:Int,t:Int,r:Int,b:Int){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b}
 open fun <T:View> findViewById(id:Int):T? { @Suppress("UNCHECKED_CAST") return if(this.id==id)this as T else null }
 companion object {const val VISIBLE=0;const val GONE=8}
}
open class ViewGroup:View(){var clipToPadding=true;val children=mutableListOf<View>();fun add(v:View){children.add(v);v.parent=this}
 override fun <T:View> findViewById(id:Int):T? {super.findViewById<T>(id)?.let{return it};for(c in children)c.findViewById<T>(id)?.let{return it};return null}}
class Resources{fun getIdentifier(n:String,t:String,p:String)=n.hashCode().let{if(it==0)1 else it}}
class Context{val packageName="com.tencent.mm"}
''',
'Linear.kt':'''package android.widget
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
 try {
 val scroll=ChattingScrollLayout();val content=ChattingContent();content.id="bki".hashCode()
 val lp=LinearLayout.LayoutParams();lp.bottomMargin=3;content.layoutParams=lp
 val footer=ChatFooter();footer.id="k52".hashCode();footer.height=356
 val strip=ViewGroup();strip.id="blr".hashCode();strip.height=56;footer.add(strip)
 val inputRow=ViewGroup();inputRow.id="ssr".hashCode();inputRow.height=56;strip.add(inputRow)
 val bl8=ViewGroup();bl8.id="bl8".hashCode();strip.add(bl8)
 val panel=com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom();panel.id="bkc".hashCode();panel.height=300;panel.visibility=View.GONE;panel.background="native-panel"
 val appPanel=com.tencent.mm.pluginsdk.ui.chat.AppPanel();appPanel.background="native-app-panel";panel.add(appPanel);footer.add(panel)
 val list=ViewGroup();list.setPadding(1,2,3,7)
 val wrapper=MMChattingListView(list);wrapper.id="bm6".hashCode();wrapper.add(list);content.add(wrapper)
 scroll.add(content);scroll.add(footer)
 val controller=ChatInputMessageUnderlay();controller.apply(scroll)
 check(lp.bottomMargin == -53 && list.paddingBottom==63 && !list.clipToPadding)
 check(panel.background == "native-panel" && appPanel.background == "native-app-panel")
 repeat(20){controller.apply(scroll)};check(lp.bottomMargin == -53 && list.paddingBottom==63)
 // Expanded panel/keyboard height does not replace the verified blr region height.
 panel.visibility=View.VISIBLE;panel.height=700;footer.height=1200;controller.apply(scroll);check(lp.bottomMargin == -53 && list.paddingBottom==63)
 // Multiline input updates blr's measured height, which remains the reserved overlap contract.
 strip.height=104;controller.apply(scroll);check(lp.bottomMargin == -101 && list.paddingBottom==111)
 // Native top/side padding changes are retained; native bottom replacement becomes the new base.
 list.setPadding(5,9,6,11);controller.apply(scroll);check(list.paddingTop==9 && list.paddingBottom==115)
 footer.visibility=View.GONE;controller.apply(scroll);check(lp.bottomMargin==3 && list.clipToPadding)
 footer.visibility=View.VISIBLE;strip.height=48;controller.apply(scroll);controller.restoreAll()
 check(lp.bottomMargin==3 && list.paddingBottom==11 && list.paddingLeft==5 && list.clipToPadding)
 // Reenable starts from clean geometry; restore is idempotent.
 controller.apply(scroll);controller.restoreAll();controller.restoreAll();check(list.paddingBottom==11)
 // Wrong parent structure must not be mutated.
 val wrong=ChattingScrollLayout();wrong.add(wrapper);controller.apply(wrong);check(list.paddingBottom==11)
 println("PASS: production controller: overlap, idempotence, panel exclusion, multiline sizing, native padding changes, hidden footer, restore/re-enable and wrong structure")
 } catch (e: Throwable) { e.printStackTrace(); throw e }
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
