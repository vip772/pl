"""Compile production single-row reader and legacy path against a faulting Cursor."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
store=(r/'app/src/main/java/t/mma/hooks/api/message/WeChatMessageStoreApi.java').read_text()
db=(r/'app/src/main/java/t/mma/hooks/api/runtime/WeChatDatabaseApi.java').read_text()
old=subprocess.check_output(['git','show','f0c5ce0:app/src/main/java/t/mma/hooks/api/message/WeChatMessageStoreApi.java'],cwd=r,text=True)
def method(text,signature):
 a=text.index(signature);b=text.index('{',a);depth=1;i=b+1
 while depth:
  depth += (text[i]=='{')-(text[i]=='}');i+=1
 return text[a:i]
parts=[method(store,s) for s in ['public WeChatMessage getMessageById(', 'private WeChatMessage queryFirstMessage(', 'private static String rowString(', 'private static int rowInt(', 'private static long rowLong(', 'private static long parseLong(']]
legacy=method(old,'public WeChatMessage getMessageById(').replace('getMessageById(', 'legacy(')
legacy_to=method(old,'private WeChatMessage toMessage(')
legacy_helpers='\n'.join(method(old,s) for s in ['private static String str(', 'private static int intValue(', 'private static long longValue('])
code='''import java.util.*; import t.mma.hooks.api.message.WeChatStoredMsgSource;
public class Main {
 interface Cursor {
  int FIELD_TYPE_NULL=0,FIELD_TYPE_INTEGER=1,FIELD_TYPE_FLOAT=2,FIELD_TYPE_STRING=3,FIELD_TYPE_BLOB=4;
  String[] getColumnNames();boolean moveToFirst();boolean moveToNext();void close();int getType(int i);long getLong(int i);double getDouble(int i);byte[] getBlob(int i);String getString(int i);
 }
 static class TextUtils {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class WeChatMessage {Object[] values;WeChatMessage(Object...v){values=v;}}
 static class Fake implements Cursor {
  String[] names;Object[] values;int fault,closed,reads;
  Fake(String[] n,Object[] v,int f){names=n;values=v;fault=f;}
  void fail(int f){if(fault==f)throw new RuntimeException("fault"+f);}
  public String[] getColumnNames(){fail(1);return fault==2?null:names;}
  public boolean moveToFirst(){fail(3);return fault!=4;}
  public boolean moveToNext(){fail(5);return false;}
  public void close(){closed++;fail(6);}
  public int getType(int i){reads++;fail(7);Object v=values[i];return v==null?0:v instanceof byte[]?4:v instanceof Double?2:v instanceof Number?1:3;}
  public long getLong(int i){fail(8);return ((Number)values[i]).longValue();}
  public double getDouble(int i){fail(8);return ((Number)values[i]).doubleValue();}
  public byte[] getBlob(int i){fail(8);return (byte[])values[i];}
  public String getString(int i){fail(8);return (String)values[i];}
 }
 static class DB {
  List<Fake> cursors=new ArrayList<>();List<String> sqls=new ArrayList<>();List<String> args=new ArrayList<>();int maps;boolean queryThrows;
  Cursor rawQuery(String s,String[] a){sqls.add(s);args.add(Arrays.toString(a));if(queryThrows)throw new RuntimeException("query");return cursors.isEmpty()?null:cursors.remove(0);}
'''+method(db,'public Object cursorValue(')+ '\n'+method(db,'public List<Map<String, Object>> query(').replace('new HashMap<>()','countedMap()')+'''
  Map<String,Object> countedMap(){maps++;return new HashMap<>();}
  void log(String s){}
 }
 DB databaseApi=new DB();List<String> tables=Arrays.asList("a","b");int accounts;
 List<String> messageTables(){return tables;}
 String quoteMessageTable(String t){return t.isEmpty()?"":"`"+t+"`";}
 String selectColumns(){return "msgId, msgSvrId, type, status, isSend, createTime, talker, content, imgPath, reserved, transContent, flag";}
 String selfWxId(){accounts++;return "self";}
 void log(String s){}
 List<Map<String,Object>> queryRows(String s,String[] a){try{return databaseApi.query(s,a);}catch(Throwable e){return new ArrayList<>();}}
 List<WeChatMessage> queryMessages(String s,String[] a){List<WeChatMessage> out=new ArrayList<>();for(Map<String,Object> v:queryRows(s,a))out.add(toMessage(v));return out;}
'''+ '\n'.join(parts)+legacy+legacy_to+legacy_helpers+'''
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  String[] keys={"msgId","msgSvrId","type","status","isSend","createTime","talker","content","imgPath","reserved","transContent","flag","msgSource","unused","content"};
  Object[] variants={null,0L,Long.MAX_VALUE,3.75,"57","bad"," 57 ","2147483648",new byte[]{1,2}};
  int cases=0;
  for(int col=0;col<keys.length;col++)for(Object value:variants)for(int fault=0;fault<=8;fault++) {
   Object[] values=new Object[keys.length];Arrays.fill(values,"7");values[col]=value;
   Main a=new Main(),b=new Main();a.tables=b.tables=Collections.singletonList("a");
   Fake ca=new Fake(keys,values,fault),cb=new Fake(keys,values,fault);a.databaseApi.cursors.add(ca);b.databaseApi.cursors.add(cb);
   WeChatMessage x=a.getMessageById(1),y=b.legacy(1);
   check((x==null)==(y==null));if(x!=null)check(Arrays.equals(x.values,y.values));
   check(ca.closed==1&&cb.closed==1);check(ca.reads==cb.reads);check(a.databaseApi.sqls.equals(b.databaseApi.sqls));check(a.accounts==b.accounts);check(a.databaseApi.maps==0);cases++;
  }
  for(int hit=0;hit<4;hit++) {
   Main a=new Main(),b=new Main();List<Fake> all=new ArrayList<>();
   for(int n=0;n<3;n++){int fault=n==hit?0:4;Fake ca=new Fake(new String[]{"msgId","content"},new Object[]{9L,"hi"},fault),cb=new Fake(new String[]{"msgId","content"},new Object[]{9L,"hi"},fault);a.databaseApi.cursors.add(ca);b.databaseApi.cursors.add(cb);all.add(ca);all.add(cb);}
   WeChatMessage x=a.getMessageById(9),y=b.legacy(9);check((x==null)==(y==null));if(x!=null)check(Arrays.equals(x.values,y.values));check(a.databaseApi.sqls.equals(b.databaseApi.sqls));check(a.databaseApi.args.equals(b.databaseApi.args));
   for(int n=0;n<all.size();n++)check(all.get(n).closed==((n/2)<Math.min(hit+1,3)?1:0));
  }
  for(String[] names:new String[][]{new String[]{},new String[]{"MSGID","content"},new String[]{null,"content"}}) {
   Main left=new Main(),right=new Main();Object[] values=new Object[names.length];Arrays.fill(values,"value");
   Fake ca=new Fake(names,values,0),cb=new Fake(names,values,0);left.databaseApi.cursors.add(ca);right.databaseApi.cursors.add(cb);
   check(Arrays.equals(left.getMessageById(9).values,right.legacy(9).values));check(ca.closed==1&&cb.closed==1);
  }
  Main a=new Main();check(a.getMessageById(0)==null&&a.databaseApi.sqls.isEmpty());
  Main q=new Main(),v=new Main();q.databaseApi.queryThrows=v.databaseApi.queryThrows=true;check(q.getMessageById(9)==null&&v.legacy(9)==null);check(q.databaseApi.sqls.equals(v.databaseApi.sqls));
  a.databaseApi=null;check(a.getMessageById(9)==null);
  System.out.println("PASS "+cases+" conversion/fault comparisons; table order, fallback, args, close, zero row Maps");
 }
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'Main.java').write_text(code)
 subprocess.run(['javac','-classpath','/usr/lib/android-sdk/platforms/android-36/android.jar','-d',d,str(r/'app/src/main/java/t/mma/hooks/api/message/WeChatStoredMsgSource.java'),str(p/'Main.java')],check=True,timeout=30)
 subprocess.run(['java','-cp',d,'Main'],check=True,timeout=30)
