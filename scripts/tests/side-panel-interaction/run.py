from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[3];s=(r/'app/src/main/java/t/mma/hooks/items/homesidepanel/HomeSidePanelDrawer.kt').read_text()
a=s.index('        fun bind(view:View)');b=s.index('        cardsBody?.setOnDragListener',a)
code='''class Prefs { fun getFloat(k:String,v:Float)=v }
object SidePanelAppearance {fun cardOpacity(id:String)=id}
class ClipData {companion object {fun newPlainText(a:String,b:String)=ClipData()}}
class View {var alpha=1f;var tag:Any="photo";var click:(()->Unit)?=null;var longClick:(()->Boolean)?=null
 class DragShadowBuilder(v:View)
 fun hasOnClickListeners()=click!=null
 fun setOnLongClickListener(l:()->Boolean){longClick=l}
 fun startDragAndDrop(c:ClipData,s:DragShadowBuilder,v:View,f:Int)=true
 fun requestDisallowInterceptTouchEvent(v:Boolean){}
 fun tap():Boolean {if(click!=null){click!!();return true};return longClick!=null}
}
class ViewGroup:View() {val children=mutableListOf<View>();val childCount get()=children.size;fun getChildAt(i:Int)=children[i]}
class Harness {
 var dragCard:View?=null;val contentScroll:View?=View();val prefs=Prefs()
 fun install(card:View){
'''+s[a:b].replace('android.content.','').replace('View.DragShadowBuilder','View.DragShadowBuilder')+'''}
}
fun main(){
 val harness=Harness();var photos=0;val card=ViewGroup();card.click={photos++};val image=View();val label=View();card.children.addAll(listOf(image,label));harness.install(card)
 check(image.longClick==null && label.longClick==null);if(!image.tap())card.tap();check(photos==1);check(card.longClick!!.invoke());check(harness.dragCard===card)
 val menu=ViewGroup();val row=ViewGroup();var opens=0;row.click={opens++};val icon=View();val text=View();row.children.addAll(listOf(icon,text));menu.children.add(row);harness.install(menu)
 check(icon.longClick==null && text.longClick==null);if(!icon.tap())row.tap();check(opens==1);check(row.longClick!!.invoke());check(harness.dragCard===menu)
 println("PASS: 图片与文字子控件不吞点击，照片和菜单行点击有效，长按仍拖动所属卡片")
}
'''
# The test uses a tiny platform-independent touch/handler model, not Android dispatch instrumentation.
code=code.replace('class View {','open class View {')
lib=Path('/tmp/pl-kotlin-libs');stdlib=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as t:
 p=Path(t);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=40)
 subprocess.run(['java','-cp',f'{p}/test.jar:{stdlib}','TestKt'],check=True,timeout=10)
