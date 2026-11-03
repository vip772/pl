#!/usr/bin/env python3
"""Source-contract checks only; no compilation, Android execution or UI acceptance."""
from pathlib import Path
import subprocess

r = Path(__file__).resolve().parents[1]
p = 'app/src/main/java/t/mma/hooks/items/homesidepanel/HomeSidePanelRuntime.kt'
s = (r / p).read_text()
base = subprocess.check_output(['git', 'show', 'ab67d25:' + p], cwd=r, text=True)

def section(text, start, end):
    return text[text.index(start):text.index(end, text.index(start))]

b = section(s, '    private fun bootstrapHomepage(', '    private fun hook(')
assert 'bootstrapHomepage(launcher)' in section(s, '    fun install()', '    private fun bootstrapHomepage(')
assert b.count('KavaReflector.findMethod(launcher, "getInstance")') == 1
assert b.index('KavaReflector.findMethod') < b.index('object : Runnable')
assert 'if (!active.get() || sessions.isNotEmpty()) return' in b
assert 'isLauncher(activity) && !activity.isFinishing && !activity.isDestroyed' in b
assert 'currentActivity()?.takeIf(::usable)' in b
assert '(KavaReflector.invoke(getInstance, null) as? Activity)?.takeIf(::usable)' in b
assert 'current?.let(::attachNow)' in b
assert 'active.get() && sessions.isEmpty() && attempts < 40' in b
assert 'handler.postDelayed(this, 250L)' in b
assert 'attempts++' in b and 'postActive { bootstrap.run() }' in b
for forbidden in ('Thread(', 'Thread.sleep', 'postOnAnimation', 'viewTreeObserver', 'findViewById', 'HLog.', 'updateCurrentActivity'):
    assert forbidden not in b, forbidden
print('PASS source contract: main-thread bootstrap, native fallback, live Launcher filter, 40-attempt bound, no scanning/global activity mutation')

for start, end in [
    ('    private val active =', '    private val HOME_TITLE'),
    ('    private fun attachNow(', '    private fun isLauncher'),
]:
    assert section(s, start, end) == section(base, start, end), start
assert s[s.index('    private fun isLauncher'):] == base[base.index('    private fun isLauncher'):]
assert 'handler.removeCallbacksAndMessages(null)' in section(s, '    fun destroy()', '    private fun isLauncher')
print('PASS source preservation: callback gate, avatar executor, attach/detach, cleanup isolation, hooks/listeners, and complete Session unchanged from ab67d25')

for other in [
    'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatActionBarBackgrounds.kt',
    'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatCardVisuals.kt',
    'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderCapsule.kt',
    'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderFeature.kt',
    'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt',
]:
    assert (r / other).read_bytes() == subprocess.check_output(['git', 'show', 'ab67d25:' + other], cwd=r), other
print('PASS source preservation: floating header and immersive background paths unchanged; gray symptom NOT claimed fixed')
print('BOUNDARY: source assertions only; no Kotlin/Android build, device behavior, memory or frame-time verification')
