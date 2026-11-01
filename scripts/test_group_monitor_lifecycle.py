"""Compile extracted production group-monitor teardown and reply-chain methods with host stubs."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items'
leave=(b/'groupleave/GroupLeaveMonitorRuntime.kt').read_text()
rename=(b/'grouprename/GroupRenameMonitorRuntime.kt').read_text()
def method(source,begin,end):
 return source[source.index(begin):source.index(end,source.index(begin))]
chain=method(leave,'    private fun executeReplyChain(','    private fun promptTasks(').replace('private fun executeReplyChain','fun executeReplyChain')
voice_enqueue=method(leave,'    @Synchronized private fun postVoice(','    private fun pathsReplyTask(').replace('private fun postVoice','fun postVoice')
stop_leave=method(leave,'    @Synchronized fun destroy()','    private fun canReply()')
stop_rename=method(rename,'    @Synchronized fun destroy()','    private fun canSend()')
for folder,prefix,source in [('groupleave','GroupLeaveMonitor',leave),('grouprename','GroupRenameMonitor',rename)]:
 feature=(b/f'{folder}/{prefix}Feature.kt').read_text()
 assert 'PreferenceRuntimeOwner' in feature and 'restartOnChange = true' in feature
 assert feature.index('resources.register { try { monitor.destroy()') < feature.index('monitor.install()')
 assert 'current.get()?.install()' in feature and 'trackSubscription(subscription)' not in feature
 assert 'PendingNoticeTasks(' in source and 'Thread({' not in source
 assert 'if (closed) return' in source and 'shutdownNow()' not in source
 assert 'finally { connection.disconnect() }' in source
assert 'if (canReply()) runCatching { WeChatApis.media()?.sendVoice' in leave
assert '@Synchronized private fun postVoice' in leave and 'postVoice(groupId, path)' in leave
assert 'if (!canReply()) break' in leave
fixture="""package t.mma.hooks.items.groupleave
class GroupLeaveReplyTemplate
class ReplyVariables
object GroupLeaveMonitorSettings{const val MEDIA_BEFORE="before";const val MEDIA_AFTER="after"}
class Pending {data class Item(val delay:Long,val action:()->Unit);val jobs=java.util.ArrayDeque<Item>();var closed=false;fun schedule(d:Long,a:()->Unit):Boolean{if(closed)return false;jobs.add(Item(d,a));return true};fun close(){closed=true;jobs.clear()};fun step():Long{val item=jobs.removeFirst();item.action();return item.delay}}
class Subscription {var calls=0;var fail=false;fun unsubscribe(){calls++;if(fail)error("unsubscribe")}}
class Main{var cleared=false;val jobs=mutableListOf<()->Unit>();fun post(f:()->Unit){jobs+=f};fun removeCallbacksAndMessages(x:Any?){cleared=true;jobs.clear()}}
object WeChatApis{var sends=0;fun media()=this;fun sendVoice(g:String,p:String){sends++}}
class Leave {var closed=false;val tasks=Pending();val main=Main();var changeSubscription:Subscription?=Subscription();var messageSubscription:Subscription?=Subscription();val memberSnapshots=mutableMapOf(1 to 1);val memberSnapshotWarmups=mutableMapOf(1 to 1);val memberNameCache=mutableMapOf(1 to 1);val observedMemberNames=mutableMapOf(1 to 1);val recentInserts=mutableMapOf(1 to 1);val pendingInviteDetails=mutableMapOf(1 to 1);var active=true;var mode="after";val effects=mutableListOf<String>();var throwFirst=false;var errors=0
 private fun canReply()=!closed&&active
 private fun logger(s:String,t:Throwable?){errors++}
 private fun promptTypeFor(g:String,t:GroupLeaveReplyTemplate?)="text"
 private fun mediaOrderFor(g:String,t:GroupLeaveReplyTemplate?)=mode
 data class ReplyTask(val type:String,val delayMs:Long,val action:()->Unit)
 private fun mediaTasks(t:String,g:String,m:String,x:GroupLeaveReplyTemplate?)=listOf(ReplyTask("media",20){effects+="media"})
 private fun promptTasks(t:String,g:String,v:ReplyVariables,p:String,x:GroupLeaveReplyTemplate?)=listOf(ReplyTask("text",10){effects+="text";if(throwFirst)error("synthetic")})
"""+chain+voice_enqueue+stop_leave+"""}
class Rename {var closed=false;var subscription:Subscription?=Subscription();val tasks=Pending();val memberSnapshots=mutableMapOf(1 to 1);val memberNickSnapshots=mutableMapOf(1 to 1);val memberSnapshotWarmups=mutableMapOf(1 to 1);val recentInserts=mutableMapOf(1 to 1);
"""+stop_rename+"""}
fun main(){
 val a=Leave();a.executeReplyChain("join","synthetic",ReplyVariables(),null);check(a.tasks.jobs.size==1);check(a.tasks.step()==10L);check(a.effects==listOf("text"));check(a.tasks.jobs.size==1);check(a.tasks.step()==320L);check(a.effects==listOf("text","media"));check(a.tasks.jobs.isEmpty())
 val b=Leave();b.mode="before";b.executeReplyChain("left","synthetic",ReplyVariables(),null);check(b.tasks.step()==20L);check(b.effects==listOf("media"));b.destroy();check(b.tasks.jobs.isEmpty()&&b.main.cleared);b.destroy()
 val c=Leave();c.executeReplyChain("join","synthetic",ReplyVariables(),null);c.active=false;c.tasks.step();check(c.effects.isEmpty()&&c.tasks.jobs.isEmpty())
 val d=Leave();d.throwFirst=true;d.executeReplyChain("join","synthetic",ReplyVariables(),null);d.tasks.step();d.tasks.step();check(d.effects==listOf("text","media")&&d.errors==1)
 val e=Leave();val first=e.changeSubscription!!;val second=e.messageSubscription!!;first.fail=true;second.fail=true;check(runCatching{e.destroy()}.isFailure);check(first.calls==1&&second.calls==1&&e.tasks.closed&&e.main.cleared&&e.memberSnapshots.isEmpty()&&e.pendingInviteDetails.isEmpty());e.destroy()
 val voice=Leave();voice.postVoice("synthetic","fake");check(voice.main.jobs.size==1);val stale=voice.main.jobs.single();voice.destroy();stale();voice.postVoice("synthetic","late");check(voice.main.jobs.isEmpty()&&WeChatApis.sends==0)
 val f=Rename();val sub=f.subscription!!;sub.fail=true;check(runCatching{f.destroy()}.isFailure);check(f.tasks.closed&&f.subscription==null&&f.memberNickSnapshots.isEmpty());f.destroy()
 println("PASS production group monitor methods: ordered delayed chain, stop between parts, disabled callback, failure continuation, teardown despite both unsubscribe failures, idempotence")
}
"""
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(fixture)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.groupleave.TestKt'],check=True,timeout=15)
