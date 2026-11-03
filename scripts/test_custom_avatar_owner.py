"""Extract production custom-avatar transaction/control methods and OwnedHooks into JVM host.
Business callbacks, locator, bitmap/UI are explicit synthetic boundaries, not device validation.
"""
import ast, subprocess, tempfile
from pathlib import Path
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs')
s=(r/'app/src/main/java/t/mma/hooks/items/customfriendavatar/CustomFriendAvatarFeature.kt').read_text()
def method(name):
 start=s.index(name);start=s.rfind(chr(10),0,start)+1
 brace=s.index('{',start);depth=1;end=brace+1
 while depth:
  depth+=(s[end]=='{')-(s[end]=='}');end+=1
 return s[start:end]
def synthetic_callbacks(body):
 while 'object : XC_MethodHook() {' in body:
  a=body.index('object : XC_MethodHook() {');b=body.index('{',a);depth=1;i=b+1
  while depth:depth+=(body[i]=='{')-(body[i]=='}');i+=1
  body=body[:a]+'XC_MethodHook()'+body[i:]
 return body
stubs={}
parsed=ast.parse((r/'scripts/test_round_avatar_owner.py').read_text())
for n in parsed.body:
 if isinstance(n,ast.Assign) and any(isinstance(x,ast.Name) and x.id=='stubs' for x in n.targets):stubs=ast.literal_eval(n.value)
