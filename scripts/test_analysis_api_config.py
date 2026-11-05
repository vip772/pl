"""Execute production config dialog and store against explicit JVM UI/network fixtures; no APK/device."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
b=r/'app/src/main/java/t/mma/hooks/items/conversationanalysis'
report=(b/'ConversationAnalysisReportDialog.kt').read_text()
start=report.index('    private fun showModelPicker(')
end=report.index('    private fun showAi(activity:',start)
body=report[start:end]
assert 'profileBar' not in body and '"▾"' not in body
assert 'heightPixels *' not in body and 'dp(activity, 240)' not in body
assert 'showContentDialog(activity, root, .86f)' in body
assert 'showContentDialog(activity, root, .92f)' in body
ai=(b/'ConversationAnalysisAi.kt').read_text()
assert 'fun saveProfile(profile: ConversationAiProfile) = configStore.saveAndActivate(profile)' in ai
assert 'private fun config() = configStore.activeProfile()' in ai
assert 'conn.connectTimeout = 10000; conn.readTimeout = 10000' in ai
ui='''package t.mma.hooks.items.conversationanalysis
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
object DialogProbe {
 fun open(a:Activity)=showAiConfig(a)
 private fun dp(a:Activity,n:Int)=n
 private fun pageColor(a:Activity)=0
 private fun textColor(a:Activity)=0
 private fun isDark(a:Activity)=false
 private fun cardColor(a:Activity)=0
 private fun rounded(c:Int,r:Int)=Any()
 private fun strokeRounded(c:Int,s:Int,r:Int,w:Int)=Any()
 private fun marginParams(a:Activity,w:Int,h:Int,l:Int,t:Int,rr:Int,bt:Int)=LinearLayout.LayoutParams(w,h)
 private fun label(a:Activity,s:String,size:Float,c:Int,b:Boolean)=TextView(a).apply{text=s}
 private fun divider(a:Activity)=View(a)
 private fun actionButton(a:Activity,s:String,c:Int,action:()->Unit)=TextView(a).apply{text=s;setOnClickListener{action()}}
 private fun showContentDialog(a:Activity,content:View,width:Float)=Dialog(a).apply{root=content;show()}
'''+body+'\n}'
sources={
'Context.kt':'''package android.content
open class Context {
 val resources=Resources()
 val applicationContext:Context get()=this
}
class Resources {val displayMetrics=Metrics()}
class Metrics {val density=1f}
interface SharedPreferences {
 fun getInt(k:String,d:Int):Int
 fun getString(k:String,d:String?):String?
 fun contains(k:String):Boolean
 fun edit():Editor
 interface Editor {fun putString(k:String,v:String):Editor;fun putInt(k:String,v:Int):Editor;fun apply()}
}
class Prefs:SharedPreferences {
 val values=mutableMapOf<String,Any>();var applies=0
 override fun getInt(k:String,d:Int)=values[k] as? Int?:d
 override fun getString(k:String,d:String?)=values[k] as? String?:d
 override fun contains(k:String)=k in values
 override fun edit()=object:SharedPreferences.Editor {
  val pending=mutableMapOf<String,Any>()
  override fun putString(k:String,v:String)=apply{pending[k]=v}
  override fun putInt(k:String,v:Int)=apply{pending[k]=v}
  override fun apply(){values.putAll(pending);applies++}
 }
}
''',
'Activity.kt':'''package android.app
open class Activity:android.content.Context() {var isFinishing=false;var isDestroyed=false}
class Dialog(val activity:Activity) {
 lateinit var root:android.view.View
 var isShowing=false
 var onDismiss:(()->Unit)?=null
 fun show(){isShowing=true;all.add(this)}
 fun dismiss(){if(!isShowing)return;isShowing=false;onDismiss?.invoke()}
 fun setOnDismissListener(f:(Any?)->Unit){onDismiss={f(null)}}
 companion object {val all=mutableListOf<Dialog>()}
}
''',
'Handler.kt':'''package android.os
class Looper {companion object {fun getMainLooper()=Looper()}}
class Handler(l:Looper) {
 fun postDelayed(f:()->Unit,n:Long){pending.add(f)}
 fun removeCallbacksAndMessages(o:Any?){pending.clear()}
 companion object {val pending=mutableListOf<()->Unit>()}
}
''',
'View.kt':'''package android.view
import android.content.Context
open class View(val context:Context) {
 var isEnabled=true;var isFocusable=false;var contentDescription:CharSequence="";var background:Any?=null
 var minHeight=0;var width=52;var height=52
 val resources get()=context.resources
 var click:((View)->Unit)?=null
 fun setOnClickListener(f:(View)->Unit){click=f}
 fun performClick(){if(isEnabled)click?.invoke(this)}
 fun setPadding(l:Int,t:Int,r:Int,b:Int){}
 fun invalidate(){}
 protected open fun onDraw(c:android.graphics.Canvas){}
}
open class ViewGroup(c:Context):View(c)
object Gravity {const val CENTER_VERTICAL=16;const val CENTER=17}
''',
'Widgets.kt':'''package android.widget
import android.content.Context
import android.view.*
open class TextView(c:Context):View(c) {
 var text:CharSequence="";var hint:CharSequence="";var gravity=0
 fun setTextColor(c:Int){};fun setHintTextColor(c:Int){}
}
class EditText(c:Context):TextView(c) {fun setText(s:String){text=s}}
class LinearLayout(c:Context):ViewGroup(c) {
 var orientation=0;var gravity=0
 val children=mutableListOf<View>()
 fun addView(v:View,p:LayoutParams){children.add(v)}
 fun addView(v:View){children.add(v)}
 class LayoutParams(val w:Int,val h:Int,val weight:Float=0f){fun setMargins(a:Int,b:Int,c:Int,d:Int){}}
 companion object {const val VERTICAL=1}
}
class MenuItem(val itemId:Int,val title:String){var isCheckable=false;var isChecked=false}
class Menu {
 val items=mutableListOf<MenuItem>()
 fun add(g:Int,id:Int,order:Int,title:String)=MenuItem(id,title).also{items.add(it)}
 fun setGroupCheckable(g:Int,c:Boolean,e:Boolean){}
}
class PopupMenu(c:Context,v:View) {
 val menu=Menu();var listener:((MenuItem)->Boolean)?=null;var showing=false
 fun setOnMenuItemClickListener(f:(MenuItem)->Boolean){listener=f}
 fun show(){showing=true;last=this}
 fun dismiss(){showing=false}
 fun select(id:Int){listener?.invoke(menu.items.first{it.itemId==id});dismiss()}
 companion object {var last:PopupMenu?=null}
}
class Toast {fun show(){};companion object {const val LENGTH_SHORT=0;val messages=mutableListOf<String>();fun makeText(c:Context,s:String,d:Int)=Toast().also{messages.add(s)}}}
''',
'Graphics.kt':'''package android.graphics
object Color {const val WHITE=-1}
class Paint(f:Int) {var color=0;var alpha=255;var style=Style.FILL;enum class Style{FILL};companion object {const val ANTI_ALIAS_FLAG=1}}
class Path {fun reset(){};fun moveTo(x:Float,y:Float){};fun lineTo(x:Float,y:Float){};fun close(){}}
class Canvas {fun drawPath(p:Path,paint:Paint){}}
''',
'Storage.kt':'''package t.mma.preferences
object HchatStorage {val prefs=android.content.Prefs();fun preferences(c:android.content.Context,n:String)=prefs}
''',
'Ai.kt':'''package t.mma.hooks.items.conversationanalysis
internal class ConversationAnalysisAi(c:android.content.Context) {
 val store=ConversationAnalysisAiConfigStore(c);var closed=false
 var requests=0;var address="";var key="";var path=""
 var modelCallback:((List<String>,String?)->Unit)?=null
 var testCallback:((Boolean,String)->Unit)?=null
 init {last=this}
 fun profiles()=(1..2).map{store.profile(it)}
 fun fetchModels(a:String,k:String,f:(List<String>,String?)->Unit){requests++;address=a;key=k;modelCallback=f}
 fun testConnection(a:String,p:String,k:String,f:(Boolean,String)->Unit){requests++;address=a;path=p;key=k;testCallback=f}
 fun saveProfile(p:ConversationAiProfile)=store.saveAndActivate(p)
 fun close(){closed=true}
 companion object {lateinit var last:ConversationAnalysisAi}
}
object ConversationAnalysisSettings {const val PREFS_NAME="config"}
''',
'Ui.kt':ui,
'Main.kt':'''package t.mma.hooks.items.conversationanalysis
import android.app.*
import android.view.*
import android.widget.*
import t.mma.preferences.HchatStorage
fun all(v:View):List<View> = listOf(v)+(if(v is LinearLayout)v.children.flatMap(::all) else emptyList())
fun current()=Dialog.all.last{it.isShowing}
fun button(d:Dialog,s:String)=all(d.root).filterIsInstance<TextView>().first{it.text.toString()==s}
fun fields(d:Dialog)=all(d.root).filterIsInstance<EditText>()
fun dropdown(d:Dialog)=all(d.root).first{it.contentDescription.startsWith("选择API方案")}
fun main() {
 val activity=Activity();val store=ConversationAnalysisAiConfigStore(activity)
 check(store.sampleLimit()==1500)
 store.setSampling("128k",0);check(store.sampleLimit()==1500)
 store.setSampling("2m",50000);check(store.sampleLimit()==1500)
 store.setSampling("128k",250);check(store.sampleLimit()==250)
 store.save(ConversationAiProfile(1,"first","address-one","/one","fake-one","model-one"))
 store.save(ConversationAiProfile(2,"second","address-two","/two","fake-two","model-two"))
 check(store.activeProfileId()==1)
 val before=HchatStorage.prefs.applies
 store.saveAndActivate(store.profile(2));check(store.activeProfileId()==2 && HchatStorage.prefs.applies==before+1)
 store.setActiveProfile(1)
 DialogProbe.open(activity);var d=current();val ai=ConversationAnalysisAi.last
 check(dropdown(d).contentDescription.contains("方案1"))
 check(all(d.root).filterIsInstance<TextView>().none{it.text=="方案1" || it.text=="方案2"})
 fields(d)[1].setText("draft-one")
 dropdown(d).performClick();check(PopupMenu.last!!.menu.items.first().isChecked);PopupMenu.last!!.select(2)
 check(fields(d)[1].text=="address-two" && dropdown(d).contentDescription.contains("方案2"))
 dropdown(d).performClick();PopupMenu.last!!.select(1);check(fields(d)[1].text=="draft-one")
 dropdown(d).performClick();PopupMenu.last!!.select(2)
 val fetch=button(d,"获取模型");fetch.performClick()
 check(fetch.text=="获取中…" && !fetch.isEnabled && !dropdown(d).isEnabled && !button(d,"保存").isEnabled)
 fetch.performClick();check(ai.requests==1 && ai.address=="address-two" && ai.key=="fake-two")
 ai.modelCallback!!(emptyList(),"timed out")
 check(fetch.text=="获取模型" && fetch.isEnabled && Toast.messages.last().contains("timed out"))
 fetch.performClick();ai.modelCallback!!(emptyList(),null)
 check(fetch.isEnabled && Toast.messages.last().contains("返回为空"))
 fetch.performClick();ai.modelCallback!!(listOf("new-model","other-model"),null)
 val picker=current();check(picker!==d && fetch.text=="获取模型")
 button(picker,"new-model").performClick();check(fields(d)[4].text=="new-model" && !picker.isShowing)
 val test=button(d,"测试连接");test.performClick();check(test.text=="测试中…" && !fetch.isEnabled)
 check(ai.address=="address-two" && ai.path=="/two" && ai.key=="fake-two")
 ai.testCallback!!(true,"ok");check(test.text=="连接成功" && fetch.isEnabled)
 button(d,"保存").performClick()
 check(!d.isShowing && ai.closed && store.activeProfileId()==2 && store.activeProfile().model=="new-model")
 check(store.profile(1).address=="address-one") // inactive draft never written
 DialogProbe.open(activity);d=current();check(fields(d)[4].text=="new-model" && dropdown(d).contentDescription.contains("方案2"))
 dropdown(d).performClick();PopupMenu.last!!.select(1);fields(d)[4].setText("discard")
 button(d,"取消").performClick();check(store.activeProfileId()==2 && store.profile(1).model=="model-one")
 DialogProbe.open(activity);d=current();val lateAi=ConversationAnalysisAi.last
 button(d,"获取模型").performClick();button(d,"取消").performClick()
 val dialogs=Dialog.all.size;lateAi.modelCallback!!(listOf("stale"),null)
 check(Dialog.all.size==dialogs && lateAi.closed && android.os.Handler.pending.isEmpty())
 println("PASS production dialog/store: single dropdown and checked active slot, two independent drafts, cancel/reopen, atomic save+activation, immediate loading and duplicate blocking, error/empty recovery, selected model, test current profile, late callback and dismiss cleanup")
}
'''
}
libs=Path('/tmp/pl-kotlin-libs');stdlib=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in sources.items():(p/n).write_text(s)
 jar=p/'test.jar'
 subprocess.run(['java','-Xmx384m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(jar),*map(str,p.glob('*.kt')),str(b/'ConversationAnalysisAiConfig.kt'),str(b/'ConversationAnalysisInput.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{stdlib}','t.mma.hooks.items.conversationanalysis.MainKt'],check=True,timeout=20)
print('PASS compact model/config helper contracts; Android sizing/rendering and live API excluded')