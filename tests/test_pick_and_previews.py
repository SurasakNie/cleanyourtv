"""2.34: the + picks from the Apps tab, Focus style gains Lift + bar, and two choice pages show a preview."""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
RES = ROOT / "launcher/app/src/main/res"


def method(text, signature):
    start = text.index(signature)
    return text[start:text.index("\n    }\n", start)]


class PlusPicksFromAppsTest(unittest.TestCase):
    def setUp(self):
        self.home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")

    def test_the_plus_starts_the_pick_and_no_list_popup_remains(self):
        self.assertIn("plus.setOnClickListener(v -> startDockPick());", self.home)
        self.assertNotIn("showDockChooser", self.home)

    def test_the_pick_goes_to_the_apps_tab_on_the_first_app_not_in_the_row(self):
        start = method(self.home, "private void startDockPick()")
        self.assertIn("tabs[TAB_APPS].requestFocus();", start)
        self.assertIn("pickingForDock = true;", start)
        self.assertIn("if (!dock.contains(all.get(i)))", start)
        self.assertIn("gtv_dock_has_everything", start, "nothing to pick: say so instead of opening an empty choice")
        self.assertLess(start.index("tabs[TAB_APPS].requestFocus();"), start.index("pickingForDock = true;"),
                        "the Apps pill's listener ends a pick, so the flag is set after it")

    def test_ok_on_an_app_adds_it_or_says_it_is_already_there(self):
        pick = method(self.home, "private void pickForDock(App a)")
        self.assertIn("dock.contains(a)", pick)
        self.assertIn("gtv_pick_already", pick)
        self.assertIn("setInDock(a, true);", pick)
        self.assertLess(pick.index("endDockPick();"), pick.index("setInDock(a, true);"))
        self.assertIn("tabs[TAB_HOME].requestFocus();", pick)
        self.assertIn("if (pickingForDock) pickForDock(a); else open(a);", self.home)

    def test_back_and_other_tabs_end_the_pick_and_no_menu_opens_while_choosing(self):
        back = method(self.home, "public void onBackPressed()")
        self.assertLess(back.index("cancelDockPick();"), back.index("tabs[currentTab]"))
        self.assertIn("if (tab != TAB_APPS) endDockPick();", self.home)
        self.assertIn("if (!pickingForDock) menu(a, v);", self.home)

    def test_apps_already_in_the_row_are_dimmed_while_choosing(self):
        self.assertIn("card.setTag(a);", self.home)
        dim = method(self.home, "private float pickDim(View v)")
        self.assertIn("pickingForDock", dim)
        self.assertIn("dock.contains((App) tag)", dim)
        self.assertIn("* pickDim(card)", self.home)
        self.assertIn("* pickDim(v)", self.home, "focusTile resets the alpha, so it must keep the dim")

    def test_the_new_strings_exist_in_english_and_hebrew(self):
        for folder in ("values", "values-iw"):
            text = (RES / folder / "strings.xml").read_text(encoding="utf-8")
            for name in ("gtv_pick_to_add", "gtv_pick_already", "focus_lift_bar"):
                with self.subTest(folder=folder, name=name):
                    self.assertIn(f'name="{name}"', text)


class LiftBarTest(unittest.TestCase):
    def setUp(self):
        self.home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")
        self.settings = (SRC / "LauncherSettingsActivity.java").read_text(encoding="utf-8")

    def test_the_style_is_number_four_and_the_old_numbers_are_unchanged(self):
        self.assertIn("FOCUS_ZOOM = 0, FOCUS_LIFT = 1, FOCUS_HALO = 2, FOCUS_UNDERLINE = 3, FOCUS_LIFT_BAR = 4;",
                      self.home)

    def test_it_lifts_and_shows_the_bar_without_zooming(self):
        self.assertIn("focusStyle == FOCUS_LIFT || focusStyle == FOCUS_LIFT_BAR", method(self.home, "private boolean liftsTile()"))
        self.assertIn("focusStyle == FOCUS_UNDERLINE || focusStyle == FOCUS_LIFT_BAR",
                      method(self.home, "private boolean showsBar()"))
        scale = method(self.home, "private float focusScale()")
        self.assertIn("FOCUS_LIFT_BAR ? 1f", scale)
        self.assertNotIn("focusStyle == FOCUS_LIFT ? -10", self.home, "the lift goes through liftsTile() everywhere")
        self.assertNotIn("focusStyle == FOCUS_UNDERLINE);", self.home, "the bar goes through showsBar() everywhere")

    def test_the_choice_page_lists_it_last_and_the_label_matches(self):
        page = method(self.settings, "private void showFocusStylePage()")
        order = ["focus_zoom", "focus_lift", "focus_halo", "focus_underline", "focus_lift_bar"]
        positions = [page.index(f"R.string.{name}") for name in order]
        self.assertEqual(positions, sorted(positions), "the labels follow the stored numbers 0-4")
        self.assertIn("case GtvActivity.FOCUS_LIFT_BAR: return R.string.focus_lift_bar;", self.settings)


class PreviewTest(unittest.TestCase):
    def setUp(self):
        self.preview = (SRC / "ChoicePreview.java").read_text(encoding="utf-8")
        self.settings = (SRC / "LauncherSettingsActivity.java").read_text(encoding="utf-8")

    def test_the_focus_and_banner_pages_open_with_a_preview(self):
        self.assertIn("}, ChoicePreview.FOCUS);", self.settings)
        self.assertIn("}, ChoicePreview.BANNER);", self.settings)

    def test_every_option_is_drawn(self):
        for case in ("FOCUS_LIFT", "FOCUS_HALO", "FOCUS_UNDERLINE", "FOCUS_LIFT_BAR"):
            with self.subTest(case=case):
                self.assertIn(f"GtvActivity.{case}", self.preview)
        for style in ("BANNER_NAME", "BANNER_GLASS", "BANNER_OFF", "BANNER_LIVE"):
            with self.subTest(style=style):
                self.assertIn(f"GtvActivity.{style}", self.preview)

    def test_the_drawing_follows_the_highlighted_row_and_adds_no_image(self):
        self.assertIn("preview.setOption(which)", self.settings)
        self.assertNotRegex(self.preview, r"R\.drawable|BitmapFactory|decodeResource")

    def test_the_rows_start_after_the_preview(self):
        self.assertIn("(preview != null ? 3 : 2)", self.settings)


class VersionTest(unittest.TestCase):
    def test_the_build_is_2_34_or_later(self):
        gradle = (ROOT / "launcher/app/build.gradle").read_text(encoding="utf-8")
        self.assertGreaterEqual(int(re.search(r"versionCode (\d+)", gradle).group(1)), 234)


if __name__ == "__main__":
    unittest.main()
