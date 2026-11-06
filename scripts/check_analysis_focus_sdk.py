"""SDK compile for the production focus editor and icons; no Gradle or device."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'app/src/main/java/t/mma/hooks/items/conversationanalysis'
LIBS = Path('/tmp/pl-kotlin-libs')
STDLIB = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
SDK = Path('/usr/lib/android-sdk/platforms/android-36/android.jar')

with tempfile.TemporaryDirectory(prefix='analysis-focus-sdk-') as directory:
    subprocess.run([
        'java', '-Xmx384m', '-cp', ':'.join(map(str, LIBS.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
        '-classpath', f'{STDLIB}:{SDK}', '-d', str(Path(directory) / 'ui.jar'),
        *[str(BASE / name) for name in (
            'ConversationAnalysisFocusInput.kt',
            'ConversationAnalysisIconPainter.kt',
            'ConversationAnalysisIconView.kt')]
    ], check=True, timeout=55)
print('PASS production focus input, gradient drawable and icon View/painter compiled against Android SDK')

report = (BASE / 'ConversationAnalysisReportDialog.kt').read_text()
focus = (BASE / 'ConversationAnalysisFocusInput.kt').read_text()
assert 'ConversationAnalysisFocusInput.create(activity)' in report
assert 'content.addView(focusInput, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))' in report
assert 'setSingleLine(true)' in focus and 'setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1' in focus
assert 'InputType.TYPE_TEXT_FLAG_MULTI_LINE' in focus
assert 'setHorizontallyScrolling(false)' in focus
assert 'addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))' in focus
assert 'Configuration.UI_MODE_NIGHT_YES' in focus
assert 'override fun onBoundsChange' in focus and 'LinearGradient(' in focus
print('PASS static contracts: single-line compact prompt, content-sized multiline editor, theme colors, bounds-based gradient')
