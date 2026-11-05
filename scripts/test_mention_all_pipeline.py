"""Execute the production outgoing observer with synthetic host messages; no device/network sends."""
import ast
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
# Reuse the lifecycle harness's host contract, not its test body or production implementation.
tree = ast.parse((root / 'scripts/test_hidden_at_owner.py').read_text())
fixtures = next(ast.literal_eval(node.value) for node in tree.body
                if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'fixtures' for t in node.targets))
fixtures['Utils.kt'] = '''package t.mma.utils
object HLog {fun e(s:String,t:Throwable) {}}
class Host {fun U(x:Host)=true;fun a(x:Any)="";fun insert(x:Any)=1L}
class Message(val talker:String="room@chatroom",val content:String="#aa hello",private val type:Int=1,val direction:Int=1,var id:Long=0L) {
 @JvmField var G=""
 fun N0()=talker;fun j()=content;fun getType()=type;fun z0()=direction;fun getMsgId()=id
 fun r3(value:String){G=value}
}
object KavaReflector {
 fun loadClass(n:String,c:ClassLoader):Class<*>?=Host::class.java
 fun findMethodRecursive(c:Class<*>,n:String,vararg p:Class<*>):java.lang.reflect.Method?=c.methods.firstOrNull {it.name==n&&it.parameterCount==p.size}
 fun invokeOrThrow(m:java.lang.reflect.Method,o:Any,vararg args:Any?):Any?=m.invoke(o,*args)
 fun findFieldRecursive(c:Class<*>,n:String)=c.getField(n)
 fun readField(f:java.lang.reflect.Field,o:Any):Any?=f.get(o)
 fun isAbstract(m:java.lang.reflect.Method)=false
}
'''
fixtures['Outgoing.kt'] = '''package t.mma.hooks.api.message
object OutgoingAtMessageHook {
 interface Observer {fun before(p:de.robv.android.xposed.XC_MethodHook.MethodHookParam);fun after(p:de.robv.android.xposed.XC_MethodHook.MethodHookParam)}
 var observer:Observer?=null
 fun locate(c:t.mma.hooks.core.FeatureContext)=t.mma.utils.Host::class.java.getDeclaredMethod("insert",Any::class.java)
 fun register(m:java.lang.reflect.Method,k:String,o:Observer):java.io.Closeable {observer=o;return java.io.Closeable {observer=null}}
}
'''
fixtures['Core.kt'] = fixtures['Core.kt'].replace(' calls++;if(calls==failAt)', ' callbacks+=c;calls++;if(calls==failAt)').replace(
    ' val handles=mutableListOf<de.robv.android.xposed.XC_MethodHook.Unhook>();',
    ' val callbacks=mutableListOf<de.robv.android.xposed.XC_MethodHook>();val handles=mutableListOf<de.robv.android.xposed.XC_MethodHook.Unhook>();')
