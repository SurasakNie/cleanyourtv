"""Guard the Animations row's visible name and that renaming it left its stored value alone."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "launcher/app/src/main/res"
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"


def strings(folder):
    return {s.attrib["name"]: "".join(s.itertext())
            for s in ET.parse(RES / folder / "strings.xml").getroot() if s.tag == "string"}


class AnimationsLabelTest(unittest.TestCase):
    def test_the_row_says_what_it_governs_in_english(self):
        self.assertEqual(strings("values")["animations_state"], "Focus & indicator animations: %1$s")
        self.assertIn("app indicator", strings("values")["hint_animations"])

    def test_hebrew_has_both_strings_with_the_same_arguments(self):
        default, hebrew = strings("values"), strings("values-iw")
        for name in ("animations_state", "hint_animations"):
            with self.subTest(string=name):
                self.assertIn(name, hebrew)
                self.assertEqual(re.findall(r"%\d+\$[a-zA-Z]", default[name]),
                                 re.findall(r"%\d+\$[a-zA-Z]", hebrew[name]))
        self.assertNotEqual(hebrew["animations_state"], "הנפשות: %1$s", "the Hebrew still has the old name")

    def test_the_stored_key_is_not_renamed_so_no_saved_choice_is_lost(self):
        settings = (SRC / "LauncherSettingsActivity.java").read_text(encoding="utf-8")
        config = (SRC / "Config.java").read_text(encoding="utf-8")
        self.assertIn('putBoolean("animations", animations)', settings)
        self.assertIn('"animations"', config)


if __name__ == "__main__":
    unittest.main()
