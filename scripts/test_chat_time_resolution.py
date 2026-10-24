"""Execute production time resolver with counted host getters; no Gradle/WeChat."""
from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/chattime/ChatTimeStyleFeature.kt').read_text()
a=s.index('    private fun resolveMessageCreateTime(');b=s.index('    private fun parseLong(',a)
end=s.index('    private fun methodCacheKey(',b)
# Extract production resolution and numeric parsing, excluding runtime cache setup.
methods=s[a:end]
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
files={'Main.kt':'''import java.util.Collections
import java.util.IdentityHashMap
import android.view.View
import t.mma.utils.KavaReflector
class Check {
'''+methods+'''
fun resolve(v:Any?)=resolveMessageCreateTime(v)
}
class Wrapper(val value:Any?)
fun main(){
 val c=Check();val m=com.tencent.mm.storage.Message(123L,9L)
 check(c.resolve(m)==123L && m.reads==1)
 check(c.resolve(Wrapper(m))==123L && m.reads==2)
 check(c.resolve(listOf(null,Wrapper(m)))==123L && m.reads==3)
 val bad=com.tencent.mm.storage.Message(456L,0L)
 check(c.resolve(bad)==null && bad.reads==1)
 check(c.resolve(com.tencent.mm.storage.Message(0L,9L))==null)
 m.time=789L;check(c.resolve(m)==789L && m.reads==4)
 println("PASS: validated time read once per resolution, wrappers/collections, invalid ID/time, mutable message rebound")
}
''','Host.kt':'''package com.tencent.mm.storage
class Message(var time:Long,val id:Long){var reads=0;fun getCreateTime():Long{reads++;return time};fun getMsgId()=id}
''','View.kt':'package android.view\nopen class View\n','Reflect.kt':'''package t.mma.utils
import java.lang.reflect.*
object KavaReflector {
 fun findMethod(c:Class<*>,n:String):Method?=c.declaredMethods.firstOrNull{it.name==n && it.parameterCount==0}
 fun invoke(m:Method?,v:Any):Any?=m?.invoke(v)
 fun declaredFields(c:Class<*>)=c.declaredFields
 fun isStatic(f:Field)=Modifier.isStatic(f.modifiers)
 fun readField(f:Field,v:Any):Any?{f.isAccessible=true;return f.get(v)}
 fun readField(v:Any,n:String):Any?=v.javaClass.declaredFields.firstOrNull{it.name==n}?.let{readField(it,v)}
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,v in files.items():(p/n).write_text(v)
 jar=p/'check.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),*[str(p/n) for n in files]],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{std}','MainKt'],check=True,timeout=15)
