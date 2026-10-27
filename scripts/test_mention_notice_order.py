"""Run production ordering gate with real message models; no Gradle."""
from pathlib import Path
import os, subprocess, tempfile
r=Path(__file__).resolve().parents[1];libs=Path(os.environ.get('KOTLIN_LIB_DIR','/tmp/pl-kotlin-libs'));std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
code='''package t.mma.hooks.items.mentionextension
import t.mma.hooks.api.model.WeChatMessage
fun main(){
 val now=1800000000500L
 fun msg(t:Long=now,id:Long=5,svr:Long=7,send:Int=0,talker:String="g@chatroom")=WeChatMessage(id,svr,1,0,send,t,talker,"sender:\\n@me", "","","",0,"", "me")
 check(MentionNoticeOrder.accepts(true,msg(),now,now))
 // The incoming PB second loses the database's millisecond ordering correction.
 val pb=now/1000*1000
 check(pb+1<now)
 check(MentionNoticeOrder.after(now)==now+1)
 check(MentionNoticeOrder.after(now/1000)==pb+1)
 check(!MentionNoticeOrder.accepts(false,msg(),now,now))
 check(!MentionNoticeOrder.accepts(true,msg(id=0),now,now))
 check(!MentionNoticeOrder.accepts(true,msg(svr=0),now,now))
 check(!MentionNoticeOrder.accepts(true,msg(send=1),now,now))
 check(!MentionNoticeOrder.accepts(true,msg(talker="friend"),now,now))
 check(!MentionNoticeOrder.accepts(true,msg(t=now-10001),now,now))
 check(!MentionNoticeOrder.accepts(true,msg(t=now-300001),now-600000,now))
 check(!MentionNoticeOrder.accepts(true,msg(t=now+300001),now,now))
 check(!MentionNoticeOrder.accepts(true,msg(t=0),now,now))
 check(MentionNoticeOrder.after(Long.MAX_VALUE)==null)
 val rows=listOf(msg(t=now+250),msg(t=now+999),msg(t=now+1000))
 for(row in rows)check(MentionNoticeOrder.after(row.createTime)!!>row.createTime)
 println("PASS DB-time correction reproduces old inversion; stored anchor follows source; insert-only, history/outgoing/private/unknown ID/time guards")
}
'''
feature=(r/'app/src/main/java/t/mma/hooks/items/mentionextension/MentionNoticeFeature.kt').read_text()
assert 'changes.subscribe' in feature and 'observe.subscribe' not in feature
assert 'WeChatApis.messageStore()' not in feature and 'postDelayed' not in feature
assert 'MentionNoticeOrder.after(msg.createTime)' in feature
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 base=r/'app/src/main/java/t/mma/hooks'
 subprocess.run(['java','-Xmx384m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(base/'api/model/WeChatMessage.kt'),str(base/'api/model/WeChatMessageTypes.kt'),str(base/'items/mentionextension/MentionNoticeOrder.kt'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.mentionextension.TestKt'],check=True,timeout=15)