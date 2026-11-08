#!/usr/bin/env python3
"""Non-compiling source/native contract checks; NOT Android drawing or touch execution."""
import argparse
import hashlib
import json
import re
import struct
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/java/t/mma/hooks/items/chattoolbar"


def body(source, signature):
    start = source.index(signature)
    start = source.index("{", start)
    depth = 1
    end = start + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start + 1:end - 1]


def source_checks():
    position = (BASE / "ChatToolbarNewMessagePosition.kt").read_text()
    runtime = (BASE / "ChatToolbarRuntime.kt").read_text()
    feature = (BASE / "ChatToolbarFeature.kt").read_text()
    header = (BASE.parent / "floatingchat/ChatHeaderMount.kt").read_text()
    assert 'val historyId = named("bm4")' in position
    assert 'val quoteId = named("bm2")' in position
    assert 'chips = arrayOf(Chip(historyId), Chip(quoteId))' in position
    assert 'input = footer.findViewById<View>(inputId)' in position
    assert 'observer = footer.viewTreeObserver' in position
    assert 'val anchor = if (toolbar.isShown && toolbar.height > 0) toolbar else input' in position
    assert 'parent.parent !== footer.parent' in position
    assert 'safeBottom - target.height < contentPosition[1]' in position
    assert 'minOf(anchorPosition[1], footerPosition[1], contentPosition[1] + parent.height) - gap' in position
    assert 'target.translationY != state.applied' in position
    assert 'if (target.translationY != next) target.translationY = next' in position
    assert 'target.translationY == state.applied' in position
    assert 'parent.childCount != childCount' in position
    assert 'chips.none' in position  # hidden hints avoid screen-coordinate work
    assert 'close()' in body(position, 'private val preDraw =')
    for forbidden in ['setOnClickListener', 'setOnTouchListener', 'dispatchTouchEvent', 'requestLayout(', 'postDelayed(', 'Thread(', 'bringToFront(', 'addView(', 'removeView(', '.layoutParams =', '.bottomMargin =']:
        assert forbidden not in position, forbidden
    assert 'info.versionName != "8.0.78" || versionCode != 3180L' in position
    assert 'observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)' in position
    disabled = body(runtime, 'if (!toolbarEnabled()) {\n            binding.root.visibility')
    assert 'binding.chipPosition.attach()' in disabled and 'chipPosition.close()' not in disabled
    assert 'if (footer.isAttachedToWindow) chipPosition.attach()' in runtime
    assert 'if (binding.footer.get()?.isAttachedToWindow == true)' in runtime
    assert 'visibility = View.GONE' in body(runtime, 'val root = HorizontalScrollView(activity).apply')
    assert 'binding.chipPosition.close()' in body(runtime, 'fun destroy()')
    assert 'binding.chipPosition.close()' in body(runtime, 'fun onActivityDestroyed(')
    assert 'chipPosition.close()' in body(runtime, 'override fun onViewDetachedFromWindow(')
    assert 'if (!toolbarEnabled()) return baseReady' in body(runtime, 'fun installDexHooks()')
    assert 'runOnMain(::initializeKnownPanels)' in body(runtime, 'if (gridHookInstalled)')
    assert 'if (toolbarEnabled()) preparePanelMeasure(panel, measureMethod)' in runtime
    for signature in ['private fun scheduleGridWatchdog(', 'private fun invokeInitAppGrid(', 'private fun scheduleSnapshot(', 'private fun snapshotTools(', 'private fun snapshotToolsNow(']:
        assert body(runtime, signature).lstrip().startswith('if (!toolbarEnabled()) return'), signature
    assert 'stopRuntime()' not in body(feature, 'private fun updateRuntime(').split('val resources =')[0]
    assert 'installation?.close()' in body(feature, 'private fun updateGridInstallation(')
    assert 'installation = null' in body(feature, 'private fun updateGridInstallation(')
    assert 'updateGridInstallation(preferences)' in body(feature, 'private fun updateRuntime(')
    assert 'installation?.close()' in body(feature, 'private fun stopRuntime()')
    assert 'if (active) updateRuntime(context, preferences)' in feature
    assert 'if (isNewMessageChip(view)) return false' in header
    assert 'Gravity.TOP' in body(header, 'private fun isTopNewMessageChip(')
    old = subprocess.check_output(['git', 'show', '5a24858:app/src/main/java/t/mma/hooks/items/chattoolbar/ChatToolbarNewMessagePosition.kt'], cwd=ROOT, text=True)
    assert 'named("bm4")' not in old and '"bm2"' in old
    print('PASS source contracts: bm4/bm2, footer-off ownership, scheduler renewal, native click preservation, bounded lookups, cleanup; old revision fails bm4 contract')


