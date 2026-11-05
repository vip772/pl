"""Source contracts and an independent Python model, not production Kotlin execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt"
OWNER = ROOT / "app/src/main/java/t/mma/hooks/items/custombottombar/CustomBottomBarFeature.kt"
MARKER = "Hchat:FloatingBottomBar"


class HostTag:
    def __init__(self):
        self.equals_calls = 0

    def __eq__(self, other):
        self.equals_calls += 1
        raise TypeError("host equals must never receive the module marker")


def marker_model(tag):
    # Independent short-circuit policy model; does not run Kotlin or Android.
    return isinstance(tag, str) and tag == MARKER


class BackgroundTagContracts(unittest.TestCase):
    def test_shared_guard_and_both_call_sites(self):
        source = SOURCE.read_text()
        a = source.index("    private fun isFloatingBottomBar(view: View): Boolean {")
        b = source.index("    private fun isBottomBar(view: View): Boolean {", a)
        helper = source[a:b]
        self.assertEqual(helper.count("view.tag"), 1)
        self.assertRegex(helper, r"return tag is String\s*&&\s*tag == "
                         r"t\.mma\.hooks\.items\.custombottombar\.CustomBottomBarFeature\.FLOATING_BOTTOM_BAR_TAG")
        self.assertIn("slot != BackgroundBeautySettings.Slot.CHAT && !isFloatingBottomBar(view)", source)
        bottom = source[b:source.index("    private fun findDescendant(", b)]
        self.assertIn("name == BOTTOM_TAB_CLASS", bottom)
        self.assertIn('name == "com.tencent.mm.ui.LauncherUITabView"', bottom)
        self.assertIn("isFloatingBottomBar(view)", bottom)
        self.assertEqual(source.count("isFloatingBottomBar(view)"), 2)
        self.assertNotRegex(source, r'\.tag\s*[!=]=\s*"')
        self.assertNotRegex(helper, r"(?:\.tag\s*=|setTag\(|toString\(|catch\s*\()")

    def test_marker_matches_existing_owner(self):
        owner = OWNER.read_text()
        self.assertIn(f'const val FLOATING_BOTTOM_BAR_TAG = "{MARKER}"', owner)
        self.assertIn("tag = CustomBottomBarFeature.FLOATING_BOTTOM_BAR_TAG", owner)

    def test_independent_model_rejects_foreign_tags_without_equals(self):
        host = HostTag()
        for tag, expected in [(None, False), (MARKER, True), ("other", False),
                              ("", False), (42, False), (host, False)]:
            self.assertEqual(marker_model(tag), expected)
        for _ in range(10000):
            self.assertFalse(marker_model(host))
        self.assertEqual(host.equals_calls, 0)

    def test_model_keeps_native_bottom_class_short_circuits(self):
        for native in (False, True):
            for tag in (None, MARKER, "other", HostTag()):
                bottom = native or marker_model(tag)
                self.assertEqual(bottom, native or isinstance(tag, str) and tag == MARKER)
                for chat in (False, True):
                    clear_native_bar = bottom and not chat and not marker_model(tag)
                    self.assertEqual(clear_native_bar, native and not chat and not marker_model(tag))
                if isinstance(tag, HostTag):
                    self.assertEqual(tag.equals_calls, 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)