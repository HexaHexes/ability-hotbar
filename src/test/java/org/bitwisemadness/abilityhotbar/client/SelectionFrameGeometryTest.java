package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bitwisemadness.abilityhotbar.config.BarAnchor;
import org.junit.jupiter.api.Test;

/**
 * Selection frame alignment (hotbar#103) and vertical orientation
 * (hotbar#109): the frame's transparent 16x16 window must sit on the slot's
 * inner area - where the icon draws, slot + 2 scaled px - in both
 * orientations at every supported scale, and the vertical frame must render
 * at <b>270 degrees</b> (the shipped 90 + the requested 180 flip).
 *
 * <p>Sprite anatomy measured from the vanilla asset
 * {@code assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png}:
 * 24x23 with the window at rows/cols 4..19, i.e. rings left 4, top 4,
 * right 4, bottom 3. The flip maps the sprite's TOP ring onto the frame's
 * LEFT, so the window-solve inset pairs with the rotation - both travel in
 * {@code selectionFrameRect}'s five-element result (frame[4] = rotation),
 * so a drift in either, or pairing the flip with the stale bottom-ring
 * inset, fails these fixtures. The ring constants are the test's independent
 * model of the texture.</p>
 */
class SelectionFrameGeometryTest {

    private static final int SPRITE_WIDTH = 24;
    private static final int SPRITE_HEIGHT = 23;
    private static final int RING_LEFT = 4;
    private static final int RING_TOP = 4;
    private static final int RING_RIGHT = 4;
    private static final int RING_BOTTOM = 3;

    private static final int[] SLOT = {100, 50, 20, 20};
    private static final double[] SCALES = {0.5, 1.0, 1.5, 2.0};

    /** Where the frame's window starts on screen X, from the blit args. */
    private static int windowX(int[] frame, boolean horizontal) {
        // horizontal: the left ring; flipped vertical: the top ring lands left
        int boxWidth = horizontal ? frame[2] : frame[3];
        int ring = horizontal ? RING_LEFT : RING_TOP;
        int spriteWidth = horizontal ? SPRITE_WIDTH : SPRITE_HEIGHT;
        return frame[0] + Math.round(ring * boxWidth / (float) spriteWidth);
    }

    /** Where the frame's window starts on screen Y, from the blit args. */
    private static int windowY(int[] frame, boolean horizontal) {
        // horizontal: the top ring; flipped vertical: the right ring lands top
        int boxHeight = horizontal ? frame[3] : frame[2];
        int ring = horizontal ? RING_TOP : RING_RIGHT;
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
            assertEquals(0, frame[4], "the horizontal frame never turns - direct blit");
            assertEquals(SLOT[0] + iconMargin(scale), windowX(frame, true), "x at scale " + scale);
            assertEquals(SLOT[1] + iconMargin(scale), windowY(frame, true), "y at scale " + scale);
        }
    }

    @Test
    void verticalFrameRendersAt270DegreesAndWindowSitsOnTheSlot() {
        // hotbar#109: 90 (shipped) + the requested 180-degree flip = 270;
        // hotbar#103: the window still lands on the slot's inner 16
        for (double scale : SCALES) {
            int[] frame = AbilityBarLayer.selectionFrameRect(SLOT, false, scale);
            assertEquals(270, frame[4],
                    "vertical selector = 90 + the requested 180 flip (hotbar#109)");
            assertEquals(SLOT[0] + iconMargin(scale), windowX(frame, false), "x at scale " + scale);
            assertEquals(SLOT[1] + iconMargin(scale), windowY(frame, false), "y at scale " + scale);
        }
    }

    @Test
    void verticalRightEdgePlaytestGeometryIsAligned() {
        // the issue's exact configuration: horizontal = false, RIGHT_EDGE,
        // scale 1 - every slot's flipped frame must land on its own rect
        BarLayout layout = new BarLayout(9, 20, 0, false, BarAnchor.RIGHT_EDGE,
                StatusCluster.BASELINE_DEPTH, 0, 0, 1.0);
        for (int slot = 0; slot < 9; slot++) {
            int[] rect = layout.slotRect(slot, 1920, 1080);
            int[] frame = AbilityBarLayer.selectionFrameRect(rect, false, 1.0);
            assertEquals(270, frame[4], "flipped frame - hotbar#109");
            assertEquals(rect[0] - 2, frame[0],
                    "x = slot + offset(2) - top-ring inset(4) under the flip");
            assertEquals(rect[0] + 2, windowX(frame, false),
                    "the window must sit on the slot's inner 16 (the icon area) - hotbar#103");
        }
    }

    @Test
    void staleInsetUnderTheFlipParksTheWindowOnePixelRight() {
        // the pairing arithmetic: with the flip the left ring is the sprite's
        // TOP (4), not its bottom (3) - reusing the pre-flip inset moves the
        // window one pixel right of the slot instead of onto it.
        int staleFrameX = SLOT[0] + 2 - RING_BOTTOM;
        assertEquals(SLOT[0] + 3, staleFrameX + RING_TOP,
                "documents why the inset must flip together with the rotation");
        assertEquals(SLOT[0] + 2, (SLOT[0] + 2 - RING_TOP) + RING_TOP,
                "the paired solve (offset - top ring + top ring) lands on the slot");
    }
}
