package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bitwisemadness.abilityhotbar.config.BarAnchor;
import org.junit.jupiter.api.Test;

/**
 * Bar layout math (REQ-HOT-01/07): slot rectangles follow orientation, scale,
 * offsets and anchors, and the two axes never overlap across a stride. Pure
 * geometry, no GUI.
 */
class BarLayoutTest {

    private static final int SCREEN_W = 1920;
    private static final int SCREEN_H = 1080;

    private static BarLayout horizontal() {
        return layout(9, 1, true, 0, 0, 1.0);
    }

    private static BarLayout vertical() {
        return layout(9, 1, false, 0, 0, 1.0);
    }

    /** Default anchor, the historical placement, so existing assertions hold. */
    private static BarLayout layout(
            int slotCount, int gap, boolean horizontal, int offX, int offY, double scale) {
        return new BarLayout(slotCount, 20, gap, horizontal, BarAnchor.ABOVE_HOTBAR,
                StatusCluster.BASELINE_DEPTH, offX, offY, scale);
    }

    private static BarLayout anchored(BarAnchor anchor, boolean horizontal) {
        return new BarLayout(9, 20, 1, horizontal, anchor, StatusCluster.BASELINE_DEPTH, 0, 0, 1.0);
    }

    /** Slot-grid width along X. */
    private static int barWidth(BarLayout layout) {
        return layout.horizontal() ? layout.extent() : layout.scaledSlotSize();
    }

