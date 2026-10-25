"""Exercise production Java add_msg source extraction and Kotlin classification together."""
from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);classes=p/'classes';classes.mkdir()
 kt=[r/'app/src/main/java/t/mma/hooks/api/model'/n for n in ['WeChatMessage.kt','WeChatMessageTypes.kt','WeChatParsedMessage.kt']]
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(classes),*map(str,kt)],check=True,timeout=60)
 files={
 'TextUtils.java':'''package android.text; public class TextUtils {public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}}''',
 'KavaReflector.java':'''package t.mma.utils; import java.lang.reflect.*;import java.util.*;
 public class KavaReflector {
 public static Field findFieldRecursive(Class<?> c,String n){while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(Exception e){c=c.getSuperclass();}}return null;}
 public static Object readField(Field f,Object o){try{f.setAccessible(true);return f.get(o);}catch(Exception e){return null;}}
 public static List<Field> declaredFields(Class<?> c){return Arrays.asList(c.getDeclaredFields());}
 }''',
 'Check.java':'''import t.mma.hooks.api.message.WeChatMessageParseApi;import t.mma.hooks.api.model.WeChatMessage;import java.lang.reflect.*;
 public class Check {
 public static class Msg {public String p="",q="",j="",k="",l="",n="",s="",t="";}
 static Method resolve;
 static String run(Msg m,String content)throws Exception{return (String)resolve.invoke(new WeChatMessageParseApi(),m,content,content);}
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[]a)throws Exception{
 resolve=WeChatMessageParseApi.class.getDeclaredMethod("resolveMsgSource",Object.class,String.class,String.class);resolve.setAccessible(true);
 String me="<msgsource><atuserlist>me</atuserlist></msgsource>";
 String all="<msgsource><atuserlist>notify@all</atuserlist></msgsource>";
 String quote="<msg><appmsg><type>57</type><title>B reply</title><refermsg><content>old</content>"+me+"</refermsg></appmsg></msg>";
 check(WeChatMessage.classifyAtMention(run(new Msg(),quote),quote,"me")==WeChatMessage.AtMentionType.NONE);
 Msg direct=new Msg();direct.p=me;
 check(WeChatMessage.classifyAtMention(run(direct,quote),quote,"me")==WeChatMessage.AtMentionType.AT_ME);
 direct.p=all;check(WeChatMessage.classifyAtMention(run(direct,quote),quote,"me")==WeChatMessage.AtMentionType.AT_ALL);
 Msg fallback=new Msg();fallback.j=quote;check(WeChatMessage.classifyAtMention(run(fallback,quote),quote,"me")==WeChatMessage.AtMentionType.NONE);
 fallback.j="";fallback.p="<refermsg>"+me+"</refermsg>";fallback.q=all;
 check(WeChatMessage.classifyAtMention(run(fallback,quote),quote,"me")==WeChatMessage.AtMentionType.AT_ALL);
 check(WeChatMessage.classifyAtMention(run(new Msg(),quote.replace("</msg>",me+"</msg>")),quote,"me")==WeChatMessage.AtMentionType.AT_ME);
 System.out.println("PASS actual Java/Kotlin pipeline: quoted source excluded before extraction; genuine own mentions, all, outer source and fallback preserved");
 }}'''}
 for n,s in files.items():(p/n).write_text(s)
 java=r/'app/src/main/java/t/mma/hooks/api/message/WeChatMessageParseApi.java'
 subprocess.run(['javac','-cp',f'{classes}:{std}','-d',str(classes),*[str(p/n) for n in files],str(java)],check=True,timeout=30)
 subprocess.run(['java','-cp',f'{classes}:{std}','Check'],check=True,timeout=15)