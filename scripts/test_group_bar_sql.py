"""Production SQL builder executed against SQLite, including native pagination/OR precedence."""
from pathlib import Path
import subprocess,tempfile,sqlite3,json,base64
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
rows=[('a',2,9),('b',0,8),('g@chatroom',1,7),('gh_pub',0,6),("o'neil",1,5),('ghXfake',0,4),('officialaccounts',4,3),('g@im.chatroom',0,2)]
cases=[]
for where in ['flag > ? OR username = ?', 'flag > ? AND username != ?', "username IN (SELECT username FROM rconversation WHERE content='order by limit')"]:
 for suffix in [' ORDER BY flag DESC LIMIT 4 OFFSET 1', ' ORDER BY flag DESC;', '']:
  cases.append('SELECT username FROM rconversation WHERE '+where+suffix)
cases += ['SELECT username FROM `rconversation` WHERE flag>0 ORDER BY flag DESC', 'SELECT username FROM [rconversation] WHERE flag>0 ORDER BY flag DESC', 'SELECT username FROM rconversation WHERE flag > 0 /* order by hidden */ ORDER BY flag DESC', 'SELECT username FROM rconversation WHERE content=\'it\'\'s limit\' OR flag>0 ORDER BY flag DESC']
main='''package t.mma.hooks.items.groupbar
import java.util.Base64
fun main(args:Array<String>){
 val filters=listOf("all","friends","rooms","official","unread","custom").map{ GroupBarFilter(BarGroup(it,it,members=setOf("a","o'neil")),setOf("a","b","o'neil","ghXfake"),setOf("gh_pub")) }
 val rows=listOf("a" to 2,"b" to 0,"g@chatroom" to 1,"gh_pub" to 0,"o'neil" to 1,"ghXfake" to 0,"officialaccounts" to 4,"g@im.chatroom" to 0)
 for(f in filters){
  println("P|"+f.group!!.id+"|"+Base64.getEncoder().encodeToString((f.predicate()?:"1").toByteArray()))
  println("A|"+f.group.id+"|"+rows.filter{f.accepts(it.first,it.second)}.joinToString(";"){it.first})
  for(sql in args){val out=GroupBarSql.rewrite(sql,f.predicate());check(GroupBarSql.rewrite(out,f.predicate())==out);println("Q|"+f.group.id+"|"+Base64.getEncoder().encodeToString(out.toByteArray()))}
 }
 check(GroupBarSql.rewrite("DELETE FROM rconversation", "0")=="DELETE FROM rconversation")
 check(GroupBarSql.rewrite("SELECT * FROM message WHERE x=1", "0")=="SELECT * FROM message WHERE x=1")
 check(GroupBarSql.rewrite("SELECT * FROM rconversation WHERE x=1 UNION SELECT * FROM rconversation", "0").contains("UNION"))
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Bar.kt').write_text('package t.mma.hooks.items.groupbar\ndata class BarGroup(val id:String,val name:String,val members:Set<String> = emptySet())');(p/'Test.kt').write_text(main)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(r/'app/src/main/java/t/mma/hooks/items/groupbar/GroupBarFilter.kt'),*map(str,p.glob('*.kt'))],check=True,timeout=45)
 output=subprocess.check_output(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.groupbar.TestKt',*cases],text=True,timeout=15)
 db=sqlite3.connect(':memory:');db.execute('CREATE TABLE rconversation(username TEXT, unReadCount INTEGER, flag INTEGER,content TEXT)');db.executemany('INSERT INTO rconversation VALUES(?,?,?,?)',[(*row,'order by limit') for row in rows])
 predicates={};accepts={};index={};count=0
 for line in output.splitlines():
  kind,group,value=line.split('|',2)
  if kind=='P':predicates[group]=base64.b64decode(value).decode();index[group]=0
  elif kind=='A':accepts[group]=value.split(';') if value else []
  else:
   original=cases[index[group]];index[group]+=1
   sql=base64.b64decode(value).decode();params=(4,'b') if '?' in sql else ()
   # Compare to original row ordering followed by semantic membership, respecting LIMIT/OFFSET.
   full=original.split(' ORDER BY')[0].rstrip(';')
   expected_ids={x[0] for x in db.execute(full,params)} & set(accepts[group])
   tail=original[len(full):]
   base_ids=[x[0] for x in db.execute(full,params) if x[0] in expected_ids]
   if 'ORDER BY' in tail:base_ids.sort(key=lambda x:next(row[2] for row in rows if row[0]==x),reverse=True)
   expected=base_ids[1:5] if 'LIMIT 4 OFFSET 1' in tail else base_ids
   actual=[x[0] for x in db.execute(sql,params)]
   assert all(x in expected_ids for x in actual),(group,sql,actual,expected_ids)
   assert actual==expected,(group,sql,actual,expected)
   count+=1
 for group,predicate in predicates.items():
  actual=[x[0] for x in db.execute('SELECT username FROM rconversation WHERE '+predicate)]
  assert actual==accepts[group],(group,actual,accepts[group])
 print(f'PASS {count} SQLite queries; SQL/incremental membership parity, quotes, placeholders, OR precedence, ORDER/LIMIT/OFFSET, idempotence and non-target query exclusion')