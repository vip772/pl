"""Execute exact production installer helpers with deterministic hook failures; no Gradle."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/moments/SnsAntiRecallFeature.kt').read_text()
a=s.index('    @Synchronized\n    fun install()');z=s.index('    private fun isMomentAntiRecallEnabled()',a)
b=s.index('    private fun hookLegacySnsUserPageSwitch()');e=s.index('    private fun findEnableFlutterSnsPageMethod()',b)
code='''import java.lang.reflect.Method
class Db { fun update(){};fun rawQuery(){} }
class Extra {fun update(){}}
class Legacy {fun enabled()=true}
class Finder {var sqliteDbWrapperClass:Class<*>?=Db::class.java}
class Context {val finder=Finder();fun dexFinder()=finder;fun hostClassLoader()=Db::class.java.classLoader}
object KavaReflector {fun loadClass(n:String,l:ClassLoader):Class<*>?=null;fun declaredMethods(c:Class<*>)=c.declaredMethods.toList()}
open class XC_MethodHook {class MethodHookParam {val thisObject:Any?=null;val args=emptyArray<Any?>();var result:Any?=null};open fun beforeHookedMethod(p:MethodHookParam){}}
object HookRegistry {val counts=mutableMapOf<Method,Int>();val failures=mutableSetOf<Method>();fun get()=this;fun hook(m:Method,c:XC_MethodHook){if(failures.remove(m))error("injected");counts[m]=(counts[m]?:0)+1}}
object HLog {var errors=0;fun e(s:String,t:Throwable){errors++}}
class Runtime {
 val context=Context();val hookedMethods=mutableSetOf<Method>();val internalQuery=ThreadLocal.withInitial{false}
 var legacy:Method?=Legacy::class.java.getDeclaredMethod("enabled")
 fun isSnsWriteMethod(m:Method)=m.name=="update"
 fun isSnsRawQueryMethod(m:Method)=m.name=="rawQuery"
 fun isSnsUpdateMethod(m:Method)=true
 fun handleSnsWrite(o:Any?,a:Array<Any?>,b:Boolean){}
 fun handleSnsQuery(a:Array<Any?>){}
 fun isMomentAntiRecallEnabled()=true
 fun isForceLegacyProfileEnabled()=true
 fun findEnableFlutterSnsPageMethod()=legacy
'''+s[a:z]+s[b:e]+'''}
fun main(){
 val r=Runtime();check(r.install());val first=HookRegistry.counts.toMap();repeat(5){check(r.install())};check(first==HookRegistry.counts)
 r.context.finder.sqliteDbWrapperClass=Extra::class.java;check(r.install());check(HookRegistry.counts.keys.any{it.declaringClass==Extra::class.java})
 HookRegistry.counts.clear();val f=Runtime();val update=Db::class.java.getDeclaredMethod("update");val legacy=f.legacy!!
 HookRegistry.failures.add(update);HookRegistry.failures.add(legacy);check(f.install());check(update !in f.hookedMethods&&legacy !in f.hookedMethods);check(f.install());check(update in f.hookedMethods&&legacy in f.hookedMethods)
 val empty=Runtime();empty.context.finder.sqliteDbWrapperClass=null;empty.legacy=null;check(!empty.install())
 println("PASS existing hooks succeed without reinstall; later wrapper supplements; DB/legacy failures roll back and retry; no target remains failure")
}
'''
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 x=subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],capture_output=True,text=True,timeout=40)
 assert x.returncode==0,x.stderr
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)
