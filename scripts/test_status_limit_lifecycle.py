"""Compile production status constructor installation and verify missing/reinstall/close paths."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
s=(r/'app/src/main/java/t/mma/hooks/items/statuslimit/StatusTextLimitFeature.kt').read_text();a=s.index('        private fun installOwned');b=s.index('        private fun expandLimit',a)
code='''package t.mma.hooks.core
class Context
open class XC_MethodHook {class Unhook {var active=true};class MethodHookParam(val thisObject:Any);open fun afterHookedMethod(param:MethodHookParam){}}
class Host
object HLog {fun e(s:String){}}
object KavaReflector {var ready=false;fun loadClass(n:String,l:ClassLoader):Class<*>?=if(ready)Host::class.java else null;fun declaredConstructors(c:Class<*>)=c.declaredConstructors.toList()}
object HookRegistry {val handles=mutableListOf<XC_MethodHook.Unhook>();val callbacks=mutableListOf<XC_MethodHook>();fun get()=this
 fun hook(c:java.lang.reflect.Constructor<*>,cb:XC_MethodHook)=XC_MethodHook.Unhook().also{handles+=it;callbacks+=cb}
 fun unhook(h:XC_MethodHook.Unhook){h.active=false}
}
internal object Owner {val TAG="test";val STATUS_ACTIVITY="host";var expanded=0
 fun isEnabled(c:Context)=true;fun expandLimit(o:Any){expanded++}
'''+s[a:b].replace('private fun installOwned','fun installOwned',1)+'''}
fun main(){
 val c=Context();val loader=Host::class.java.classLoader
 val missing=FeatureResourceScope{throw it};Owner.installOwned(c,loader,missing);check(HookRegistry.handles.isEmpty())
 KavaReflector.ready=true;Owner.installOwned(c,loader,missing);check(HookRegistry.handles.single().active)
 val cb=HookRegistry.callbacks.single();cb.afterHookedMethod(XC_MethodHook.MethodHookParam(Host()));check(Owner.expanded==1)
 missing.close();check(!HookRegistry.handles.single().active);cb.afterHookedMethod(XC_MethodHook.MethodHookParam(Host()));check(Owner.expanded==1)
 val fresh=FeatureResourceScope{throw it};Owner.installOwned(c,loader,fresh);check(HookRegistry.handles.last().active);fresh.close()
 println("PASS status constructor missing-class retry, scope release, stale callback and fresh owner reinstall")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)