"""Exercise exact production note-card resolver and bottom-anchor bodies, no Gradle/device."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIB = Path(os.environ.get('KOTLIN_LIB_DIR', '/tmp/pl-kotlin-libs'))
STD = next(LIB.glob('kotlin-stdlib-[0-9]*.jar'))
SDK = Path(os.environ.get('ANDROID_JAR', '/usr/lib/android-sdk/platforms/android-37.0/android.jar'))
SOURCE = ROOT / 'app/src/main/java/t/mma/hooks/items/hchatextra/HchatExtraFeature.kt'
s = SOURCE.read_text()
def method(name):
    start = s.index('    private fun ' + name + '(')
    end = s.find('\n    private fun ', start + 1)
    return s[start:end] if end != -1 else s[start:]
methods = '\n'.join(method(n) for n in ('noteCardContainer', 'messageContentFields', 'cardBottomAnchor', 'isMeasuredCardBranch'))
assert '24 -> noteCardContainer(holder, row)' in methods
assert 'knownCardHolder || completeRecordCard' in methods
assert '1, 2, 8, 57, 87 -> false' in (ROOT / 'app/src/main/java/t/mma/hooks/items/hchatextra/MessageDetailsCardHierarchy.kt').read_text()
controller = '''package t.mma.hooks.items.hchatextra
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import t.mma.utils.KavaReflector
import t.mma.hooks.api.model.WeChatMessageTypes
class Resolver {
    data class BottomDetailsAnchor(val parent:ViewGroup,val layoutView:View,val alignmentView:View)
    val holderContentFieldCache=ConcurrentHashMap<Class<*>,List<Field>>()
    var lookups=0;var fallbackCalls=0
    val TAG_MESSAGE_DETAILS_VIEW="injected-time"
    fun fieldsRecursive(c:Class<*>):List<Field>{lookups++;return generateSequence(c){it.superclass}.flatMap{it.declaredFields.asSequence()}.onEach{it.isAccessible=true}.toList()}
    fun isAuxiliaryMessageViewField(n:String)=n.lowercase() in setOf("usertv","timetv","avatariv","clickarea")
    fun isViewWithinRoot(v:View,r:ViewGroup):Boolean {var x:View?=v;repeat(32){if(x===r)return true;x=x?.parent as? View};return false}
    fun holderMainContentView(h:Any,r:ViewGroup):View?{fallbackCalls++;return null}
    fun holderCardContainer(h:Any,r:ViewGroup):View?=null
    fun forwardedRecordContainer(h:Any,r:ViewGroup):View?=null
    fun messageContentView(h:Any,r:ViewGroup):View?=null
    fun findHolderTextView(h:Any,n:String,c:Any):View?=null
    fun findHolderAvatarView(h:Any):View?=null
    fun findChattingAvatarView(v:View):View?=null
    val holderTimeFieldCache=Any()
    fun directChildOf(r:ViewGroup,v:View)=MessageDetailsCardHierarchy.contentBranch(r,v)
    fun viewScoreForBottomDetails(v:View)=v.bottom
    fun dp(c:android.content.Context,f:Float)=f.toInt()
''' + methods + '''
    fun note(h:Any,r:ViewGroup)=noteCardContainer(h,r)
    fun anchor(h:Any,r:ViewGroup)=cardBottomAnchor(r,h,49,24)
}
'''
stubs = '''package android.view
open class View {
 val context=android.content.Context();var parent:ViewGroup?=null;var visibility=VISIBLE;var tag:Any?=null
 var width=100;var height=100;var bottom=100;val measuredWidth get()=width;val measuredHeight get()=height
 companion object {const val VISIBLE=0;const val GONE=8}
}
open class ViewGroup:View(){val children=mutableListOf<View>();val childCount get()=children.size
 fun getChildAt(i:Int)=children[i];fun addView(v:View){children+=v;v.parent=this}
 fun removeView(v:View){children-=v;v.parent=null}
}
'''
widgets='''package android.widget
open class FrameLayout:android.view.ViewGroup()
open class RelativeLayout:android.view.ViewGroup()
open class LinearLayout:android.view.ViewGroup(){var orientation=VERTICAL;companion object{const val VERTICAL=1;const val HORIZONTAL=0}}
'''
reflect='''package t.mma.utils
object KavaReflector {fun readField(f:java.lang.reflect.Field,h:Any):Any?=f.get(h)}
'''
fixture='''package t.mma.hooks.items.hchatextra
import android.view.*
import android.widget.*
import com.tencent.mm.ui.chatting.viewitems.mvvmview.ChattingNoteMvvmView
open class Holder(@JvmField var b:ChattingNoteMvvmView?){@JvmField var userTV:View=View();@JvmField var timeTV:View=View()}
class Child(b:ChattingNoteMvvmView?):Holder(b)
class Unknown(@JvmField var body:View)
fun main(){
 for(self in listOf(false,true)){
  val row=RelativeLayout();row.width=945;row.height=600
  val branch=LinearLayout();branch.orientation=if(self) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL;branch.width=700;branch.height=500;branch.bottom=530
  val header=View();header.height=40;header.bottom=40;if(!self) branch.addView(header)
  val frame=FrameLayout();frame.width=700;frame.height=440;frame.bottom=500;branch.addView(frame)
  val note=ChattingNoteMvvmView();note.width=700;note.height=440;note.bottom=440;frame.addView(note);row.addView(branch)
  val holder=Child(note);val resolver=Resolver()
  check(resolver.note(holder,row)===note)
  val a=resolver.anchor(holder,row)!!;check(a.parent===row && a.layoutView===branch && a.alignmentView===branch)
  check(a.layoutView!==header && a.layoutView.bottom>header.bottom)
  val reads=resolver.lookups
  repeat(5000){check(resolver.anchor(holder,row)?.layoutView===branch)}
  check(resolver.lookups==reads && resolver.fallbackCalls==0 && resolver.holderContentFieldCache.size==1)
  note.height=0;note.width=0;branch.height=0;branch.bottom=0
  check(resolver.anchor(holder,row)==null)
  branch.height=500;branch.bottom=530;note.height=440;note.width=700
  check(resolver.anchor(holder,row)?.layoutView===branch)
  note.visibility=View.GONE;check(resolver.anchor(holder,row)==null);note.visibility=View.VISIBLE
  frame.removeView(note);check(resolver.anchor(holder,row)==null);frame.addView(note)
  holder.b=null;check(resolver.anchor(holder,row)==null);holder.b=note
  val foreign=RelativeLayout();check(resolver.anchor(holder,foreign)==null)
  branch.tag=resolver.TAG_MESSAGE_DETAILS_VIEW;check(resolver.anchor(holder,row)==null);branch.tag=null
  val replacement=ChattingNoteMvvmView();frame.removeView(note);frame.addView(replacement);holder.b=replacement
  check(resolver.note(holder,row)===replacement && resolver.anchor(holder,row)?.layoutView===branch)
  val unknown=Unknown(header);check(resolver.anchor(unknown,row)==null)
  println("PASS note card self=$self: full branch below nickname, 5000 warm binds no metadata lookup, zero/late layout, hidden/detached/null/foreign rejection, recycled holder refreshed, no guessed header")
 }
}
'''
with tempfile.TemporaryDirectory(prefix='pl-note-card-time-') as directory:
    d=Path(directory)
    files={'Resolver.kt':controller,'View.kt':stubs,'Widget.kt':widgets,'Context.kt':'package android.content\nclass Context','Reflect.kt':reflect,'Note.kt':'package com.tencent.mm.ui.chatting.viewitems.mvvmview\nclass ChattingNoteMvvmView:android.view.ViewGroup()','Main.kt':fixture}
    for name,text in files.items():(d/name).write_text(text)
    production=[ROOT/'app/src/main/java/t/mma/hooks/items/hchatextra/MessageDetailsCardHierarchy.kt',ROOT/'app/src/main/java/t/mma/hooks/api/model/WeChatMessageTypes.kt']
    compiler=['java','-Xmx256m','-cp',str(LIB/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect']
    subprocess.run([*compiler,'-classpath',str(STD),'-d',str(d/'test.jar'),*map(str,d.glob('*.kt')),*map(str,production)],check=True,timeout=60)
    subprocess.run(['java','-ea','-cp',str(d/'test.jar')+':'+str(STD),'t.mma.hooks.items.hchatextra.MainKt'],check=True,timeout=20)
    subprocess.run([*compiler,'-classpath',str(STD)+':'+str(SDK),'-d',str(d/'sdk.jar'),str(d/'Resolver.kt'),str(d/'Reflect.kt'),*map(str,production)],check=True,timeout=60)
    print('PASS exact production note resolver, cached field helper and card-bottom anchor SDK37 compile; synthetic boundary helpers, NOT full app/R8 build')
