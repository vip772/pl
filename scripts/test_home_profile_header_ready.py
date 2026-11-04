#!/usr/bin/env python3
"""Check the cold-start profile header readiness contract without Android build/device."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / "app/src/main/java/t/mma/hooks/items/homesidepanel/HomeSidePanelRuntime.kt"
text = source.read_text()
size_start = text.index("        private fun sizeHeader(): Boolean {")
size_end = text.index("        private var avatar:", size_start)
size = text[size_start:size_end]
refresh_start = text.index("        fun refresh() {")
refresh_end = text.index("        fun setChatVisible", refresh_start)
refresh = text[refresh_start:refresh_end]

assert "private fun sizeHeader(): Boolean" in size
assert "host.height < dp(38)" in size
assert "return false" in size
assert "return true" in size
assert "val headerReady = if (headerVisible) sizeHeader() else false" in refresh
assert "headerReady -> View.VISIBLE" in refresh
assert "else -> View.INVISIBLE" in refresh
assert "View.INVISIBLE" in refresh
for forbidden in ("Thread(", "Thread.sleep", "postDelayed", "postOnAnimation", "ViewTreeObserver"):
    assert forbidden not in size, forbidden

# Keep the image inside the dynamically sized wrapper to avoid clipping by an ancestor.
assert "val imageSide = minOf(dp(38), side)" in size
assert "imageLp.width != imageSide || imageLp.height != imageSide" in size
assert "FrameLayout.LayoutParams(imageSide, imageSide, Gravity.CENTER)" in size
assert "FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER)" in text
print("PASS profile header readiness: undersized host stays invisible, stable host becomes visible")
print("PASS avatar bounds: image dimensions never exceed the wrapper, preserving the normal 38dp avatar")
print("PASS profile header cost: bounded parameter comparison per existing refresh, no new timer/thread/listener")
print("BOUNDARY: source contract only; no Android/R8 build or device UI verification")
