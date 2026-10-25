"""Compile production hint runtime AND styled renderer against SDK; host dependencies are stubs."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
test=r/'scripts/test_input_hint_lifecycle.py'
ns={'__file__':str(test)}
s=test.read_text();exec(s[:s.index('with tempfile.TemporaryDirectory()')],ns)
files=ns['files'];lib=ns['lib'];std=ns['std']
files['Runtime.kt']=files['Runtime.kt'].split('fun main(){')[0]
for key in ['Context.kt','Handler.kt','View.kt','Text.kt','Widget.kt','Footer.kt','Span.kt']:
 files.pop(key)
files['Script.kt']='''package t.mma.hooks.items.script
object ScriptSendButtonHook {fun findInputView(f:Any):Any?=null;fun trackInputView(i:Any){}}
'''
files['Settings.kt']=(r/'app/src/main/java/t/mma/hooks/items/inputhint/InputHintSettings.kt').read_text()
files['Storage.kt']='''package t.mma.preferences
object HchatStorage {fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()}
'''
files['Stats.kt']='''package t.mma.hooks.items.inputhint
object OutgoingMessageStatsRepository {class Subscription;fun install(l:(String,Throwable?)->Unit)=true;fun subscribe(f:()->Unit)=Subscription();fun current()=InputHintStats()}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,v in files.items():(p/n).write_text(v)
 result=subprocess.run(['java','-Xmx384m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',f'{std}:/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'check.jar'),*[str(p/n) for n in files]],capture_output=True,text=True,timeout=60)
 if result.returncode:print(result.stderr)
 result.check_returncode()
 print('PASS: production InputHintRuntime + InputHintSettings SDK compile (host dependencies stubbed)')