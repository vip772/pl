"""Execute production time resolver with counted host getters; no Gradle/WeChat."""
from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/chattime/ChatTimeStyleFeature.kt').read_text()
a=s.index('    private fun resolveMessageCreateTime(');b=s.index('    private fun parseLong(',a)
end=s.index('    private fun methodCacheKey(',b)
# Compile production resolver and cache declarations with counted host reflection.
methods=s[a:end]
assert 'synchronized(resolutionMetadata) { resolutionMetadata.clear() }' in s[s.index('    fun destroy()'):s.index('    @Synchronized')]
metadata=s[s.index('    private class ResolutionMetadata'):s.index('    private val bindings =')]
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
files={'Main.kt':'''import java.util.Collections
import java.util.IdentityHashMap
import java.lang.reflect.Field
import java.lang.reflect.Method
import android.view.View
import t.mma.utils.KavaReflector
class Check {
'''+metadata+methods+'''
fun resolve(v:Any?)=resolveMessageCreateTime(v)
fun clear(){synchronized(resolutionMetadata){resolutionMetadata.clear()}}
fun size()=resolutionMetadata.size
fun touch(c:Class<*>)=resolutionNestedFields(c)
}
open class Wrapper(var value:Any?)
class Child(value:Any?):Wrapper(value)
class Cycle(var next:Any?=null)
fun main(){
 val c=Check();val m=com.tencent.mm.storage.Message(123L,9L)
 check(c.resolve(m)==123L && m.reads==1)
 check(c.resolve(Wrapper(m))==123L && m.reads==2)
 check(c.resolve(listOf(null,Wrapper(m)))==123L && m.reads==3)
 val bad=com.tencent.mm.storage.Message(456L,0L)
 check(c.resolve(bad)==null && bad.reads==1)
 check(c.resolve(com.tencent.mm.storage.Message(0L,9L))==null)
 m.time=789L;check(c.resolve(m)==789L && m.reads==4)
 val priority=com.tencent.mm.storage.Priority()
 check(c.resolve(priority)==111L)
 priority.methodTime=0L;check(c.resolve(priority)==222L)
 priority.field_createTime=0L;check(c.resolve(priority)==333L)
 priority.methodId=0L;check(c.resolve(priority)==333L)
 val w=Child(m); check(c.resolve(w)==789L)
 val misses=KavaReflector.lookups
 repeat(3000){m.time=1000L+it;check(c.resolve(w)==m.time)}
 check(KavaReflector.lookups==misses)
 w.value=com.tencent.mm.storage.FieldChild();check(c.resolve(w)==345L)
 val f=w.value as com.tencent.mm.storage.FieldChild
 f.field_createTime=678L;check(c.resolve(w)==678L)
 val empty=com.tencent.mm.storage.Empty();check(c.resolve(empty)==null)
 val negative=KavaReflector.lookups;repeat(100){check(c.resolve(empty)==null)}
 check(KavaReflector.lookups==negative)
 val cyc=Cycle();cyc.next=cyc;check(c.resolve(cyc)==null)
 check(c.resolve(Wrapper(Wrapper(Wrapper(Wrapper(Wrapper(m))))))==null)
 val bytes=com.tencent.mm.storage.Message::class.java.getResourceAsStream("Message.class")!!.readBytes()
 fun isolated():Class<*> = object:ClassLoader(null){fun make()=defineClass("com.tencent.mm.storage.Message",bytes,0,bytes.size)}.make()
 val one=isolated();val two=isolated();check(one!==two)
 check(c.resolve(one.getConstructor(Long::class.javaPrimitiveType,Long::class.javaPrimitiveType).newInstance(22L,1L))==22L)
 check(c.resolve(two.getConstructor(Long::class.javaPrimitiveType,Long::class.javaPrimitiveType).newInstance(33L,2L))==33L)
 repeat(140){c.touch(isolated())};check(c.size()==128)
 c.clear();check(c.size()==0);check(c.resolve(m)==m.time)
 println("PASS: 3000 warm resolutions no lookup, inherited fields, missing members, cycles/depth, loader isolation, LRU bound/clear; validated time read once per resolution, wrappers/collections, invalid ID/time, mutable message rebound")
}
''','Host.kt':'''package com.tencent.mm.storage
class Message(var time:Long,val id:Long){var reads=0;fun getCreateTime():Long{reads++;return time};fun getMsgId()=id}
open class FieldBase{@JvmField var field_createTime=345L;@JvmField var field_msgId=7L}
class FieldChild:FieldBase()
class Empty
class Priority {
 var methodTime=111L;var methodId=9L
 @JvmField var field_createTime=222L
 @JvmField var createTime=333L
 @JvmField var field_msgId=7L
 fun getCreateTime()=methodTime
 fun getMsgId()=methodId
}
''','View.kt':'package android.view\nopen class View\n','Reflect.kt':'''package t.mma.utils
import java.lang.reflect.*
object KavaReflector {
 var lookups=0
 fun findDeclaredField(c:Class<*>,n:String):Field?{lookups++;return c.declaredFields.firstOrNull{it.name==n}}
 fun findMethod(c:Class<*>,n:String):Method?{lookups++;return c.declaredMethods.firstOrNull{it.name==n && it.parameterCount==0}?:c.methods.firstOrNull{it.name==n && it.parameterCount==0}}
 fun invoke(m:Method?,v:Any):Any?=m?.invoke(v)
 fun declaredFields(c:Class<*>):Array<Field>{lookups++;return c.declaredFields}
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
