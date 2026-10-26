"""Exercise production filter and compile extracted runtime hook with host stubs; no Gradle."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
feature=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
start=feature.index('    private fun installMeEntryVisibility()');end=feature.index('    private fun installFragmentHooks()',start)
main='''package t.mma.hooks.items.backgroundbeauty
fun main(){
 val entries=MeEntryVisibility.Entry.values()
 check(MeEntryVisibility.hiddenKeys{false}.isEmpty())
 for(mask in 0 until 32){
  val requested=MeEntryVisibility.hiddenKeys{key -> entries.withIndex().any{(i,e)->e.key==key && mask and (1 shl i)!=0}}
  val nativeHidden=mutableSetOf("settings_mm_cardpackage","unrelated")
  val original=nativeHidden.toSet()
  val added=MeEntryVisibility.overlay(nativeHidden,requested)
  check(nativeHidden.containsAll(requested))
  nativeHidden.removeAll(added);check(nativeHidden==original)
 }
 val all=MeEntryVisibility.hiddenKeys{true};check(all.size==7)
 check("more_setting" !in all && "settings_mm_wallet" !in all)
 println("PASS 32 switch combinations, both gray branches, host-hidden preservation and restore")
}
'''
# A syntax/type check of the exact runtime methods, with only platform types stubbed.
stub='''package t.mma.hooks.items.backgroundbeauty
import java.util.*
class Bundle
open class XC_MethodHook {
 class MethodHookParam(val thisObject:Any)
 class Unhook
 open fun beforeHookedMethod(param:MethodHookParam){}
 open fun afterHookedMethod(param:MethodHookParam){}
}
object KavaReflector {
 fun loadClass(n:String,l:ClassLoader):Class<*>?=null
 fun findMethod(c:Class<*>,n:String,vararg p:Class<*>):java.lang.reflect.Method?=null
 fun findFieldRecursive(c:Class<*>,n:String):java.lang.reflect.Field?=null
 fun readField(f:java.lang.reflect.Field,o:Any):Any?=null
 fun invokeOrThrow(m:java.lang.reflect.Method,o:Any):Any?=null
}
object HookRegistry {fun get()=this;fun hook(m:java.lang.reflect.Method,c:XC_MethodHook)=XC_MethodHook.Unhook()}
object HLog {fun e(s:String,t:Throwable){}}
class Ctx {fun hostClassLoader()=javaClass.classLoader}
class Prefs {fun getBoolean(k:String,d:Boolean)=d}
class Runtime {
 val context=Ctx(); val prefs=Prefs();val ownedHooks=mutableListOf<XC_MethodHook.Unhook>()
 val meEntryAdapters=Collections.newSetFromMap(WeakHashMap<Any,Boolean>())
 val meEntryOverlays=IdentityHashMap<Any,Set<String>>()
 var destroyed=false;var meEntryFailureLogged=false
 val TAG="test";val ME_FRAGMENT="com.tencent.mm.ui.MoreTabUI"
 fun chatChromeVersionName()="8.0.78"
 fun readNativeField(o:Any,n:String):Any?=null
'''+feature[start:end]+'}\n'
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Main.kt').write_text(main);(p/'Runtime.kt').write_text(stub)
 jar=p/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),str(p/'Main.kt'),str(p/'Runtime.kt'),str(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/MeEntryVisibility.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{std}','t.mma.hooks.items.backgroundbeauty.MainKt'],check=True,timeout=30)