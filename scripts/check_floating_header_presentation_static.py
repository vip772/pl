"""Source contracts plus an independent policy model; no Kotlin compilation or device execution."""
from pathlib import Path
import itertools
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/java/t/mma/hooks/items"


def source(name):
    return (BASE / name).read_text()


class HeaderPresentationContracts(unittest.TestCase):
    def test_capability_not_native_readiness(self):
        capsule = source("floatingchat/FloatingChatHeaderCapsule.kt")
        self.assertIn('fun supportsHost(): Boolean = hostVersionName() == "8.0.78"', capsule)
        self.assertIn('fun usesLegacyPresentation(): Boolean = hostVersionName()?.let { it != "8.0.78" } == true', capsule)
        self.assertIn("if (!supportsHost()) return fail(header)", capsule)
        self.assertIn("return states[header]?.let { it.row.parent === header } == true", capsule)
        self.assertIn('val nativeMore = findVerifiedMore(menu, header) ?: return fail(header)', capsule)

    def test_single_legacy_gate_controls_surface_shadow_and_clip(self):
        header = source("floatingchat/FloatingChatHeaderFeature.kt")
        self.assertIn("val legacyCard = capsule.usesLegacyPresentation() && !split", header)
        self.assertIn("if (legacyCard) FloatingChatCardVisuals.apply(header,", header)
        self.assertNotIn("if (!split) FloatingChatCardVisuals.apply(header,", header)
        self.assertIn("val elevation = if (legacyCard) dp(ELEVATION_DP).toFloat() else 0f", header)
        self.assertIn("if (header.clipToOutline != legacyCard) header.clipToOutline = legacyCard", header)
        self.assertEqual(len(re.findall(r"FloatingChatCardVisuals\.apply\(header,", header)), 1)

    def test_native_actions_theme_opacity_and_restore_preserved(self):
        capsule = source("floatingchat/FloatingChatHeaderCapsule.kt")
        for fragment in ["nativeBack.performClick()", "nativeMore.performClick()", "titleAction?.performClick()",
                         "Configuration.UI_MODE_NIGHT_YES", "0xE6242424", "0xE6F7F7F7",
                         "FloatingChatOpacity.color(", "header.background = state.background",
                         "view.alpha = old.alpha", "header.removeView(state.row)"]:
            self.assertIn(fragment, capsule)
        self.assertLess(capsule.index("FloatingChatCardVisuals.ownPresentation(header)"),
                        capsule.index("header.addView(row,"))

    def test_immersive_ownership_and_native_layers_preserved(self):
        beauty = source("backgroundbeauty/BackgroundBeautyFeature.kt")
        self.assertIn("FloatingChatCardVisuals.contains(view)) return@hookOnce", beauty)
        self.assertIn("FloatingChatCardVisuals.contains(view)) return", beauty)
        self.assertIn("!t.mma.hooks.items.floatingchat.FloatingChatCardVisuals.contains(view) &&", beauty)
        self.assertIn("FloatingChatActionBarBackgrounds.contains(target)) return@hookOnce", beauty)
        self.assertIn("FloatingChatActionBarBackgrounds.onNativeWrite(view, method, color as? Drawable)", beauty)
        layers = source("floatingchat/FloatingChatActionBarBackgrounds.kt")
        for setter in ["setPrimaryBackground", "setStackedBackground", "setSplitBackground"]:
            self.assertIn(setter, layers)
        self.assertIn("layers[method] = drawable", layers)
        self.assertIn("originals.remove(view)", layers)

    def test_independent_policy_matrix(self):
        # This model does not execute production Kotlin. Source contracts above
        # tie its decision expression to the edited caller, not to runtime UI.
        cases = 0
        for version, installed, native_ready, dark, immersive, transparency in itertools.product(
                [None, "8.0.78", "other"], [False, True], [False, True], [False, True], [False, True], [-1, 0, 50, 100]):
            supported = version == "8.0.78"
            split = supported and (installed or native_ready)
            legacy = version is not None and not supported and not split
            if supported:
                self.assertFalse(legacy)
                self.assertEqual(split, installed or native_ready)
            elif version is None:
                self.assertFalse(legacy)
            else:
                self.assertTrue(legacy)
            cases += 1
        self.assertEqual(cases, 192)


if __name__ == "__main__":
    unittest.main(verbosity=2)