"""Production round-avatar ownership/install/callback methods + OwnedHooks, synthetic JVM host.
Locator and bitmap rendering are explicit synthetic boundaries; no Android/R8/device proof.
"""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]; libs=Path('/tmp/pl-kotlin-libs')
src=(r/'app/src/main/java/t/mma/hooks/items/roundavatar/RoundAvatarFeature.kt').read_text()
runtime=src[src.index('private class RoundAvatarRuntime('):src.index('    private fun locateAvatarMembers()')]
owned=(r/'app/src/main/java/t/mma/hooks/core/FeatureOwnership.kt').read_text()
owned=owned[owned.index('private fun reportOwnershipError'):owned.index('/** 本功能独占的主线程投递')]
stubs={
'Core.kt':'''package t.mma.hooks.core
import java.lang.reflect.Member
import de.robv.android.xposed.XC_MethodHook
class FeatureContext { fun hostContext()=Any() }
class HookRegistry {
 val installed=mutableListOf<XC_MethodHook.Unhook>()
 var calls=0; var failAt=0; var beforeHook:(()->Unit)?=null
 fun hook(m:Member,c:XC_MethodHook):XC_MethodHook.Unhook {
  calls++; beforeHook?.invoke(); if(calls==failAt) error("injected installation failure")
  return XC_MethodHook.Unhook(m,c).also { installed.add(it) }
 }
 fun unhook(h:XC_MethodHook.Unhook?) { h?.unhook() }
 fun reset(){installed.clear();calls=0;failAt=0;beforeHook=null}
 companion object { val instance=HookRegistry(); @JvmStatic fun get()=instance }
}
''',
'Xposed.kt':'''package de.robv.android.xposed
open class XC_MethodHook {
 open fun beforeHookedMethod(param:MethodHookParam) {}
 class MethodHookParam(var args:Array<Any?>)
 class Unhook(val method:java.lang.reflect.Member,val callback:XC_MethodHook) {
  var releases=0; var fail=false
  fun unhook(){releases++;if(fail) error("injected unhook failure")}
 }
}
''',
'Notification.kt':'''package android.app
class Notification {
 var largeIcon:android.graphics.Bitmap?=null
 class Builder {
  fun setLargeIcon(b:android.graphics.Bitmap) {}
  fun setLargeIcon(i:android.graphics.drawable.Icon) {}
 }
}
class NotificationManager {
 fun notify(id:Int,n:Notification) {}
 fun notify(tag:String,id:Int,n:Notification) {}
}
''',
'Bitmap.kt':'''package android.graphics
class Bitmap
''',
'Icon.kt':'''package android.graphics.drawable
class Icon { fun getBitmap()=android.graphics.Bitmap()
 companion object { @JvmStatic fun createWithBitmap(b:android.graphics.Bitmap)=Icon() }
}
''',
'Utils.kt':'''package t.mma.utils
object HLog { fun e(s:String,t:Throwable?=null) {} }
object KavaReflector {
 fun findMethod(c:Class<*>,n:String,vararg p:Class<*>):java.lang.reflect.Method?=c.getDeclaredMethod(n,*p)
 fun invokeMethod(o:Any,n:String):Any?=o.javaClass.getMethod(n).invoke(o)
}
''',
'Dex.kt':'''package t.mma.dexkit
object DexMethodCache { fun prefs(c:Any,n:String)=Any() }
''',
}
append='''
    private fun locateAvatarMembers(): AvatarMembers? {
        if (Fixture.failLocate) error("injected locator failure")
        return AvatarMembers(Fixture.legacy,Fixture.constructor,Fixture.modify)
    }
    private fun lastIntParameterIndex(types:Array<Class<*>>)=types.indexOfLast { it==Integer.TYPE }
    private data class AvatarMembers(val legacyLoad:java.lang.reflect.Method,val workerConstructor:java.lang.reflect.Constructor<*>,val workerModify:java.lang.reflect.Method?)
    companion object { const val TAG="round-test"; const val DEFAULT_RADIUS_MASK=4 }
}
private object RoundAvatarSettings {
 var enable=true
 fun enabled(c:Any)=enable
 fun radiusFactor(c:Any)=0.25f
}
private object RoundAvatarBitmapProcessor {
 var rounds=0;var clears=0
 fun clear(){clears++}
 fun round(b:Bitmap,f:Float):Bitmap {rounds++;return b}
}
private class Worker(val name:String,val user:String,val radius:Float,val mask:Int) {
 fun modify(worker:Worker,name:String,user:String,radius:Float,mask:Int) {}
}
private object Fixture {
 var failLocate=false
 @JvmStatic fun load(name:String,user:String,radius:Float,enabled:Boolean) {}
 val legacy=Fixture::class.java.getDeclaredMethod("load",String::class.java,String::class.java,java.lang.Float.TYPE,java.lang.Boolean.TYPE)
 val constructor=Worker::class.java.declaredConstructors.single()
 val modify=Worker::class.java.getDeclaredMethod("modify",Worker::class.java,String::class.java,String::class.java,java.lang.Float.TYPE,Integer.TYPE)
}
private fun radius(cb:XC_MethodHook):Any? {
 val p=XC_MethodHook.MethodHookParam(arrayOf("name","user",0.7f,true))
 cb.beforeHookedMethod(p);return p.args[2]
}
fun main() {
 val registry=HookRegistry.get()
 for (failure in 1..7) {
  registry.reset();Fixture.failLocate=false
  val runtime=RoundAvatarRuntime(FeatureContext())
  registry.failAt=failure
  check(!runtime.installWeChatHooks()) { "failure=$failure not propagated" }
  check(registry.installed.all {it.releases==1}) { "failure=$failure partial hook leaked" }
  val old=registry.installed.toList()
  registry.failAt=0
  check(runtime.installWeChatHooks()) { "failure=$failure retry failed" }
  val active=registry.installed.filter {it.releases==0}
  check(active.size==7) { "failure=$failure retry installed only ${active.size}/7 hooks (notification hooks lost)" }
  old.filter {it.method==Fixture.legacy}.forEach {check(radius(it.callback)==0.7f)}
  check(radius(active.single {it.method==Fixture.legacy}.callback)==0.25f)
  val count=registry.calls;check(runtime.installWeChatHooks());check(registry.calls==count)
  runtime.destroy();runtime.destroy()
  check(active.all {it.releases==1});check(!runtime.installWeChatHooks());check(!runtime.installNotificationHooks())
  check(radius(active.single {it.method==Fixture.legacy}.callback)==0.7f)
 }
 println("PASS 7 installation failure positions: rollback, full notification+WeChat retry, stale callback, idempotent destroy")
 registry.reset();Fixture.failLocate=true
 val exceptional=RoundAvatarRuntime(FeatureContext())
 check(!exceptional.installWeChatHooks());check(registry.installed.all {it.releases==1})
 Fixture.failLocate=false;check(exceptional.installWeChatHooks());exceptional.destroy()
 println("PASS locator exception rolls back notifications and permits retry")
 registry.reset()
 val reentrant=RoundAvatarRuntime(FeatureContext())
 registry.beforeHook={registry.beforeHook=null;reentrant.destroy()}
 check(!reentrant.installWeChatHooks());check(registry.installed.all {it.releases==1})
 println("PASS destroy during registration releases late handle and rejects runtime resurrection")
 registry.reset();val isolate=RoundAvatarRuntime(FeatureContext());check(isolate.installWeChatHooks())
 val handles=registry.installed.toList();handles[3].fail=true
 isolate.destroy();check(handles.all {it.releases==1})
 check(radius(handles.single {it.method==Fixture.legacy}.callback)==0.7f)
 val post=handles.first {it.method.name=="notify"};val n=Notification();n.largeIcon=Bitmap()
 val prior=RoundAvatarBitmapProcessor.rounds
 post.callback.beforeHookedMethod(XC_MethodHook.MethodHookParam(arrayOf(1,n)))
 check(RoundAvatarBitmapProcessor.rounds==prior)
 println("PASS unhook exception does not block remaining resources; stale radius and notification callbacks inert")
 registry.reset();val fresh=RoundAvatarRuntime(FeatureContext());check(fresh.installWeChatHooks());fresh.destroy()
 println("PASS fresh runtime installs after destroyed runtime; synthetic host only")
}
'''
imports='''package t.mma.hooks.items.roundavatar
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.*
import t.mma.utils.*
import t.mma.dexkit.DexMethodCache
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
'''
with tempfile.TemporaryDirectory(prefix='pl-round-owner-') as td:
 t=Path(td)
 for name,body in stubs.items():(t/name).write_text(body)
 (t/'Ownership.kt').write_text('package t.mma.hooks.core'+chr(10)+'import de.robv.android.xposed.XC_MethodHook'+chr(10)+'import java.lang.reflect.Member'+chr(10)+owned)
 (t/'Check.kt').write_text(imports+runtime+append)
 compiler=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler']
 std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
 subprocess.run(compiler+['-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(t/'out'),*map(str,t.glob('*.kt'))],check=True,timeout=100)
 subprocess.run(['java','-cp',str(t/'out')+':'+str(std),'t.mma.hooks.items.roundavatar.CheckKt'],check=True,timeout=20)