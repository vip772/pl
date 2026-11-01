"""Production detached API + script convenience methods against guarded lazy synthetic results.
No native library or host business operations run; real DexKit ABI has separate tests."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
stubs={
'Gate.kt':"""package t.mma.hooks.core
object Probe {var active=false;var reads=0;fun read(){check(active){"native read escaped gate"};reads++}}
object DexInstallScheduler {fun <T> queryDexKit(f:()->T):T{check(!Probe.active);Probe.active=true;try{return f()}finally{Probe.active=false}}}
""",
'Manager.kt':"""package t.mma.dexkit
import org.luckypray.dexkit.DexKitBridge
import t.mma.hooks.core.DexInstallScheduler
class DexKitBridgeManager(val bridge:DexKitBridge){fun <T> withCurrentBridge(f:(DexKitBridge)->T):T=DexInstallScheduler.queryDexKit{f(bridge)};fun rawCompatibilityBridge(s:String)=bridge}
class DexBridgeHolder
""",
'Query.kt':"""package org.luckypray.dexkit.query
import org.luckypray.dexkit.query.matchers.*
class FindClass{var value=ClassMatcher();fun matcher(m:ClassMatcher){value=m}}
class FindMethod{var value=MethodMatcher();fun matcher(m:MethodMatcher){value=m}}
""",
'Match.kt':"""package org.luckypray.dexkit.query.matchers
class ClassMatcher{var strings:List<String> = emptyList();fun usingStrings(s:List<String>){strings=s}}
class MethodMatcher{var strings:List<String> = emptyList();fun usingStrings(s:List<String>){strings=s}}
""",
'Dex.kt':"""package org.luckypray.dexkit
import org.luckypray.dexkit.query.*
import t.mma.hooks.core.Probe
class ClassData{val name:String get(){Probe.read();return "java.lang.String"}}
class MethodData{val className:String get(){Probe.read();return "java.lang.String"};fun getMethodInstance(l:ClassLoader):java.lang.reflect.Method{Probe.read();return String::class.java.getDeclaredMethod("length")}}
class DexKitBridge{
 fun findClass(q:FindClass):Sequence<ClassData>{Probe.read();return sequence{Probe.read();if(q.value.strings.size==1)yield(ClassData())}}
 fun findMethod(q:FindMethod):Sequence<MethodData>{Probe.read();return sequence{Probe.read();if(q.value.strings.size==1)yield(MethodData())}}
}
""",
'Log.kt':"""package t.mma.utils
object HLog {fun e(s:String,t:Throwable){}}
object KavaReflector {fun loadClass(n:String,l:ClassLoader)=l.loadClass(n);fun declaredConstructors(c:Class<*>)=c.declaredConstructors;fun declaredMethods(c:Class<*>)=c.declaredMethods}
""",
'Test.kt':"""package t.mma.hooks.items.script
import t.mma.hooks.core.*
import t.mma.dexkit.*
import org.luckypray.dexkit.DexKitBridge
fun main(){
 val manager=DexKitBridgeManager(DexKitBridge());val loader=ClassLoader.getSystemClassLoader()
 val classes=DexKitQueryApi.findClassesByStrings(manager,loader,"a","b");check(!Probe.active&&classes==listOf(String::class.java))
 val members=DexKitQueryApi.findMembersByStrings(manager,loader,"a","b");check(members.isNotEmpty()&&members.all{it.declaringClass==String::class.java})
 val names=DexKitQueryApi.findClassNamesByStrings(manager,"a","b");check(names==listOf("java.lang.String"))
 val managed=ScriptDexKitBridge(null,DexBridgeHolder(),loader,manager)
 check(managed.findClassList(listOf("a","b"))==classes);check(managed.findMemberList(arrayOf("a","b") as Any).isNotEmpty())
 val legacy=ScriptDexKitBridge(manager.bridge,null,loader)
 check(legacy.findClassList(listOf("a","b"))==classes);check(legacy.findMemberList(listOf("a","b")).isNotEmpty())
 val before=Probe.reads;repeat(3){classes.forEach{it.declaredMethods.size};members.forEach{it.name};names.forEach{it.length}};check(Probe.reads==before&&!Probe.active)
 check(DexKitQueryApi.findClassNamesByStrings(manager," ").isEmpty());check(Probe.reads==before)
 println("PASS production new API and script complete helper chains: guarded lazy Data consumption, multi-anchor fallback, detached Class/Member/String iteration outside gate, blank fast path")
}
"""}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,s in stubs.items():(p/name).write_text(s)
 sources=[str(x) for x in p.glob('*.kt')]+[str(r/'app/src/main/java/t/mma/hooks/core/DexKitQueryApi.kt'),str(r/'app/src/main/java/t/mma/hooks/items/script/ScriptDexKitBridge.kt')]
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar')]+sources,check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.script.TestKt'],check=True,timeout=15)
