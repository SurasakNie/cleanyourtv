"""Guard the Hebrew resource copy against new strings and diverging layouts."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


RES = Path(__file__).resolve().parents[1] / "launcher/app/src/main/res"
ANDROID_ID = "{http://schemas.android.com/apk/res/android}id"


def strings(folder):
    return {s.attrib["name"]: "".join(s.itertext())
            for s in ET.parse(RES / folder / "strings.xml").getroot()
            if s.tag == "string" and s.get("translatable") != "false"}


def views(path):
    return {v.attrib[ANDROID_ID]: v.tag
            for v in ET.parse(path).iter()
            if ANDROID_ID in v.attrib}


class HebrewResourcesTest(unittest.TestCase):
    def test_translations_cover_default_strings_and_format_arguments(self):
        default, hebrew = strings("values"), strings("values-iw")
        self.assertFalse(default.keys() - hebrew.keys(), "Missing Hebrew translations")
        for name, value in default.items():
            with self.subTest(string=name):
                self.assertEqual(re.findall(r"%\d+\$[a-zA-Z]", value),
                                 re.findall(r"%\d+\$[a-zA-Z]", hebrew[name]))

    def test_hebrew_layouts_keep_their_default_view_ids_and_types(self):
        """Every layout with a Hebrew copy must keep the same views: the code finds them by id.

        This used to compare activity_main.xml, the Apple TV layout's Home screen. That layout was removed in
        2.20 and the Google TV Home is built in code, so what is left to compare is the smaller layouts.
        """
        hebrew_copies = sorted(p.name for p in (RES / "layout-iw").glob("*.xml"))
        self.assertTrue(hebrew_copies, "No Hebrew layouts left to check")
        for name in hebrew_copies:
            with self.subTest(layout=name):
                default = views(RES / "layout" / name)
                hebrew = views(RES / "layout-iw" / name)
                for view_id, tag in default.items():
                    self.assertEqual(tag, hebrew.get(view_id), view_id)


if __name__ == "__main__":
    unittest.main()
