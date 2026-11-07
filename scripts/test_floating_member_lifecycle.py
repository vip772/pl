"""Run the complete statistics owner on JVM platform/API fixtures, including blocked old reads."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
sources={
'Os.kt':'''package android.os
class Looper {companion object{fun getMainLooper()=Looper()}}
class Handler(l:Looper){private val q=java.util.concurrent.ConcurrentLinkedQueue<Runnable>();init{all.add(this)}
 fun post(r:Runnable):Boolean{q.add(r);return true};fun removeCallbacksAndMessages(t:Any?){q.clear()}
 companion object{val all=mutableListOf<Handler>();fun drain(){all.forEach{h->while(true){val r=h.q.poll()?:break;r.run()}}}}}
''',
'View.kt':'''package android.view
open class View {var isAttachedToWindow=true;val listeners=mutableListOf<OnAttachStateChangeListener>()
 interface OnAttachStateChangeListener{fun onViewAttachedToWindow(v:View);fun onViewDetachedFromWindow(v:View)}
 fun addOnAttachStateChangeListener(l:OnAttachStateChangeListener){listeners.add(l)}
 fun removeOnAttachStateChangeListener(l:OnAttachStateChangeListener){listeners.remove(l)}
 fun detach(){isAttachedToWindow=false;listeners.toList().forEach{it.onViewDetachedFromWindow(this)}}}
class ViewGroup:View()
''',
'Page.kt':'''package t.mma.hooks.api.ui
class WeChatChatPageApi {var talker="a@chatroom";var listener:((Event)->Unit)?=null
 inner class Subscription {fun unsubscribe(){listener=null}}
 class Event(val exit:Boolean){fun isExit()=exit}
 fun currentTalker()=talker
 fun subscribe(l:(Event)->Unit):Subscription{listener=l;return Subscription()}}
''',
'Apis.kt':'''package t.mma.hooks.api.core
object WeChatApis {val page=t.mma.hooks.api.ui.WeChatChatPageApi();val contacts=Contacts()
 fun chatPage()=page;fun contacts()=contacts}
class Result(val male:Int,val female:Int=2,val unknown:Int=3,val friends:Int=4)
class Contacts {val calls=java.util.concurrent.atomic.AtomicInteger();@Volatile var block=false;@Volatile var entered=false
 fun getGroupMemberStatistics(room:String):Result {calls.incrementAndGet();if(room=="a@chatroom"&&block){entered=true;while(block){try{Thread.sleep(2)}catch(e:InterruptedException){}}};return Result(if(room=="a@chatroom")1 else 9)}}
''',
'Log.kt':'''package t.mma.utils
object HLog{fun e(s:String,e:Throwable){throw AssertionError(s,e)}}
''',
'Test.kt':'''package t.mma.hooks.items.floatingchat
import android.os.Handler
import android.view.ViewGroup
import t.mma.hooks.api.core.WeChatApis as A
fun await(p:()->Boolean){val end=System.nanoTime()+2_000_000_000;while(!p()){check(System.nanoTime()<end);Thread.sleep(2)}}
fun main(){
 var changes=0;val owner=FloatingChatMemberStatistics{changes++};val view=ViewGroup()
 check(owner.bind(view,false)==null&&A.contacts.calls.get()==0)
 A.contacts.block=true;check(owner.bind(view,true)!!.contains("—"));await{A.contacts.entered}
 repeat(1000){owner.bind(view,true)};check(A.contacts.calls.get()==1)
 A.page.talker="b@chatroom";owner.bind(view,true);A.contacts.block=false
 await{A.contacts.calls.get()==2};await{Handler.drain();changes>0}
 check(owner.bind(view,true)!!.startsWith("男 9"))
 val before=A.contacts.calls.get();repeat(1000){owner.bind(view,true)};check(A.contacts.calls.get()==before)
 view.detach();check(view.listeners.isEmpty());view.isAttachedToWindow=true
 owner.bind(view,true);await{A.contacts.calls.get()>before}
 owner.stop();Handler.drain();check(view.listeners.isEmpty()&&A.page.listener==null)
 check(owner.bind(view,false)==null)
 owner.bind(view,true);owner.close();val count=A.contacts.calls.get();Handler.drain()
 check(owner.bind(view,true)==null&&view.listeners.isEmpty()&&A.page.listener==null)
 println("PASS whole owner: default-off zero reads, 1000 unchanged binds one read, blocked old generation discarded after switch, reattach refresh, disable/close cleanup and no stale UI callback")
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,text in sources.items():(p/name).write_text(text)
 production=r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatMemberStatistics.kt'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'out'),*map(str,p.glob('*.kt')),str(production)],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"out"}:{std}','t.mma.hooks.items.floatingchat.TestKt'],check=True,timeout=15)