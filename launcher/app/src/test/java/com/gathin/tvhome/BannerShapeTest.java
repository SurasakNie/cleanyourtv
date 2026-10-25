package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BannerShapeTest {
    @Test
    public void wideBannersAreKept() {
        assertTrue(AppArt.isBannerShaped(320, 180));
        assertTrue(AppArt.isBannerShaped(1920, 1080));
    }

    @Test
    public void squareAndTallPicturesAreNotBanners() {
        assertFalse(AppArt.isBannerShaped(64, 64));
        assertFalse(AppArt.isBannerShaped(256, 256));
        assertFalse(AppArt.isBannerShaped(100, 160));
    }

    @Test
    public void drawablesWithoutAnIntrinsicSizeStillFillTheTile() {
        assertTrue(AppArt.isBannerShaped(-1, -1));
        assertTrue(AppArt.isBannerShaped(0, 0));
    }
}
