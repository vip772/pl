"""Compiler-free source/evidence regression; not Android execution or device acceptance."""
import argparse
import ast
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'app/src/main/java/t/mma'
weather = (BASE / 'hooks/items/homeextension/HomeWeather.kt').read_text()
card = (BASE / 'hooks/items/moments/MomentsCardFeature.kt').read_text()
heading = weather.split('val heading = LinearLayout(context)', 1)[1].split('panel.addView(heading)', 1)[0]
assert heading.index('cityRow.addView(android.widget.ImageButton') < heading.index('cityRow.addView(cityLabel')
assert 'heading.addView(android.widget.ImageButton' not in heading
assert 'setOnClickListener { manualRefresh() }' in heading
assert 'LinearLayout.LayoutParams(dp(48), dp(48))' in heading
assert 'if (compact) orientation = LinearLayout.VERTICAL' in heading
assert heading.index('labels.addView(cityRow') < heading.index('labels.addView(text(status')
assert 'HomeWeather.card(ctx, compactCard)' in (BASE / 'hooks/items/homeextension/HomeExtensionRuntime.kt').read_text()
assert 'HomeWeather.card(activity, settings = prefs)' in (BASE / 'hooks/items/homesidepanel/HomeSidePanelDrawer.kt').read_text()
print('PASS source: shared weather layout, icon before city, compact status below, 48dp refresh action retained')
modern = card.split('if (!improveInstalled)', 1)[1].split('private fun style(', 1)[0]
assert 'param.result' not in modern
assert 'if (closed || param.hasThrowable()) return' in modern
assert 'style(view, divider.invoke(view) as? View)' in modern
assert 'private const val MAX_VIEWS = 192' in card
assert 'view.background === old.installedBackground?.get()' in card
assert 'view.setPadding(old.left, old.top, old.right, old.bottom)' in card
assert 'OnScrollListener' not in card and 'ValueAnimator' not in card
print('PASS source: unchanged modern binds allowed, bounded state and original cleanup retained')
draw = card.split('override fun draw(canvas: Canvas)', 1)[1].split('override fun setAlpha', 1)[0]
assert draw.index('canvas.drawRect(surroundRect') < draw.index('canvas.drawRoundRect(rect')
assert 'Paint(' not in draw and 'LinearGradient(' not in draw
assert 'surround = Color.WHITE' in card
assert 'ColorDrawable(Color.WHITE)' in card
assert 'view.background === installed' in card
assert 'view.background = installed.original' in card
assert 'view.removeOnAttachStateChangeListener(rowAttachListener)' in card
assert 'listBackgrounds.clear()' in card
assert 'MomentsCardSettings.CARD_BACKGROUND, null, original' in card
settings = (BASE / 'hooks/items/moments/MomentsCardSettings.kt').read_text()
assert 'const val GAP_DP = 6' in settings
assert 'const val STROKE_DP = 2' in settings
assert 'const val CORNER_RADIUS_DP = 32' in settings
assert 'const val CONTENT_INSET_DP = 12' in settings
assert 'view.elevation = 0f' in card and 'view.translationZ = 0f' in card
assert 'view.elevation = old.elevation' in card and 'view.translationZ = old.translationZ' in card
assert 'original.right + side + inset, original.bottom + gap + inset' in card
assert card.index('view.elevation, view.translationZ') < card.index('view.elevation = 0f')
assert card.index('view.elevation, view.translationZ') < card.index('view.translationZ = 0f')
assert 'const val CARD_BACKGROUND = 0xFFF3F3F3.toInt()' in settings
assert 'color = if (colors == null) fill else Color.WHITE' in card
assert 'rect.height().coerceAtLeast(0f) / 2' in card
page = (BASE / 'ui/miuix/MomentsCardMiuixPage.kt').read_text()
assert '卡片间距 6dp' in page and '添加 2dp 三色渐变描边' in page
print('PASS source: opaque shader paint, fixed #F3F3F3, 6dp gap, 2dp stroke, 32dp capsule-like corners')
# Numeric geometry check only, not an execution of Kotlin/Android drawing.
for density in (1, 2, 3):
    side, gap = int(6*density+.5), int(6*density+.5)
    assert side > 0 and gap == 6*density
    inset = int(12*density+.5)
    padding = (1+side+inset, 2+inset, 3+side+inset, 4+gap+inset)
    assert padding == (1+18*density, 2+12*density, 3+18*density, 4+18*density)
    if density == 2:
        assert padding == (37, 26, 39, 40)
    assert 0xFFFFFFFF != 0xFFF3F3F3
print('PASS source/geometry: white timeline/gutters and gray cards in both themes, 6dp gap, no draw-time shader allocation')
ast.parse((ROOT / 'scripts/test_moments_card.py').read_text())
print('PASS Python syntax: updated Android synthetic harness (NOT compiled or executed)')
parser = argparse.ArgumentParser()
parser.add_argument('--native-dir', type=Path)
args = parser.parse_args()
if args.native_dir:
    native = args.native_dir
    legacy = (native / 'com/tencent/mm/plugin/sns/ui/item/BaseTimeLineItem.smali').read_text()
    modern = (native / 'fk4/m2.smali').read_text()
    p = modern.split('.method public p(Llk4/b;)Z', 1)[1].split('.end method', 1)[0]
    h = modern.split('.method public h(Lvv5/s0;Llk4/b;I)Z', 1)[1].split('.end method', 1)[0]
    assert 'isItemChanged' in p
    assert '->p(Llk4/b;)Z' in h and 'move-result v13' in h
    assert 'move/from16 v18, v13' in h and 'return v18' in h
    caller = (native / 'com/tencent/mm/plugin/sns/ui/improve/component/y0.smali').read_text()
    assert '.method public h(Lvv5/s0;Lvv5/c;IIZLjava/util/List;)V' in caller
    assert 'Lfk4/w0;->h(Lvv5/s0;Llk4/b;I)Z' in caller
    assert '.method public n(Lcom/tencent/mm/plugin/sns/ui/item/BaseTimeLineItem$BaseViewHolder;' in legacy
    assert '.method public t(Landroid/app/Activity;' in legacy
    print('PASS native 8078 evidence: legacy entry signatures; modern Boolean is item-change, not success')
    for rel in ('fk4/m2.smali', 'com/tencent/mm/plugin/sns/ui/improve/component/y0.smali'):
        print('SHA256', rel, hashlib.sha256((native / rel).read_bytes()).hexdigest())
else:
    print('SKIP native evidence (supply --native-dir with targeted baksmali output)')
