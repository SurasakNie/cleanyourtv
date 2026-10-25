"""Guard the Google TV Favorites store: its file format, cap and the menu strings it needs."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
RES = ROOT / "launcher/app/src/main/res"
FAVORITES = SRC / "Favorites.java"


def strings(folder):
    return {s.attrib["name"]: "".join(s.itertext())
            for s in ET.parse(RES / folder / "strings.xml").getroot() if s.tag == "string"}


class FavoritesStoreTest(unittest.TestCase):
    def test_kept_in_the_launchers_own_files_and_never_sent_anywhere(self):
        source = FAVORITES.read_text(encoding="utf-8")
        self.assertIn('FILE_NAME = "gtv_favorites.txt"', source)
        self.assertIn("getFilesDir()", source)
        for leak in ("http", "URL", "Socket", "exec("):
            self.assertNotIn(leak, source, f"Favorites must not reference {leak}")

    def test_written_atomically_so_a_cut_power_cannot_truncate_the_list(self):
        source = FAVORITES.read_text(encoding="utf-8")
        for call in ("AtomicFile", "startWrite()", "finishWrite(out)", "failWrite(out)"):
            self.assertIn(call, source)

    def test_an_unreadable_file_is_ignored_rather_than_replaced_with_an_empty_one(self):
        source = FAVORITES.read_text(encoding="utf-8")
        load = source[source.index("static List<String> load"):source.index("static boolean save")]
        self.assertIn("return null", load, "load must report 'no list' instead of an empty one")
        self.assertNotIn("Collections.emptyList", load)

    def test_the_list_is_capped_and_only_holds_valid_package_names(self):
        source = FAVORITES.read_text(encoding="utf-8")
        self.assertIn("MAX = 64", source)
        self.assertIn("LibraryApps.validPackage(pkg)", source)
        self.assertIn("Too many favorites", source)
        self.assertIn("Duplicate package", source)

    def test_home_reads_the_store_and_seeds_it_on_first_run(self):
        home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")
        self.assertIn("Favorites.load(this)", home)
        self.assertIn("Favorites.save(this, favPackages)", home)
        self.assertRegex(home, r"saved == null")

    def test_menu_strings_exist_in_english_and_hebrew_with_the_same_arguments(self):
        default, hebrew = strings("values"), strings("values-iw")
        for name in ("gtv_add_favorite", "gtv_remove_favorite", "gtv_added_favorite",
                     "gtv_removed_favorite", "gtv_favorites_full", "gtv_favorite_not_saved"):
            with self.subTest(string=name):
                self.assertIn(name, default)
                self.assertIn(name, hebrew)
                self.assertEqual(re.findall(r"%\d+\$[a-zA-Z]", default[name]),
                                 re.findall(r"%\d+\$[a-zA-Z]", hebrew[name]))


if __name__ == "__main__":
    unittest.main()
