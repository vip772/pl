"""Run production auto-merge runtime/owner with explicit JVM host fixtures; no APK/device."""
from pathlib import Path
import subprocess, tempfile
repo = Path(__file__).resolve().parents[1]
base = repo / 'app/src/main/java/t/mma'
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
# Registration contracts supplement (not replace) the runtime behavior checks below.
assert '.register(AutoMergeFeature())' in (base/'hooks/core/FeatureRegistry.kt').read_text()
ui = (base/'ui/miuix/MiuixSettingsPagePart02.kt').read_text()
assert '                AutoMergeFeature.ID,' in ui and 'AutoMergeFeature.ID -> listOf(' in ui
assert 'AutoMergeFeature.ID -> AutoMergeMiuixPage(context, provider, onBack)' in (base/'ui/miuix/MiuixSettingsPagePart03.kt').read_text()
assert '"auto_merge" to Toggle("Hchat_auto_merge_config", "auto_merge_enable")' in (base/'ui/FeatureToggleRegistry.kt').read_text()
provider = (base/'hooks/items/automerge/AutoMergeSettingsProvider.kt').read_text()
assert 'CATEGORY_PRACTICAL,\n    true' in provider
sources = {
'Context.kt': '''package android.content
open class Context {
 val packageName="com.tencent.mm"
 val packageManager=android.content.pm.PackageManager()
}
class Intent {
 var source=3;var toUser:String?="room@chatroom"
 fun getIntExtra(k:String,d:Int)=source
 fun getStringExtra(k:String)=toUser
}
interface SharedPreferences {
 fun getBoolean(k:String,d:Boolean):Boolean
 fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun interface OnSharedPreferenceChangeListener {fun onSharedPreferenceChanged(p:SharedPreferences,k:String?)}
}
class Prefs:SharedPreferences {
 var value:Boolean?=null
 val listeners=mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
 override fun getBoolean(k:String,d:Boolean)=value?:d
 override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners.add(l)}
 override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners.remove(l)}
 fun set(v:Boolean){value=v;listeners.toList().forEach{it.onSharedPreferenceChanged(this,"auto_merge_enable")}}
}
''',
'Pm.kt': '''package android.content.pm
class PackageInfo {var versionName:String?="8.0.78"}
class PackageManager {val info=PackageInfo();fun getPackageInfo(n:String,f:Int)=info}
''',
'Activity.kt': '''package android.app
open class Activity:android.content.Context() {
 var intent:android.content.Intent?=android.content.Intent()
 var isFinishing=false;var isDestroyed=false
}
''',
'Looper.kt': '''package android.os
class Looper {companion object {val main=Looper();var mainThread=true;fun myLooper()=if(mainThread) main else null;fun getMainLooper()=main}}
''',
'View.kt': '''package android.view
open class View {
 var visibility=0;var isEnabled=true;var clicks=0;var action:(()->Unit)?=null
 fun performClick():Boolean {val a=action?:return false;clicks++;a();return true}
 companion object {const val VISIBLE=0;const val GONE=8}
}
''',
'CheckBox.kt':'package android.widget\nclass CheckBox:android.view.View() {var isChecked=false}\n',
'Xposed.kt': '''package de.robv.android.xposed
open class XC_MethodHook {
 class MethodHookParam(val thisObject:Any){fun hasThrowable()=false}
 protected open fun afterHookedMethod(p:MethodHookParam){}
 fun fire(a:Any)=afterHookedMethod(MethodHookParam(a))
}
''',
'Core.kt': '''package t.mma.hooks.core
import java.lang.reflect.Method
import de.robv.android.xposed.XC_MethodHook
class FeatureContext(val host:android.content.Context=android.content.Context()) {
 fun hostContext()=host
 fun hostClassLoader()=javaClass.classLoader
}
class Handle(val m:Method)
class HookRegistry {
 val hooks=linkedMapOf<Method,XC_MethodHook>()
 fun hook(m:Method,c:XC_MethodHook):Handle {check(!hooks.containsKey(m));hooks[m]=c;return Handle(m)}
 fun unhook(h:Handle){hooks.remove(h.m)}
 fun fire(o:Any){hooks.entries.firstOrNull{it.key.declaringClass==o.javaClass}?.value?.fire(o)}
 companion object {val instance=HookRegistry();fun get()=instance}
}
abstract class BaseFeature {
 abstract fun featureId():String
 abstract fun name():String
 protected open fun onFeatureInit(c:FeatureContext){}
 protected abstract fun onFeatureInstall(c:FeatureContext)
 protected open fun onFeatureDestroy(c:FeatureContext){}
 protected fun registerSettingsProvider(p:Any){}
 fun start(c:FeatureContext){onFeatureInit(c);onFeatureInstall(c)}
 fun stop(c:FeatureContext){onFeatureDestroy(c)}
}
''',
'Util.kt': '''package t.mma.utils
import java.lang.reflect.Field
import java.lang.reflect.Method
object HLog {val errors=mutableListOf<String>();fun e(s:String,t:Throwable){errors.add(s+":"+t.message)}}
object KavaReflector {
 fun loadClass(n:String,c:ClassLoader):Class<*>?=runCatching{Class.forName(n,false,c)}.getOrNull()
 fun findDeclaredMethod(c:Class<*>,n:String):Method?=runCatching{c.getDeclaredMethod(n).apply{isAccessible=true}}.getOrNull()
 fun findMethodRecursive(c:Class<*>,n:String):Method?=runCatching{c.getMethod(n).apply{isAccessible=true}}.getOrNull()
 fun findDeclaredField(c:Class<*>,n:String):Field?=runCatching{c.getDeclaredField(n).apply{isAccessible=true}}.getOrNull()
 fun readField(f:Field?,o:Any?):Any?=f?.get(o)
 fun invokeOrThrow(m:Method?,o:Any?):Any?=m!!.invoke(o)
}
''',
'Storage.kt': '''package t.mma.preferences
object HchatStorage {val prefs=android.content.Prefs();fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=prefs}
''',
'Ui.kt': '''package t.mma.ui
interface FeatureSettingsProvider {companion object {const val CATEGORY_PRACTICAL="practical"}}
open class SimpleFeatureSettingsProvider(val id:String,val title:String,val subtitle:String,val category:String,val newFeature:Boolean)
''',
'Media.kt': '''package com.tencent.mm.plugin.gallery.model
class GalleryItem {open class MediaItem(val kind:Int,val e:String) {fun getType()=kind}}
''',
'Adapter.kt': '''package l93
class b0 {val g=arrayListOf<com.tencent.mm.plugin.gallery.model.GalleryItem.MediaItem>()}
class `c0$$f`(val d:com.tencent.mm.plugin.gallery.ui.AlbumPreviewUI):Runnable {
 override fun run(){t.mma.hooks.core.HookRegistry.get().fire(this)}
}
class `q3$$k`(val d:com.tencent.mm.plugin.gallery.ui.ImagePreviewUI):Runnable {
 override fun run(){t.mma.hooks.core.HookRegistry.get().fire(this)}
}
''',
'Pages.kt': '''package com.tencent.mm.plugin.gallery.ui
class AlbumPreviewUI:android.app.Activity() {
 val m=l93.b0();val j2=android.view.View();val k2=android.widget.CheckBox();var i2=false
 init {j2.action={i2=!i2;k2.isChecked=i2}}
}
class ImagePreviewUI:android.app.Activity() {
 val n=arrayListOf<com.tencent.mm.plugin.gallery.model.GalleryItem.MediaItem>()
 val m=arrayListOf<String>();val U1=android.view.View();val V1=android.widget.CheckBox();var T1=false
 init {U1.action={T1=!T1;V1.isChecked=T1}}
}
''',
'Main.kt': '''package t.mma.hooks.items.automerge
import android.content.Prefs
import com.tencent.mm.plugin.gallery.ui.AlbumPreviewUI
import com.tencent.mm.plugin.gallery.ui.ImagePreviewUI
import com.tencent.mm.plugin.gallery.model.GalleryItem.MediaItem
import t.mma.hooks.core.*
fun main() {
 val ctx=FeatureContext();val feature=AutoMergeFeature();val prefs=t.mma.preferences.HchatStorage.prefs
 val hooks=HookRegistry.get();feature.start(ctx)
 check(hooks.hooks.isEmpty() && prefs.listeners.size==1)
 check(!AutoMergeSettings.DEFAULT_ENABLE)
 prefs.set(true);check(hooks.hooks.size==2)
 val album=AlbumPreviewUI();val task=l93.`c0$$f`(album)
 fun select(vararg kinds:Int) {album.m.g.clear();kinds.forEachIndexed{i,k->album.m.g.add(MediaItem(k,"p$i"))};album.i2=false;album.k2.isChecked=false;task.run()}
 select(1);check(!album.i2);select(1,1);check(!album.i2)
 select(1,2);check(!album.i2)
 select(1,1,1);check(album.i2 && album.k2.isChecked && album.j2.clicks==1)
 task.run();check(album.j2.clicks==1)
 album.j2.performClick();check(!album.i2 && !album.k2.isChecked) // no click hook forcing it back
 select(1,1,1,1);check(album.i2)
 select(1,1,1,2);check(album.i2)
 select(1,1,2);check(album.i2)
 select(1,2,2);check(album.i2)
 select(2,2,2);check(album.i2)
 select(0,1,2);check(!album.i2)
 album.j2.visibility=8;select(1,1,1);check(!album.i2);album.j2.visibility=0
 album.intent!!.source=4;select(1,1,1);check(!album.i2);album.intent!!.source=3
 album.intent!!.toUser="";select(1,1,1);check(!album.i2);album.intent!!.toUser="room"
 album.isDestroyed=true;select(1,1,1);check(!album.i2);album.isDestroyed=false
 android.os.Looper.mainThread=false;select(1,1,1);check(!album.i2);android.os.Looper.mainThread=true
 val preview=ImagePreviewUI();val previewTask=l93.`q3$$k`(preview)
 preview.n.addAll(listOf(MediaItem(1,"a"),MediaItem(1,"b"),MediaItem(1,"c"),MediaItem(2,"v")))
 preview.m.addAll(listOf("a","v"));previewTask.run();check(!preview.T1)
 preview.m.add("b");previewTask.run();check(preview.T1 && preview.V1.isChecked && preview.U1.clicks==1)
 preview.T1=false;preview.V1.isChecked=false;preview.m.remove("v");previewTask.run();check(!preview.T1)
 preview.m.add("c");previewTask.run();check(preview.T1 && preview.V1.isChecked && preview.U1.clicks==2)
 previewTask.run();check(preview.U1.clicks==2)
 prefs.set(false);check(hooks.hooks.isEmpty());select(1,1,1);check(!album.i2)
 repeat(20){prefs.set(true);check(hooks.hooks.size==2);prefs.set(false);check(hooks.hooks.isEmpty())}
 prefs.set(true);feature.stop(ctx);check(hooks.hooks.isEmpty() && prefs.listeners.isEmpty())
 ctx.host.packageManager.info.versionName="8.0.77"
 val unsupported=AutoMergeFeature();unsupported.start(ctx);check(hooks.hooks.isEmpty());unsupported.stop(ctx)
 check(t.mma.utils.HLog.errors.size==1)
 println("PASS production runtime: media threshold 1/2/3/4, 2-photo+1-video, 1-photo+2-video, pure video, unknown types, selected preview paths, native sending state, idempotence, manual uncheck, business/visibility/thread/destroy guards, default-off, 20 enable/disable cycles, destroy cleanup and unverified version rejection")
}
'''
}
production = list((base/'hooks/items/automerge').glob('*.kt')) + [base/'hooks/core'/n for n in ['PreferenceRuntimeOwner.kt','FeatureResourceScope.kt']]
with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    for name, text in sources.items(): (p/name).write_text(text)
    jar = p/'test.jar'
    subprocess.run(['java','-Xmx384m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(jar),*map(str,p.glob('*.kt')),*map(str,production)],check=True,timeout=60)
    subprocess.run(['java','-cp',f'{jar}:{stdlib}','t.mma.hooks.items.automerge.MainKt'],check=True,timeout=20)
print('PASS registration, chat/new feature provider, search, enabled aggregation and route source contracts; UI rendering not verified')
