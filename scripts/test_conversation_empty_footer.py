"""Exercise production footer repair/hooks with host stubs, then compile against Android SDK."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
s=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a=s.index('    private fun repairConversationEmptyFooter');b=s.index('    private fun installRecyclerBinding',a);methods=s[a:b]
fixture='''import android.view.View
import java.lang.reflect.Method
object BackgroundBeautySettings {enum class Slot {WECHAT,CONTACTS};const val KEY_ENABLE="on";const val DEFAULT_ENABLE=false}
class Bitmap(var isRecycled:Boolean=false)
class Loaded(val bitmap:Bitmap)
class State {val loadedBitmaps=mutableMapOf(BackgroundBeautySettings.Slot.WECHAT to Loaded(Bitmap()))}
class Prefs {var enabled=true;fun getBoolean(k:String,d:Boolean)=enabled}
class Param(val thisObject:Any,val result:Any?=null)
object KavaReflector {fun findDeclaredMethod(c:Class<*>,n:String):Method?=try{c.getDeclaredMethod(n)}catch(e:Exception){null}}
class Fixture {
var destroyed=false;var backgroundRenderingEnabled=true;val prefs=Prefs();val owner=Any();val state=State()
val states=mutableMapOf(owner to state);val homeLists=mutableMapOf<View,BackgroundBeautySettings.Slot>()
val applied=mutableListOf<View>();val hooks=mutableMapOf<String,(Param)->Unit>();var reads=0
fun findActivity(v:View)=owner
fun resourceId(n:String)=77
fun readNativeField(v:View,n:String):Any?{reads++;return v.javaClass.getField(n).get(v)}
fun setTransparentSurface(s:State,v:View,slot:BackgroundBeautySettings.Slot){applied.add(v)}
fun hookOnce(m:Method,after:(Param)->Unit){hooks.putIfAbsent(m.name,after)}
'''+methods+'''
fun install(v:View)=installConversationEmptyFooterHooks(v)
fun repair(v:View,f:View?=null)=repairConversationEmptyFooter(v,f)
}
'''
main='''fun main(){
 val f=Fixture();val list=com.tencent.mm.ui.conversation.ConversationListView();val footer=View(77);list.T.parent=footer
 f.homeLists[list]=BackgroundBeautySettings.Slot.WECHAT;f.install(list)
 check(f.hooks.keys==setOf("getEmptyFooter","h"));check(f.applied==listOf(footer,list.T))
 // Native fixed footer grows after filtering; this does not invoke adapter.getView.
 for(color in listOf(0xff000000.toInt(),0xffffffff.toInt()))for(rows in listOf(0,1,4,40)){
  footer.nativeColor=color;footer.height=if(rows==40)0 else 2000-rows*200
  f.applied.clear();f.hooks.getValue("h")(Param(list));check(f.applied==listOf(footer,list.T))
 }
 // A recreated footer is picked up through T, not stale row-cache or list child count.
 val next=View(77);list.T.parent=next;f.applied.clear();f.hooks.getValue("getEmptyFooter")(Param(list,next));check(f.applied==listOf(next,list.T))
 f.applied.clear();f.repair(list,footer);check(f.applied.isEmpty())
 next.id=78;f.repair(list);check(f.applied.isEmpty());next.id=77
 f.prefs.enabled=false;f.repair(list);check(f.applied.isEmpty());f.prefs.enabled=true
 f.state.loadedBitmaps.getValue(BackgroundBeautySettings.Slot.WECHAT).bitmap.isRecycled=true;f.repair(list);check(f.applied.isEmpty())
 f.state.loadedBitmaps.getValue(BackgroundBeautySettings.Slot.WECHAT).bitmap.isRecycled=false
 f.homeLists[list]=BackgroundBeautySettings.Slot.CONTACTS;f.repair(list);check(f.applied.isEmpty())
 f.homeLists[list]=BackgroundBeautySettings.Slot.WECHAT;f.destroyed=true;f.repair(list);check(f.applied.isEmpty())
 println("PASS production footer: dark/light, empty/short/full, resize, recreation, identity, disabled/recycled/other-page/destroyed gates")
}
'''
compiler=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect']
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Fixture.kt').write_text(fixture)
 (p/'View.kt').write_text('package android.view\nopen class View(var id:Int=0){var parent:View?=null;var height=0;var nativeColor=0}')
 (p/'Native.kt').write_text('package com.tencent.mm.ui.conversation\nclass ConversationListView:android.view.View(){@JvmField var T=android.view.View();fun getEmptyFooter():android.view.View=T.parent!!;fun h(){}}')
 (p/'Main.kt').write_text('import android.view.View\n'+main)
 subprocess.run(compiler+['-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt'))],check=True,timeout=60)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'MainKt'],check=True,timeout=20)
 subprocess.run(compiler+['-classpath',str(std)+':/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(p/'Fixture.kt')],check=True,timeout=60)
 print('PASS production repair and hook methods Android SDK compile (host state/registry stubbed)')
assert 'installConversationEmptyFooterHooks(view)' in s[s.index('    private fun installRecyclerBinding'):]
assert 'dispatchDraw' not in methods and 'childCount' not in methods
print('PASS integration: creation/resize hooks, no draw/scroll or subtree traversal')