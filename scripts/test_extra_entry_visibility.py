"""Compile actual extra visibility runtime against host stubs; no Gradle/APK/device."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
lib = Path('/tmp/pl-kotlin-libs')
std = next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
sources = {
'Context.kt': '''package android.content
open class Context
interface SharedPreferences {
 fun getBoolean(k:String,d:Boolean):Boolean
 fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun interface OnSharedPreferenceChangeListener { fun changed(p:SharedPreferences,k:String?) }
}
''',
'Os.kt': '''package android.os
class Looper { companion object { val main=Looper(); fun getMainLooper()=main; fun myLooper()=main } }
class Handler(l:Looper) {
 fun post(r:Runnable):Boolean {r.run();return true}
 fun removeCallbacks(r:Runnable){}
 fun removeCallbacksAndMessages(t:Any?){}
}
''',
'Util.kt': 'package android.util\ninterface AttributeSet\n',
'View.kt': '''package android.view
open class View {
 var visibility=0;var tag:Any?=null
 companion object {const val GONE=8}
}
open class ViewGroup:View(){val children=mutableListOf<View>();val childCount get()=children.size;fun getChildAt(i:Int)=children[i]}
class Window { val decorView=ViewGroup() }
class Activity { val window=Window() }
''',
'Xposed.kt': '''package de.robv.android.xposed
import java.lang.reflect.Member
open class XC_MethodHook {
 open fun beforeHookedMethod(param:MethodHookParam){}
 open fun afterHookedMethod(param:MethodHookParam){}
 class MethodHookParam(val thisObject:Any,val method:Member,val args:Array<Any?>) {
  var result:Any?=null;var failed=false;fun hasThrowable()=failed
 }
 class Unhook(val member:Member,val callback:XC_MethodHook){var active=true}
}
''',
'Core.kt': '''package t.mma.hooks.core
import java.lang.reflect.Member
import de.robv.android.xposed.XC_MethodHook
class FeatureContext {fun hostContext()=android.content.Context();fun hostClassLoader()=javaClass.classLoader}
object HookRegistry {
 val handles=mutableListOf<XC_MethodHook.Unhook>();var calls=0;var failAt=0
 fun get()=this
 fun hook(m:Member,c:XC_MethodHook):XC_MethodHook.Unhook {
  calls++;if(calls==failAt)error("install failure")
  return XC_MethodHook.Unhook(m,c).also{handles.add(it)}
 }
 fun unhook(h:XC_MethodHook.Unhook){h.active=false;handles.remove(h)}
 fun fire(o:Any,n:String,vararg args:Any?,body:()->Any?):Any? {
  val method=o.javaClass.methods.first{it.name==n && it.parameterCount==args.size}
  val p=XC_MethodHook.MethodHookParam(o,method,arrayOf(*args))
  val hs=handles.filter{it.active && it.member==method}.toList()
  hs.forEach{it.callback.beforeHookedMethod(p)}
  p.result=body()
  hs.asReversed().forEach{it.callback.afterHookedMethod(p)}
  return p.result
 }
}
''',
'Apis.kt': '''package t.mma.hooks.api.core
object WeChatApis {
 var version="8.0.78"
 fun version()=this;fun versionName()=version
 fun currentActivity()=this
 fun currentActivity():android.view.Activity?=null
}
'''.replace(' fun currentActivity():android.view.Activity?=null',''),
'Storage.kt': '''package t.mma.preferences
import android.content.*
class Prefs:SharedPreferences {
 val values=mutableMapOf<String,Boolean>();val listeners=mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
 var failRegistration=false
 override fun getBoolean(k:String,d:Boolean)=values[k]?:d
 override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners.add(l);if(failRegistration)error("retained then throws")}
 override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners.remove(l)}
 fun set(k:String,v:Boolean){values[k]=v;listeners.toList().forEach{it.changed(this,k)}}
}
object HchatStorage {var prefs=Prefs();fun preferences(c:Context,n:String)=prefs}
''',
'Utils.kt': '''package t.mma.utils
import java.lang.reflect.*
object HLog { val errors=mutableListOf<String>();fun e(s:String,t:Throwable){errors.add(s)} }
object KavaReflector {
 fun loadClass(n:String,l:ClassLoader):Class<*>?=when(n){
 "com.tencent.mm.pluginsdk.ui.chat.ChatFooter"->fake.Footer::class.java
 "com.tencent.mm.ui.conversation.banner.l0"->fake.Banner::class.java
 "com.tencent.mm.ui.conversation.MainUI"->fake.MainUI::class.java
 else->null }
 fun findFieldRecursive(c:Class<*>,n:String):Field?=runCatching{c.getField(n)}.getOrNull()
 fun findMethod(c:Class<*>,n:String,vararg p:Class<*>):Method?=runCatching{c.getMethod(n,*p)}.getOrNull()
 fun readField(f:Field?,o:Any?):Any?=f?.get(o)
 fun readField(o:Any,n:String):Any?=runCatching{o.javaClass.getField(n).get(o)}.getOrNull()
 fun invokeOrThrow(m:Method?,o:Any,vararg a:Any?):Any?=m?.invoke(o,*a)
}
''',
'Fake.kt': '''package fake
import android.view.*
class Footer:ViewGroup() {
 @JvmField var q=View();var mode=1
 fun Z0(c:android.content.Context,a:android.util.AttributeSet){}
 fun M2(i:Int){}
 fun m0(){}
 fun onAttachedToWindow(){}
 fun getMode()=mode
}
class Banner {
 @JvmField var n=View();@JvmField var p=View();@JvmField var f=View();@JvmField var h=View()
 fun i()=true;fun k(v:Int){}
}
class Helper {var calls=0;fun h(){calls++}}
class MainUI {@JvmField var x=Helper();fun onResume(){}}
''',
'Settings.kt': '''package t.mma.hooks.items.backgroundbeauty
object BackgroundBeautySettings {const val PREFS_NAME="Hchat_background_beauty_config"}
''',
'Test.kt': '''package t.mma.hooks.items.backgroundbeauty
import t.mma.preferences.*
import t.mma.hooks.core.*
import t.mma.hooks.api.core.WeChatApis
import android.view.View
fun main(){
 for(mask in 0..3){
  HchatStorage.prefs=Prefs();val p=HchatStorage.prefs
  p.values[ExtraEntryVisibility.VOICE]=mask and 1 != 0
  p.values[ExtraEntryVisibility.COMPUTER]=mask and 2 != 0
  val runtime=ExtraEntryVisibilityRuntime(FeatureContext());runtime.install()
  check(HookRegistry.handles.size==listOf(0,4,3,7)[mask])
  runtime.destroy();runtime.destroy();check(HookRegistry.handles.isEmpty() && p.listeners.isEmpty())
 }
 println("PASS four independent switch combinations and idempotent release")
 HchatStorage.prefs=Prefs();val p=HchatStorage.prefs
 val runtime=ExtraEntryVisibilityRuntime(FeatureContext());runtime.install()
 p.set(ExtraEntryVisibility.VOICE,true)
 val footer=fake.Footer()
 HookRegistry.fire(footer,"onAttachedToWindow"){null};check(footer.q.visibility==8)
 HookRegistry.fire(footer,"M2",0x7f08052d){footer.mode=2;null};check(footer.q.visibility==0)
 HookRegistry.fire(footer,"M2",ExtraEntryVisibility.VOICE_DRAWABLE){footer.mode=1;null};check(footer.q.visibility==8)
 HookRegistry.fire(footer,"m0"){footer.q.visibility=8;null}
 p.set(ExtraEntryVisibility.VOICE,false);check(footer.q.visibility==8 && HookRegistry.handles.isEmpty())
 footer.q.visibility=4;p.set(ExtraEntryVisibility.VOICE,true)
 HookRegistry.fire(footer,"onAttachedToWindow"){null};check(footer.q.visibility==8)
 p.set(ExtraEntryVisibility.VOICE,false);check(footer.q.visibility==4)
 println("PASS microphone hidden, keyboard retained, native GONE/INVISIBLE restored")
 p.set(ExtraEntryVisibility.COMPUTER,true)
 val banner=fake.Banner();banner.p.tag=0x7f11028b
 check(HookRegistry.fire(banner,"i"){banner.n.visibility=0;true}==false)
 check(banner.n.visibility==8 && banner.f.visibility==8 && banner.h.visibility==8)
 // Native i() calls k(): nested callbacks preserve the host snapshot.
 check(HookRegistry.fire(banner,"i") {
  HookRegistry.fire(banner,"k",0){banner.n.visibility=0;null};true
 }==false)
 p.set(ExtraEntryVisibility.COMPUTER,false)
 check(banner.n.visibility==0 && banner.f.visibility==0 && banner.h.visibility==0)
 p.set(ExtraEntryVisibility.COMPUTER,true)
 HookRegistry.fire(banner,"i") {
  HookRegistry.fire(banner,"k",8){banner.n.visibility=8;null};false
 }
 p.set(ExtraEntryVisibility.COMPUTER,false);check(banner.n.visibility==8)
 p.set(ExtraEntryVisibility.COMPUTER,true)
 banner.p.tag=0x7f1109dc
 check(HookRegistry.fire(banner,"i"){banner.n.visibility=0;true}==true)
 check(banner.n.visibility==0 && banner.h.visibility==0)
 banner.p.tag=0x7f110289
 HookRegistry.fire(banner,"k",0){banner.n.visibility=0;null};check(banner.n.visibility==8)
 val main=fake.MainUI();HookRegistry.fire(main,"onResume"){null};check(main.x.calls==1)
 p.set(ExtraEntryVisibility.COMPUTER,false);check(banner.n.visibility==0 && HookRegistry.handles.isEmpty() && main.x.calls==2)
 for(tag in listOf(null,0x7f110050,0x7f1109dc,0x7f110c45,"computer")) check(!ExtraEntryVisibility.isComputerIcon(tag))
 runtime.destroy();check(p.listeners.isEmpty())
 println("PASS computer tags, tablet passthrough, nested i/k snapshots, resume and shared-header restore")
 HchatStorage.prefs=Prefs();HchatStorage.prefs.values[ExtraEntryVisibility.VOICE]=true
 HookRegistry.failAt=HookRegistry.calls+2
 val failed=ExtraEntryVisibilityRuntime(FeatureContext());failed.install();check(HookRegistry.handles.isEmpty())
 HookRegistry.failAt=0;HchatStorage.prefs.set(ExtraEntryVisibility.VOICE,true);check(HookRegistry.handles.size==4)
 failed.destroy();check(HookRegistry.handles.isEmpty())
 HchatStorage.prefs=Prefs().also{it.failRegistration=true}
 ExtraEntryVisibilityRuntime(FeatureContext()).install();check(HchatStorage.prefs.listeners.isEmpty())
 WeChatApis.version="other";HchatStorage.prefs=Prefs();HchatStorage.prefs.values[ExtraEntryVisibility.VOICE]=true
 val other=ExtraEntryVisibilityRuntime(FeatureContext());other.install();check(HookRegistry.handles.isEmpty());other.destroy()
 println("PASS partial install rollback/retry, retained-listener failure and unsupported host")
}
'''
}
# Chained public API has two distinct receiver types.
sources['Apis.kt'] = '''package t.mma.hooks.api.core
object WeChatApis {
 var version="8.0.78"
 fun version()=this;fun versionName()=version
 fun currentActivity()=Current()
 class Current {fun currentActivity():android.view.Activity?=null}
}
'''
# Kotlin property accessor must not clash with the host's explicit getMode method.
sources['Fake.kt'] = sources['Fake.kt'].replace('var mode=1','@JvmField var mode=1')
with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    for name, text in sources.items():
        (p/name).write_text(text)
    jar = p/'test.jar'
    production = root/'app/src/main/java/t/mma/hooks/items/backgroundbeauty'
    subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect',
        '-classpath',str(std),'-d',str(jar),*[str(p/n) for n in sources],
        str(production/'ExtraEntryVisibility.kt'),str(production/'ExtraEntryVisibilityRuntime.kt')],check=True,timeout=60)
    subprocess.run(['java','-cp',f'{jar}:{std}','t.mma.hooks.items.backgroundbeauty.TestKt'],check=True,timeout=30)
