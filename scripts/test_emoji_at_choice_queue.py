"""Execute production request queue with search-window lifecycle transitions."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
code='''package t.mma.hooks.items.emojiat
class Request(val args:Array<Any?>)
fun main(){
 val q=EmojiAtChoiceQueue<Request>();val emoji=Any();val extension=Any()
 val first=Request(arrayOf("group@chatroom",emoji,null,Any(),extension,7))
 val second=Request(arrayOf("other@chatroom",Any(),null,Any(),null,0))
 q.offer(first);q.offer(second)
 check(q.acquire()===first);check(q.acquire()==null)
 check(q.retry(first));check(q.acquire()===first)
 check(first.args[1]===emoji&&first.args[4]===extension&&first.args[5]==7)
 check(q.complete(first));check(!q.complete(first));check(!q.retry(first))
 check(q.acquire()===second);check(q.complete(second));check(q.acquire()==null)
 q.offer(first);check(q.acquire()===first);q.clear()
 check(!q.complete(first)&&q.acquire()==null)
 repeat(1000){check(q.acquire()==null)}
 println("PASS retained search args, host destroy/retry, FIFO, single dialog, duplicate completion, cancel/disable cleanup and idle no work")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(r/'app/src/main/java/t/mma/hooks/items/emojiat/EmojiAtChoiceQueue.kt'),str(p/'Test.kt')],check=True,timeout=40)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.emojiat.TestKt'],check=True,timeout=10)