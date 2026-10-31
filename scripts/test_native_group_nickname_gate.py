"""Compile production gate with real DexKit; exercise native-flag transitions on JVM."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
base=r/'app/src/main/java/t/mma'
libs=Path('/tmp/pl-kotlin-libs'); std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
stubs={
'Android.kt':"""package android.view
open class View {var visibility=0;companion object{const val GONE=8}}
""",
'Widget.kt':"""package android.widget
class TextView:android.view.View(){var title=true;var tail=true;fun post(r:()->Unit){r()}}
""",
'Looper.kt':"""package android.os
object Looper{fun myLooper()=1;fun getMainLooper()=1}
""",
'Xposed.kt':"""package de.robv.android.xposed
open class XC_MethodHook{class Unhook;class MethodHookParam(val thisObject:Any?);open fun afterHookedMethod(p:MethodHookParam){}}
""",
'Core.kt':"""package t.mma.hooks.core
class FeatureContext{fun hostClassLoader():ClassLoader=javaClass.classLoader;fun hostContext():android.content.Context=TODO();fun dexKitManager()=Manager()}
class Manager{fun <T> withCurrentBridge(f:(org.luckypray.dexkit.DexKitBridge)->T):T=TODO()}
object HookRegistry{var unhooks=0;fun get()=this;fun hook(m:java.lang.reflect.Method,h:de.robv.android.xposed.XC_MethodHook)=de.robv.android.xposed.XC_MethodHook.Unhook();fun unhook(h:de.robv.android.xposed.XC_MethodHook.Unhook?){if(h!=null)unhooks++}}
""",
'Cache.kt':"""package t.mma.dexkit
object DexMethodCache{fun prefs(c:android.content.Context,n:String):android.content.SharedPreferences=TODO();fun runtimeKey(c:android.content.Context,l:ClassLoader)="runtime";fun load(p:android.content.SharedPreferences,k:String,l:ClassLoader,n:String):java.lang.reflect.Method?=null;fun save(p:android.content.SharedPreferences,k:String,n:String,m:java.lang.reflect.Method?){}}
""",
'Reflector.kt':"""package t.mma.utils
object KavaReflector{fun declaredMethods(c:Class<*>)=c.declaredMethods.toList();fun invoke(m:java.lang.reflect.Method,o:Any,vararg a:Any?):Any?=m.invoke(o,*a);fun readField(f:java.lang.reflect.Field,o:Any):Any?=f.get(o)}
""",
'Layout.kt':"""package t.mma.hooks.items.chatname
object ChatNameDecorationLayout{fun suppressNativeName(v:android.widget.TextView){v.title=false;v.tail=false}}
""",
'Test.kt':"""package t.mma.hooks.items.chatname
import android.widget.TextView
interface RoomApi
class Room(@JvmField var enabled:Boolean):RoomApi
class Chat(val room:Room){fun component(c:Class<*>):RoomApi=room}
fun main(){
 val ctor=NativeGroupNicknameGate::class.java.declaredConstructors.single { it.parameterCount == 3 };ctor.isAccessible=true
 val flag=Room::class.java.getField("enabled");val getter=Chat::class.java.getMethod("component",Class::class.java)
 val gate=ctor.newInstance(flag,getter,listOf(RoomApi::class.java)) as NativeGroupNicknameGate
 val a=Room(true);val b=Room(false);val va=TextView();val vb=TextView()
 va.visibility=8 // blank native nickname while the setting is ON must still be allowed
 check(gate.bind(va,Chat(a),"a@chatroom","member"))
 check(!gate.bind(vb,Chat(b),"b@chatroom","member"));check(vb.visibility==8&&!vb.title&&!vb.tail)
 check(gate.allows(va,"a@chatroom","member"));check(!gate.allows(va,"b@chatroom","member"))
 a.enabled=false;va.title=true;va.tail=true;va.visibility=0
 val refresh=NativeGroupNicknameGate::class.java.getDeclaredMethod("onNativeRefresh",Any::class.java);refresh.isAccessible=true;refresh.invoke(gate,a)
 check(va.visibility==8&&!va.title&&!va.tail);repeat(10000){check(!gate.allows(va))}
 a.enabled=true;check(gate.allows(va));check(!gate.allows(vb))
 check(gate.bind(va,Chat(b),"b@chatroom","newMember")==false) // recycled same row and sender guard
 check(!gate.allows(va,sender="member"));b.enabled=true;check(gate.allows(va,sender="newMember"))
 gate.forget(va);check(!gate.allows(va));check(!gate.bind(va,null,"room","member"))
 println("PASS native OFF hides nickname/title/tail; ON allows blank names; per-room isolation; live refresh; 10000 delayed checks; recycling; sender mismatch; unknown context")
}
"""}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in stubs.items():(p/n).write_text(s)
 cp=f'{std}:/tmp/pl-dexkit.jar:/usr/lib/android-sdk/platforms/android-36/android.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(p/'test.jar'),str(base/'hooks/items/chatname/NativeGroupNicknameGate.kt'),*map(str,p.glob('*.kt'))],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{cp}','t.mma.hooks.items.chatname.TestKt'],check=True,timeout=15)
for file,entry in [('membertitle/MemberTitleRenderer.kt','private fun applyTitle('),('realtail/RealNameTailRenderer.kt','private fun applyTail(tv: TextView, sender: String, rawTail: String)')]:
 s=(base/'hooks/items'/file).read_text();body=s[s.index(entry):]
 assert body.index('nativeNicknameGate?.allows') < body.index('tv.visibility = View.VISIBLE')
 assert 'nativeNicknameGate?.bind(tv, args.getOrNull(1), roomId, sender)' in s
print('PASS both production reveal paths and rebind identity are guarded')
