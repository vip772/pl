#!/usr/bin/env python3
"""JVM checks for production window observer and account-scoped recall store; no APK."""
from pathlib import Path
import subprocess,tempfile
repo=Path(__file__).resolve().parents[3]
libs=Path('/tmp/pl-kotlin-libs')
if not libs.exists():
    raise SystemExit('Prepare /tmp/pl-kotlin-libs with Kotlin compiler/stdlib dependencies first')
std=next(libs.glob('kotlin-stdlib-*.jar'))
stubs={
'View.kt':'''package android.view
class View {
 var isAttachedToWindow=true
 var viewTreeObserver=ViewTreeObserver()
 interface OnAttachStateChangeListener { fun onViewAttachedToWindow(view:View); fun onViewDetachedFromWindow(view:View) }
 val attaches=linkedSetOf<OnAttachStateChangeListener>()
 val queued=mutableListOf<Runnable>();val delayed=mutableListOf<Runnable>()
 fun addOnAttachStateChangeListener(l:OnAttachStateChangeListener){attaches.add(l)}
 fun removeOnAttachStateChangeListener(l:OnAttachStateChangeListener){attaches.remove(l)}
 fun postOnAnimation(r:Runnable){queued.add(r)}
 fun postDelayed(r:Runnable,d:Long){delayed.add(r)}
 fun removeCallbacks(r:Runnable){queued.removeAll{it===r};delayed.removeAll{it===r}}
 fun tick(){val old=queued.toList();queued.clear();old.forEach{it.run()}}
 fun delays(){val old=delayed.toList();delayed.clear();old.forEach{it.run()}}
 fun detach(){isAttachedToWindow=false;attaches.toList().forEach{it.onViewDetachedFromWindow(this)}}
 fun attach(){isAttachedToWindow=true;attaches.toList().forEach{it.onViewAttachedToWindow(this)}}
}
class ViewTreeObserver {
 fun interface OnPreDrawListener { fun onPreDraw():Boolean }
 fun interface OnGlobalLayoutListener { fun onGlobalLayout() }
 val globals=linkedSetOf<OnGlobalLayoutListener>()
 fun addOnGlobalLayoutListener(l:OnGlobalLayoutListener){globals.add(l)}
 fun removeOnGlobalLayoutListener(l:OnGlobalLayoutListener){globals.remove(l)}
 fun layout(){globals.toList().forEach{it.onGlobalLayout()}}
 var isAlive=true
 val listeners=linkedSetOf<OnPreDrawListener>()
 fun addOnPreDrawListener(l:OnPreDrawListener){listeners.add(l)}
 fun removeOnPreDrawListener(l:OnPreDrawListener){listeners.remove(l)}
 fun dispatch(){listeners.toList().forEach { check(it.onPreDraw()) }}
 fun mergeInto(next:ViewTreeObserver){next.listeners.addAll(listeners);listeners.clear();isAlive=false}
}
''',
'Context.kt':'package android.content\nclass Context',
'Prefs.kt':'''package t.mma.preferences
import android.content.Context
class Prefs {
 val sets=mutableMapOf<String,Set<String>>()
 fun getStringSet(k:String,default:Set<String>):Set<String> = sets[k] ?: default
 fun edit()=Editor(this)
 class Editor(val p:Prefs){fun putStringSet(k:String,s:Set<String>):Editor{p.sets[k]=s.toSet();return this};fun apply(){}}
}
object HchatStorage{val prefs=Prefs();fun preferences(c:Context,n:String)=prefs}
''',
'Apis.kt':'''package t.mma.hooks.api.core
object WeChatApis {var id="";class Account{fun selfWxId()=id};fun account()=Account()}
''',
'Message.kt':'''package t.mma.hooks.api.model
class WeChatMessage(val talker:String,val msgId:Long,val msgSvrId:Long,val recalled:Boolean=false){fun isRecalled()=recalled}
''',
'Check.kt':'''package t.mma.hooks.items.dynamicbadge
import android.view.View
import android.view.ViewTreeObserver
import android.content.Context
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.model.WeChatMessage
import t.mma.hooks.items.antirecall.DynamicRecallStore as Store
fun main(){
 val view=View();var calls=0;val registration=BadgeWindowObserver(view){calls++}
 registration.attach();registration.attach();check(view.viewTreeObserver.listeners.size==1)
 view.viewTreeObserver.dispatch();check(calls==1)
 val old=view.viewTreeObserver;view.isAttachedToWindow=false;registration.detach();check(old.listeners.isEmpty())
 view.viewTreeObserver=ViewTreeObserver();view.isAttachedToWindow=true;registration.attach();view.viewTreeObserver.dispatch();check(calls==2)
 println("PASS: detach/reattach moves observation to new window; duplicate attach coalesces")
 val dead=view.viewTreeObserver;val next=ViewTreeObserver();dead.mergeInto(next);view.viewTreeObserver=next
 registration.attach();check(next.listeners.size==1);next.dispatch();check(calls==3)
 val snapshot=next.listeners.toList();registration.dispose();snapshot.forEach{it.onPreDraw()};check(calls==3 && next.listeners.isEmpty())
 registration.attach();check(next.listeners.isEmpty())
 println("PASS: dead observer migration, disposal and old dispatch snapshot do not leak/restart")
 val list=View();var repairs=0;var available=false;var mounted=false
 val recovery=BadgeListRecovery(list){repairs++;if(available)mounted=true}
 list.tick();check(repairs==1 && !mounted)
 available=true;repeat(10){list.viewTreeObserver.layout()};check(list.queued.size==1)
 list.tick();check(mounted && repairs==2)
 list.delays();check(list.queued.size==1);list.tick();check(list.delayed.isEmpty())
 val saved=list.viewTreeObserver.globals.toList();list.detach();check(list.queued.isEmpty() && list.delayed.isEmpty())
 list.viewTreeObserver=ViewTreeObserver();list.attach();check(list.delayed.size==4)
 recovery.dispose();saved.forEach{it.onGlobalLayout()};list.tick()
 check(list.queued.isEmpty() && list.delayed.isEmpty() && list.attaches.isEmpty() && list.viewTreeObserver.globals.isEmpty())
 println("PASS: initially empty list is repaired on later layout; coalesced frame, finite retries, detach/dispose cleanup")
 val c=Context();WeChatApis.id="accountA"
 check(!Store.contains(c,"room",7,70));Store.mark(c,WeChatMessage("room",7,70))
 check(Store.contains(c,"room",7,0) && Store.contains(c,"room",0,70))
 Store.mark(c,WeChatMessage("room",8,80,true));check(!Store.contains(c,"room",8,80))
 WeChatApis.id="accountB";check(!Store.contains(c,"room",7,70))
 WeChatApis.id="";check(!Store.contains(c,"room",7,70))
 WeChatApis.id="accountA";check(Store.contains(c,"room",7,70));check(!Store.contains(c,"other",7,70))
 println("PASS: first mark activates existing identity; account/talker isolation and recalled-message rejection")
}
'''
}
with tempfile.TemporaryDirectory(prefix='pl-dynamic-lifecycle-') as d:
 p=Path(d)
 for name,text in stubs.items():(p/name).write_text(text)
 sources=[repo/'app/src/main/java/t/mma/hooks/items/dynamicbadge/BadgeWindowObserver.kt',repo/'app/src/main/java/t/mma/hooks/items/dynamicbadge/BadgeListRecovery.kt',repo/'app/src/main/java/t/mma/hooks/items/antirecall/DynamicRecallStore.kt',repo/'app/src/main/java/t/mma/hooks/items/antirecall/AntiRecallSettings.kt',*p.glob('*.kt')]
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,sources)],check=True)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'t.mma.hooks.items.dynamicbadge.CheckKt'],check=True)