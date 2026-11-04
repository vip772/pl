"""Compile actual chip methods with actual toolbar settings and companion visibility.
Dependencies are stubbed; declarations under test are copied verbatim, not public substitutes.
No Gradle, APK, R8, Compose or device validation.
"""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
base=r/'app/src/main/java/t/mma/hooks/items'
header=(base/'floatingchat/FloatingChatHeaderFeature.kt').read_text()
toolbar=(base/'chattoolbar/ChatToolbarRuntime.kt').read_text()
start=header.index('    private fun applyBottomNewMessageChip(')
end=header.index('    private fun isNewMessageChip(',start)
assert 'ChatToolbarRuntime.TOOLBAR_TAG' not in header
assert toolbar.count('ChatToolbarSettings.TOOLBAR_TAG') == 2
assert 'const val TOOLBAR_TAG' not in toolbar
companion=toolbar[toolbar.index('    private companion object {'):toolbar.rfind('\n}')]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
sdk=Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
fixture='''package t.mma.hooks.items.floatingchat
import android.view.*
import android.widget.FrameLayout
import java.util.WeakHashMap
class Fixture {
 class ChipMarginState(var bottom:Int,var appliedBottom:Int=bottom)
 val newMessageChipMargins=WeakHashMap<View,ChipMarginState>()
 val toolbarPrefs:android.content.SharedPreferences=TODO()
 val newMessageChipLocation=IntArray(2)
 val CHAT_TOOLBAR_CHIP_GAP_DP=8
 fun resourceId(v:View,n:String)=0
 fun find(v:View,p:(View)->Boolean):View?=null
 fun dp(v:Int)=v
'''+header[start:end]+'}\n'
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 (p/'Header.kt').write_text(fixture)
 (p/'Toolbar.kt').write_text('package t.mma.hooks.items.chattoolbar\nclass ChatToolbarRuntime {\n'+companion+'\n}\n')
 (p/'Storage.kt').write_text('package t.mma.preferences\nobject HchatStorage { fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO() }')
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',f'{std}:{sdk}','-d',str(p/'test.jar')]
 result=subprocess.run(cmd+[str(x) for x in p.glob('*.kt')]+[str(base/'chattoolbar/ChatToolbarSettings.kt')],text=True,capture_output=True,timeout=90)
 if result.returncode:
  print(result.stderr)
  raise SystemExit(result.returncode)
 # Negative control: restore the exact bad cross-feature access against the real private companion.
 baseline=subprocess.check_output(['git','show','80c4ed2:app/src/main/java/t/mma/hooks/items/chattoolbar/ChatToolbarRuntime.kt'],cwd=r,text=True)
 old_companion=baseline[baseline.index('    private companion object {'):baseline.rfind('\n}')]
 (p/'Toolbar.kt').write_text('package t.mma.hooks.items.chattoolbar\nclass ChatToolbarRuntime {\n'+old_companion+'\n}\n')
 (p/'Header.kt').write_text(fixture.replace('ChatToolbarSettings.TOOLBAR_TAG','ChatToolbarRuntime.TOOLBAR_TAG'))
 negative=subprocess.run(cmd+[str(x) for x in p.glob('*.kt')]+[str(base/'chattoolbar/ChatToolbarSettings.kt')],text=True,capture_output=True,timeout=90)
 assert negative.returncode != 0 and 'private' in negative.stderr and 'ChatToolbarRuntime' in negative.stderr, negative.stderr
 print('PASS negative control rejects exact 80c4ed2 private-companion access with real Kotlin compiler')
print('PASS actual production chip methods + actual ChatToolbarSettings + verbatim private runtime companion SDK contract; no visibility-substituting stubs')