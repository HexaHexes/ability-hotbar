package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(40, AbilityMenuScreen.slotWidth(9, 400), "the 40px cap");
        assertEquals(15, AbilityMenuScreen.slotWidth(18, 300), "the plain division");
        assertEquals(5, AbilityMenuScreen.slotWidth(9, 64), "narrow windows shrink cells");
        assertEquals(1, AbilityMenuScreen.slotWidth(60, 64), "the 1px floor");
        assertEquals(0, AbilityMenuScreen.slotWidth(0, 400), "no slots, no cells");
    }

    @Test
    void footerSlotIconsFitThenYieldToTheNumberByWidth() {
        // hotbar#156: the 12px icon at 2px pad needs 16px; the number needs
        // 12 more beside it. Icon-less cells always keep the plain number.
        assertEquals(true, AbilityMenuScreen.slotIconFits(40), "a normal cell");
        assertEquals(true, AbilityMenuScreen.slotIconFits(16), "the icon's own width");
        assertEquals(false, AbilityMenuScreen.slotIconFits(15), "one pixel short");
        assertEquals(false, AbilityMenuScreen.slotIconFits(1), "the 1px floor cell");

        assertEquals(16, AbilityMenuScreen.slotLabelX(40, true), "icon + number");
        assertEquals(16, AbilityMenuScreen.slotLabelX(28, true), "both still fit at 28");
        assertEquals(-1, AbilityMenuScreen.slotLabelX(27, true), "icon-only cell");
        assertEquals(-1, AbilityMenuScreen.slotLabelX(16, true), "icon-only at the floor");
        assertEquals(3, AbilityMenuScreen.slotLabelX(40, false), "no icon: today's number");
        assertEquals(3, AbilityMenuScreen.slotLabelX(1, false), "even the 1px cell");
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
    void tooltipPanelHeightCountsItsLines() {
        // issues #119/#148: 14px base + 12 per extra line - draw and the
        // toggle button's hit test share this exact arithmetic
        assertEquals(14, AbilityMenuScreen.panelHeight(false, 0, false, false),
                "meta only keeps the original one-liner");
        assertEquals(26, AbilityMenuScreen.panelHeight(false, 0, false, true),
                "description alone matches the pre-#119 panel exactly");
        assertEquals(26, AbilityMenuScreen.panelHeight(true, 0, false, false),
                "stats alone takes one extra line");
        assertEquals(74, AbilityMenuScreen.panelHeight(true, 2, true, true),
                "stats + two fields + toggle + description");
        assertEquals(13, AbilityMenuScreen.toggleLineOffset(false, 0),
                "the toggle line sits under meta");
        assertEquals(49, AbilityMenuScreen.toggleLineOffset(true, 2),
                "stats and two fields push it down by their lines");
    }
}
