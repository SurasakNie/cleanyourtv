"""Guard the decision that the Inputs row on Home is out of the APK but kept in the tree behind one flag."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
RES = ROOT / "launcher/app/src/main/res"


def read(path):
    return path.read_text(encoding="utf-8")


class InputsRowHiddenTest(unittest.TestCase):
    def test_one_flag_turns_the_row_off(self):
        home = read(SRC / "GtvActivity.java")
        self.assertIn("static final boolean INPUTS_ROW_READY = false;", home)

    def test_home_never_draws_the_row_while_the_flag_is_off_whatever_was_stored(self):
        home = read(SRC / "GtvActivity.java")
        self.assertIn("inputsOnHome = INPUTS_ROW_READY && effects.getBoolean(PREF_INPUTS_ON_HOME, false);", home)

    def test_the_settings_row_and_its_hint_go_with_it(self):
        settings = read(SRC / "LauncherSettingsActivity.java")
        self.assertIn("GtvActivity.INPUTS_ROW_READY ? android.view.View.VISIBLE : android.view.View.GONE", settings)
        self.assertIn("inputsHomeChoice.setVisibility(inputsRowRows);", settings)
        self.assertIn("findViewById(R.id.hintInputsHome).setVisibility(inputsRowRows);", settings)
        layout = read(RES / "layout/activity_launcher_settings.xml")
        self.assertIn('android:id="@+id/hintInputsHome"', layout)

    def test_nothing_is_deleted_so_the_row_can_come_back(self):
        home = read(SRC / "GtvActivity.java")
        settings = read(SRC / "LauncherSettingsActivity.java")
        self.assertIn('PREF_INPUTS_ON_HOME = "gtv_inputs_on_home"', home, "the stored key must not change")
        self.assertIn("homeInputs = new LinearLayout(this);", home)
        self.assertIn("parent == homeInputs", home, "the row's Left/Right rule stays")
        self.assertIn("inputsHomeChoice.setOnClickListener", settings)
        self.assertIn("private void fillInputs(LinearLayout row, boolean onHome)", home, "the Inputs tab shares it")

    def test_the_inputs_tab_is_untouched(self):
        home = read(SRC / "GtvActivity.java")
        self.assertIn("inputsPage = buildInputs();", home)
        self.assertIn("fillInputs(inputsRow, false);", home)


if __name__ == "__main__":
    unittest.main()
