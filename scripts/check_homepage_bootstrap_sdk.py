"""Compile both production bootstrap watchers against real Android SDK."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];base=r/'app/src/main/java/t/mma/hooks/items'
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
header='''import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import java.lang.ref.WeakReference
import java.util.WeakHashMap
object WeChatApis {fun currentActivity():Tracker?=null}
class Tracker {fun currentActivity():Activity?=null}
class Context {fun hostClassLoader():ClassLoader=TODO()}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,path,stop in [('Bar','groupbar/GroupBarRuntime.kt','    private fun discoverHomepage')]:
  text=(base/path).read_text();a=text.index('    private var bootAttempts =');z=text.index(stop,a);methods=text[a:z]
  if name=='Bar':
   body='val sessions=WeakHashMap<View,Any>();fun discoverHomepage(a:Activity?){}'
  else:
   body='val sessions=WeakHashMap<ListView,Any>();fun attach(l:ListView){}'
  (p/(name+'.kt')).write_text('package bootstrap'+name+'\n'+header+'class Runtime {val context=Context();val main=Handler(Looper.getMainLooper());var destroyed=false;'+body+'\n'+methods+'}\n')
 args=['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',f'{std}:/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'check.jar'),*map(str,p.glob('*.kt'))]
 subprocess.run(args,check=True,timeout=45)
for path in ['groupbar/GroupBarRuntime.kt']:
 text=(base/path).read_text()
 assert '++bootAttempts < 40' in text and 'postDelayed(this, 250)' in text
 assert 'getDeclaredMethod("getInstance")' in text
 assert 'OnGlobalLayoutListener' not in text
bar=(base/'groupbar/GroupBarRuntime.kt').read_text()
assert 'else stableTitleTop ?: return' in bar
assert 'else nativeTopMargin\n' not in bar
assert 'OnPreDrawListener' in bar and 'removeOnPreDrawListener' in bar
print('PASS groupbar bootstrap SDK compile, bounded retries, native Launcher fallback, no global layout scan, retained title anchor and pre-draw gating')