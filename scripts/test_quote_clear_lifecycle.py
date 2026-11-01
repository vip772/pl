"""Verify production quote registrar partial-failure retry and resource release."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
s=(r/'app/src/main/java/t/mma/hooks/items/quoteclear/QuoteDeleteClearFeature.kt').read_text();a=s.index('    private val resources');b=s.index('    @Synchronized\n    fun install()',a)
code='''package t.mma.hooks.core
import java.lang.reflect.Method
import java.lang.ref.WeakReference
class XC_MethodHook
object HookRegistry {class Handle {var active=true};val handles=mutableListOf<Handle>();var fail=false;fun get()=this
 fun hook(m:Method,c:XC_MethodHook):Handle {if(fail)error("failure");return Handle().also{handles+=it}}
 fun unhook(h:Handle){h.active=false}
}
class Host {fun a(){};fun b(){}}
class Owner {val logger:(String,Throwable?)->Unit={_,_->};val footerFieldCache=mutableMapOf<String,String>();val currentFooterRef=WeakReference<Any>(Any());var supportOnKeyMethod:Method?=null;var quoteClearMethod:Method?=null
'''+s[a:b].replace('private fun hookOwned','fun hookOwned')+'''}
fun main(){
 val a=Host::class.java.getDeclaredMethod("a");val b=Host::class.java.getDeclaredMethod("b");val owner=Owner();val cb=XC_MethodHook()
 owner.hookOwned(a,cb);HookRegistry.fail=true;check(runCatching{owner.hookOwned(b,cb)}.isFailure);HookRegistry.fail=false
 owner.hookOwned(a,cb);owner.hookOwned(b,cb);check(HookRegistry.handles.size==2)
 owner.footerFieldCache["test"]="value";owner.supportOnKeyMethod=a;owner.quoteClearMethod=b;owner.destroy();owner.destroy()
 check(HookRegistry.handles.none{it.active});check(owner.currentFooterRef.get()==null&&owner.footerFieldCache.isEmpty());check(owner.supportOnKeyMethod==null&&owner.quoteClearMethod==null)
 owner.hookOwned(a,cb);check(HookRegistry.handles.size==2);val fresh=Owner();fresh.hookOwned(a,cb);check(HookRegistry.handles.last().active);fresh.destroy()
 println("PASS quote partial failure deduplication, failed-method retry, idempotent release, cache clearing and fresh owner")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)
