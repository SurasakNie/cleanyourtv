"""2.37: Down into a scrolled Your apps, the + dim by app, the focus bar outside the tile, and three preview fixes."""
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


class DownFromTheHomePillTest(unittest.TestCase):
    """After an add the row sits scrolled to its end, so position 0 has no view holder and Down did nothing."""

    def setUp(self):
        self.home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")

    def test_a_missing_first_tile_is_scrolled_to_and_focused_after_layout(self):
        first = code(method(self.home, "private void focusHomeFirst()"))
        self.assertIn("dockRow.scrollToPosition(0)", first)
        self.assertIn("addOnGlobalLayoutListener", first, "the holder exists only after the next layout pass")
        self.assertIn("removeOnGlobalLayoutListener", first)
        self.assertRegex(first, r"\+\+pass >= 3", "gives up after a few passes rather than listening forever")


class PickDimFollowsTheAppTest(unittest.TestCase):
    def setUp(self):
        self.home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")

    def test_the_tag_is_set_before_the_dim_reads_it(self):
        bind = method(self.home, "public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position)")
        self.assertLess(bind.index("card.setTag(a);"), bind.index("pickDim(card)"),
                        "pickDim reads the tag: set after it, the dim belonged to the tile's previous app")


class FocusBarOutsideTheTileTest(unittest.TestCase):
    def setUp(self):
        self.home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")
        self.body = code(self.home)

    def test_the_bar_is_no_longer_a_child_of_the_clipped_card(self):
        self.assertNotIn("TAG_FOCUS_BAR", self.body)
        self.assertNotIn("showFocusBar(", self.body)

    def test_every_tile_row_draws_the_bar_below_its_focused_tile(self):
        for row in ("private RecyclerView dockRow()", "private RecyclerView grid(List<?> items)"):
            with self.subTest(row=row):
                self.assertIn("addItemDecoration(focusBar)", method(self.home, row))
        draw = code(self.home[self.home.index("class FocusBar extends RecyclerView.ItemDecoration"):])
        draw = draw[:draw.index("\n    }\n")]
        self.assertIn("onDrawOver", draw)
        self.assertIn("showsBar()", draw)
        self.assertIn("isFocused()", draw)
        # 2.37 also hid the bar while carrying; 2.38 shows it (owner, 7 Nov) — see test_238.MoveShowsTheFocusStyleTest.
        self.assertIn("child.getBottom()", draw, "the layout bottom, so a lifted tile leaves a gap above the bar")
        self.assertIn("child.getLeft()", draw)
        self.assertIn("child.getRight()", draw, "full tile width")
        self.assertIn("barColour(", draw)

    def test_a_focus_change_redraws_the_row(self):
        tile = code(method(self.home, "private void focusTile(View v, boolean has)"))
        self.assertIn("invalidate()", tile)

    def test_a_colour_too_dark_for_home_falls_back_to_white(self):
        colour = code(method(self.home, "static int barColour(int appColour)"))
        self.assertIn("0xFFFFFFFF", colour)
        threshold = float(re.search(r"contrast\([^)]*\) < ([0-9.]+)f?", colour).group(1))
        backdrop = int(re.search(r"BAR_BACKDROP = (0x[0-9A-Fa-f]{8})", self.home).group(1), 16)

        def lum(c):
            def ch(v):
                v /= 255
                return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
            return 0.2126 * ch((c >> 16) & 255) + 0.7152 * ch((c >> 8) & 255) + 0.0722 * ch(c & 255)

        def contrast(a, b):
            hi, lo = sorted((lum(a), lum(b)), reverse=True)
            return (hi + 0.05) / (lo + 0.05)

        self.assertLess(contrast(0x000000, backdrop), threshold, "a black app colour must fall back to white")
        self.assertLess(contrast(0x1A1A2E, backdrop), threshold, "HBO Max's near-black must fall back too")
        self.assertGreater(contrast(0x00CC36, backdrop), threshold, "iQIYI's green keeps its colour")
        self.assertGreater(contrast(0x00A8E1, backdrop), threshold, "Prime Video's blue keeps its colour")


class PreviewDrawingsTest(unittest.TestCase):
    def setUp(self):
        self.preview = code((SRC / "ChoicePreview.java").read_text(encoding="utf-8"))

    def test_the_focus_preview_draws_the_bar_below_the_tile_and_a_gap_for_lift_bar(self):
        focus = method(self.preview, "private void drawFocus(Canvas c, float w, float h)")
        self.assertNotIn("rect.bottom - 4 * dp", focus, "the bar no longer sits inside the tile")
        self.assertIn("restBottom", focus, "the bar hangs from where the tile rests, not where it is lifted to")
        self.assertIn("restBottom, ", focus)

    def test_the_live_banner_puts_the_name_under_the_window(self):
        banner = method(self.preview, "private void drawBanner(Canvas c, float w, float h)")
        self.assertLess(banner.index("setTextSize"), banner.index("paint.ascent()"),
                        "the text size must be set before its height is used to place it")
        self.assertRegex(banner, r"titleBase = top \+ wh \+ [^;]*- paint\.ascent\(\)")

    # 2.37's still white marks for the moving glow styles were replaced in 2.38 by a moving glow (owner, 7 Nov):
    # see test_238.GlowPreviewMovesTest.


if __name__ == "__main__":
    unittest.main()
