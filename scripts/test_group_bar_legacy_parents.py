"""Run production SQL/membership against legacy module parents without mutating storage."""
from pathlib import Path
import subprocess, tempfile, sqlite3
r = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
main = '''package t.mma.hooks.items.groupbar
fun main() {
 val all = GroupBarFilter(BarGroup("all", "全部"))
 val room = GroupBarFilter(BarGroup("rooms", "群聊"))
 val off = GroupBarFilter(null)
 check(all.acceptsNativeParent(false, "wxid_hchat_group_old"))
 check(all.acceptsNativeParent(false, "hchat_conv_group:old"))
 check(!all.acceptsNativeParent(false, "officialaccounts"))
 check(!off.acceptsNativeParent(false, "wxid_hchat_group_old"))
 check(!all.accepts("wxid_hchat_group_old", 3))
 check(!all.accepts("hchat_conv_group:old", 3))
 check(room.accepts("48651937268@chatroom", 1))
 val sql = "SELECT username FROM rconversation WHERE flag > ? AND (parentRef is null OR parentRef='' OR parentRef='message_fold') ORDER BY flag DESC LIMIT 20 OFFSET 0"
 println(GroupBarSql.rewrite(sql, all.predicate()))
 println(GroupBarSql.rewrite(sql, room.predicate()))
 check(GroupBarSql.rewrite(sql, off.predicate()) == sql)
 val quoted = "SELECT username FROM rconversation WHERE content='parentRef is null' AND flag>?"
 check(!GroupBarSql.rewrite(quoted, all.predicate()).contains("OR parentRef LIKE"))
 check(GroupBarSql.rewrite(GroupBarSql.rewrite(sql, all.predicate()), all.predicate()) == GroupBarSql.rewrite(sql, all.predicate()))
}
'''
with tempfile.TemporaryDirectory() as td:
 p = Path(td)
 (p/'Bar.kt').write_text('package t.mma.hooks.items.groupbar\ndata class BarGroup(val id:String,val name:String,val members:Set<String> = emptySet())')
 (p/'Test.kt').write_text(main)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(r/'app/src/main/java/t/mma/hooks/items/groupbar/GroupBarFilter.kt'),*map(str,p.glob('*.kt'))],check=True,timeout=45)
 queries = subprocess.check_output(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.groupbar.TestKt'],text=True,timeout=10).splitlines()
 db = sqlite3.connect(':memory:')
 db.execute('CREATE TABLE rconversation(username TEXT,parentRef TEXT,flag INT,content TEXT)')
 rows = [('normal',None,9,''),('48651937268@chatroom','wxid_hchat_group_old',8,''),('old@chatroom','hchat_conv_group:old',7,''),('nativehidden@chatroom','other_native_parent',6,''),('wxid_hchat_group_old','',5,''),('hchat_conv_group:old','',4,''),('blocked@chatroom','wxid_hchat_group_old',0,'')]
 db.executemany('INSERT INTO rconversation VALUES(?,?,?,?)',rows)
 assert [x[0] for x in db.execute(queries[0],(1,))] == ['normal','48651937268@chatroom','old@chatroom']
 assert [x[0] for x in db.execute(queries[1],(1,))] == ['48651937268@chatroom','old@chatroom']
 assert list(db.execute('SELECT * FROM rconversation')) == rows
print('PASS legacy parents visible, virtual rows excluded, native hidden/flag filters retained, incremental parity, disabled passthrough, no DB mutations')
