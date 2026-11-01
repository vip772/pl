"""Compile actual picker with synthetic Android/hooks; no device or real picker calls."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
files={
'Context.kt':'''package android.content
open class Context {open val applicationContext:Context get()=this}
class Intent(val action:String="") {var type="";var data:Any?=null;fun addCategory(s:String){};fun addFlags(n:Int){};companion object {const val ACTION_OPEN_DOCUMENT="open";const val ACTION_GET_CONTENT="get";const val CATEGORY_OPENABLE="openable";const val FLAG_GRANT_READ_URI_PERMISSION=1;fun createChooser(i:Intent,s:String)=i}}
''',
'Activity.kt':'''package android.app
import android.content.*
open class Activity:Context(){var isFinishing=false;var isDestroyed=false;val codes=mutableListOf<Int>();override val applicationContext=Context();fun startActivityForResult(i:Intent,c:Int){codes.add(c)};open fun onActivityResult(a:Int,b:Int,c:Intent?){};open fun onDestroy(){};companion object {const val RESULT_OK=-1}}
''',
'Os.kt':'''package android.os
class Looper {companion object {val owner=Thread.currentThread();fun getMainLooper():Any=owner;fun myLooper():Any=Thread.currentThread()}}
class Handler(x:Any){val owned=mutableSetOf<Runnable>();fun post(r:Runnable):Boolean {synchronized(all){if(reject)return false;owned.add(r);all.add(r)};return true};fun removeCallbacks(r:Runnable){synchronized(all){owned.remove(r);all.remove(r)}};fun removeCallbacksAndMessages(x:Any?){synchronized(all){all.removeAll(owned);owned.clear()}};companion object {val all=java.util.concurrent.ConcurrentLinkedQueue<Runnable>();var reject=false;fun drain(){while(true)(all.poll()?:return).run()}}}
''',
'Hook.kt':'''package de.robv.android.xposed
open class XC_MethodHook {open fun afterHookedMethod(param:MethodHookParam){};class MethodHookParam(val thisObject:Any,val args:Array<Any?>);class Unhook(val release:()->Unit){fun unhook(){release()}}}
''',
'Registry.kt':'''package t.mma.hooks.core
import java.lang.reflect.Method
import de.robv.android.xposed.XC_MethodHook
class HookRegistry {val entries=linkedMapOf<XC_MethodHook.Unhook,XC_MethodHook>();fun hook(m:Method,c:XC_MethodHook):XC_MethodHook.Unhook {lateinit var h:XC_MethodHook.Unhook;h=XC_MethodHook.Unhook{entries.remove(h);error("native unhook failure")};entries[h]=c;return h};fun unhook(h:XC_MethodHook.Unhook){h.unhook()};companion object {val instance=HookRegistry();fun get()=instance}}
''',
'Store.kt':'''package t.mma.hooks.items.custombottombar
import android.content.Context
object CustomBottomBarIconStore {var gate:java.util.concurrent.CountDownLatch?=null;val entered=java.util.concurrent.LinkedBlockingQueue<String>();val deleted=java.util.concurrent.ConcurrentLinkedQueue<String>();var sequence=java.util.concurrent.atomic.AtomicInteger();fun isSupportedTabKey(s:String)=s=="wechat";fun saveFromUri(c:Context,k:String,u:Any):String {val p="saved-"+sequence.incrementAndGet();entered.add(p);gate?.await();return p};fun delete(c:Context,p:String?):Boolean {if(p!=null)deleted.add(p);return true}}
''',
'Test.kt':'''package t.mma.hooks.items.custombottombar
import android.app.Activity
import android.content.Intent
import android.os.*
import java.util.concurrent.*
import t.mma.hooks.core.HookRegistry
fun pending(s:CustomBottomBarIconPicker.Session):Map<*,*> {val f=s.javaClass.getDeclaredField("pending").apply{isAccessible=true};return f.get(s) as Map<*,*>}
fun worker(s:CustomBottomBarIconPicker.Session):ThreadPoolExecutor {val f=s.javaClass.getDeclaredField("worker").apply{isAccessible=true};return f.get(s) as ThreadPoolExecutor}
fun result(s:CustomBottomBarIconPicker.Session,a:Activity,c:Int){val f=s.javaClass.getDeclaredMethod("onResult",Activity::class.java,Array<Any?>::class.java).apply{isAccessible=true};val intent=Intent();intent.data=Any();f.invoke(s,a,arrayOf<Any?>(c,Activity.RESULT_OK,intent))}
fun done(s:CustomBottomBarIconPicker.Session){val gate=CountDownLatch(1);worker(s).execute{gate.countDown()};check(gate.await(3,TimeUnit.SECONDS))}
fun main(){
 Looper.getMainLooper();var callbacks=0
 val first=CustomBottomBarIconPicker.openSession();val second=CustomBottomBarIconPicker.openSession();val a=Activity()
 first.launch(a,"wechat"){callbacks++};second.launch(a,"wechat"){callbacks++};check(a.codes.distinct().size==2)
 first.close();check(pending(first).isEmpty());check(HookRegistry.get().entries.size==2)
 result(second,a,a.codes.last());done(second);Handler.drain();check(callbacks==1);second.close();check(HookRegistry.get().entries.isEmpty())
 val old=CustomBottomBarIconPicker.openSession();val gate=CountDownLatch(1);CustomBottomBarIconStore.gate=gate;val b=Activity();old.launch(b,"wechat"){callbacks++};result(old,b,b.codes.last());check(CustomBottomBarIconStore.entered.poll(3,TimeUnit.SECONDS)!=null)
 // Drain old queue entries to observe the actual entered operation, not a prior completed decode.
 while(CustomBottomBarIconStore.sequence.get()<2) Thread.sleep(1)
 old.close();gate.countDown();check(worker(old).awaitTermination(3,TimeUnit.SECONDS));Handler.drain();check(callbacks==1&&CustomBottomBarIconStore.deleted.isNotEmpty())
 CustomBottomBarIconStore.gate=null
 val cap=CustomBottomBarIconPicker.openSession();val c=Activity();repeat(20){cap.launch(c,"wechat"){}};check(pending(cap).size==16);cap.close()
 val queued=CustomBottomBarIconPicker.openSession();val d=Activity();queued.launch(d,"wechat"){callbacks++};result(queued,d,d.codes.last());done(queued);val saved="saved-"+CustomBottomBarIconStore.sequence.get();queued.close();Handler.drain();check(callbacks==1&&saved in CustomBottomBarIconStore.deleted)
 val rejected=CustomBottomBarIconPicker.openSession();val e=Activity();Handler.reject=true;rejected.launch(e,"wechat"){callbacks++};result(rejected,e,e.codes.last());done(rejected);check(pending(rejected).isEmpty());Handler.reject=false;rejected.close()
 val sessions=List(8){CustomBottomBarIconPicker.openSession()};check(runCatching{CustomBottomBarIconPicker.openSession()}.isFailure);CustomBottomBarIconPicker.destroy();check(sessions.all{worker(it).isShutdown});check(HookRegistry.get().entries.isEmpty())
 println("PASS actual picker: session isolation; unique codes; exact hook cleanup despite unhook exceptions; 16 pending and 8 session caps; close during entered decode; unpublished file release; rejected delivery; no late callback")
}
'''
}
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,text in files.items():(p/name).write_text(text)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt')),str(r/'app/src/main/java/t/mma/hooks/items/custombottombar/CustomBottomBarIconPicker.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'t.mma.hooks.items.custombottombar.TestKt'],check=True,timeout=30)