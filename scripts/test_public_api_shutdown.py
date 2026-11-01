"""Compile and exercise production API subscription owners against cached ABI, no business calls."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
cp=[r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes',r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
cp+=list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))+list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar'))+list(Path('/tmp/pl-kotlin-libs').glob('*.jar'))
base=r/'app/src/main/java/t/mma/hooks/api'
files=['runtime/WeChatDatabaseListenerApi.java','message/WeChatMessageChangeApi.java','message/WeChatMessageObserveApi.java','message/WeChatMessageEventApi.java','conversation/WeChatConversationChangeApi.java','contact/WeChatContactChangeApi.java','contact/WeChatChatroomChangeApi.java']
test='''import java.lang.reflect.*;
import java.util.*;
import t.mma.hooks.api.runtime.*;
import t.mma.hooks.api.message.*;
import t.mma.hooks.api.contact.*;
import t.mma.hooks.api.conversation.*;
public class ApiShutdownTest {
 static Object get(Object o,String n)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void set(Object o,String n,Object v)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args)throws Exception {
  WeChatDatabaseListenerApi db=new WeChatDatabaseListenerApi(null,ApiShutdownTest.class.getClassLoader(),null);
  WeChatContactChangeApi contact=new WeChatContactChangeApi(db,null,null);
  WeChatChatroomChangeApi room=new WeChatChatroomChangeApi(db,null,null);
  WeChatConversationChangeApi conversation=new WeChatConversationChangeApi(db,null,null);
  contact.install();room.install();conversation.install();contact.install();
  check(((Collection<?>)get(db,"listeners")).size()==3);
  Object child=contact.subscribe(e->{});contact.close();contact.close();
  check(!(boolean)child.getClass().getMethod("isActive").invoke(child));
  check(contact.subscribe(e->{})==null);contact.install();
  check(((Collection<?>)get(db,"listeners")).size()==2);
  room.close();conversation.close();check(((Collection<?>)get(db,"listeners")).isEmpty());
  WeChatMessageChangeApi change=new WeChatMessageChangeApi(db,null,null,null);
  Object upstream=db.subscribe(e->{});set(change,"upstream",upstream);set(change,"installed",true);
  WeChatMessageObserveApi observe=new WeChatMessageObserveApi(null,change,null,null,null);
  observe.install();observe.subscribe(e->{});
  check(((Collection<?>)get(change,"listeners")).size()==1);
  ((Map<String,Long>)get(observe,"recentOutgoing")).put("fixture",1L);
  ((Map<String,Long>)get(observe,"recentIncomingDatabaseMessages")).put("fixture",1L);
  Map<String,Long> outgoing=(Map<String,Long>)get(observe,"recentOutgoing");
  Map<String,Long> incoming=(Map<String,Long>)get(observe,"recentIncomingDatabaseMessages");
  for(int i=0;i<5000;i++){outgoing.put("o"+i,1L);incoming.put("i"+i,1L);}
  check(outgoing.size()<=64 && incoming.size()<=1024);
  observe.close();observe.close();observe.install();
  check(((Collection<?>)get(change,"listeners")).isEmpty());
  check(((Map<?,?>)get(observe,"recentOutgoing")).isEmpty());
  check(((Map<?,?>)get(observe,"recentIncomingDatabaseMessages")).isEmpty());
  check(observe.subscribe(e->{})==null);
  change.close();change.close();check(((Collection<?>)get(db,"listeners")).isEmpty());
  Method enter=WeChatDatabaseListenerApi.class.getDeclaredMethod("enterMutation");enter.setAccessible(true);
  Method leave=WeChatDatabaseListenerApi.class.getDeclaredMethod("leaveMutation");leave.setAccessible(true);
  enter.invoke(db);enter.invoke(db);check(!(boolean)leave.invoke(db));check((boolean)leave.invoke(db));
  Method enterQ=WeChatDatabaseListenerApi.class.getDeclaredMethod("enterQuery");enterQ.setAccessible(true);
  Method leaveQ=WeChatDatabaseListenerApi.class.getDeclaredMethod("leaveQuery");leaveQ.setAccessible(true);
  check((boolean)enterQ.invoke(db));check(!(boolean)enterQ.invoke(db));leaveQ.invoke(db);leaveQ.invoke(db);
  Object subscription=db.subscribe(e->{});Object query=db.subscribeQuery(sql->sql);
  db.close();db.close();db.install();check(!db.isOperational());check(db.subscribe(e->{})==null);check(db.subscribeQuery(sql->sql)==null);
  check(!(boolean)subscription.getClass().getMethod("isActive").invoke(subscription));
  check(!(boolean)query.getClass().getMethod("isActive").invoke(query));
  WeChatMessageEventApi events=new WeChatMessageEventApi(null,null,null,null,null);
  Map<String,Long> pats=(Map<String,Long>)get(events,"recentPatEvents");for(int i=0;i<1000;i++)pats.put("p"+i,1L);check(pats.size()<=128);
  events.close();events.close();events.installAddMsgHook();check(pats.isEmpty());
  WeChatDatabaseListenerApi fresh=new WeChatDatabaseListenerApi(null,null);check(fresh.subscribe(e->{})!=null);fresh.close();
  System.out.println("PASS production public API subscriptions: reverse close, idempotence, closed admission, fresh instances, cache cleanup");
 }
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'ApiShutdownTest.java').write_text(test)
 classpath=':'.join(map(str,cp))
 subprocess.run(['javac','-encoding','UTF-8','-cp',classpath,'-d',td,*[str(base/f) for f in files],str(p/'ApiShutdownTest.java')],check=True,timeout=60)
 subprocess.run(['java','-cp',td+':'+classpath,'ApiShutdownTest'],check=True,timeout=30)