fixtures['Apis.kt'] = '''package t.mma.hooks.api.core
import t.mma.utils.Message
import t.mma.hooks.api.message.OutgoingAtMessageHook as Out
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
object WeChatApis {fun contact()=Contact();fun messages():Messages?=Messages()}
class Contact {fun account():Account?=Account();fun contacts():Contacts?=Contacts()}
class Account {fun selfWxId()=State.self}
object State {
 var self="self";var members=listOf("self","alice","bob","bob");var queries=0;var nameQueries=0
 var canSend=true;var result=42L;var sends=0;var xml="";var replacement:Message?=null
}
class Contacts {
 fun getGroupMemberIds(t:String):List<String>{State.queries++;return State.members}
 fun getGroupMemberDisplayName(t:String,i:String):String{State.nameQueries++;return i}
 fun getDisplayName(i:String):String{State.nameQueries++;return i}
}
class Messages {
 fun canSendXml()=State.canSend
 fun sendAppMsgXmlWithLocalId(t:String,x:String):Long {
  State.sends++;State.xml=x
  val msg=Message(t,"replacement",0x31000031,1,State.result);State.replacement=msg
  val p=MethodHookParam();p.args=arrayOf(msg);Out.observer!!.before(p)
  if(p.result==null)p.result=State.result
  Out.observer!!.after(p)
  return (p.result as Number).toLong()
 }
}
'''
fixtures['Test.kt'] = '''package t.mma.hooks.items.hiddenat
import t.mma.hooks.core.*
import t.mma.preferences.HchatStorage as Store
import t.mma.hooks.api.message.OutgoingAtMessageHook as Out
import t.mma.hooks.api.core.State
import t.mma.utils.Message
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
fun main() {
 Store.prefs=android.content.Prefs().also{it.enabled=false}
 Store.allPrefs=android.content.Prefs().also{it.enabled=true}
 val runtime=HiddenAtRuntime(FeatureContext());runtime.install();check(DexInstallScheduler.tickets.last().run()==true)
 fun send(msg:Message):MethodHookParam {val p=MethodHookParam();p.args=arrayOf(msg);Out.observer!!.before(p);return p}
 check(send(Message()).result==42L)
 check(State.queries==1&&State.nameQueries==0&&State.sends==1)
 check(State.xml.contains("<title>hello</title>")&&!State.xml.contains("#aa"))
 check(HiddenAtText.users(State.replacement!!.G.toByteArray(),"self")==listOf("alice","bob"))
 val source=MethodHookParam();source.args=arrayOf(State.replacement);source.result="<msgsource><bizflag>1</bizflag></msgsource>"
 HookRegistry.callbacks.last().afterHookedMethod(source)
 check((source.result as String).contains("<bizflag>1</bizflag>"))
 check(HiddenAtText.users((source.result as String).toByteArray(),"")==listOf("alice","bob"))
 val unrelated=MethodHookParam();unrelated.args=arrayOf(Message(type=0x31000031,id=99));unrelated.result="unchanged"
 HookRegistry.callbacks.last().afterHookedMethod(unrelated);check(unrelated.result=="unchanged")
 for(msg in listOf(Message(talker="friend"),Message(direction=0),Message(type=49),Message(content="#aabb"),Message(content="prefix #aa")))check(send(msg).result==null)
 check(State.queries==1&&State.sends==1)
 Store.allPrefs.command="#notify";Store.allPrefs.toggle(true,"command")
 check(send(Message(content="#aa hello")).result==null)
 check(send(Message(content="#notify <hello>&")).result==42L&&State.xml.contains("&lt;hello&gt;&amp;"))
 State.members=(1..700).map{"user$it"}+"self";check(send(Message(content="#notify")).result==42L)
 check(HiddenAtText.users(State.replacement!!.G.toByteArray(),"").size==499)
 State.self="";val before=State.sends;check(send(Message(content="#notify")).result==null&&State.sends==before)
 State.self="self";State.members=emptyList();check(send(Message(content="#notify")).result==null)
 State.members=listOf("alice");State.canSend=false;check(send(Message(content="#notify")).result==null)
 State.canSend=true;State.result=-1L;check(send(Message(content="#notify")).result==null)
 Store.prefs.toggle(true);Store.allPrefs.toggle(false)
 val hidden=Message(content="@alice\u2005hello").also{it.G="<msgsource><atuserlist>alice</atuserlist></msgsource>"}
 State.result=42L;check(send(hidden).result==42L)
 check(send(Message(content="#notify")).result==null)
 runtime.destroy();check(Out.observer==null)
 println("PASS production observer: independent switches, command refresh, group/type/direction scope, 499 recipients, XML escaping, local/source propagation, unrelated-source isolation, fallback, original hidden mention")
}
'''
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    tmp = Path(td)
    for name, text in fixtures.items():
        (tmp / name).write_text(text)
    production = root / 'app/src/main/java/t/mma/hooks/items'
    sources = list((production / 'hiddenat').glob('*.kt')) + list((production / 'mentionall').glob('*.kt'))
    jar = tmp / 'test.jar'
    subprocess.run(['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect',
                    '-classpath', str(stdlib), '-d', str(jar), *map(str, sources), *map(str, tmp.glob('*.kt'))],
                   check=True, timeout=60)
    subprocess.run(['java', '-cp', f'{jar}:{stdlib}', 't.mma.hooks.items.hiddenat.TestKt'], check=True, timeout=20)
