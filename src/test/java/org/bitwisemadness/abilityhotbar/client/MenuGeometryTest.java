package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The menu's pure layout math (hotbar#27): the geometry the design
 * comment moved - row hit-testing under the collapsed filter strip, slot
 * cell widths in the bottom stack, and the meter fill width. Draw calls
 * themselves cannot be proven headless (AGENTS), so these helpers are the
 * fixture-backed share.
 */
class MenuGeometryTest {

    // A representative window: list 58..238, 16px rows -> 11 drawable rows.
    private static final int LIST_TOP = 58;
    private static final int LIST_BOTTOM = 238;
    private static final int ROW_HEIGHT = 16;

    @Test
    void rowIndexAtMapsCoordinatesToDrawableRows() {
        assertEquals(0, AbilityMenuScreen.rowIndexAt(LIST_TOP, LIST_TOP, LIST_BOTTOM, ROW_HEIGHT),
                "the first pixel of the list is row 0");
        assertEquals(0, AbilityMenuScreen.rowIndexAt(73, LIST_TOP, LIST_BOTTOM, ROW_HEIGHT),
                "row 0 spans 58..73");
        assertEquals(1, AbilityMenuScreen.rowIndexAt(74, LIST_TOP, LIST_BOTTOM, ROW_HEIGHT),
                "row 1 starts at 74");
        assertEquals(10, AbilityMenuScreen.rowIndexAt(233, LIST_TOP, LIST_BOTTOM, ROW_HEIGHT),
                "the last drawable row ends at 233");
    }

    @Test
    void rowIndexAtRejectsEverythingOutsideTheList() {
        assertEquals(-1, AbilityMenuScreen.rowIndexAt(LIST_TOP - 1, LIST_TOP, LIST_BOTTOM, ROW_HEIGHT),
                "above the list");
        assertEquals(-1, AbilityMenuScreen.rowIndexAt(LIST_BOTTOM, LIST_TOP, LIST_BOTTOM, ROW_HEIGHT),
                "the bottom edge is exclusive");
        assertEquals(-1, AbilityMenuScreen.rowIndexAt(234, LIST_TOP, LIST_BOTTOM, ROW_HEIGHT),
                "the leftover strip past the last full row is not a row");
        assertEquals(-1, AbilityMenuScreen.rowIndexAt(100, LIST_TOP, LIST_BOTTOM, 0),
                "a degenerate row height never divides");
    }

    @Test
    void slotWidthStretchesToFillTheRowMinusTheCluster() {
        // owner's stretch brief: the row shares everything left of the chip
        // cluster; the divisor caps at NINE so both 18-slot rows align
        assertEquals(31, AbilityMenuScreen.slotWidth(9, 400, 100),
                "(400-16-100)/9 floored");
        assertEquals(AbilityMenuScreen.slotWidth(9, 400, 100),
                AbilityMenuScreen.slotWidth(18, 400, 100),
                "18 slots divide by the same nine: aligned rows");
        assertEquals(96, AbilityMenuScreen.slotWidth(4, 400, 0),
                "no cluster: cells stretch past the old 80px cap");
        assertEquals(5, AbilityMenuScreen.slotWidth(9, 64, 0),
                "narrow windows shrink cells");
        assertEquals(1, AbilityMenuScreen.slotWidth(9, 24, 0), "the 1px floor");
        assertEquals(0, AbilityMenuScreen.slotWidth(0, 400, 0), "no slots, no cells");
    }

    @Test
    void stripRowsSplitAtNineAndKeepTheirHeight() {
        assertEquals(1, AbilityMenuScreen.rowsFor(9), "nine slots stay one row");
        assertEquals(2, AbilityMenuScreen.rowsFor(10), "ten slots split");
        assertEquals(2, AbilityMenuScreen.rowsFor(18), "eighteen = two rows of nine");
        assertEquals(32, AbilityMenuScreen.stripHeight(9), "single row height");
        assertEquals(66, AbilityMenuScreen.stripHeight(18), "two rows plus their gap");
    }