    /** Slot-grid height along Y. */
    private static int barHeight(BarLayout layout) {
        return layout.horizontal() ? layout.scaledSlotSize() : layout.extent();
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
    void defaultAnchorMatchesLegacyPlacement() {
        // The pre-anchor formulas, asserted literally so a refactor cannot
        // silently move the default position.
        for (boolean horizontal : new boolean[] {true, false}) {
            for (int[] offset : new int[][] {{0, 0}, {15, -10}}) {
                BarLayout layout = new BarLayout(7, 20, 1, horizontal, BarAnchor.ABOVE_HOTBAR,
                        StatusCluster.BASELINE_DEPTH, offset[0], offset[1], 1.5);
                int width = horizontal ? layout.extent() : layout.scaledSlotSize();
                int height = horizontal ? layout.scaledSlotSize() : layout.extent();

                assertEquals((SCREEN_W - width) / 2 + offset[0], layout.originX(SCREEN_W));
                assertEquals(SCREEN_H - height - 22 + offset[1], layout.originY(SCREEN_H));
            }
        }
    }

    @Test
    void aboveXpRestsOnTheExperienceBar() {
        for (boolean horizontal : new boolean[] {true, false}) {
            BarLayout layout = anchored(BarAnchor.ABOVE_XP, horizontal);
            assertEquals(SCREEN_H - 29, layout.originY(SCREEN_H) + barHeight(layout),
                    "slot-grid bottom sits on the vanilla XP bar's top edge");
        }
    }

    @Test
    void aboveStatusUsesTheSuppliedDepth() {
        int depth = 70;
        BarLayout layout = new BarLayout(9, 20, 1, true, BarAnchor.ABOVE_STATUS, depth, 0, 0, 1.0);

        assertEquals(SCREEN_H - depth, layout.originY(SCREEN_H) + layout.scaledSlotSize());
    }

    @Test
    void leftEdgeIsInsetAndVerticallyCentred() {
        for (boolean horizontal : new boolean[] {true, false}) {
            BarLayout layout = anchored(BarAnchor.LEFT_EDGE, horizontal);

            assertEquals(4, layout.originX(SCREEN_W), "inset from the left border");
            assertEquals((SCREEN_H - barHeight(layout)) / 2, layout.originY(SCREEN_H),
                    "centred on the border's midpoint");
        }
    }

    @Test
    void rightEdgeMirrorsLeftEdge() {
        for (boolean horizontal : new boolean[] {true, false}) {
            BarLayout layout = anchored(BarAnchor.RIGHT_EDGE, horizontal);

            assertEquals(SCREEN_W - barWidth(layout) - 4, layout.originX(SCREEN_W));
            assertEquals((SCREEN_H - barHeight(layout)) / 2, layout.originY(SCREEN_H));
        }
    }

    @Test
    void edgeAnchorsKeepTheSelectionFrameOnScreen() {
        // The vanilla selection frame overhangs a slot by 2 px per side, scaled.
        for (double scale : new double[] {0.5, 1.0, 2.0}) {
            int overhang = (int) Math.round(2 * scale);
            for (boolean horizontal : new boolean[] {true, false}) {
                BarLayout left = new BarLayout(9, 20, 1, horizontal, BarAnchor.LEFT_EDGE,
                        StatusCluster.BASELINE_DEPTH, 0, 0, scale);
                BarLayout right = new BarLayout(9, 20, 1, horizontal, BarAnchor.RIGHT_EDGE,
                        StatusCluster.BASELINE_DEPTH, 0, 0, scale);

                assertTrue(left.originX(SCREEN_W) - overhang >= 0,
                        "left frame on-screen at scale " + scale);
                assertTrue(right.originX(SCREEN_W) + barWidth(right) + overhang <= SCREEN_W,
                        "right frame on-screen at scale " + scale);
            }
        }
    }

    @Test
    void everyAnchorStaysInBoundsForBothOrientations() {
        for (BarAnchor anchor : BarAnchor.values()) {
            for (boolean horizontal : new boolean[] {true, false}) {
                BarLayout layout = anchored(anchor, horizontal);
                for (int i = 0; i < 9; i++) {
                    int[] rect = layout.slotRect(i, SCREEN_W, SCREEN_H);
                    String at = anchor + "/" + (horizontal ? "h" : "v") + " slot " + i;
                    assertTrue(rect[0] >= 0 && rect[1] >= 0, at + " on-screen origin");
                    assertTrue(rect[0] + rect[2] <= SCREEN_W, at + " fits width");
                    assertTrue(rect[1] + rect[3] <= SCREEN_H, at + " fits height");
                }
            }
        }
    }

    @Test
    void scaleGrowsSlotSizeAndStride() {
        BarLayout small = layout(3, 2, true, 0, 0, 1.0);
        BarLayout big = layout(3, 2, true, 0, 0, 2.0);

        assertEquals(20, small.scaledSlotSize());
        assertEquals(40, big.scaledSlotSize());
        assertTrue(big.extent() > small.extent(), "scaled bar is larger");
        assertEquals(small.extent() * 2, big.extent(), "extent scales linearly");
    }

    @Test
    void offsetsShiftTheOrigin() {
        BarLayout base = layout(5, 1, true, 0, 0, 1.0);
        BarLayout shifted = layout(5, 1, true, 15, -10, 1.0);

        assertEquals(base.originX(SCREEN_W) + 15, shifted.originX(SCREEN_W));
        assertEquals(base.originY(SCREEN_H) - 10, shifted.originY(SCREEN_H));
    }

    @Test
    void offsetsRefineEveryAnchor() {
        for (BarAnchor anchor : BarAnchor.values()) {
            BarLayout base = anchored(anchor, true);
            BarLayout shifted = new BarLayout(9, 20, 1, true, anchor,
                    StatusCluster.BASELINE_DEPTH, 15, -10, 1.0);

            assertEquals(base.originX(SCREEN_W) + 15, shifted.originX(SCREEN_W), anchor.toString());
            assertEquals(base.originY(SCREEN_H) - 10, shifted.originY(SCREEN_H), anchor.toString());
        }
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
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(0, 20, 1, true,
                BarAnchor.ABOVE_HOTBAR, StatusCluster.BASELINE_DEPTH, 0, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(3, 0, 1, true,
                BarAnchor.ABOVE_HOTBAR, StatusCluster.BASELINE_DEPTH, 0, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(3, 20, -1, true,
                BarAnchor.ABOVE_HOTBAR, StatusCluster.BASELINE_DEPTH, 0, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(3, 20, 1, true,
                BarAnchor.ABOVE_HOTBAR, StatusCluster.BASELINE_DEPTH, 0, 0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(3, 20, 1, true,
                BarAnchor.ABOVE_HOTBAR, StatusCluster.BASELINE_DEPTH, 0, 0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(3, 20, 1, true,
                null, StatusCluster.BASELINE_DEPTH, 0, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BarLayout(3, 20, 1, true,
                BarAnchor.ABOVE_STATUS, 0, 0, 0, 1.0));
    }

    @Test
    void segmentsSplitAtNineCells() {
        // hotbar#86: one segment through nine slots, two beyond (the ceiling
        // is 18), and a partial final segment never exceeds nine cells.
        for (int n = 1; n <= BarLayout.CELLS_PER_SEGMENT; n++) {
            assertEquals(1, layout(n, 1, true, 0, 0, 1.0).segmentCount(),
                    n + " slots render as one segment");
        }
        for (int n = BarLayout.CELLS_PER_SEGMENT + 1; n <= 18; n++) {
            BarLayout split = layout(n, 1, true, 0, 0, 1.0);
            assertEquals(2, split.segmentCount(), n + " slots render as two segments");
            int lastSegmentCells =
                    n - BarLayout.CELLS_PER_SEGMENT * (split.segmentCount() - 1);
            assertTrue(lastSegmentCells >= 1 && lastSegmentCells <= BarLayout.CELLS_PER_SEGMENT,
                    "the partial segment holds 1..9 cells, got " + lastSegmentCells);
        }
        // the documented splits: 10 -> 9 + 1, 18 -> 9 + 9
        assertEquals(2, layout(10, 1, true, 0, 0, 1.0).segmentCount());
        assertEquals(2, layout(18, 1, true, 0, 0, 1.0).segmentCount());
    }

    @Test
    void segmentOffsetsAreFlushAndMoveAsOneUnit() {
        // Flush placement: segment 1 starts where the ninth cell ends, so the
        // pair's bounding box is this layout's own box - anchor, offsets, scale
        // and orientation apply once and every segment moves together (#86 r4).
        BarLayout atScaleOne = layout(18, 0, true, 0, 0, 1.0);
        assertEquals(0, atScaleOne.segmentOffset(0));
        assertEquals(9 * atScaleOne.stride(), atScaleOne.segmentOffset(1));
        assertEquals(180, atScaleOne.segmentOffset(1), "nine 20px cells at scale 1");

        BarLayout scaled = layout(18, 0, true, 0, 0, 2.0);
        assertEquals(9 * scaled.stride(), scaled.segmentOffset(1),
                "the seam scales with the cells");

        BarLayout vertical = layout(18, 0, false, 0, 0, 1.0);
        assertEquals(9 * vertical.stride(), vertical.segmentOffset(1),
                "the same math runs down the Y axis");

        // out-of-range segments fail loudly, like slotRect
        assertThrows(IndexOutOfBoundsException.class,
                () -> layout(10, 1, true, 0, 0, 1.0).segmentOffset(2));
        assertThrows(IndexOutOfBoundsException.class,
                () -> layout(9, 1, true, 0, 0, 1.0).segmentOffset(1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> layout(9, 1, true, 0, 0, 1.0).segmentOffset(-1));
    }
}
