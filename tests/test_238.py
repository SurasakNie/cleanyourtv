"""2.38: Move shows the chosen focus style, the Glow preview moves its glow, and Left/Right wrap at a grid row's ends."""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"


def method(text, signature):
    start = text.index(signature)
    return text[start:text.index("\n    }\n", start)]


def code(text):
    """The text without // comments, so a comment cannot satisfy a check."""
    return re.sub(r"//[^\n]*", "", text)


class MoveShowsTheFocusStyleTest(unittest.TestCase):
    def setUp(self):
        self.home = code((SRC / "GtvActivity.java").read_text(encoding="utf-8"))

    def test_the_carried_tile_is_styled_like_any_focused_tile(self):
        bind = method(self.home, "public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position)")
        tile = method(self.home, "private void focusTile(View v, boolean has)")
        for where, text in (("bind", bind), ("focusTile", tile)):
            with self.subTest(where=where):
                self.assertNotIn("1.15f", text, "no fixed carried zoom: the chosen style decides the size")
                self.assertNotIn("!carrying && liftsTile()", text, "the lift applies while carrying too")
                self.assertNotIn("!carrying && focusStyle == FOCUS_HALO", text, "the halo applies while carrying too")
                self.assertNotIn("GHOST", text, "the carried tile is not made see-through")

    def test_the_bar_is_drawn_while_carrying(self):
        bar = self.home[self.home.index("class FocusBar extends RecyclerView.ItemDecoration"):]
        bar = bar[:bar.index("\n    }\n")]
        self.assertNotIn("moving != null", bar)

    def test_the_other_tiles_still_dim_so_the_moving_one_stands_out(self):
        tile = method(self.home, "private void focusTile(View v, boolean has)")
        self.assertIn("DIM_MOVING", tile)
        self.assertIn("if (has && carrying) dimOthers(v);", tile)

    def test_picking_up_and_putting_down_use_the_same_look(self):
        self.assertNotIn("styleCarried", self.home)
        self.assertIn("focusTile(vh.itemView, true);", method(self.home, "private void refocusMoving()"))


class GlowPreviewMovesTest(unittest.TestCase):
    MOVING = ("RANDOM", "RANDOM_2", "RANDOM_3", "RANDOM_SHAPE", "DRIFT")

    def setUp(self):
        self.preview = code((SRC / "ChoicePreview.java").read_text(encoding="utf-8"))

    def test_the_white_marks_are_gone(self):
        self.assertNotIn("private void mark(", self.preview)
        self.assertNotIn("0xCCFFFFFF", self.preview)

    def test_exactly_the_five_moving_styles_animate(self):
        moves = method(self.preview, "static boolean moves(int style)")
        for style in self.MOVING:
            with self.subTest(style=style):
                self.assertIn(f"GlowStyle.{style}", moves)
        for still in ("CLASSIC", "PER_APP", "FOLLOW", "TWO_GLOWS", "RIBBON"):
            with self.subTest(still=still):
                self.assertNotIn(f"GlowStyle.{still}", moves)

    def test_the_ticker_uses_the_launchers_own_motion(self):
        tick = self.preview[self.preview.index("private final Runnable tick"):]
        tick = tick[:tick.index("\n    };\n")]
        self.assertIn("GlowStyle.drift(", tick)
        self.assertIn("GlowStyle.random(", tick)
        delays = [int(d) for d in re.findall(r"postDelayed\(this, (\d+)\)", tick)]
        self.assertTrue(delays, "the ticker re-posts itself with a delay")
        self.assertGreaterEqual(min(delays), 100, "light for a 1 GB TV: no per-frame redraws")

    def test_it_runs_only_while_shown_and_on_a_moving_row(self):
        tick = self.preview[self.preview.index("private final Runnable tick"):]
        tick = tick[:tick.index("\n    };\n")]
        self.assertIn("isShown()", tick)
        self.assertIn("moves(option)", tick)
        self.assertIn("removeCallbacks(tick)", method(self.preview, "void setOption(int option)"))
        self.assertIn("removeCallbacks(tick)", method(self.preview, "protected void onDetachedFromWindow()"))

    def test_only_the_focus_preview_uses_a_software_layer(self):
        ctor = method(self.preview, "ChoicePreview(Context context, int kind, int option)")
        self.assertRegex(ctor, r"if \(kind == FOCUS\) setLayerType\(LAYER_TYPE_SOFTWARE")


class RowWrapTest(unittest.TestCase):
    def setUp(self):
        self.home = code((SRC / "GtvActivity.java").read_text(encoding="utf-8"))

    def test_left_and_right_try_the_wrap_before_stopping(self):
        edge = method(self.home, "private boolean handleEdgeKey(int code)")
        self.assertLess(edge.index("wrapRow(f, "), edge.index("atRowEnd(f, "))

    def test_the_wrap_covers_the_favorite_rows_and_the_apps_tab_only(self):
        wrap = method(self.home, "private boolean wrapRow(View f, boolean right)")
        self.assertIn("parent == homeGrid", wrap)
        self.assertIn("parent == appsGrid", wrap)
        self.assertNotIn("dockRow", wrap, "Your apps is one row: nothing to wrap to")
        self.assertIn("right ? at + 1 : at - 1", wrap)

    def test_it_acts_only_at_a_rows_end_and_stops_at_the_lists_ends(self):
        wrap = method(self.home, "private boolean wrapRow(View f, boolean right)")
        self.assertIn("at % cols == cols - 1", wrap)
        self.assertIn("at % cols == 0", wrap)
        self.assertRegex(wrap, r"target < 0 \|\| target >= count")

    def test_a_target_off_screen_is_scrolled_to_first(self):
        focus = method(self.home, "private static void focusGridAt(RecyclerView r, int pos)")
        self.assertIn("scrollToPosition(pos)", focus)

    def test_move_mode_never_reaches_the_wrap(self):
        dispatch = method(self.home, "public boolean dispatchKeyEvent(KeyEvent e)")
        self.assertRegex(dispatch, r"if \(moving == null\) \{\s*if \(e\.getAction\(\) == KeyEvent\.ACTION_DOWN && handleEdgeKey\(code\)\)")


if __name__ == "__main__":
    unittest.main()
