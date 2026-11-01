package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Bar layout math (REQ-HOT-01/07): slot rectangles follow orientation, scale
 * and offsets, and the two axes never overlap across a stride. Pure geometry,
 * no GUI.
 */
class BarLayoutTest {

    private static final int SCREEN_W = 1920;
    private static final int SCREEN_H = 1080;

    private static BarLayout horizontal() {
        return new BarLayout(9, 20, 1, true, 0, 0, 1.0);
    }

    private static BarLayout vertical() {
        return new BarLayout(9, 20, 1, false, 0, 0, 1.0);
    }

    @Test
    void horizontalSlotsAdvanceAlongXOnly() {
        BarLayout layout = horizontal();
        int[] first = layout.slotRect(0, SCREEN_W, SCREEN_H);
        int[] second = layout.slotRect(1, SCREEN_W, SCREEN_H);

        assertEquals(first[1], second[1], "y must not move in a horizontal bar");
        assertTrue(second[0] > first[0], "x must advance to the right");
        assertEquals(first[2] + 1, second[0] - first[0], "stride = size + gap");
    }

    @Test
    void verticalSlotsAdvanceAlongYOnly() {
        BarLayout layout = vertical();
        int[] first = layout.slotRect(0, SCREEN_W, SCREEN_H);
        int[] second = layout.slotRect(1, SCREEN_W, SCREEN_H);

        assertEquals(first[0], second[0], "x must not move in a vertical bar");
        assertTrue(second[1] > first[1], "y must advance downward");
    }

    @Test
    void horizontalBarIsCentered() {
        BarLayout layout = horizontal();
        int extent = layout.extent();
        int originX = layout.originX(SCREEN_W);
        assertEquals((SCREEN_W - extent) / 2, originX, "bar centered on screen");
        // Symmetric: right edge mirrors left edge.
        assertEquals(SCREEN_W - extent - originX, originX, "centering is symmetric");
    }

    @Test
    void scaleGrowsSlotSizeAndStride() {
        BarLayout small = new BarLayout(3, 20, 2, true, 0, 0, 1.0);
        BarLayout big = new BarLayout(3, 20, 2, true, 0, 0, 2.0);

        assertEquals(20, small.scaledSlotSize());
        assertEquals(40, big.scaledSlotSize());
        assertTrue(big.extent() > small.extent(), "scaled bar is larger");
        assertEquals(small.extent() * 2, big.extent(), "extent scales linearly");
    }

    @Test
    void offsetsShiftTheOrigin() {
        BarLayout base = new BarLayout(5, 20, 1, true, 0, 0, 1.0);
        BarLayout shifted = new BarLayout(5, 20, 1, true, 15, -10, 1.0);

        assertEquals(base.originX(SCREEN_W) + 15, shifted.originX(SCREEN_W));
        assertEquals(base.originY(SCREEN_H) - 10, shifted.originY(SCREEN_H));
    }

    @Test
    void slotRectIsInBoundsForEverySlot() {
        BarLayout layout = horizontal();
        for (int i = 0; i < 9; i++) {
            int[] r = layout.slotRect(i, SCREEN_W, SCREEN_H);
            assertTrue(r[0] >= 0 && r[1] >= 0, "slot " + i + " on-screen origin");
            assertTrue(r[0] + r[2] <= SCREEN_W, "slot " + i + " fits width");
            assertTrue(r[1] + r[3] <= SCREEN_H, "slot " + i + " fits height");
        }
    }

    @Test
    void outOfRangeSlotThrows() {
        BarLayout layout = horizontal();
        assertThrows(IndexOutOfBoundsException.class, () -> layout.slotRect(-1, SCREEN_W, SCREEN_H));
        assertThrows(IndexOutOfBoundsException.class, () -> layout.slotRect(9, SCREEN_W, SCREEN_H));
    }

    @Test
    void invalidConstructionThrows() {
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(0, 20, 1, true, 0, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(3, 0, 1, true, 0, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(3, 20, -1, true, 0, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(3, 20, 1, true, 0, 0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(3, 20, 1, true, 0, 0, Double.NaN));
    }
}