stubs['Os.kt']='''package android.os
class Looper {companion object {fun getMainLooper()=Looper()}}
class Handler(l:Looper) {
 fun post(r:Runnable)=true
 fun postDelayed(r:Runnable,d:Long)=true
 fun removeCallbacks(r:Runnable) {}
}
'''
owned=(r/'app/src/main/java/t/mma/hooks/core/FeatureOwnership.kt').read_text()
owned=owned[owned.index('private fun reportOwnershipError'):owned.index('/** 本功能独占的后台执行器') ] if '/** 本功能独占的后台执行器' in owned else owned[owned.index('private fun reportOwnershipError'):owned.index('class OwnedExecutor')]
# Include only the complete Hooks/Handler declarations; strip trailing doc comment if present.
owned=owned[:owned.rfind('}')+1]
methods=[method(x) for x in ['fun installNotificationHooks(', 'fun installDesktopShortcutHook(', 'fun install():', 'fun destroy()', 'fun rollbackMainHooks()', 'fun installConversationMenu()', 'fun hook(']]
methods=[synthetic_callbacks(x) for x in methods]
prefix='''package t.mma.hooks.items.customfriendavatar
import android.app.Notification
import android.app.NotificationManager
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.*
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
private class Runtime {
 var destroyed=false
 private fun report(e:Throwable) {}
 private fun newHooks()=OwnedHooks(::report)
 var hookResources=newHooks();var notificationHooks=newHooks();val desktopHooks=newHooks()
 val uiTasks=OwnedHandler(::report)
 val hookedMembers=ConcurrentHashMap.newKeySet<Member>()
 val notificationMembers=ConcurrentHashMap.newKeySet<Member>()
 val desktopMembers=ConcurrentHashMap.newKeySet<Member>()
 var installed=false;var desktopShortcutInstalled=false;var menuRegistered=false
 val context=FeatureContext();val logger:(String,Throwable?)->Unit={_,_->}
 val conversationMenuExtension=Any()
 val drawableWxids=mutableMapOf<Any,Any>();val drawableSurfaces=mutableMapOf<Any,Any>()
 val drawableRadiusFactors=mutableMapOf<Any,Any>();val conversationBindings=mutableMapOf<Any,Any>()
 val conversationGroupAvatarCache=mutableMapOf<Any,Any>()
 private fun release(a:()->Unit){try{a()}catch(e:Throwable){report(e)}}
 private fun locateAvatarMembers():Members? {if(Fixture.failLocate) error("locator fault");return Fixture.members}
 private fun locateDesktopShortcutMethod()=Fixture.shortcut
'''
post='''
}
private class Worker()
private data class Members(val legacyLoad:Method,val workerConstructor:java.lang.reflect.Constructor<*>,val workerModify:Method?,val draw:Method)
private object Fixture {
 var failLocate=false
 @JvmStatic fun legacy() {}
 @JvmStatic fun modify() {}
 @JvmStatic fun draw() {}
 @JvmStatic fun menu() {}
 @JvmStatic fun shortcut() {}
 fun member(n:String)=Fixture::class.java.getDeclaredMethod(n)
 val shortcut=member("shortcut")
 val members=Members(member("legacy"),Worker::class.java.declaredConstructors.single(),member("modify"),member("draw"))
}
private object ConversationMenuLocator {fun menuCreateMethod(c:FeatureContext,l:(String,Throwable?)->Unit)=Fixture.member("menu")}
private object ConversationMenuExtensionRegistry {
 var registered=false;var failUnregister=false
 fun register(a:Any){registered=true}
 fun unregister(a:Any){registered=false;if(failUnregister)error("registry release fault")}
}
private object CustomFriendAvatarStore {fun preload(c:Any) {}}
private object CustomFriendAvatarBitmapProcessor {var cleared=0;fun clear(){cleared++}}
fun main(){
 val reg=HookRegistry.get()
 for(failure in 1..7){
  reg.reset();val runtime=Runtime();reg.failAt=failure
  check(!runtime.install()){"failure=$failure unexpectedly succeeded"}
  check(!runtime.installed);check(!ConversationMenuExtensionRegistry.registered)
  check(reg.installed.filter{it.method.declaringClass!=NotificationManager::class.java}.all{it.releases==1})
  reg.failAt=0;check(runtime.install())
  check(reg.installed.count{it.releases==0}==7){"retry missing hooks, failure=$failure"}
  check(runtime.installDesktopShortcutHook());val shortcut=reg.installed.last()
  val notify=reg.installed.filter{it.method.declaringClass==NotificationManager::class.java&&it.releases==0}
  val count=reg.calls;check(runtime.install());check(runtime.installDesktopShortcutHook());check(reg.calls==count)
  runtime.drawableWxids[Any()]=Any();runtime.conversationBindings[Any()]=Any()
  shortcut.fail=true;ConversationMenuExtensionRegistry.failUnregister=true
  runtime.destroy();runtime.destroy()
  check(reg.installed.all{it.releases==1});check(runtime.drawableWxids.isEmpty());check(runtime.conversationBindings.isEmpty())
  check(!runtime.install());check(!runtime.installNotificationHooks());check(!runtime.installDesktopShortcutHook())
  check(!ConversationMenuExtensionRegistry.registered)
  ConversationMenuExtensionRegistry.failUnregister=false
 }
 println("PASS 7 failure positions: transaction rollback, retry, independent groups, cleanup exception isolation, idempotence")
 reg.reset();val r=Runtime();check(r.installDesktopShortcutHook());val desktop=reg.installed.single()
 Fixture.failLocate=true;check(!r.install());check(desktop.releases==0);Fixture.failLocate=false
 check(r.install());r.destroy();check(reg.installed.all{it.releases==1})
 println("PASS locator exception leaves separate shortcut owner valid; subsequent retry succeeds")
 reg.reset();val late=Runtime();reg.beforeHook={reg.beforeHook=null;late.destroy()}
 check(!late.install());check(reg.installed.all{it.releases==1})
 println("PASS reentrant destroy releases late handle and prevents resurrection; synthetic business boundaries")
}
'''
assert 'if (destroyed || owner.isClosed()) return' in s
assert s.count('resources.register {')>=3
with tempfile.TemporaryDirectory(prefix='pl-custom-owner-') as td:
 t=Path(td)
 for name,body in stubs.items():(t/name).write_text(body)
 (t/'Ownership.kt').write_text('package t.mma.hooks.core\nimport android.os.*\nimport de.robv.android.xposed.XC_MethodHook\nimport java.lang.reflect.Member\n'+owned)
 (t/'Check.kt').write_text(prefix+chr(10).join(methods)+post)
 std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(t/'out'),*map(str,t.glob('*.kt'))],check=True,timeout=100)
 subprocess.run(['java','-cp',str(t/'out')+':'+str(std),'t.mma.hooks.items.customfriendavatar.CheckKt'],check=True,timeout=20)