"""Execute the production pre-dialog guard: native payloads must remain synchronous."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/emojiat/EmojiAtRuntime.kt').read_text()
start=s.index('                if (!enabled || scope.get() != null) return')
end=s.index('                val talker',start)
guard=s[start:end]
code='''class Param(val args:Array<Any?>) {var blocked=false}
class Scope {var active:Any?=null;fun get()=active}
class Runtime {var enabled=true;val scope=Scope()
 fun before(param:Param) {
'''+guard+'''
 param.blocked=true
 }
}
fun main() {
 val r=Runtime()
 fun request(message:Any?,extra:Any?):Param {val p=Param(arrayOf("room@chatroom",Any(),message,Any(),extra,0));r.before(p);return p}
 check(request(null,null).blocked)
 check(!request(Any(),null).blocked)
 check(!request(null,Any()).blocked)
 check(!request(Any(),Any()).blocked)
 r.enabled=false;check(!request(null,null).blocked)
 r.enabled=true;r.scope.active=Any();check(!request(null,null).blocked)
 println("PASS native existing-message/extended/search payloads bypass dialog; normal new sends retain chooser; disabled and recursive sends pass through")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)