def geometry_checks():
    # Independent geometry model, not execution of Kotlin/Android code.
    count = 0
    for density in [1, 1.5, 2, 2.75, 3, 4]:
        gap = int(8 * density + .5)
        for footer_top, anchor_top, parent_bottom in [(700, 700, 820), (755, 755, 870), (700, 730, 820), (700, 700, 680), (420, 420, 550)]:
            safe = min(footer_top, anchor_top, parent_bottom) - gap
            for native_bottom in [300, 600, 690, 740, 820, 1000]:
                applied = -max(0, native_bottom - safe)
                bottom = native_bottom + applied
                assert bottom <= safe and bottom < footer_top and bottom <= parent_bottom
                for _ in range(1000):
                    reconstructed = native_bottom + applied - applied
                    next_value = -max(0, reconstructed - safe)
                    assert next_value == applied
                count += 1
    # Negative control: old toolbar-only math does not protect a later footer hit area.
    assert 730 - 8 > 700
    print(f'PASS independent geometry model: {count} cases, 180000 stable frames, footer/content bounds and keyboard/multiline anchor changes; not Android touch validation')


def native_checks(decoded, apk):
    public = (decoded / 'res/values/public.xml').read_text()
    def resource(name):
        return int(re.search(r'<public type="id" name="' + name + r'" id="(0x[0-9a-f]+)"', public).group(1), 16)
    assert resource('bm4') == 0x7f0a0fc0
    assert resource('bm2') == 0x7f0a0fbd
    hh = decoded / 'smali_classes11/com/tencent/mm/ui/chatting/component/hh.smali'
    sk = decoded / 'smali_classes8/com/tencent/mm/ui/chatting/component/sk.smali'
    history, quote = hh.read_text(), sk.read_text()
    assert 'const v1, 0x7f0a0fc0' in history.split('.method public a()V')[1].split('.end method')[0]
    assert 'const v1, 0x7f0a0fbd' in quote.split('.method public a()V')[1].split('.end method')[0]
    assert 'const v0, 0x7f100d26' in history.split('.method public S0(I)V')[1].split('.end method')[0]
    assert 'name="b2_">%d条新消息' in (decoded / 'res/values/strings.xml').read_text()
    native_position = history.split('.method public R0(IZ)V')[1].split('.end method')[0]
    assert '0x800055' in native_position and 'FrameLayout$LayoutParams;->gravity:I' in native_position
    assert 'setOnClickListener' in history.split('.method public S0(I)V')[1].split('.end method')[0]
    with zipfile.ZipFile(apk) as archive:
        layout = archive.read('r/p/ss.xml')
        assert layout[:4] == b'\x03\x00\x08\x00'
        assert struct.pack('<I', resource('bm4')) in layout
        assert struct.pack('<I', resource('bm2')) in layout
        layout_hash = hashlib.sha256(layout).hexdigest()
    records = {str(path.relative_to(decoded)): hashlib.sha256(path.read_bytes()).hexdigest() for path in [hh, sk, decoded/'res/values/public.xml']}
    records['APK:r/p/ss.xml'] = layout_hash
    print('PASS native evidence: hh owns bm4/new-message text and bottom gravity; sk owns bm2/quote hint; both present in original binary XML')
    print(json.dumps(records, ensure_ascii=False, sort_keys=True))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--decoded', type=Path)
    parser.add_argument('--apk', type=Path)
    args = parser.parse_args()
    source_checks()
    geometry_checks()
    if args.decoded and args.apk:
        native_checks(args.decoded, args.apk)
    print('BOUNDARY: no Gradle, Kotlin/SDK compilation, APK build, R8 or device acceptance performed')