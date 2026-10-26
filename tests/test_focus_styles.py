"""Guard the focus styles, and the rule that broke in 2.26: only ask a RecyclerView about its own children.

`RecyclerView.getChildAdapterPosition(View)` casts the view's layout parameters to RecyclerView's own without
checking them, so passing a view from any other container throws ClassCastException. In 2.26 `haloColour` did
exactly that, and Glass halo crashed the launcher the moment focus reached an input tile — the Inputs row is a
LinearLayout. The process died and Android cleared the launcher's preferred-activity registration with it.
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/GtvActivity.java"


def source():
    return HOME.read_text(encoding="utf-8")


def body(text, signature):
    """The text of one method, from its signature to the closing brace at the same indent."""
    start = text.index(signature)
    end = text.index("\n    }\n", start)
    return text[start:end]


class HaloColourTest(unittest.TestCase):
    def test_every_adapter_position_lookup_is_guarded_by_the_views_parent(self):
        lines = source().splitlines()
        calls = 0
        for line_no, line in enumerate(lines, 1):
            code = line.strip()
            # Skip prose: this rule is also described in a comment, and a comment cannot throw.
            if code.startswith(("*", "//", "/*")) or "getChildAdapterPosition" not in code:
                continue
            calls += 1
            view = re.search(r"getChildAdapterPosition\((\w+)\)", code)
            self.assertIsNotNone(view, f"line {line_no}: cannot read the argument")
            name = view.group(1)
            window = "\n".join(lines[max(0, line_no - 8):line_no])
            self.assertRegex(
                window, rf"{name}\.getParent\(\)|parent == \w+",
                f"line {line_no}: {name} reaches getChildAdapterPosition with no parent established above it",
            )
        # Two in haloColour, one in the Down handler, two in atRowEnd (Your apps, Favorite rows); each must establish the parent first.
        self.assertEqual(calls, 5, "a new getChildAdapterPosition call needs the same parent guard")

    def test_halo_colour_asks_each_row_only_about_its_own_children(self):
        halo = body(source(), "private int haloColour(View v)")
        self.assertIn("ViewParent parent = v.getParent()", halo)
        self.assertIn("parent == dockRow", halo)
        self.assertIn("parent == homeGrid", halo)
        # An else-if, not two ifs: a view belongs to one parent, so the second lookup must not run for a dock tile.
        self.assertIn("} else if (parent == homeGrid) {", halo)

    def test_a_tile_from_anywhere_else_falls_back_to_white_instead_of_throwing(self):
        halo = body(source(), "private int haloColour(View v)")
        self.assertIn("return 0xFFFFFFFF;", halo)

    def test_the_inputs_rows_really_are_linear_layouts_which_is_why_the_guard_is_needed(self):
        text = source()
        self.assertIn("inputsRow = new LinearLayout(this)", text)
        self.assertIn("private void fillInputs(LinearLayout row, boolean onHome)", text)
        self.assertIn("LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH)", text)

    def test_the_white_focus_ring_stays_out(self):
        # Tried in 2.09 and 2.11 and turned down both times: it says nothing on white artwork.
        text = source()
        self.assertNotIn("FOCUS_RING", text)


if __name__ == "__main__":
    unittest.main()
