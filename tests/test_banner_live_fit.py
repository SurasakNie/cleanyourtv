"""Guard the rule that the live input window starts only on a fresh fit, never on a stale one, and never late."""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/GtvActivity.java"
GRADLE = ROOT / "launcher/app/build.gradle"


def method(text, signature):
    start = text.index(signature)
    return text[start:text.index("\n    }\n", start)]


class BannerLiveFitTest(unittest.TestCase):
    def setUp(self):
        self.home = HOME.read_text(encoding="utf-8")

    def test_selecting_an_app_refits_the_banner_before_showing_it(self):
        show = method(self.home, "private void showApp(App a, View tile)")
        self.assertIn("fitBanner();", show, "bannerFits may be stale: refit before deciding to show the banner")
        self.assertLess(show.index("fitBanner();"), show.index("updateBanner();"))

    def test_the_banner_never_starts_the_window_in_the_same_pass(self):
        update = method(self.home, "private void updateBanner()")
        self.assertNotIn("updateLivePreview(show && resumed)", update)
        self.assertIn("banner.post(liveStart)", update, "the start waits for one more pass, with show recomputed")
        self.assertIn("updateLivePreview(false)", update, "stopping still happens at once")

    def test_the_deferred_start_recomputes_every_condition(self):
        start = self.home.index("private final Runnable liveStart")
        body = self.home[start:self.home.index(";", start)]
        self.assertIn("bannerShown()", body, "the start re-reads the whole gate, not a cached value")
        self.assertIn("resumed", body)

    def test_every_stop_cancels_a_start_still_waiting(self):
        # open(), openInput() and onPause() stop the window directly; a posted start must not undo that.
        live = method(self.home, "private void updateLivePreview(boolean mayRun)")
        self.assertRegex(live, r"if \(!mayRun && banner != null\) banner\.removeCallbacks\(liveStart\);")
        self.assertLess(live.index("removeCallbacks(liveStart)"), live.index("if (mayRun == liveRunning) return;"),
                        "cancel before the early return, or a stop while already stopped leaves the start queued")

    def test_the_build_is_2_32_or_later(self):
        gradle = GRADLE.read_text(encoding="utf-8")
        self.assertGreaterEqual(int(re.search(r"versionCode (\d+)", gradle).group(1)), 232)


if __name__ == "__main__":
    unittest.main()