    @Test
    void slotNumbersCentreInsideTheVanillaSelector() {
        // owner: an icon-sized selector box (like the world hotbar) - the
        // number centres INSIDE the box, whatever the cell width
        assertEquals(28, AbilityMenuScreen.selectorSide(40),
                "a normal cell takes the 28px box");
        assertEquals(23, AbilityMenuScreen.selectorSide(27),
                "a narrow cell clamps the box to its width");
        assertEquals(13, AbilityMenuScreen.slotLabelCenteredX(40, 6),
                "a six-pixel label centres in the 28px box");
        assertEquals(13, AbilityMenuScreen.slotLabelCenteredX(36, 6),
                "centred again at 36");
        assertEquals(3, AbilityMenuScreen.slotLabelCenteredX(1, 6),
                "the floored 8px box still never leaves the cell");
    }

    @Test
    void rowToggleButtonsSitAtTheRowsRightEnd() {
        // owner's brief: #148's button moved from the pane into the row,
        // onto the bottom line beside the state text (with the mode pill)
        int[] button = AbilityMenuScreen.rowToggleButtonRect(58, 300);
        assertEquals(8 + 300 - 58, button[0], "hugs the row's right end");
        assertEquals(58 + 36 - 18, button[1], "bottom line of the 36px row");
        assertEquals(56, button[2], "button width");
        assertEquals(12, button[3], "button height");
    }

    @Test
    void footerSlotIconsFitByWidth() {
        // hotbar#156 doubled: the 24px icon at 2px pad needs 28px
        assertEquals(true, AbilityMenuScreen.slotIconFits(40), "a normal cell");
        assertEquals(true, AbilityMenuScreen.slotIconFits(28), "the icon's own width");
        assertEquals(false, AbilityMenuScreen.slotIconFits(27), "one pixel short");
        assertEquals(false, AbilityMenuScreen.slotIconFits(1), "the 1px floor cell");
    }

    @Test
    void footerIconsFillTheSelectorsWindow() {
        // owner: the icon fills the selection sprite's 16x16 window at
        // (4,4) of 24x23 - centring a smaller icon in the whole box left
        // it 1px too high with a gap beneath (tensura's icons)
        int[] normal = AbilityMenuScreen.slotIconRect(28);
        assertEquals(5, normal[0], "the window's x offset inside the 28px box");
        assertEquals(5, normal[1], "the window's y offset - no longer riding high");
        assertEquals(19, normal[2],
                "the window rounds to 19: one pixel bigger than the old 18");

        int[] narrow = AbilityMenuScreen.slotIconRect(24);
        assertEquals(16, narrow[2], "a narrow cell's window is 16px wide");

        for (int side : new int[] {8, 16, 24, 25, 26, 27, 28}) {
            int[] rect = AbilityMenuScreen.slotIconRect(side);
            assertTrue(rect[0] >= 0 && rect[1] >= 0,
                    "offsets never leave the box at side " + side);
            assertTrue(rect[0] + rect[2] <= side && rect[1] + rect[2] <= side,
                    "the icon stays inside the box at side " + side);
            assertTrue(rect[2] >= 4, "the floor size at side " + side);
        }
    }

    @Test
    void slotCellAtMirrorsTheStripGeometry() {
        // owner's drag-to-assign brief: the drop target is the drawn cell -
        // 9 slots of 31px, strip top at height 400 - 16 - 32 = 352
        assertEquals(0, AbilityMenuScreen.slotCellAt(12, 355, 9, 400, 100, 400),
                "the first cell's first pixels");
        assertEquals(1, AbilityMenuScreen.slotCellAt(40, 355, 9, 400, 100, 400),
                "the second cell");
        assertEquals(-1, AbilityMenuScreen.slotCellAt(12, 340, 9, 400, 100, 400),
                "above the strip is not a cell");
        assertEquals(-1, AbilityMenuScreen.slotCellAt(12, 355, 0, 400, 100, 400),
                "no slots, no cells");
    }

