"""Execute production batched statistics against Cursor/database fixtures; no device/Gradle."""
from pathlib import Path
import subprocess, tempfile, sqlite3
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/api/contact/WeChatContactApi.java').read_text()
a=s.index('    public static final class GroupMemberStatistics');b=s.index('    public List<String> getGroupMemberIds',a)
code=s[a:b]
stub='''import java.util.*;
class TextUtils { static boolean isEmpty(String s){return s==null||s.isEmpty();} }
public class Test {
 DB databaseApi=new DB(); boolean isGroup(String s){return s!=null&&s.endsWith("@chatroom");}
 static class Profile {int gender;}
 Profile parseLvContactProfile(byte[] b){Profile p=new Profile();p.gender=b==null?0:b[0];return p;}
 void log(String s){}
 static class DB {
  String members="self;a;b;unknown;missing;a";int failAt=0,calls=0,closed=0;
  android.database.Cursor rawQuery(String sql,String[] args){
   calls++;if(failAt==calls)return null;
   List<Object[]> rows=new ArrayList<>();
   if(sql.contains("SELECT memberlist")){if(members!=null)rows.add(new Object[]{members});}
   else if(sql.startsWith("SELECT value"))rows.add(new Object[]{"self"});
   else for(String id:args){if(id.equals("missing"))continue;
    int gender=id.equals("a")?1:id.equals("b")?2:0;
    rows.add(new Object[]{id,gender==0?null:new byte[]{(byte)gender},id.equals("a")||id.equals("self")?1:0});}
   return new android.database.Cursor(rows,()->closed++);
  }
 }
'''+code+'''
 public static void main(String[] args){
  Test t=new Test();GroupMemberStatistics v=t.getGroupMemberStatistics("r@chatroom");
  if(v.male!=1||v.female!=1||v.unknown!=3||v.friends!=1)throw new AssertionError();
  if(t.databaseApi.calls!=3||t.databaseApi.closed!=3)throw new AssertionError();
  for(int i=1;i<=3;i++){t=new Test();t.databaseApi.failAt=i;if(t.getGroupMemberStatistics("r@chatroom")!=null)throw new AssertionError();}
  t=new Test();t.databaseApi.members="";if(t.getGroupMemberStatistics("r@chatroom")!=null)throw new AssertionError();
  t=new Test();t.databaseApi.members=null;if(t.getGroupMemberStatistics("r@chatroom")!=null)throw new AssertionError();
  t=new Test();StringJoiner members=new StringJoiner(";");for(int i=0;i<450;i++)members.add("x"+i);t.databaseApi.members=members.toString();
  v=t.getGroupMemberStatistics("r@chatroom");if(v.unknown!=450||t.databaseApi.calls!=5||t.databaseApi.closed!=5)throw new AssertionError();
  Thread.currentThread().interrupt();if(t.getGroupMemberStatistics("r@chatroom")!=null)throw new AssertionError();Thread.interrupted();
  System.out.println("PASS production statistics: duplicate removal, missing/unknown gender, self excluded from friends, 200-member batches, read failure, empty/unavailable list, cancellation and cursor closure");
 }
}
'''
cursor='''package android.database;
import java.util.*;
public class Cursor implements AutoCloseable {
 final List<Object[]> rows;final Runnable closed;int i=-1;
 public Cursor(List<Object[]> rows,Runnable closed){this.rows=rows;this.closed=closed;}
 public boolean moveToFirst(){i=0;return !rows.isEmpty();} public boolean moveToNext(){return ++i<rows.size();}
 public boolean isNull(int c){return rows.get(i)[c]==null;}public String getString(int c){return (String)rows.get(i)[c];}
 public byte[] getBlob(int c){return (byte[])rows.get(i)[c];}public int getInt(int c){return (int)rows.get(i)[c];}
 public void close(){closed.run();}
}
'''
with tempfile.TemporaryDirectory() as td:
    p=Path(td);(p/'Test.java').write_text(stub);(p/'Cursor.java').write_text(cursor)
    subprocess.run(['javac','-d',td,str(p/'Test.java'),str(p/'Cursor.java')],check=True,timeout=30)
    subprocess.run(['java','-cp',td,'Test'],check=True,timeout=15)
# Exercise the exact predicate embedded in production rather than a parallel friend rule.
a=s.index('                String sql = "SELECT r.username,r.lvbuff,CASE');b=s.index(';',a)
import re
sql=''.join(re.findall(r'"([^"]*)"',s[a:b])).replace('IN ()','IN (?)')
db=sqlite3.connect(':memory:')
db.executescript('CREATE TABLE rcontact(username TEXT,lvbuff BLOB,verifyFlag INT,type INT,encryptUsername TEXT);CREATE TABLE userinfo(id INT,value TEXT);INSERT INTO userinfo VALUES(2,"self");')
for flag,kind,encrypt,expected in [(0,1,'yes',1),(0,9,'yes',0),(0,33,'yes',0),(1,1,'yes',0),(0,1,'',0),(0,0,'yes',0)]:
    db.execute('DELETE FROM rcontact');db.execute('INSERT INTO rcontact VALUES(?,?,?,?,?)',('a',None,flag,kind,encrypt))
    assert db.execute(sql,('a',)).fetchone()[2]==expected
print('PASS production SQL friend predicate: friend, blacklist, deleted, official, nonfriend and empty encryption identity')
