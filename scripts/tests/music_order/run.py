#!/usr/bin/env python3
"""JVM production runtime regression, network/Android/JNI/WeChat boundaries stubbed."""
from pathlib import Path
import os, subprocess, tempfile
repo=Path(__file__).resolve().parents[3]
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
def jar(g,n,v):return next((cache/g/n/v).glob('*/'+n+'-'+v+'.jar'))
stdlib=jar('org.jetbrains.kotlin','kotlin-stdlib','2.4.0')
compiler=[jar('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.4.0'),stdlib,
 jar('org.jetbrains.kotlin','kotlin-reflect','2.3.20'),jar('org.jetbrains.kotlin','kotlin-script-runtime','2.4.0'),
 jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'),jar('org.jetbrains','annotations','13.0')]
stubs={
'Context.kt':'''package android.content
import java.io.File
open class Context(val cacheDir:File) { val applicationContext:Context get()=this }
''',
'Clock.kt':'''package android.os
object SystemClock { fun elapsedRealtime()=System.nanoTime()/1000000 }
''',
'Retriever.kt':'''package android.media
class MediaMetadataRetriever {
 fun setDataSource(p:String) {};fun release() {}
 fun extractMetadata(k:Int):String="180000"
 companion object { const val METADATA_KEY_DURATION=9 }
}
''',
'Codec.kt':'''package me.yun.silk
import java.io.File
class SilkCodec
object AacCodec {
 var code=0;var calls=0
 fun mp4ToSilk(input:String,output:String,codec:SilkCodec,rate:Int):Int {
  calls++;check(File(input).length()>0);check(rate==24000)
  if(code==0)File(output).writeText("#!SILK_V3fake")
  return code
 }
}
''',
'Message.kt':'''package t.mma.hooks.api.message
class Message(val msgId:Long,val content:String,val createTime:Long=System.currentTimeMillis(),
 val talker:String="chat",val outgoing:Boolean=true) {
 fun isText()=true;fun isSystem()=false;fun isOutgoing()=outgoing;fun isGroupChat()=false
 fun getSendTalker()="friend"
}
class WeChatMessageChangeApi {
 class MessageChange(val message:Message?,val isInsert:Boolean=true)
 val listeners=mutableListOf<(MessageChange)->Unit>()
 fun install() {};fun subscribe(f:(MessageChange)->Unit):Any? {listeners.add(f);return Any()}
 fun emit(m:Message) {listeners.toList().forEach{it(MessageChange(m))}}
}
''',
'Apis.kt':'''package t.mma.hooks.api.core
import java.io.File
import t.mma.hooks.api.message.WeChatMessageChangeApi
object WeChatApis {
 val changes=WeChatMessageChangeApi();val media=Media();val voice=Voice();val sender=Sender()
 fun message()=Messages();fun interaction()=Interaction();fun contact()=Contacts();fun media()=Medias()
 class Messages {fun changes():WeChatMessageChangeApi?=WeChatApis.changes;fun sender():Sender?=WeChatApis.sender
  fun local():Local?=Local()}
 class Local {fun insertSystemMessage(t:String,c:String,time:Long) {}}
 class Sender {var replies=0;fun sendQuote(t:String,id:Long,c:String)=false
  fun sendText(t:String,c:String){replies++}}
 class Interaction {fun chatPage():Chat?=Chat();fun media():Media?=WeChatApis.media}
 class Chat {fun currentTalker()="chat"}
 class Media {@Volatile var cards=0
 fun shareMusicWithMetadata(t:String,title:String,singer:String,url:String,play:String,lyric:String,
 cover:String,thumb:ByteArray?,app:String):Boolean {cards++;return true}}
 class Contacts {fun account():Account?=Account();fun contacts():Book?=Book();fun chatrooms():Rooms?=Rooms()}
 class Account {fun selfWxId()="me"}
 class Book {fun getAvatarUrl(s:String,h:Boolean)="";fun getContact(s:String):Contact?=Contact()
  fun getGroupMemberDisplayName(t:String,s:String)=""}
 class Contact {val remarkName="";val nickname=""}
 class Rooms {fun getRoomDisplayName(t:String,s:String)=""}
 class Medias {fun voices():Voice?=WeChatApis.voice}
 class Voice {@Volatile var sends=0;var duration=0;var path=""
 fun canSendSilently()=true
 fun send(t:String,p:String,d:Int):Boolean {check(p.endsWith(".silk"));check(File(p).readText().startsWith("#!SILK"))
 duration=d;path=p;sends++;return true}}
}
''',
'HLog.kt':'''package t.mma.utils
object HLog {fun i(s:String) {}}
''',
'SendButton.kt':'''package t.mma.hooks.items.script
object ScriptSendButtonHook {
 var handler:((String)->Boolean)?=null
 fun registerHandler(id:String,h:(String)->Boolean):Any {handler=h;return Any()}
}
''',
'Settings.kt':'''package t.mma.hooks.items.musicorder
import android.content.Context
object QQMusicOrderFeature { const val ID="qq_music_order" }
class QQMusicOrderSettings(c:Context) {
 companion object {var voice=false;var intercept=false;var card=true;var allowed=setOf<String>()}
 fun isEnabled()=true;fun interceptOwnCommand()=intercept;fun allowedTalkers()=allowed
 fun saveAllowedTalkers(s:Set<String>){allowed=s}
 fun sendAsCard()=card;fun sendAsVoice()=voice;fun replaceCoverWithAvatar()=false
 fun replaceSingerWithNickname()=false;fun customSingerEnabled()=false;fun defaultSinger()=""
 fun appId()="test";fun triggers()=listOf("点歌")
}
internal data class QQMusicTrack(val title:String="song",val singer:String="singer",val landingUrl:String="",
 val playUrl:String="",val lyric:String="",val coverUrl:String="",val mid:String="song")
internal sealed class QQMusicSearchResult {
 data class Success(val track:QQMusicTrack):QQMusicSearchResult()
 object NotFound:QQMusicSearchResult();object Unavailable:QQMusicSearchResult()
}
internal class QQMusicClient {
 companion object {@Volatile var searches=0;var url=""}
 fun search(k:String):QQMusicSearchResult {searches++;return QQMusicSearchResult.Success(QQMusicTrack(playUrl=url,mid=k))}
}
''',
'Regression.kt':'''package t.mma.hooks.items.musicorder
import android.content.Context
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.message.Message
import t.mma.hooks.items.script.ScriptSendButtonHook
import me.yun.silk.AacCodec
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import com.sun.net.httpserver.HttpServer
fun main(args:Array<String>) {
 var cacheTime=System.currentTimeMillis()
 val cache=MusicVoiceCache(File(args[0],"bounded-cache")){cacheTime}
 val cf=cache.file("one");cf.writeText("silk");cache.commit(cf,1000)
 check(cache.duration(cf)==1000)
 File(cf.path+".duration").writeText("invalid");check(cache.duration(cf)==0)
 cache.commit(cf,1000);cacheTime+=25L*60*60*1000;check(cache.duration(cf)==0);cache.trim();check(!cf.exists())
 repeat(12){val f=cache.file("item$it");f.writeText("silk");cache.commit(f,1000);cacheTime++}
 check(File(args[0],"bounded-cache").listFiles()!!.count{it.extension=="silk"}<=8)
 var now=1000L;val d=MusicCommandDeduplicator({now},100L,4)
 check(d.claim("a",1,0,"点歌夜曲"));check(!d.claim("a",1,0,"different"))
 check(d.claim("a",2,0,"点歌夜曲"));check(d.claim("b",1,0,"点歌夜曲"))
 check(d.claim("a",0,10,"x"));check(!d.claim("a",0,10,"x"));check(d.claim("a",0,11,"x"))
 now+=101;check(d.claim("a",2,0,"点歌夜曲"));d.clear();check(d.claim("a",2,0,"点歌夜曲"))
 val concurrent=MusicCommandDeduplicator({0L});val accepted=AtomicInteger();val start=CountDownLatch(1)
 val workers=(1..20).map{Thread{start.await();if(concurrent.claim("chat",42,0,"x"))accepted.incrementAndGet()}.apply{start()}}
 start.countDown();workers.forEach{it.join()};check(accepted.get()==1)
 val root=File(args[0]);val r=QQMusicOrderRuntime(Context(root)){_,_->}
 r.install{};r.install{};check(WeChatApis.changes.listeners.size==1)
 fun await(f:()->Boolean){val end=System.currentTimeMillis()+5000;while(!f()) {check(System.currentTimeMillis()<end);Thread.sleep(10)}}
 val message=Message(1,"点歌夜曲");repeat(5){WeChatApis.changes.emit(message)}
 await{WeChatApis.media.cards==1};Thread.sleep(100);check(QQMusicClient.searches==1)
 WeChatApis.changes.emit(Message(2,"点歌夜曲"));await{WeChatApis.media.cards==2}
 QQMusicOrderSettings.intercept=true
 check(ScriptSendButtonHook.handler!!("点歌起风了"));await{WeChatApis.media.cards==3}
 val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
 server.createContext("/song.mp3"){e->val b="fake compressed audio".toByteArray()
 e.responseHeaders.set("Content-Type","audio/mpeg");e.sendResponseHeaders(200,b.size.toLong())
 e.responseBody.use{it.write(b)}};server.start()
 try {
 QQMusicClient.url="http://127.0.0.1:${server.address.port}/song.mp3"
 QQMusicOrderSettings.voice=true;QQMusicOrderSettings.card=false
 WeChatApis.changes.emit(Message(3,"点歌夜曲"));WeChatApis.changes.emit(Message(3,"点歌夜曲"))
 await{WeChatApis.voice.sends==1};Thread.sleep(100)
 check(AacCodec.calls==1 && WeChatApis.voice.duration==180000)
 check(File(root,"Hchat_qq_music_order_voice").listFiles()!!.all{it.isDirectory})
 AacCodec.code=-1;val replies=WeChatApis.sender.replies
 WeChatApis.changes.emit(Message(4,"点歌不同歌曲"));await{WeChatApis.sender.replies>replies}
 check(WeChatApis.voice.sends==1)
 check(File(root,"Hchat_qq_music_order_voice").listFiles()!!.all{it.isDirectory})
 AacCodec.code=0;WeChatApis.changes.emit(Message(6,"点歌夜曲"));await{WeChatApis.voice.sends==2};check(AacCodec.calls==2)
 val searches=QQMusicClient.searches;r.destroy();WeChatApis.changes.emit(Message(5,"点歌夜曲"))
 check(!ScriptSendButtonHook.handler!!("点歌夜曲"));Thread.sleep(100);check(QQMusicClient.searches==searches)
 }finally {server.stop(0);r.destroy()}
 println("PASS: message/concurrent dedup, distinct repeated songs, one subscription/card, system-decoder -> Silk -> explicit-duration voice, decoder failure, temp cleanup and destroyed runtime")
}
'''
}
with tempfile.TemporaryDirectory(prefix='hchat-music-test-') as temp:
 root=Path(temp)
 for n,s in stubs.items():(root/n).write_text(s)
 sources=[repo/'app/src/main/java/t/mma/hooks/items/musicorder'/n for n in ['QQMusicOrderRuntime.kt','MusicCommandDeduplicator.kt','MusicVoiceCache.kt']]
 target=root/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',os.pathsep.join(map(str,compiler)),
  'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),
  '-d',str(target),*map(str,sources),*map(str,root.glob('*.kt'))],check=True,timeout=90)
 subprocess.run(['java','-cp',str(target)+os.pathsep+str(stdlib),
  't.mma.hooks.items.musicorder.RegressionKt',str(root)],check=True,timeout=30)