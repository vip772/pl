"""Check native viewport coordination against the user-confirmed baseline; no compilation."""
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
path = 'app/src/main/java/t/mma/hooks/items/groupbar/GroupBarRuntime.kt'
current = (root / path).read_text()
baseline = subprocess.check_output(['git', 'show', f'c9932b0:{path}'], cwd=root, text=True)

def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return re.sub(r'\s+', '', re.sub(r'//[^\n]*', '', source[start:end]))

for signature in (
    'private fun applyNativeOffset(', 'fun beforeNativeOffset(',
    'fun beforeTaskBarResize(', 'fun beforeNativeSelection(', 'fun afterNativeOffset(',
    'private fun selectNativeTop(', 'fun switchGroup(', 'private fun restoreAvoidance(',
):
    assert method(current, signature) == method(baseline, signature), signature
for forbidden in ('list.setPadding(', 'list.clipToPadding', 'list.clipChildren',
                  'appliedPaddingInset', 'nativeSelectionOffset',
                  'RelativeLayout.BELOW, bar.id'):
    assert forbidden not in current, forbidden
assert 'param.args[1] =' not in current
assert 'ensureBarOnTop()' in current
assert '+ titleTranslation(parent)' in current
assert 'RejectedExecutionException' in current and '@Synchronized fun destroy()' in current

# Prove all three rejected fixes were removed without resetting unrelated files.
for restored in (
    'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt',
    'app/src/main/java/t/mma/hooks/items/custombottombar/CustomBottomBarFeature.kt',
    'docs/BACKGROUND_BEAUTY_1058_MAIN_STATUS_MASK.md',
):
    expected = subprocess.check_output(['git', 'show', f'46f9ec6:{restored}'], cwd=root)
    assert (root / restored).read_bytes() == expected, restored
assert not (root / 'docs/BACKGROUND_BEAUTY_STATUS_PARENT_CHAIN.md').exists()
assert not (root / 'scripts/check_background_status_scope.py').exists()
print('PASS eight production geometry/selection methods match c9932b0; no added padding/clipping/selection-argument hook')
print('PASS later bar ordering/coordinates/lifecycle retained; three rejected status fixes exactly removed')
