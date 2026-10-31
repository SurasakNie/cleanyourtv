"""Guard the rule that the live input window stays on Home without a selected app; only the name and dots need one."""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/GtvActivity.java"
GRADLE = ROOT / "launcher/app/build.gradle"


def method(text, signature):
    start = text.index(signature)
    return text[start:text.index("\n    }\n", start)]


class BannerLiveHomeTest(unittest.TestCase):
    def setUp(self):
        self.home = HOME.read_text(encoding="utf-8")

    def test_one_gate_decides_whether_the_banner_shows(self):
        gate = method(self.home, "private boolean bannerShown()")
        for condition in ("bannerStyle != BANNER_OFF", "currentTab == TAB_HOME", "bannerFits"):
            with self.subTest(condition=condition):
                self.assertIn(condition, gate)

    def test_the_live_style_needs_no_selected_app_and_the_others_still_do(self):
        gate = method(self.home, "private boolean bannerShown()")
        self.assertRegex(gate, r"appSelected\s*\|\|\s*bannerStyle == BANNER_LIVE|bannerStyle == BANNER_LIVE\s*\|\|\s*appSelected")

    def test_showing_and_starting_both_use_that_gate(self):
        update = method(self.home, "private void updateBanner()")
        self.assertIn("bannerShown()", update)
        start = self.home.index("private final Runnable liveStart")
        body = self.home[start:self.home.index(";", start)]
        self.assertIn("bannerShown()", body)
        self.assertIn("resumed", body)

    def test_the_name_and_dots_hide_without_a_selection_but_keep_their_room(self):
        update = method(self.home, "private void updateBanner()")
        self.assertIn("title.setVisibility(appSelected ? View.VISIBLE : View.INVISIBLE)", update)
        self.assertIn("dots.setVisibility(View.INVISIBLE)", update)
        self.assertNotIn("View.GONE", update, "GONE would change the banner's height and move the window")

    def test_the_version_is_2_33(self):
        gradle = GRADLE.read_text(encoding="utf-8")
        self.assertRegex(gradle, r"versionCode 233\b")
        self.assertIn("versionName '2.33'", gradle)


if __name__ == "__main__":
    unittest.main()
