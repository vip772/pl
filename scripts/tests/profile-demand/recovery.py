"""Run the production recovery object against a deterministic protocol/queue fixture."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[3]
libs=list(Path('/tmp/pl-kotlin-libs').glob('*.jar'))
stdlib=next(p for p in libs if p.name.startswith('kotlin-stdlib-') and p.name[14:15].isdigit())
sources={
'Clock.kt':'''package android.os
object SystemClock { var now=0L; fun elapsedRealtime()=now }
''',
'Hook.kt':'''package de.robv.android.xposed
open class XC_MethodHook {
 open fun beforeHookedMethod(p:MethodHookParam) {}
 class MethodHookParam(val thisObject:Any,val args:Array<Any?>) {var result:Any?=Unit}
 class Unhook
}
''',
'Registry.kt':'''package t.mma.hooks.core
object HookRegistry {
 var callback:de.robv.android.xposed.XC_MethodHook?=null
 fun get()=this
 fun hook(m:java.lang.reflect.Method,c:de.robv.android.xposed.XC_MethodHook):de.robv.android.xposed.XC_MethodHook.Unhook {callback=c;return de.robv.android.xposed.XC_MethodHook.Unhook()}
 fun unhook(u:de.robv.android.xposed.XC_MethodHook.Unhook?) {if(u!=null)callback=null}
}
''',
'Reflect.kt':'''package t.mma.utils
object HLog {fun e(s:String,t:Throwable){throw AssertionError(s,t)}}
object KavaReflector {
 fun readField(o:Any,n:String):Any?=o.javaClass.getField(n).get(o)
 fun writeField(o:Any,n:String,v:Any?):Boolean {o.javaClass.getField(n).set(o,v);return true}
 fun findCompatibleMethod(c:Class<*>,n:String,a:Array<out Any?>)=c.methods.firstOrNull{it.name==n && it.parameterCount==a.size}
 fun invokeOrThrow(m:java.lang.reflect.Method,o:Any?,vararg a:Any?)=m.invoke(o,*a)
 fun findDeclaredMethod(c:Class<*>,n:String,vararg a:Class<*>)=c.getDeclaredMethod(n,*a)
 fun findConstructor(c:Class<*>,vararg a:Class<*>)=c.getConstructor(*a)
}
''',
'Api.kt':'''package t.mma.hooks.api.core
object WeChatApis {var supported=true;fun version()=Version()}
class Version {fun versionCode()=3180L;fun versionName()=if(WeChatApis.supported) "8.0.78" else "8.0.77"}
''',
'Account.kt':'''package b41
object y1 {var account="account-A";@JvmStatic fun u()=account}
''',
'Network.kt':'''package com.tencent.mm.network
class y0
''',
'Scene.kt':'''package qn
class Box(@JvmField val a:Any)
class Body {@JvmField var e=42}
class Rows {@JvmField var e:Collection<Row> = listOf(Row("member",1700000000))}
class Row(@JvmField val d:String,@JvmField val v:Int)
class Response {@JvmField val f=Rows()}
class Rpc {@JvmField val a=Box(Body());@JvmField val b=Box(Response())}
class End {var calls=0;fun onSceneEnd(t:Int,c:Int,s:String?,w:w){calls++}}
class w(val group:String,val version:Int) {
 @JvmField val d=Rpc();@JvmField val e=End();var canceled=false
 fun onGYNetEnd(i:Int,t:Int,c:Int,s:String?,r:com.tencent.mm.network.y0?,b:ByteArray?) {}
}
''',
'Queue.kt':'''package gp0
object j1 {@JvmField val b=Queue();@JvmStatic fun q()=this}
class Queue {
 @Volatile var calls=0;var cancelCalls=0;var hold=false;@Volatile var last:qn.w?=null
 fun g(s:qn.w):Boolean {
  check((s.d.a.a as qn.Body).e==0);calls++;last=s
  if(!hold)deliver(s)
  return true
 }
 fun d(s:qn.w){s.canceled=true;cancelCalls++}
 fun deliver(s:qn.w) {
  val p=de.robv.android.xposed.XC_MethodHook.MethodHookParam(s,arrayOf(0,0,0,"",null,null))
  t.mma.hooks.core.HookRegistry.callback!!.beforeHookedMethod(p)
  check(p.result==null);check(s.e.calls==1)
 }
}
''',
'Test.kt':'''package t.mma.hooks.items.profileid
fun main(){
 val loader=JoinTimeRecovery.javaClass.classLoader;val q=gp0.j1.b
 check(JoinTimeRecovery.read(loader,"g@chatroom").isEmpty());check(q.calls==0)
 JoinTimeRecovery.open()
 t.mma.hooks.api.core.WeChatApis.supported=false
 check(JoinTimeRecovery.read(loader,"g@chatroom").isEmpty());check(q.calls==0)
 t.mma.hooks.api.core.WeChatApis.supported=true
 check(JoinTimeRecovery.read(loader,"g@chatroom")["member"]==1700000000000L)
 check(t.mma.hooks.core.HookRegistry.callback==null)
 check(JoinTimeRecovery.read(loader,"g@chatroom").size==1);check(q.calls==1)
 // A complete zero response cannot erase a previously retained positive value.
 q.hold=true;android.os.SystemClock.now=300001
 val worker=Thread {check(JoinTimeRecovery.read(loader,"g@chatroom")["member"]==1700000000000L)}
 worker.start();while(q.calls<2)Thread.yield()
 val scene=q.last!!;(scene.d.b.a as qn.Response).f.e=listOf(qn.Row("member",0))
 q.deliver(scene);worker.join(2000);check(!worker.isAlive)
 b41.y1.account="account-B";q.hold=false
 check(JoinTimeRecovery.read(loader,"g@chatroom").size==1);check(q.calls==3)
 q.hold=true
 val closing=Thread {check(JoinTimeRecovery.read(loader,"other@chatroom").isEmpty())}
 closing.start();while(q.calls<4)Thread.yield()
 JoinTimeRecovery.close();closing.join(2000)
 check(!closing.isAlive);check(q.last!!.canceled);check(q.cancelCalls==1)
 check(t.mma.hooks.core.HookRegistry.callback==null)
 check(JoinTimeRecovery.read(loader,"closed@chatroom").isEmpty());check(q.calls==4)
 JoinTimeRecovery.open()
 check(JoinTimeRecovery.read(loader,"timeout@chatroom").isEmpty())
 check(q.last!!.canceled);check(q.cancelCalls==2)
 check(t.mma.hooks.core.HookRegistry.callback==null)
 JoinTimeRecovery.close()
 println("PASS recovery production: version gate, disabled no request, wire version zero, positive merge, cooldown, exact-scene interception, account isolation, close cancels and wakes worker, hook release")
}
'''
}
with tempfile.TemporaryDirectory() as td:
 p=Path(td);files=[]
 for name,text in sources.items():
  f=p/name;f.write_text(text);files.append(str(f))
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'test.jar'),*files,str(r/'app/src/main/java/t/mma/hooks/items/profileid/JoinTimeRecovery.kt')]
 subprocess.run(cmd,check=True,timeout=55)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(stdlib),'t.mma.hooks.items.profileid.TestKt'],check=True,timeout=20)
