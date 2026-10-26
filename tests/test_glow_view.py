"""Guard the glow view's geometry: the elliptical glow must never leave the screen uncovered.

In 2.26 `placeGlow` squashed the full-screen glow view with setScaleY(ry / rx). With a ratio below 1 that shrinks the
view's rectangle below the screen, and the gradient - not yet transparent at that edge - showed as a hard dark bar
along the top and a thick dark area at the bottom. Only GlowStyle.random(true) has a ratio other than 1. The view
may only ever grow.
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
SCREEN_W, SCREEN_H = 1920.0, 1080.0


def place_glow(spot_x, spot_y, rx, ry, fixed=True):
    """The geometry of GtvActivity.placeGlow, as numbers: radius and scales for a spot."""
    ratio = ry / rx
    radius = SCREEN_W * 0.6 * (rx / 0.78)
    if not fixed:                                   # 2.26: squash the view
        return radius, 1.0, ratio
    return (radius if ratio >= 1 else radius * ratio,
            1.0 if ratio >= 1 else 1 / ratio,
            ratio if ratio >= 1 else 1.0)


def covers_screen(spot_x, spot_y, sx, sy):
    px, py = spot_x * SCREEN_W, spot_y * SCREEN_H
    left, right = px - px * sx, px + (SCREEN_W - px) * sx
    top, bottom = py - py * sy, py + (SCREEN_H - py) * sy
    return left <= 0 and right >= SCREEN_W and top <= 0 and bottom >= SCREEN_H


class GlowViewTest(unittest.TestCase):
    def ratios(self):
        # What GlowStyle.random(true) can produce: r in 0.45-0.95, aspect in 0.55-1.55, ry = r / aspect.
        for r in (0.45, 0.7, 0.95):
            for aspect in (0.55, 0.8, 1.0, 1.2, 1.55):
                yield r, r / aspect

    def test_the_view_covers_the_screen_for_every_shape_the_random_style_can_make(self):
        for x in (0.1, 0.5, 0.9):
            for y in (0.0, 0.25, 0.55):
                for rx, ry in self.ratios():
                    with self.subTest(x=x, y=y, rx=rx, ry=ry):
                        _, sx, sy = place_glow(x, y, rx, ry)
                        self.assertGreaterEqual(min(sx, sy), 1.0, "the view must never be squashed")
                        self.assertTrue(covers_screen(x, y, sx, sy))

    def test_the_old_squash_left_the_screen_uncovered_so_this_test_can_fail(self):
        # The owner's screenshot: edges at 83 px and 830 px put the spot at y = 0.25 and the squash near 0.69.
        _, sx, sy = place_glow(0.21, 0.25, 0.78, 0.78 * 0.69, fixed=False)
        self.assertFalse(covers_screen(0.21, 0.25, sx, sy))
        py = 0.25 * SCREEN_H
        self.assertAlmostEqual(py - py * sy, 83.7, delta=1.5)
        self.assertAlmostEqual(py + (SCREEN_H - py) * sy, 825.0, delta=8)

    def test_the_ellipse_drawn_is_the_same_as_before(self):
        for rx, ry in self.ratios():
            with self.subTest(rx=rx, ry=ry):
                radius, sx, sy = place_glow(0.5, 0.2, rx, ry)
                old_radius, _, old_sy = place_glow(0.5, 0.2, rx, ry, fixed=False)
                self.assertAlmostEqual(radius * sx, old_radius, places=6, msg="width of the glow")
                self.assertAlmostEqual(radius * sy, old_radius * old_sy, places=6, msg="height of the glow")

    def test_a_round_glow_is_untouched(self):
        radius, sx, sy = place_glow(0.8, 0.06, 0.78, 0.78)
        self.assertEqual((sx, sy), (1.0, 1.0))
        self.assertAlmostEqual(radius, SCREEN_W * 0.6)

    def test_the_java_matches_these_numbers_and_never_scales_below_one(self):
        home = (SRC / "GtvActivity.java").read_text(encoding="utf-8")
        start = home.index("private void placeGlow")
        body = home[start:home.index("\n    }\n", start)]
        self.assertIn("float ratio = spot.ry / spot.rx, radius = w * 0.6f * (spot.rx / 0.78f);", body)
        self.assertIn("glowDrawable.setGradientRadius(ratio >= 1f ? radius : radius * ratio);", body)
        self.assertIn("glow.setScaleX(ratio >= 1f ? 1f : 1f / ratio);", body)
        self.assertIn("glow.setScaleY(ratio >= 1f ? ratio : 1f);", body)
        self.assertNotIn("setScaleY(spot.ry / spot.rx)", body, "the 2.26 squash must not come back")
        for scale in re.findall(r"glow\.setScale[XY]\(([^;]*)\);", body):
            self.assertNotRegex(scale, r"^\s*(0\.\d+f?|spot\.ry / spot\.rx)\s*$")


if __name__ == "__main__":
    unittest.main()
