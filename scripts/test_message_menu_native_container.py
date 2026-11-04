"""Execute the COMPLETE production runtime via synthetic Hook/Android containers.
Fixtures model verified 8.0.78 wrapping and 5-per-row binding; not device/R8 validation.
"""
from pathlib import Path
import ast,subprocess,tempfile
r=Path(__file__).resolve().parents[1]
existing=ast.parse((r/'scripts/check_message_menu_grid_sdk.py').read_text())
stubs=ast.literal_eval(next(n.value for n in existing.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stubs' for t in n.targets)))
stubs.update({
'X.kt':'''package de.robv.android.xposed
open class XC_MethodHook {
 open fun beforeHookedMethod(param:MethodHookParam){}
 open fun afterHookedMethod(param:MethodHookParam){}
 class Unhook(val method:java.lang.reflect.Method,val callback:XC_MethodHook){var active=true}
 class MethodHookParam(val thisObject:Any,val args:Array<Any?>){var result:Any?=null;var error:Throwable?=null;private val extra=mutableMapOf<String,Any?>();fun hasThrowable()=error!=null;fun setObjectExtra(k:String,v:Any?){extra[k]=v};fun getObjectExtra(k:String)=extra[k]}
}
''',
'Core.kt':'''package t.mma.hooks.core
import de.robv.android.xposed.XC_MethodHook
class FeatureContext {fun hostContext()=android.content.Context();fun hostClassLoader()=javaClass.classLoader!!}
class HookRegistry {
 val handles=mutableListOf<XC_MethodHook.Unhook>()
 fun hook(m:java.lang.reflect.Method,h:XC_MethodHook)=XC_MethodHook.Unhook(m,h).also{handles+=it}
 fun unhook(h:XC_MethodHook.Unhook){h.active=false}
 fun invoke(o:Any,m:java.lang.reflect.Method,a:Array<Any?>,body:()->Any?):Any? {
  val p=XC_MethodHook.MethodHookParam(o,a);val callbacks=handles.filter{it.active&&it.method==m}
  callbacks.forEach{it.callback.beforeHookedMethod(p)}
  try{p.result=body()}catch(t:Throwable){p.error=t}
  callbacks.asReversed().forEach{it.callback.afterHookedMethod(p)}
  p.error?.let{throw it};return p.result
 }
 companion object {private val instance=HookRegistry();fun get()=instance}
}
''',
'Api.kt':'''package t.mma.hooks.api.message
object SingleMessageMenuLocator {fun menuCreateMethods(c:t.mma.hooks.core.FeatureContext,e:(String,Throwable)->Unit)=listOf(eu5.Creator::class.java.getDeclaredMethod("create",android.view.Menu::class.java))}
''',
'Reflect.kt':'''package t.mma.utils
object HLog {val errors=mutableListOf<String>();fun i(s:String){};fun e(s:String,t:Throwable?=null){errors+=s+":"+t}}
object KavaReflector {
 fun loadClass(n:String,c:ClassLoader):Class<*>?=Class.forName(n,false,c)
 fun findDeclaredField(c:Class<*>,n:String):java.lang.reflect.Field?=c.getDeclaredField(n).also{it.isAccessible=true}
 fun findDeclaredMethod(c:Class<*>,n:String,vararg t:Class<*>):java.lang.reflect.Method?=c.getDeclaredMethod(n,*t).also{it.isAccessible=true}
 fun declaredMethods(c:Class<*>)=c.declaredMethods.toList()
 fun readField(f:java.lang.reflect.Field,o:Any):Any?=f.get(o)
 fun writeField(f:java.lang.reflect.Field,o:Any,v:Any?):Boolean {f.set(o,v);return true}
 fun invokeOrThrow(m:java.lang.reflect.Method,o:Any,vararg a:Any?):Any?=m.invoke(o,*a)
}
''',
'Storage.kt':'''package t.mma.preferences
object HchatStorage {val prefs=android.content.Prefs();fun preferences(c:android.content.Context,n:String)=prefs}
''',
'Context.kt':'''package android.content
class Prefs:SharedPreferences {var columns=6;var rows=3;var enabled=true;override fun getInt(k:String,d:Int)=if(k.endsWith("columns"))columns else rows;override fun getBoolean(k:String,d:Boolean)=enabled}
interface SharedPreferences {fun getInt(k:String,d:Int):Int;fun getBoolean(k:String,d:Boolean):Boolean}
class Context {val packageName="com.tencent.mm";val packageManager=android.content.pm.PackageManager();val resources=android.content.res.Resources()}
''',
'Package.kt':'''package android.content.pm
class Info {val versionName="8.0.78";val longVersionCode=3180L;val versionCode=3180}
class PackageManager {fun getPackageInfo(n:String,f:Int)=Info()}
''',
'Resources.kt':'''package android.content.res
class Metrics {var density=1f;var widthPixels=1080;var heightPixels=1920}
class Resources {val displayMetrics=Metrics()}
''',
'Os.kt':'''package android.os
object Build {object VERSION {const val SDK_INT=36}}
class Looper {companion object{private val main=Looper();fun myLooper()=main;fun getMainLooper()=main}}
''',
'Rect.kt':'''package android.graphics
class Rect {var left=0;var top=0;var right=0;var bottom=0;val isEmpty get()=right<=left||bottom<=top;fun set(l:Int,t:Int,r:Int,b:Int){left=l;top=t;right=r;bottom=b};fun width()=right-left;fun height()=bottom-top}
''',
'View.kt':'''package android.view
import android.content.Context
open class View(val context:Context=Context()) {
 var id=0;var parent:ViewGroup?=null;var visibility=VISIBLE;var paddingLeft=0;var paddingRight=0;var paddingTop=0;var paddingBottom=0
 var layoutParams=ViewGroup.LayoutParams(-2,-2);var measuredWidth=0;var measuredHeight=0
 var nativeWidth=96;var nativeHeight=56;var click:(()->Unit)?=null
 val resources get()=context.resources
 open fun <T:View> findViewById(n:Int):T?=if(id==n)this as T else null
 fun performClick(){click?.invoke()}
 fun setPadding(l:Int,t:Int,r:Int,b:Int){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b}
 fun requestLayout(){}
 fun getWindowVisibleDisplayFrame(r:android.graphics.Rect){r.set(0,0,resources.displayMetrics.widthPixels,resources.displayMetrics.heightPixels)}
 open fun measure(w:Int,h:Int){measuredWidth=if(MeasureSpec.mode(w)==MeasureSpec.EXACTLY)MeasureSpec.size(w) else if(layoutParams.width>0)layoutParams.width else nativeWidth;measuredHeight=if(MeasureSpec.mode(h)==MeasureSpec.EXACTLY)MeasureSpec.size(h) else if(layoutParams.height>0)layoutParams.height else nativeHeight}
 object MeasureSpec {const val UNSPECIFIED=0;const val EXACTLY=0x40000000;const val AT_MOST=-2147483648;fun makeMeasureSpec(s:Int,m:Int)=s or m;fun size(s:Int)=s and 0x3fffffff;fun mode(s:Int)=s and -1073741824}
 companion object {const val VISIBLE=0;const val GONE=8}
}
open class ViewGroup(context:Context=Context()):View(context){
 open class LayoutParams(var width:Int,var height:Int){companion object{const val MATCH_PARENT=-1;const val WRAP_CONTENT=-2}}
 val children=mutableListOf<View>();val childCount get()=children.size
 fun getChildAt(i:Int)=children[i];fun indexOfChild(v:View)=children.indexOf(v)
 fun addView(v:View){addView(v,childCount,v.layoutParams)}
 fun addView(v:View,p:LayoutParams){addView(v,childCount,p)}
 fun addView(v:View,i:Int,p:LayoutParams){check(v.parent==null){"already parented"};children.add(i,v);v.parent=this;v.layoutParams=p}
 fun removeView(v:View){if(children.remove(v))v.parent=null}
 fun removeAllViews(){children.toList().forEach{removeView(it)}}
 override fun <T:View> findViewById(n:Int):T? {super.findViewById<T>(n)?.let{return it};for(v in children)v.findViewById<T>(n)?.let{return it};return null}
}
interface MenuItem {val isVisible:Boolean}
interface Menu {fun size():Int;fun getItem(i:Int):MenuItem}
class Item(val key:Int,override val isVisible:Boolean=true):MenuItem
class Items(n:Int):Menu {val items=(0 until n).map{Item(it)};override fun size()=items.size;override fun getItem(i:Int)=items[i]}
object Gravity {const val LEFT=3;const val TOP=48;const val HORIZONTAL_GRAVITY_MASK=7;const val VERTICAL_GRAVITY_MASK=112}
''',
'Widgets.kt':'''package android.widget
import android.content.Context
import android.view.*
open class LinearLayout(context:Context=Context()):ViewGroup(context){
 var orientation=VERTICAL;var gravity=0
 class LayoutParams:ViewGroup.LayoutParams {constructor(w:Int,h:Int):super(w,h);constructor(p:ViewGroup.LayoutParams):super(p.width,p.height)}
 override fun measure(w:Int,h:Int){
  val visible=children.filter{it.visibility!=View.GONE};visible.forEach{it.measure(0,0)}
  val cw=if(orientation==HORIZONTAL)visible.sumOf{it.measuredWidth} else visible.maxOfOrNull{it.measuredWidth}?:0
  val ch=if(orientation==HORIZONTAL)visible.maxOfOrNull{it.measuredHeight}?:0 else visible.sumOf{it.measuredHeight}
  measuredWidth=if(View.MeasureSpec.mode(w)==View.MeasureSpec.EXACTLY)View.MeasureSpec.size(w) else if(layoutParams.width>0)layoutParams.width else cw+paddingLeft+paddingRight
  measuredHeight=if(layoutParams.height>0)layoutParams.height else ch+paddingTop+paddingBottom
 }
 companion object {const val HORIZONTAL=0;const val VERTICAL=1}
}
open class FrameLayout(context:Context=Context()):ViewGroup(context){class LayoutParams(w:Int,h:Int):ViewGroup.LayoutParams(w,h)}
class ScrollView(context:Context=Context()):FrameLayout(context){var isFillViewport=false;var isHorizontalScrollBarEnabled=false;var isVerticalScrollBarEnabled=false;override fun measure(w:Int,h:Int){children.forEach{it.measure(0,0)};measuredWidth=if(layoutParams.width>0)layoutParams.width else children.maxOfOrNull{it.measuredWidth}?:0;measuredHeight=if(layoutParams.height>0)layoutParams.height else children.maxOfOrNull{it.measuredHeight}?:0}}
open class PopupWindow {
 var contentView:View?=null;var width=500;var height=400
 open fun setContentView(v:View){contentView=v}
 fun showAtLocation(v:View,g:Int,x:Int,y:Int){t.mma.hooks.core.HookRegistry.get().invoke(this,PopupWindow::class.java.getDeclaredMethod("showAtLocation",View::class.java,Integer.TYPE,Integer.TYPE,Integer.TYPE),arrayOf(v,g,x,y)){null}}
}
''',
'Popup.kt':'''package kj5
import android.view.View
import android.widget.*
class e5:FrameLayout()
class f5:PopupWindow(){init{contentView=e5()};override fun setContentView(v:View){t.mma.hooks.core.HookRegistry.get().invoke(this,f5::class.java.getDeclaredMethod("setContentView",View::class.java),arrayOf(v)){val outer=contentView as? e5?:e5();outer.removeAllViews();outer.addView(v,FrameLayout.LayoutParams(-1,-2));contentView=outer;null}}}
interface t4 {fun a(v:View,i:Int,item:android.view.MenuItem)}
''',
'Owner.kt':'''package eu5
import android.view.*
import android.widget.*
import t.mma.hooks.core.HookRegistry
class Creator {fun create(m:Menu){HookRegistry.get().invoke(this,Creator::class.java.getDeclaredMethod("create",Menu::class.java),arrayOf(m)){null}}}
class s0 {
 @JvmField var t:kj5.t4?=null;@JvmField var x:Menu=Items(35);@JvmField var m=kj5.f5();@JvmField var f=View();@JvmField var F=true
 var success=true;var wrappedArgument=false;lateinit var root:LinearLayout;lateinit var rows:List<LinearLayout>;var clicks=mutableListOf<Int>();var originals=emptyList<View>();var originalParams=emptyList<ViewGroup.LayoutParams>()
 fun p(a:Int,b:Int):Boolean=HookRegistry.get().invoke(this,s0::class.java.getDeclaredMethod("p",Integer.TYPE,Integer.TYPE),arrayOf(a,b)){
  Creator().create(x)
  root=LinearLayout();val parent=LinearLayout().apply{orientation=LinearLayout.VERTICAL;setPadding(8,0,8,0)};root.addView(parent)
  rows=listOf(0x7f0a48cf,0x7f0a48d2,0x7f0a48d1).map{key->LinearLayout().apply{id=key;orientation=LinearLayout.HORIZONTAL}}
  rows.forEachIndexed{i,row->parent.addView(row);if(i<2)parent.addView(View().apply{nativeHeight=1})}
  originals=(0 until x.size()).map{i->LinearLayout().apply{nativeHeight=56;orientation=LinearLayout.VERTICAL;addView(View(),LinearLayout.LayoutParams(96,56));click={clicks+=i};t?.a(this,i,x.getItem(i));if(i<15)rows[i/5].addView(this)}}
  originalParams=originals.map{it.layoutParams}
  val passed:View=if(wrappedArgument)FrameLayout().apply{addView(root)} else root
  m.setContentView(passed)
  m.showAtLocation(f,Gravity.LEFT or Gravity.TOP,a,b)
  success
 } as Boolean
}
''',
'Test.kt':'''package t.mma.hooks.items.messagemenuextension
import android.view.*
import android.widget.*
import t.mma.hooks.core.*
import t.mma.preferences.HchatStorage
fun main(){
 val runtime=MessageMenuGridRuntime(FeatureContext());check(runtime.install());check(runtime.install())
 val registry=HookRegistry.get();check(registry.handles.count{it.active&&it.method.name=="setContentView"}==1)
 check(registry.handles.single{it.active&&it.method.name=="setContentView"}.method.declaringClass.name=="kj5.f5")
 val owner=eu5.s0();val original=object:kj5.t4{var count=0;override fun a(v:View,i:Int,item:MenuItem){count++}};owner.t=original
 fun verify(columns:Int,n:Int=35){
  HchatStorage.prefs.columns=columns;owner.x=Items(n);check(owner.p(100,100));check(owner.t===original&&owner.F)
  val outer=owner.m.contentView as kj5.e5;check(outer.children.single()===owner.root)
  val scroll=owner.root.children.single() as ScrollView;val parent=scroll.children.single() as LinearLayout
  val active=parent.children.filterIsInstance<LinearLayout>().filter{it.visibility==View.VISIBLE}
  check(active.size==(n+columns-1)/columns);check(active.first().childCount==minOf(columns,n))
  val items=active.flatMap{it.children};check(items==owner.originals);items.forEach{it.performClick()};check(owner.clicks.takeLast(n)==(0 until n).toList())
  check(scroll.layoutParams.height<=3*56+2);check(owner.m.width==-2&&owner.m.height==-2)
 }
 verify(6);verify(8,41);verify(5,17);verify(7,35);check(original.count==128)
 val savedWidth=owner.m.width;val savedHeight=owner.m.height
 owner.success=false;owner.x=Items(35);check(!owner.p(100,100));check(owner.t===original&&owner.F)
 check(owner.root.children.single() is LinearLayout);check(owner.rows.map{it.childCount}==listOf(5,5,5))
 owner.originals.forEachIndexed{i,v->check(v.layoutParams===owner.originalParams[i]);check(v.parent===if(i<15)owner.rows[i/5] else null)}
 check(owner.m.width==savedWidth&&owner.m.height==savedHeight)
 owner.success=true;owner.wrappedArgument=true;check(owner.p(100,100)) // explicit beforeShow recovery through wrapper
 val wrapped=owner.root.parent as FrameLayout;check(wrapped.parent is kj5.e5);check(owner.root.children.single() is ScrollView)
 owner.wrappedArgument=false;HchatStorage.prefs.enabled=false;check(owner.p(100,100));check(owner.rows.map{it.childCount}==listOf(5,5,5));check(owner.t===original)
 runtime.destroy();runtime.destroy();check(registry.handles.none{it.active})
 println("PASS COMPLETE production menu Hook runtime: concrete f5 entry, e5 wrapper, 5/6/7/8 columns, all-item capture past 15, scroll viewport, original click/index, callback/flag restoration, failed-show rollback, wrapper fallback, disabled native rows, cleanup; synthetic Android only")
}
'''
})
# Avoid a JVM setter collision in the fixture while retaining the Android-facing Kotlin API.
stubs['Widgets.kt']=stubs['Widgets.kt'].replace('var contentView:View?=null','@JvmField var contentView:View?=null')
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in stubs.items():(p/n).write_text(s)
 src=r/'app/src/main/java/t/mma/hooks/items/messagemenuextension'
 cmd=['java','-Xmx320m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar')]
 subprocess.run(cmd+[str(x) for x in p.glob('*.kt')]+[str(src/n) for n in ['MessageMenuExtensionSettings.kt','MessageMenuGridGeometry.kt','MessageMenuGridRuntime.kt']],check=True,timeout=100)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.messagemenuextension.TestKt'],check=True,timeout=20)
 # Regression control: restore the previous base-class Hook; verify this fixture rejects it.
 old=(src/'MessageMenuGridRuntime.kt').read_text().replace('hook(setContent, object : XC_MethodHook()', 'hook(requireNotNull(KavaReflector.findDeclaredMethod(PopupWindow::class.java, "setContentView", View::class.java)), object : XC_MethodHook()')
 old=old.replace('''if (!frame.changed) window.contentView?.let { wrapped ->
                            val row = wrapped.findViewById<View>(ROW_IDS.first())
                            val menuRoot = (row?.parent as? View)?.parent as? View
                            if (menuRoot != null) reflow(frame, menuRoot)
                        }''', 'if (!frame.changed) window.contentView?.let { reflow(frame, it) }')
 (p/'OldRuntime.kt').write_text(old)
 test=(p/'Test.kt').read_text()
 test='\n'.join(line for line in test.splitlines() if 'method.declaringClass.name==' not in line)
 (p/'Test.kt').write_text(test)
 sources=[str(x) for x in p.glob('*.kt')]+[str(src/n) for n in ['MessageMenuExtensionSettings.kt','MessageMenuGridGeometry.kt']]
 subprocess.run(cmd+sources,check=True,timeout=100)
 negative=subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.messagemenuextension.TestKt'],capture_output=True,text=True,timeout=20)
 assert negative.returncode!=0 and 'ClassCastException' in negative.stderr,negative.stderr
 print('PASS negative control: previous base Hook plus wrapper-as-root fallback fails native-container fixture with native rows instead of ScrollView')
