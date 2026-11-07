"""Exercise the production passive observer with the verified scene/response field layout."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[3]
libs=list(Path('/tmp/pl-kotlin-libs').glob('*.jar'))
stdlib=next(Path('/tmp/pl-kotlin-libs').glob('kotlin-stdlib-[0-9]*.jar'))
sources={
'Hook.kt':'''package de.robv.android.xposed
open class XC_MethodHook {
 open fun beforeHookedMethod(p:MethodHookParam){};open fun afterHookedMethod(p:MethodHookParam){}
 class Unhook
 class MethodHookParam(val thisObject:Any,val args:Array<Any?>,val failed:Boolean=false){fun hasThrowable()=failed}
}''',
'Registry.kt':'''package t.mma.hooks.core
import de.robv.android.xposed.XC_MethodHook
class HookRegistry {
 val hooks=linkedMapOf<String,XC_MethodHook>()
 fun hook(m:java.lang.reflect.Method,h:XC_MethodHook):XC_MethodHook.Unhook {hooks[m.name]=h;return XC_MethodHook.Unhook()}
 fun unhook(u:XC_MethodHook.Unhook?){hooks.clear()}
 companion object {val instance=HookRegistry();fun get()=instance}
}''',
'Utils.kt':'''package t.mma.utils
object HLog {fun e(s:String,t:Throwable){throw AssertionError(s,t)}}
object KavaReflector {
 fun readField(o:Any,n:String):Any?=o.javaClass.getField(n).get(o)
 fun findCompatibleMethod(c:Class<*>,n:String,vararg args:Any?):java.lang.reflect.Method?=c.methods.firstOrNull{it.name==n && it.parameterCount==args.size}
 fun invokeOrThrow(m:java.lang.reflect.Method,o:Any?,vararg args:Any?):Any?=m.invoke(o,*args)
}''',
'Apis.kt':'''package t.mma.hooks.api.core
object WeChatApis {fun version()=Version()}
class Version {fun versionName()="8.0.78";fun versionCode()=3180L}''',
'Recovery.kt':'''package t.mma.hooks.items.profileid
object JoinTimeRecovery {var account="self";fun accountKey(l:ClassLoader?)=account}''',
'Text.kt':'''package x91
class j1 {companion object {@JvmStatic fun g(v:Any)=v.toString()}}''',
'Scene.kt':'''package qn
class Envelope(@JvmField val a:Any)
class Protocol(@JvmField val a:Envelope,@JvmField val b:ResponseHolder)
class ResponseHolder(@JvmField val a:Response,var ret:Int=0){fun getRetCode()=ret}
class Request(@JvmField val f:List<Member>)
class Member(@JvmField val d:String,@JvmField val e:Int=0)
class Response(@JvmField val i:String,@JvmField val g:Int,@JvmField val h:List<Member>)
class o(selected:List<String>,room:String="new@chatroom",joined:List<Member> = selected.map{Member(it)}) {
 @JvmField val e=Protocol(Envelope(Request(selected.map{Member(it)})),ResponseHolder(Response(room,joined.size,joined)))
 fun doScene(a:Any,b:Any){};fun onGYNetEnd(a:Int,b:Int,c:Int,d:String,e:Any,f:ByteArray){}
}''',
'Test.kt':'''package t.mma.hooks.items.profileid
import t.mma.hooks.core.HookRegistry
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
fun main(){
 val observer=InitialGroupJoinObserver()
 val events=mutableListOf<Triple<String,String,Set<String>>>()
 observer.start(qn.o::class.java.classLoader){a,g,m->events.add(Triple(a,g,m))}
 val hooks=HookRegistry.get().hooks
 fun begin(s:qn.o){hooks.getValue("doScene").beforeHookedMethod(MethodHookParam(s,emptyArray()))}
 fun end(s:qn.o,err:Int=0,failed:Boolean=false){hooks.getValue("onGYNetEnd").afterHookedMethod(MethodHookParam(s,arrayOf(0,0,err,"",Any(),byteArrayOf()),failed))}
 val scene=qn.o(listOf("A","B","C"),joined=listOf(qn.Member("A"),qn.Member("B",3),qn.Member("outsider")))
 begin(scene);end(scene)
 check(events.single()==Triple("self","new@chatroom",setOf("A")))
 end(scene);check(events.size==1)
 val failed=qn.o(listOf("A"));begin(failed);end(failed,1);check(events.size==1)
 val thrown=qn.o(listOf("A"));begin(thrown);end(thrown,failed=true);check(events.size==1)
 val ret=qn.o(listOf("A"));begin(ret);ret.e.b.ret=1;end(ret);check(events.size==1)
 val invalid=qn.o(listOf("A"),"not-room");begin(invalid);end(invalid);check(events.size==1)
 val switched=qn.o(listOf("A"));begin(switched);JoinTimeRecovery.account="other";end(switched);check(events.size==1)
 JoinTimeRecovery.account="self"
 val unobserved=qn.o(listOf("A"));end(unobserved);check(events.size==1)
 val pending=qn.o(listOf("A"));begin(pending)
 val oldEnd=hooks.getValue("onGYNetEnd")
 observer.close();check(hooks.isEmpty())
 oldEnd.afterHookedMethod(MethodHookParam(pending,arrayOf(0,0,0,"",Any(),byteArrayOf())))
 check(events.size==1)
 println("PASS production create observer: selected/success intersection, duplicate, failure, exception, retcode, invalid group, account switch, unobserved scene, teardown")
}'''
}
with tempfile.TemporaryDirectory(prefix='creation-observer-') as td:
 p=Path(td);files=[]
 for name,text in sources.items():
  f=p/name;f.write_text(text);files.append(str(f))
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'out'),*files,str(r/'app/src/main/java/t/mma/hooks/items/profileid/InitialGroupJoinObserver.kt')],check=True,timeout=55)
 subprocess.run(['java','-cp',str(p/'out')+':'+str(stdlib),'t.mma.hooks.items.profileid.TestKt'],check=True,timeout=15)
