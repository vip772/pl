from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/homeextension/HomeExtensionRuntime.kt').read_text();a=s.index('                    val selected = ids.asSequence()');b=s.index('\n                }\n                if (!destroyed)',a)
code='''data class Item(val wxId:String,val nickname:String="name",val remarkName:String="",val type:Int=1,val avatarUrl:String="big",val avatarBackupUrl:String="small"){fun displayName()=nickname}
data class Contact(val id:String,val name:String,val avatar:String,val group:Boolean,val fallbackAvatar:String)
class Api {var batches=0;var fallbacks=0;fun isGroup(id:String)=id.endsWith("@chatroom");fun isFriend(id:String)=id!="skip";fun getContactsByIds(ids:List<String>):List<Item>{batches++;return ids.map{if(it=="missing")Item(it,"","",0,"","")else Item(it)}};fun getDisplayName(id:String):String{fallbacks++;return id};fun getAvatarUrl(id:String,big:Boolean)=if(big)"fallback-big" else "fallback-small"}
fun load(ids:List<String>,favorites:Boolean,contacts:Api):List<Contact>{return run {

'''+s[a:b]+'''}}
fun main(){val api=Api();val result=load(listOf("skip","a","g@chatroom","missing")+(1..20).map{it.toString()},false,api);check(result.size==8&&result[0].id=="a"&&result[1].group);check(result[0].avatar=="small"&&result[0].fallbackAvatar=="big");check(result[2].avatar=="fallback-small"&&api.batches==1&&api.fallbacks==1);check(load(listOf("skip"),true,api).single().id=="skip");println("PASS final-eight batch, exact order/filter, favorites bypass, small/big avatar semantics and placeholder fallback")}
'''
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True)