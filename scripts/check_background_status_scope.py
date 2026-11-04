"""Static contracts for status-mask scope; no Android/JVM compilation."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
bottom = (root / 'app/src/main/java/t/mma/hooks/items/custombottombar/CustomBottomBarFeature.kt').read_text()
start = source.index('    private fun applyWechatStatusMasks(')
end = source.index('    private fun applySystemBars(', start)
status = source[start:end]
parents = status[status.index('    private fun applyWechatStatusParents('):]
assert source.count('applyWechatStatusMasks(activity, param.thisObject)') == 2
assert 'chatChromeVersionName() == "8.0.78"' in status
assert 'readNativeField(it, "g")' in status
assert 'readNativeField(mainUi, "r")' in status
assert 'nativeMask ?: decor.findViewById<View>(id)' in status
assert 'state.loadedBitmaps[BackgroundBeautySettings.Slot.WECHAT]?.bitmap?.isRecycled == false' in status
assert 'resolveTarget(activity) == BackgroundBeautySettings.Slot.WECHAT' in status
assert 'parent !== decor && visited.add(parent)' in parents
assert 'snapshot?.appliedBitmap != null || snapshot?.slot == BackgroundBeautySettings.Slot.CHAT' in parents
assert 'parent = parent.parent as? View' in parents
assert 'setTransparentSurface(state, parent, BackgroundBeautySettings.Slot.WECHAT)' in parents
for forbidden in ('childCount', 'getChildAt', 'postOnAnimation', 'OnPreDraw', 'postDelayed'):
    assert forbidden not in status, forbidden
start = bottom.index('private class BottomBarNavigationAppearance')
end = bottom.index('private class BottomBarComposeOwner', start)
appearance = bottom[start:end]
restore = appearance[appearance.index('    fun restore()'): ]
assert 'window.decorView.systemUiVisibility = originalSystemUiVisibility' not in restore
assert '(decor.systemUiVisibility and layoutFlags.inv()) or' in restore
assert '(originalSystemUiVisibility and layoutFlags)' in restore
# Exhaust the relevant flags with unrelated visibility/light-bar bits as sentinels.
layout = 0x100 | 0x200
fullscreen = 0x400
bits = (0x100, 0x200, fullscreen, 0x4, 0x10, 0x2000)
values = [sum(bit for index, bit in enumerate(bits) if mask & (1 << index)) for mask in range(1 << len(bits))]
for original in values:
    for current in values:
        restored = (current & ~layout) | (original & layout)
        assert restored & fullscreen == current & fullscreen
        assert restored & ~layout == current & ~layout
        assert restored & layout == original & layout
print('PASS static status scope: native references, version gate, ancestor-only traversal, bitmap/chat/decor guards, no frame callbacks')
print('PASS 4096 flag combinations: fullscreen and unrelated bits preserved; owned layout bits restored')
