"""Exercise production store and projections, without Gradle or device writes."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'));json=Path('/tmp/backup-json.jar')
source=r/'app/src/main/java/t/mma/hooks/items/groupbar/GroupBarStore.kt'
stubs='''package android.content
open class Context
class Prefs {val data=mutableMapOf<String,Any>();fun getString(k:String,d:String)=data[k] as? String?:d;fun getInt(k:String,d:Int)=data[k] as? Int?:d;fun edit()=Editor(data)}
class Editor(val data:MutableMap<String,Any>){fun putString(k:String,v:String)=apply{data[k]=v};fun putInt(k:String,v:Int)=apply{data[k]=v};fun apply(){}}
'''
store='''package t.mma.preferences
object HchatStorage {val prefs=android.content.Prefs();fun preferences(c:android.content.Context,n:String)=prefs}
'''
main='''package t.mma.hooks.items.groupbar
import android.content.Context
fun main(){
 val c=Context();check(GroupBarStore.load(c).map{it.id}==listOf("all","friends","rooms","official","unread"))
 repeat(5){check(GroupBarStore.add(c))};check(!GroupBarStore.add(c));check(GroupBarStore.load(c).size==10)
 check(GroupBarStore.load(c).last().name=="分组5")
 val named=GroupBarStore.load(c).map { if(!it.fixed)it.copy(name="自定义昵称",members=setOf("friend","room@chatroom")) else it }
 GroupBarStore.save(c,named);check(GroupBarStore.load(c).last().members==setOf("friend","room@chatroom"))
 check(GroupBarStore.load(c).last().name=="自定义昵称")
 val list=GroupBarStore.load(c);GroupBarStore.save(c,list.map{if(it.id=="all")it.copy(enabled=false) else it})
 check(!GroupBarStore.load(c).first().enabled)
 GroupBarStore.move(c,"unread",-1);check(GroupBarStore.load(c)[3].id=="unread")
 data class Row(val id:String,val count:Int)
 val rows=listOf(Row("a",1),Row("g@chatroom",0),Row("gh_b",2),Row("system",0))
 val groups=GroupBarStore.normalize(listOf(BarGroup("custom_a","自定义",members=setOf("a","g@chatroom"))))
 var reads=0;val index=GroupBarIndex(rows,groups,{reads++;it.id},{it.count},setOf("a"),setOf("gh_b"))
 check(reads==4);check(index.rows("all")==rows);check(index.rows("friends").map{it.id}==listOf("a"))
 check(index.rows("rooms").map{it.id}==listOf("g@chatroom"));check(index.rows("official").map{it.id}==listOf("gh_b"))
 check(index.rows("unread").map{it.id}==listOf("a","gh_b"));check(index.rows("custom_a").size==2)
 val cached=index.rows("rooms");repeat(100000){check(index.rows("rooms")===cached)};check(reads==4)
 val changed=GroupBarIndex(rows+Row("new",3),groups,{it.id},{it.count},setOf("a","new"),emptySet())
 check(changed.rows("unread").size==3);check(index.rows("all").size==4)
 println("PASS defaults, 10-group cap, numbering, rename/order persistence; all/friend/room/official/unread/custom; 100000 cached switches without row reads")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Context.kt').write_text(stubs);(p/'Storage.kt').write_text(store);(p/'Test.kt').write_text(main)
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',f'{std}:{json}','-d',str(p/'test.jar'),str(source),*map(str,p.glob('*.kt'))]
 subprocess.run(cmd,check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}:{json}','t.mma.hooks.items.groupbar.TestKt'],check=True,timeout=15)