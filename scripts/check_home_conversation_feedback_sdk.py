"""Compile production conversation eligibility and feedback ownership with Android SDK."""
from pathlib import Path
import subprocess, tempfile
r = Path(__file__).resolve().parents[1]
s = (r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a = s.index('    private fun isConversationFeedbackRow(')
b = s.index('    private fun updateListRipple(', a)
method = s[a:b]
a = s.index('        val conversationFeedback =')
b = s.index('\n        if (slot == BackgroundBeautySettings.Slot.CHAT', a)
predicate = s[a:b]
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
compiler = ['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect']
with tempfile.TemporaryDirectory() as td:
 p = Path(td)
 code = '''import android.view.View
import android.view.ViewGroup
object BackgroundBeautySettings {enum class Slot {WECHAT,CHAT,ME}}
class Check {
 val homeLists=java.util.WeakHashMap<View,BackgroundBeautySettings.Slot>()
 val appGridItems=java.util.WeakHashMap<View,Boolean>()
 fun resourceId(n:String)=1
 fun isWechatTaskSurface(v:View)=false
 METHOD
 fun feedback(view:View,slot:BackgroundBeautySettings.Slot,preserveFeedback:Boolean,footerHost:Boolean,inAppPanel:Boolean):Boolean {
 PREDICATE
 return feedback
 }
}
'''.replace('METHOD',method).replace('PREDICATE',predicate)
 (p/'Check.kt').write_text(code)
 (p/'Surface.kt').write_text('package t.mma.hooks.items.homeextension\nobject HomeExtensionSurface {fun contains(v:android.view.View)=false}')
 sdk=Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
 x=subprocess.run(compiler+['-classpath',f'{std}:{sdk}','-d',str(p/'sdk.jar'),str(p/'Check.kt'),str(p/'Surface.kt')],capture_output=True,text=True,timeout=45)
 assert x.returncode==0,x.stdout+x.stderr
print('PASS production title eligibility and homepage surface ownership compile against real Android SDK')
assert 'view.selector = if (homeLists[view] == BackgroundBeautySettings.Slot.WECHAT)' in s
assert 'manualOnly = slot == BackgroundBeautySettings.Slot.WECHAT ||' in s
assert 'clearListRipple(list)' in s
print('PASS homepage selector and automatic pressed state disabled; stale row cleanup retained')
