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
    void slotWidthIsCappedFlooredAndNeverZero() {
        assertEquals(80, AbilityMenuScreen.slotWidth(4, 400), "the 80px cap");
        assertEquals(42, AbilityMenuScreen.slotWidth(9, 400), "the plain division");
        assertEquals(15, AbilityMenuScreen.slotWidth(18, 300), "wide slots divide");
        assertEquals(5, AbilityMenuScreen.slotWidth(9, 64), "narrow windows shrink cells");
        assertEquals(1, AbilityMenuScreen.slotWidth(60, 64), "the 1px floor");
        assertEquals(0, AbilityMenuScreen.slotWidth(0, 400), "no slots, no cells");
    }

    @Test
    void footerSlotIconsFitThenYieldToTheNumberByWidth() {
        // hotbar#156 doubled: the 24px icon at 2px pad needs 28px; the
        // number needs ~8 more beside it (threshold 36). Icon-less cells
        // always keep the plain number.
        assertEquals(true, AbilityMenuScreen.slotIconFits(40), "a normal cell");
        assertEquals(true, AbilityMenuScreen.slotIconFits(28), "the icon's own width");
        assertEquals(false, AbilityMenuScreen.slotIconFits(27), "one pixel short");
        assertEquals(false, AbilityMenuScreen.slotIconFits(1), "the 1px floor cell");

        assertEquals(28, AbilityMenuScreen.slotLabelX(40, true), "icon + number");
        assertEquals(28, AbilityMenuScreen.slotLabelX(36, true), "both still fit at 36");
        assertEquals(-1, AbilityMenuScreen.slotLabelX(35, true), "icon-only cell");
        assertEquals(-1, AbilityMenuScreen.slotLabelX(28, true), "icon-only below the fit");
        assertEquals(3, AbilityMenuScreen.slotLabelX(40, false), "no icon: today's number");
        assertEquals(3, AbilityMenuScreen.slotLabelX(1, false), "even the 1px cell");
    }

    @Test
    void barNavRectsSitBelowTheChipAlignedToTheStrip() {
        // owner's brief: the prev/next pair sits under the bar chip, left
        // edges aligned, 14px cells with the 2px gap (#157's glyphs)
        int[][] navs = AbilityMenuScreen.barNavRects(new int[] {736, 494, 70, 14});
        assertEquals(14, navs[0][2], "prev cell width");
        assertEquals(14, navs[0][3], "prev cell height");
        assertEquals(736, navs[0][0], "aligned to the chip's left edge");
        assertEquals(navs[0][0] + 16, navs[1][0], "2px gap between the pair");
        assertEquals(494 + 14 + 2, navs[0][1], "directly below the chip");
        assertTrue(navs[0][1] + navs[0][3] <= 494 + 32,
                "the pair stays inside the slot strip's band");
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
    void detailPaneReservesTheRightThird() {
        // owner's brief #9: the pane owns exactly the rightmost third
        assertEquals(640, AbilityMenuScreen.detailX(960), "two thirds for the list");
        assertEquals(641, AbilityMenuScreen.detailX(961), "integer third, no rounding back");
        assertEquals(642, AbilityMenuScreen.detailX(963), "exact thirds split evenly");
        assertEquals(214, AbilityMenuScreen.detailX(320), "the minimum width keeps a third");
        assertTrue(AbilityMenuScreen.detailX(960) + 960 / 3 == 960,
                "the pane reaches the right edge");
    }

    @Test
    void detailToggleLineCountsItsSections() {
        // name block (16) + activation + tags/cost, then stats and fields
        // at 12 each - the draw pass and the button's hit test share it
        assertEquals(40, AbilityMenuScreen.detailToggleLine(0, false, 0),
                "three fixed lines before fields");
        assertEquals(52, AbilityMenuScreen.detailToggleLine(0, true, 0),
                "stats adds one line");
        assertEquals(116, AbilityMenuScreen.detailToggleLine(40, true, 2),
                "top offset + stats + two fields");

        int[] button = AbilityMenuScreen.detailToggleButton(0, 100, 200, true, 2);
        assertEquals(76, button[1], "draw and hit agree on the toggle row");
        assertEquals(100 + 200 - 60, button[0], "the button hugs the pane's right edge");
        assertEquals(56, button[2], "button width");
        assertEquals(12, button[3], "button height");
    }
}
