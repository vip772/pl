"""Execute the complete production contact repository with synthetic host APIs."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]; libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
fixtures={
'Model.kt':'''package t.mma.hooks.api.model
class WeChatContact(val wxId:String) { val remarkName=""; val nickname=wxId; val customWxId=""; val avatarUrl=""; val avatarBackupUrl="" }
''',
'Ui.kt':'''package t.mma.ui.miuix
import t.mma.hooks.api.model.WeChatContact
fun WeChatContact.pickerDisplayName(group:Boolean)=nickname
object VoiceForwardMiuixDialog { data class ContactItem(val id:String,val label:String,val group:Boolean,val official:Boolean,val avatarUrl:String,val avatarBackupUrl:String,val labels:List<String>,val searchAliases:List<String>) }
''',
'Contact.kt':'''package t.mma.hooks.api.contact
import t.mma.hooks.api.model.WeChatContact
class Label(val labelName:String="",val labelId:String="",val userNameList:List<String> = emptyList())
class WeChatContactApi(val count:Int=1) {
val isAvailable=true; var query:()->Unit = {}; var queries=0
fun getContactLabelList():List<Label> {queries++;query();return emptyList()}
fun getPickerContacts()=List(count){WeChatContact("friend$it")}
fun getPickerOfficialAccounts()=listOf(WeChatContact("official"))
fun getPickerGroups()=listOf(WeChatContact("group"))
}
''',
'Conversation.kt':'''package t.mma.hooks.api.conversation
class WeChatConversationApi {fun getRecentConversationUsernames(limit:Int)=listOf("group","friend0")}
''',
'Apis.kt':'''package t.mma.hooks.api.core
import t.mma.hooks.api.contact.WeChatContactApi
import t.mma.hooks.api.conversation.WeChatConversationApi
object WeChatApis { @Volatile var api:WeChatContactApi?=null; fun contact()=this;fun contacts()=api;fun conversations()=WeChatConversationApi() }
''',
'Test.kt':'''package t.mma.hooks.items.selectedmessages
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.contact.WeChatContactApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
fun main() {
 repeat(100) {
  val api=WeChatContactApi();WeChatApis.api=api
  val a=SelectedMessageContactRepository.openSession();val b=SelectedMessageContactRepository.openSession()
  check(a.load(false).size==3);check(b.cached(true)?.size==1)
  a.close();a.close();check(b.cached(false)?.size==3);check(a.load(false).isEmpty())
  b.close();val c=SelectedMessageContactRepository.openSession();check(c.cached(false)==null);c.close()
  val entered=CountDownLatch(1);val release=CountDownLatch(1)
  api.query={entered.countDown();check(release.await(3,TimeUnit.SECONDS))}
  val old=SelectedMessageContactRepository.openSession();val live=SelectedMessageContactRepository.openSession()
  val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>()
  val thread=Thread {try {check(old.load(false).isEmpty())} catch(t:Throwable) {failure.set(t)}};thread.start();check(entered.await(3,TimeUnit.SECONDS))
  old.close();release.countDown();thread.join(3000);check(!thread.isAlive);check(failure.get()==null);check(live.cached(false)==null)
  api.query={};check(live.load(false).size==3)
  WeChatApis.api=WeChatContactApi();check(live.cached(false)==null);live.close()
 }
 val api=WeChatContactApi();WeChatApis.api=api
 val owner=SelectedMessageContactRepository.openSession();api.query={WeChatApis.api=WeChatContactApi()};check(owner.load(false).isEmpty());check(owner.cached(false)==null);owner.close()
 WeChatApis.api=WeChatContactApi(10001);val big=SelectedMessageContactRepository.openSession();check(big.load(false).size==10003);check(big.cached(false)==null);big.close()
 println("PASS full production repository: 100 close/query races; shared consumers; last-owner release; API replacement; oversized cache bypass")
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,code in fixtures.items(): (p/name).write_text(code)
 sources=list(p.glob('*.kt'))+[r/'app/src/main/java/t/mma/hooks/items/selectedmessages/SelectedMessageContactRepository.kt']
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,sources)],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'t.mma.hooks.items.selectedmessages.TestKt'],check=True,timeout=30)