    @Test
    void barNavRectsSitVerticalBesideTheChipColumn() {
        // owner's brief: prev over next, right of the chip/clear column
        int[][] navs = AbilityMenuScreen.barNavRects(new int[] {736, 494, 70, 14});
        assertEquals(14, navs[0][2], "prev cell width");
        assertEquals(14, navs[0][3], "prev cell height");
        assertEquals(736 + 70 + 4, navs[0][0], "right of the chip column");
        assertEquals(navs[0][0], navs[1][0], "rotated: the pair shares an x");
        assertEquals(494, navs[0][1], "prev top-aligns with the chip");
        assertEquals(494 + 16, navs[1][0 + 1], "next stacks below with the 2px gap");
        assertTrue(navs[1][1] + navs[1][3] <= 494 + 32,
                "the vertical pair stays inside the slot strip's band");

        // a narrow chip still leaves room for the clear pill column
        int[][] narrow = AbilityMenuScreen.barNavRects(new int[] {400, 494, 30, 14});
        assertEquals(400 + 56 + 4, narrow[0][0], "the clear pill's width floors the column");
    }

    @Test
    void barCycleIndexWrapsBothWays() {
        // hotbar#157: the chip's forward step and the buttons' backward step
        // share one wrap rule; an empty list never cycles
        assertEquals(0, AbilityMenuScreen.cycleIndex(-1, 3, 1), "no selection enters forward");
        assertEquals(2, AbilityMenuScreen.cycleIndex(-1, 3, -1), "enters at the last going back");
        assertEquals(1, AbilityMenuScreen.cycleIndex(0, 3, 1), "forward one");
        assertEquals(0, AbilityMenuScreen.cycleIndex(2, 3, 1), "forward wraps to the first");
        assertEquals(2, AbilityMenuScreen.cycleIndex(0, 3, -1), "backward wraps to the last");
        assertEquals(1, AbilityMenuScreen.cycleIndex(2, 3, -1), "backward one");
        assertEquals(-1, AbilityMenuScreen.cycleIndex(0, 0, 1), "no bars, no cycle");
    }

    @Test
    void meterFillWidthScalesAndClamps() {
        assertEquals(0, MenuChrome.meterFillWidth(44, 0f), "empty track");
        assertEquals(42, MenuChrome.meterFillWidth(44, 1f), "full track keeps its 1px border");
        assertEquals(21, MenuChrome.meterFillWidth(44, 0.5f), "half");
        assertEquals(28, MenuChrome.meterFillWidth(44, 12f / 18f), "12 of 18 slots");
        assertEquals(42, MenuChrome.meterFillWidth(44, 2f), "over-full clamps");
        assertEquals(0, MenuChrome.meterFillWidth(44, -1f), "under-empty clamps");
        assertEquals(0, MenuChrome.meterFillWidth(1, 1f), "a track too thin to fill");
    }

    @Test
    void selectorWindowMatchesTheRasterizedSpriteWindow() {
        // the sprite is 24x23 with a 16x16 window at (4,4); nearest-
        // sampled at side 28 it lands on pixels 5..22 by 5..23 - miss it
        // and a white sliver or a clipped ring shows (owner's subtraction)
        int[] window = AbilityMenuScreen.selectorWindow(28);
        assertEquals(5, window[0], "window x");
        assertEquals(5, window[1], "window y");
        assertEquals(18, window[2], "window width");
        assertEquals(19, window[3], "window height");
        for (int side : new int[] {8, 16, 24, 26, 27, 28}) {
            int[] rect = AbilityMenuScreen.selectorWindow(side);
            assertTrue(rect[0] >= 0 && rect[1] >= 0
                            && rect[0] + rect[2] <= side && rect[1] + rect[3] <= side,
                    "the window stays inside the box at side " + side);
            assertTrue(rect[2] > 0 && rect[3] > 0,
                    "the window is never empty at side " + side);
        }
    }

    @Test
    void detailPaneReservesItsConfiguredShare() {
        // owner's brief #9 (the rightmost third), later configurable as
        // menu.detailPaneWidthPercent: 33% of the width, integer math
        assertEquals(644, AbilityMenuScreen.detailX(960), "the rightmost 33 percent");
        assertEquals(644, AbilityMenuScreen.detailX(961), "integer percent, no rounding back");
        assertEquals(646, AbilityMenuScreen.detailX(963));
        assertEquals(215, AbilityMenuScreen.detailX(320), "the minimum width keeps its share");
        assertEquals(316, 960 - AbilityMenuScreen.detailX(960),
                "the pane measures exactly 33 percent of the width");
        assertTrue(AbilityMenuScreen.detailX(960) < 960, "the pane reaches the right edge");
    }

}
