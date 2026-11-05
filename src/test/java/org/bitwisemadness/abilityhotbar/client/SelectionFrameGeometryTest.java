package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bitwisemadness.abilityhotbar.config.BarAnchor;
import org.junit.jupiter.api.Test;

/**
 * Selection frame alignment (hotbar#103): the frame's transparent 16x16
 * window must sit on the slot's inner area - where the icon draws, slot +
 * 2 scaled px - in both orientations at every supported scale, and in the
 * issue's exact configuration (vertical + RIGHT_EDGE).
 *
 * <p>Sprite anatomy measured from the vanilla asset
 * {@code assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png}:
 * 24x23 with the window at rows/cols 4..19, i.e. rings left 4, top 4,
 * right 4, bottom 3. A quarter turn lands the thin 3px bottom ring on the
 * frame's <b>left</b>, so solving the blit x for the box (the pre-fix code)
 * parks the window at slot+1 - the reported one-pixel-left frame. The
 * constants below are the test's independent model of that texture; if the
 * implementation's ring constant drifts from the real sprite, the window
 * assertions fail.</p>
 */
class SelectionFrameGeometryTest {

    private static final int SPRITE_WIDTH = 24;
    private static final int SPRITE_HEIGHT = 23;
    private static final int RING_LEFT = 4;
    private static final int RING_TOP = 4;
    private static final int RING_BOTTOM = 3;

    private static final int[] SLOT = {100, 50, 20, 20};
    private static final double[] SCALES = {0.5, 1.0, 1.5, 2.0};

    /** Where the frame's window starts on screen X, from the blit args. */
    private static int windowX(int[] frame, boolean horizontal) {
        // a turned blit's drawn box width comes from the blit's height arg
        int boxWidth = horizontal ? frame[2] : frame[3];
        int ring = horizontal ? RING_LEFT : RING_BOTTOM;
        int spriteWidth = horizontal ? SPRITE_WIDTH : SPRITE_HEIGHT;
        return frame[0] + Math.round(ring * boxWidth / (float) spriteWidth);
    }

    /** Where the frame's window starts on screen Y, from the blit args. */
    private static int windowY(int[] frame, boolean horizontal) {
        // a turned blit's drawn box height comes from the blit's width arg
        int boxHeight = horizontal ? frame[3] : frame[2];
        int ring = horizontal ? RING_TOP : RING_LEFT;
        int spriteHeight = horizontal ? SPRITE_HEIGHT : SPRITE_WIDTH;
        return frame[1] + Math.round(ring * boxHeight / (float) spriteHeight);
    }

    private static int iconMargin(double scale) {
        return Math.round((float) (2 * scale));
    }

    @Test
    void horizontalWindowSitsOnTheSlotAtEveryScale() {
        for (double scale : SCALES) {
            int[] frame = AbilityBarLayer.selectionFrameRect(SLOT, true, scale);
            assertEquals(SLOT[0] + iconMargin(scale), windowX(frame, true), "x at scale " + scale);
            assertEquals(SLOT[1] + iconMargin(scale), windowY(frame, true), "y at scale " + scale);
        }
    }

    @Test
    void turnedWindowSitsOnTheSlotAtEveryScale() {
        // hotbar#103: with the symmetric offset this read slot+1 at scale 1
        // - the one-pixel-left frame the playtest reported.
        for (double scale : SCALES) {
            int[] frame = AbilityBarLayer.selectionFrameRect(SLOT, false, scale);
            assertEquals(SLOT[0] + iconMargin(scale), windowX(frame, false), "x at scale " + scale);
            assertEquals(SLOT[1] + iconMargin(scale), windowY(frame, false), "y at scale " + scale);
        }
    }

    @Test
    void verticalRightEdgePlaytestGeometryIsAligned() {
        // the issue's exact configuration: horizontal = false, RIGHT_EDGE,
        // scale 1 - every slot's frame must land on its own rect
        BarLayout layout = new BarLayout(9, 20, 0, false, BarAnchor.RIGHT_EDGE,
                StatusCluster.BASELINE_DEPTH, 0, 0, 1.0);
        for (int slot = 0; slot < 9; slot++) {
            int[] rect = layout.slotRect(slot, 1920, 1080);
            int[] frame = AbilityBarLayer.selectionFrameRect(rect, false, 1.0);
            assertEquals(rect[0] - 1, frame[0],
                    "the turned frame blits one pixel right of the old x - hotbar#103");
            assertEquals(rect[0] + 2, windowX(frame, false),
                    "the window must sit on the slot's inner 16 (the icon area) - hotbar#103");
        }
    }

    @Test
    void solvingForTheBoxIsExactlyTheOnePixelLeftRegression() {
        // the pre-fix formula: symmetric offset on the turned path. The thin
        // bottom ring (3) minus offset (2) leaves the window at slot+1.
        int oldFrameX = SLOT[0] - 2;
        assertEquals(SLOT[0] + 1, oldFrameX + RING_BOTTOM, "documents the reported bug arithmetic");
    }
}
