"""Compile complete production coordinator and actual shared declarations against Android SDK.
No Gradle/APK/R8/device validation; retains the historical visibility negative control.
"""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];base=r/'app/src/main/java/t/mma/hooks/items'
toolbar=(base/'chattoolbar/ChatToolbarRuntime.kt').read_text()
header=(base/'floatingchat/FloatingChatHeaderFeature.kt').read_text()
assert 'applyBottomNewMessageChip' not in header and 'newMessageChipMargins' not in header
assert toolbar.count('ChatToolbarSettings.TOOLBAR_TAG')==2
assert 'const val TOOLBAR_TAG' not in toolbar
companion=toolbar[toolbar.index('    private companion object {'):toolbar.rfind('\n}')]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
sdk=Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 (p/'Header.kt').write_text('package t.mma.hooks.items.floatingchat\nimport t.mma.hooks.items.chattoolbar.ChatToolbarSettings\nclass Good {val value=ChatToolbarSettings.TOOLBAR_TAG}')
 (p/'Toolbar.kt').write_text('package t.mma.hooks.items.chattoolbar\nclass ChatToolbarRuntime {\n'+companion+'\n}')
 (p/'Storage.kt').write_text('package t.mma.preferences\nobject HchatStorage { fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO() }')
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',f'{std}:{sdk}','-d',str(p/'test.jar')]
 subprocess.run(cmd+[str(x) for x in p.glob('*.kt')]+[str(base/'chattoolbar/ChatToolbarSettings.kt'),str(base/'chattoolbar/ChatToolbarNewMessagePosition.kt')],check=True,timeout=90)
 # Negative control: restore the exact bad cross-feature access against the real private companion.
 baseline=subprocess.check_output(['git','show','80c4ed2:app/src/main/java/t/mma/hooks/items/chattoolbar/ChatToolbarRuntime.kt'],cwd=r,text=True)
 old_companion=baseline[baseline.index('    private companion object {'):baseline.rfind('\n}')]
 (p/'Toolbar.kt').write_text('package t.mma.hooks.items.chattoolbar\nclass ChatToolbarRuntime {\n'+old_companion+'\n}\n')
 (p/'Header.kt').write_text('package t.mma.hooks.items.floatingchat\nimport t.mma.hooks.items.chattoolbar.ChatToolbarRuntime\nclass Bad {val value=ChatToolbarRuntime.TOOLBAR_TAG}')
 negative=subprocess.run(cmd+[str(x) for x in p.glob('*.kt')]+[str(base/'chattoolbar/ChatToolbarSettings.kt')],text=True,capture_output=True,timeout=90)
 assert negative.returncode != 0 and 'private' in negative.stderr and 'ChatToolbarRuntime' in negative.stderr, negative.stderr
 print('PASS negative control rejects exact 80c4ed2 private-companion access with real Kotlin compiler')
print('PASS complete production coordinator and real shared declarations Android SDK compilation; historical private-access negative control retained')
