"""Compiler-free source and independent contrast checks; not production/UI execution."""
from pathlib import Path
import ast
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
feature = ROOT / 'app/src/main/java/t/mma/hooks/items/moments/MomentsCardFeature.kt'
s = feature.read_text()
settings = feature.with_name('MomentsCardSettings.kt').read_text()
assert s.count('themeResources = view.resources') == 2
assert 'TimelineBackground(view.background, view.resources)' in s
assert 'ColorDrawable(timelineColor(resources))' in s
assert 'resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK' in s
assert 'Configuration.UI_MODE_NIGHT_YES' in s
assert 'if (isNight(resources)) MomentsCardSettings.CARD_BACKGROUND_DARK else MomentsCardSettings.CARD_BACKGROUND' in s
assert 'if (isNight(resources)) MomentsCardSettings.TIMELINE_BACKGROUND_DARK else Color.WHITE' in s
assert 'if (colors == null && paint.color != nextFill)' in s
assert 'paint.alpha = alpha' in s and 'it.alpha = alpha' in s
assert 'if (color != next) color = next' in s
for forbidden in ['setTextColor(', 'setText(', 'setOnClickListener(', 'TextView', 'ValueAnimator', 'OnScrollListener', 'requestLayout(', 'postInvalidate(', 'invalidateSelf()\n        themeResources']:
    assert forbidden not in s, forbidden
body = s.split('internal class MomentsCardDrawable(', 1)[1].split('override fun draw(canvas: Canvas)', 1)[1].split('override fun setAlpha', 1)[0]
for forbidden in ['Paint(', 'LinearGradient(', 'RectF(', 'post(', 'requestLayout(', 'invalidateSelf(']:
    assert forbidden not in body, forbidden
assert 'private const val MAX_VIEWS = 192' in s
assert 'view.background === old.installedBackground?.get()' in s
assert 'view.background === installed' in s and 'view.background = installed.original' in s
assert 'view.removeOnAttachStateChangeListener(rowAttachListener)' in s
old = subprocess.check_output(['git','show','b3c3865:app/src/main/java/t/mma/hooks/items/moments/MomentsCardFeature.kt'],cwd=ROOT,text=True)
# Hook entrypoints/style geometry and restoration are unchanged.
for start,end in [('    @Synchronized fun install()', '    private fun style('), ('        if (view.elevation != 0f)', '    private fun restore('), ('    private fun restore(', '    private data class Original(')]:
    assert s.split(start,1)[1].split(end,1)[0] == old.split(start,1)[1].split(end,1)[0], start
assert 'ColorDrawable(Color.WHITE)' in old and 'themeResources' not in old
colors = {name:int(value,16) for name,value in re.findall(r'const val (\w+) = 0x([A-Fa-f0-9]+)\.toInt\(\)',settings)}
assert colors['CARD_BACKGROUND']==0xfff3f3f3
assert colors['CARD_BACKGROUND_DARK']==0xff232323
assert colors['TIMELINE_BACKGROUND_DARK']==0xff191919

def luminance(rgb):
    parts = [(rgb >> shift & 255) / 255 for shift in (16,8,0)]
    parts = [x/12.92 if x<=.04045 else ((x+.055)/1.055)**2.4 for x in parts]
    return sum(x*w for x,w in zip(parts,(.2126,.7152,.0722)))

def contrast(a,b):
    x,y = sorted((luminance(a),luminance(b)))
    return (y+.05)/(x+.05)

# Representative colors only; not measured native text colors from the screenshot.
for label,text,background in [('dark light text',0xffeeeeee,colors['CARD_BACKGROUND_DARK']),('dark secondary text',0xff999999,colors['CARD_BACKGROUND_DARK']),('light dark text',0xff222222,colors['CARD_BACKGROUND'])]:
    ratio=contrast(text,background)
    assert ratio>=4.5,(label,ratio)
    print(f'PASS independent contrast example: {label} {ratio:.2f}:1')
assert contrast(0xffeeeeee,colors['CARD_BACKGROUND'])<1.1
for name in ['test_moments_card.py','test_weather_moments_surface.py','check_moments_night_surface_static.py']:
    ast.parse((ROOT/'scripts'/name).read_text())
print('PASS source contracts: host night configuration, both fill creation paths, timeline, alpha retention, no text/click mutation, unchanged Hook/layout/restore, bounded state')
print('PASS negative baseline: previous revision forces light surfaces and has no theme input')
print('BOUNDARY: no Gradle/Kotlin/SDK/APK/R8 build; synthetic Kotlin fixture not executed; device visual/performance acceptance remains pending')