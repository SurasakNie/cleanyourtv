"""Guard the Your apps store: its file format, cap, seeding and the strings the row needs."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
RES = ROOT / "launcher/app/src/main/res"
DOCK = SRC / "DockApps.java"


def strings(folder):
    return {s.attrib["name"]: "".join(s.itertext())
            for s in ET.parse(RES / folder / "strings.xml").getroot() if s.tag == "string"}


class DockStoreTest(unittest.TestCase):
    def test_kept_in_the_launchers_own_files_and_never_sent_anywhere(self):
        source = DOCK.read_text(encoding="utf-8")
        self.assertIn('FILE_NAME = "gtv_dock.txt"', source)
        self.assertIn("getFilesDir()", source)
        for leak in ("http", "URL", "Socket", "exec("):
            self.assertNotIn(leak, source, f"DockApps must not reference {leak}")

    def test_written_atomically_so_a_cut_power_cannot_truncate_the_row(self):
        source = DOCK.read_text(encoding="utf-8")
        for call in ("AtomicFile", "startWrite()", "finishWrite(out)", "failWrite(out)"):
            self.assertIn(call, source)

    def test_an_unreadable_file_is_ignored_rather_than_replaced_with_an_empty_one(self):
        source = DOCK.read_text(encoding="utf-8")
        load = source[source.index("static List<String> load"):source.index("static boolean save")]
        self.assertIn("return null", load, "load must report 'no list' instead of an empty one")
        self.assertNotIn("Collections.emptyList", load)

    def test_the_list_is_capped_and_only_holds_valid_package_names(self):
        source = DOCK.read_text(encoding="utf-8")
        self.assertIn("MAX = 32", source)
        self.assertIn("LibraryApps.validPackage(pkg)", source)
        self.assertIn("Dock is full", source)
        self.assertIn("Duplicate package", source)

    def test_home_seeds_the_row_from_the_configuration_on_first_run(self):
        home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")
        self.assertIn("DockApps.load(this)", home)
        self.assertIn("DockApps.save(this", home)
        self.assertRegex(home, r"savedDock == null")
        self.assertIn("cfg.dockApps", home, "the first run must start from dock_apps, not from nothing")

    def test_the_row_keeps_at_least_one_app_so_home_is_never_only_a_plus_tile(self):
        home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")
        self.assertIn("dock.size() <= 1", home)
        self.assertIn("gtv_dock_last_app", home)

    def test_remove_is_only_offered_while_the_row_holds_more_than_apps_per_row(self):
        home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")
        menu = home[home.index("private void menu(App a, View card)"):home.index("// ---- moving a Home tile")]
        self.assertRegex(
            menu,
            r"if \(dock\.size\(\) > cols\) \{\s+titles\.add\(getString\(R\.string\.gtv_remove_from_dock\)\)",
            "the Remove entry must sit inside the size check",
        )
        # The check hides the entry; setInDock still refuses the last app on its own.
        self.assertIn("dock.size() <= 1", home)

    def test_a_dock_edit_takes_the_focus_after_a_layout_pass_on_add_and_on_remove(self):
        home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")
        edit = home[home.index("private void setInDock"):home.index("private void startDockPick")]
        self.assertIn("focusLastDockApp();", edit)
        self.assertNotIn("if (inDock) dockRow.post", edit, "removing must take the focus too, not only adding")
        start = home.index("private void focusLastDockApp()")
        helper = home[start:home.index("\n    }\n", start)]
        for call in ("addOnGlobalLayoutListener", "findViewHolderForAdapterPosition", "scrollToPosition",
                     "++pass >= 3", "removeOnGlobalLayoutListener"):
            self.assertIn(call, helper)
        # A runnable posted straight after notifyDataSetChanged() can run before the layout pass: that was the race.
        self.assertNotIn("dockRow.post(", helper)

    def test_row_strings_exist_in_english_and_hebrew_with_the_same_arguments(self):
        default, hebrew = strings("values"), strings("values-iw")
        for name in ("gtv_add_to_dock", "gtv_remove_from_dock", "gtv_added_to_dock", "gtv_removed_from_dock",
                     "gtv_dock_full", "gtv_dock_not_saved", "gtv_dock_last_app", "gtv_dock_has_everything"):
            with self.subTest(string=name):
                self.assertIn(name, default)
                self.assertIn(name, hebrew)
                self.assertEqual(re.findall(r"%\d+\$[a-zA-Z]", default[name]),
                                 re.findall(r"%\d+\$[a-zA-Z]", hebrew[name]))


if __name__ == "__main__":
    unittest.main()
