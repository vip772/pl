#!/usr/bin/env python3
"""Production parent policy and pending layout observer JVM regression; no APK build."""
from pathlib import Path
import subprocess, tempfile
repo=Path(__file__).resolve().parents[3]
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
def jar(g,n,v): return next((cache/g/n/v).glob('*/'+n+'-'+v+'.jar'))
std=jar('org.jetbrains.kotlin','kotlin-stdlib','2.4.0')
cp=[jar('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.4.0'),std,
    jar('org.jetbrains.kotlin','kotlin-reflect','2.3.20'),jar('org.jetbrains.kotlin','kotlin-script-runtime','2.4.0'),
    jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'),jar('org.jetbrains','annotations','13.0')]
stub='''package android.view
class View {
 interface OnAttachStateChangeListener { fun onViewAttachedToWindow(v:View); fun onViewDetachedFromWindow(v:View) }
 var isAttachedToWindow=true
 val viewTreeObserver=ViewTreeObserver()
 val attaches=linkedSetOf<OnAttachStateChangeListener>()
 val queued=mutableListOf<Runnable>()
 fun addOnAttachStateChangeListener(l:OnAttachStateChangeListener){attaches.add(l)}
 fun removeOnAttachStateChangeListener(l:OnAttachStateChangeListener){attaches.remove(l)}
 fun post(action:()->Unit){queued.add(Runnable(action))}
 fun postDelayed(r:Runnable,delay:Long){queued.add(r)}
 fun removeCallbacks(r:Runnable){queued.removeAll { it===r }}
 fun tick(){val old=queued.toList();queued.clear();old.forEach{it.run()}}
 fun detach(){isAttachedToWindow=false;attaches.toList().forEach{it.onViewDetachedFromWindow(this)}}
}
class ViewTreeObserver {
 fun interface OnGlobalLayoutListener { fun onGlobalLayout() }
 val isAlive=true
 val listeners=linkedSetOf<OnGlobalLayoutListener>()
 fun addOnGlobalLayoutListener(l:OnGlobalLayoutListener){listeners.add(l)}
 fun removeOnGlobalLayoutListener(l:OnGlobalLayoutListener){listeners.remove(l)}
 fun layout(){listeners.toList().forEach{it.onGlobalLayout()}}
}
'''
test='''package t.mma.hooks.items.hchatextra
import t.mma.hooks.items.conversationgroup.ConversationGroupParentPolicy as P
import android.view.View
fun main(){
 val parent="wxid_hchat_group_test"
 val unknown=P.plan(emptyMap(),mapOf("room" to parent),emptySet(),emptyMap())
 check(unknown.unresolved==setOf("room") && unknown.updates.isEmpty())
 val restored=P.plan(emptyMap(),mapOf("room" to parent),emptySet(),mapOf("room" to ""))
 check(restored.updates["room"]=="" && restored.restoring==setOf("room"))
 val assigned=P.plan(mapOf("room" to parent),mapOf("room" to "native"),emptySet(),emptyMap())
 check(assigned.originalParents["room"]=="native" && assigned.updates["room"]==parent)
 val missing=P.plan(mapOf("room" to parent),emptyMap(),emptySet(),emptyMap())
 check(missing.unresolved==setOf("room") && missing.updates.isEmpty())
 println("PASS: unknown parents protected, trusted restore, backup before mutation, missing row protected")
 val observer=MessageDetailsPendingLayoutObserver();val view=View();var calls=0
 fun failing():Boolean{calls++;observer.observe(view){failing()};return false}
 observer.observe(view){failing()}
 repeat(50){view.tick()}
 check(view.queued.isEmpty() && calls in 1..16)
 val before=calls;view.viewTreeObserver.layout();view.tick();check(calls==before+1)
 println("PASS: reentrant retries preserve finite poll budget; later layout retries")
 observer.observe(view){true};view.viewTreeObserver.layout();view.tick()
 check(view.attaches.isEmpty() && view.viewTreeObserver.listeners.isEmpty())
 observer.observe(view){error("detached retry")};view.detach();repeat(4){view.tick()};observer.clear()
 check(view.attaches.isEmpty() && view.viewTreeObserver.listeners.isEmpty())
 println("PASS: successful bind and shutdown remove observers; detach suppresses queued retry")
}
'''
with tempfile.TemporaryDirectory(prefix='pl-function-recovery-') as d:
 p=Path(d);(p/'Stub.kt').write_text(stub);(p/'Test.kt').write_text(test)
 sources=[repo/'app/src/main/java/t/mma/hooks/items/conversationgroup/ConversationGroupParentPolicy.kt',repo/'app/src/main/java/t/mma/hooks/items/hchatextra/MessageDetailsPendingLayoutObserver.kt',p/'Stub.kt',p/'Test.kt']
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,cp)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,sources)],check=True)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'t.mma.hooks.items.hchatextra.TestKt'],check=True)
