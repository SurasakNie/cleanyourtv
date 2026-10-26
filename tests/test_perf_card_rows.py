"""Guard the rule that the performance card is forced off at the most Favorite rows, without losing the choice."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
RES = ROOT / "launcher/app/src/main/res"


def strings(folder):
    return {s.attrib["name"]: "".join(s.itertext())
            for s in ET.parse(RES / folder / "strings.xml").getroot() if s.tag == "string"}


def method(text, signature):
    start = text.index(signature)
    return text[start:text.index("\n    }\n", start)]


class PerfCardRowsTest(unittest.TestCase):
    def test_the_threshold_is_the_most_rows_there_can_be(self):
        config = (SRC / "Config.java").read_text(encoding="utf-8")
        self.assertIn("static final int PERF_CARD_OFF_AT_ROWS = 3;", config)
        self.assertIn("rowsBelow = integer(json, \"rows_below_dock\", rowsBelow, 0, 3);", config,
                      "if the most rows ever changes, the threshold has to move with it")

    def test_home_does_not_build_the_card_at_that_many_rows_whatever_was_chosen(self):
        home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")
        build = method(home, "private void buildPerfCard(Config cfg)")
        self.assertLess(build.index("rowsBelow >= Config.PERF_CARD_OFF_AT_ROWS"), build.index("PERF_CARD_KEY"),
                        "the rows check has to come before the stored choice is even read")

    def test_the_settings_row_greys_and_reads_off_but_never_rewrites_the_stored_choice(self):
        settings = (SRC / "LauncherSettingsActivity.java").read_text(encoding="utf-8")
        refresh = method(settings, "private void refresh()")
        self.assertIn("rowsBelowDock >= Config.PERF_CARD_OFF_AT_ROWS", refresh)
        self.assertIn("perfChoice.setEnabled(!perfBlocked)", refresh)
        self.assertIn("hintPerfRows.setVisibility", refresh)
        # refresh() only displays: the value is written by the click handler alone.
        self.assertNotRegex(refresh, r"putBoolean\(\s*Config\.PERF_CARD_KEY")

    def test_the_explanation_exists_in_english_and_hebrew_and_the_layout_has_its_view(self):
        for folder in ("values", "values-iw"):
            with self.subTest(folder=folder):
                self.assertIn("hint_perf_rows", strings(folder))
        layout = (RES / "layout/activity_launcher_settings.xml").read_text(encoding="utf-8")
        self.assertRegex(layout, r'android:id="@\+id/hintPerfRows"[^>]*android:text="@string/hint_perf_rows"')

    def test_the_rows_setting_carries_its_new_visible_name(self):
        self.assertEqual(strings("values")["rows_below_dock_label"], "Favorite apps rows")
        self.assertIn("rows_below_dock_label", strings("values-iw"))
        config = (SRC / "Config.java").read_text(encoding="utf-8")
        self.assertIn('ROWS_BELOW_DOCK_KEY = "rows_below_dock"', config, "the stored key must not be renamed")


if __name__ == "__main__":
    unittest.main()
