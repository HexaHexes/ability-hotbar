package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilityhotbar.registry.MenuStyle;
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
    void slotWidthIsTheSameSizeForEveryBarIncludingProviders() {
        // owner's uniform-pill brief: the row shares everything left of the
        // chip cluster AND every bar divides by NINE - a short bar or a
        // provider strip draws the same-size pills, its row just ends earlier
        assertEquals(31, AbilityMenuScreen.slotWidth(9, 400, 100),
                "(400-16-100)/9 floored");
        assertEquals(AbilityMenuScreen.slotWidth(9, 400, 100),
                AbilityMenuScreen.slotWidth(18, 400, 100),
                "18 slots divide by the same nine: aligned rows");
        assertEquals(AbilityMenuScreen.slotWidth(9, 400, 100),
                AbilityMenuScreen.slotWidth(4, 400, 100),
                "a 4-slot provider strip draws the assignable bar's pills");
        assertEquals(42, AbilityMenuScreen.slotWidth(4, 400, 0),
                "no cluster: (400-16)/9");
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
    void meterColoursWithoutAnAlphaByteReadAsOpaque() {
        // the play-test report: mastery bars had no visible progress overlay
        // because the palette triple's zero alpha drew a fully transparent fill
        assertEquals(0xFF000000 | MenuStyle.DEFAULT.accent(),
                MenuStyle.opaque(MenuStyle.DEFAULT.accent()),
                "a palette triple must draw as a visible fill");
        int translucent = MenuStyle.alpha(MenuStyle.DEFAULT.background(), 40);
        assertEquals(translucent, MenuStyle.opaque(translucent),
                "an intentional alpha is never flattened");
    }

    @Test
    void selectorWindowMatchesTheRasterizedSpriteWindow() {
        // the sprite is 24x23 with a 16x16 window at (4,4); the owner saw
        // white survive one pixel beyond the centre solve on left/right/
        // bottom (top exact) - so the cut reaches {4,5,20,20} at side 28
        int[] window = AbilityMenuScreen.selectorWindow(28);
        assertEquals(4, window[0], "window x (one pixel wider left)");
        assertEquals(5, window[1], "window y (the top edge is exact)");
        assertEquals(20, window[2], "window width");
        assertEquals(20, window[3], "window height (one pixel taller)");
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

    @Test
    void listTopGrowsWithTheFilterRow() {
        // the filter row matches the slot pills' height (owner's brief):
        // FILTER_Y 40 + the shipped cell height 32 + the standing 6px rule
        assertEquals(78, AbilityMenuScreen.listTop(),
                "the list starts 6px under the grown filter row");
    }

    @Test
    void subStripScrollClampsToItsOverflow() {
        // the pill step is the slot cell's width now (owner's brief); the
        // clamp math is pinned with a chosen 64px step: overflow =
        // count*step - viewport, else nothing moves
        assertEquals(0, AbilityMenuScreen.clampSubScroll(-5, 3, 400, 64),
                "a negative scroll never shows");
        assertEquals(0, AbilityMenuScreen.clampSubScroll(40, 3, 400, 64),
                "a strip that fits does not scroll");
        assertEquals(96, AbilityMenuScreen.clampSubScroll(999, 4, 160, 64),
                "overflow clamps to count*step - viewport");
        assertEquals(96, AbilityMenuScreen.clampSubScroll(96, 4, 160, 64),
                "the clamp is idempotent at the end");
    }

    @Test
    void masteryBarColoursTheThreeStates() {
        MenuStyle style = MenuStyle.TRS_SAODARK;
        assertEquals(style.action(), AbilityMenuScreen.masteryBarColor(style,
                Optional.of(true), 1f), "mastered reads gold");
        assertEquals(style.accent(), AbilityMenuScreen.masteryBarColor(style,
                Optional.of(true), 0.4f), "learned reads cyan");
        assertEquals(style.muted(), AbilityMenuScreen.masteryBarColor(style,
                Optional.of(false), 0.4f), "known reads grey");
        assertEquals(style.muted(), AbilityMenuScreen.masteryBarColor(style,
                Optional.empty(), 0.4f), "unknown never guesses a state");
    }

    @Test
    void masteryBarSitsOnTheBottomLine() {
        int[] bar = AbilityMenuScreen.masteryBarRect(100, 46, 300);
        assertEquals(46, bar[0], "starts at the name's left edge");
        assertEquals(100 + 36 - 18, bar[1], "the row's bottom line");
        assertEquals(144, bar[2], "capped width (150% of the original 96)");
        assertEquals(13, bar[3],
                "an odd height, so the value line has equal margins top and bottom");
        assertEquals(40, AbilityMenuScreen.masteryBarRect(100, 46, 80)[2],
                "a squeezed row keeps the floor");
    }

    @Test
    void coreMasteryBarNeedsAnAnsweredSnapshotWithXp() {
        assertFalse(AbilityMenuScreen.coreMasteryBar(false, 45.0),
                "an unanswered snapshot shows no generic bar");
        assertFalse(AbilityMenuScreen.coreMasteryBar(true, 0.0),
                "declared but unearned keeps the presentation's meter on the row");
        assertTrue(AbilityMenuScreen.coreMasteryBar(true, 0.5),
                "the first grant hands the bottom line to the generic bar");
    }

    @Test
    void bonusPercentReadsTheServerMultiplierAsSignedWholePercent() {
        assertEquals("-5%", AbilityMenuScreen.bonusPercent(0.95),
                "a cost reduction reads negative");
        assertEquals("+5%", AbilityMenuScreen.bonusPercent(1.05),
                "a power bonus reads positive");
        assertEquals("0%", AbilityMenuScreen.bonusPercent(1.0),
                "an untouched axis reads zero, never a fake sign");
        assertEquals("-10%", AbilityMenuScreen.bonusPercent(0.9),
                "whole percents round the server's multiplier for the eye");
    }

    @Test
    void masterySlicePercentReadsTheDeltaAsSignedWholePercent() {
        // the pane's parenthetical is a DELTA from 1.0, not a multiplier
        assertEquals("+2%", AbilityMenuScreen.masterySlicePercent(0.02),
                "a positive slice reads with its sign");
        assertEquals("-3%", AbilityMenuScreen.masterySlicePercent(-0.03),
                "a COST reduction slice reads negative");
        assertEquals("0%", AbilityMenuScreen.masterySlicePercent(0.0),
                "no share contributes nothing, never a fake sign");
        assertEquals("+1%", AbilityMenuScreen.masterySlicePercent(0.006),
                "whole percents round the slice for the eye");
    }

    @Test
    void zeroPercentBonusesOmitTheirLine() {
        // owner's brief: 0% is noise in the description - the line goes
        assertTrue(AbilityMenuScreen.bonusShows(1.05), "a real bonus shows");
        assertTrue(AbilityMenuScreen.bonusShows(0.95), "a real reduction shows");
        assertFalse(AbilityMenuScreen.bonusShows(1.0),
                "an untouched axis omits its line");
        assertFalse(AbilityMenuScreen.bonusShows(1.004),
                "under half a percent rounds away too");
        assertTrue(AbilityMenuScreen.bonusShows(1.006),
                "over half a percent still shows");
    }

    @Test
    void costValueSitsOnTheBottomLineUnderItsLabel() {
        // the value shares the meter's value baseline: bar top + equal margins
        int[] bar = AbilityMenuScreen.masteryBarRect(100, 46, 300);
        assertEquals(bar[1] + 2, AbilityMenuScreen.costValueY(100),
                "the cost value aligns with the meter's inside value");
    }

    @Test
    void theMeterStopsBeforeTheCostValue() {
        // the helper only runs when a cost line exists - without one the
        // caller keeps limitX untouched (the "no squeeze" case)
        assertEquals(174, AbilityMenuScreen.costMeterLimit(200, 20),
                "the meter clears a short value by 6px");
        assertEquals(154, AbilityMenuScreen.costMeterLimit(200, 40),
                "the meter clears a 40px value by 6px");
        assertEquals(48, AbilityMenuScreen.costMeterLimit(154, 100),
                "a wide value squeezes the meter, never overlaps it");
    }

    @Test
    void descriptionTextDistinguishesKeysFromProse() {
        // the pane's contract: a dotted space-less value is a translation
        // key (untranslated answers null - a raw key never reaches the
        // player); everything else is prose, "Wololo!" included
        assertTrue(
                AbilityMenuScreen.keyShaped("spell.irons_spellbooks.wololo"));
        assertTrue(
                AbilityMenuScreen.keyShaped("tensura.skill.fireball.description"));
        assertTrue(
                AbilityMenuScreen.keyShaped("foo:bar.baz"), "namespaced keys count");
        assertFalse(
                AbilityMenuScreen.keyShaped("Wololo!"), "single-token prose renders");
        assertFalse(
                AbilityMenuScreen.keyShaped("fireball"), "a bare word is prose");
        assertFalse(AbilityMenuScreen.keyShaped(
                "Manifest and throw a ball of fire, exploding on impact."));
        assertFalse(
                AbilityMenuScreen.keyShaped("Has Space.key"), "spaces make it prose");
        assertFalse(
                AbilityMenuScreen.keyShaped(".leadingdot"), "a leading dot is no key");
    }

    // ---- informational gate (owner's settable brief) ----

    private static AbilityDefinition definitionWith(ActivationType type) {
        return new AbilityDefinition("tensura.skill.gate", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                type, java.util.List.of(),
                ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of());
    }

    @Test
    void informationalGateSparePassiveAndUnknownRows() {
        // NONE (a passive mastery) and UNKNOWN slot anywhere - the owner's
        // "settable to the hotbars" brief; the gate is never a wall for them
        assertFalse(AbilityMenuScreen.informationalGateApplies(
                definitionWith(ActivationType.NONE), true),
                "a NONE row slots even under an informational tab");
        assertFalse(AbilityMenuScreen.informationalGateApplies(
                definitionWith(ActivationType.UNKNOWN), true),
                "an UNKNOWN row slots even under an informational tab");
    }

    @Test
    void informationalGateHoldsCastingRowsOnInfoTabs() {
        // #154's inert-spells rule stays for every activatable type: on an
        // informational tab the press never slots, cycles or toggles
        assertTrue(AbilityMenuScreen.informationalGateApplies(
                definitionWith(ActivationType.INSTANT), true));
        assertTrue(AbilityMenuScreen.informationalGateApplies(
                definitionWith(ActivationType.CHARGED), true));
        assertTrue(AbilityMenuScreen.informationalGateApplies(
                definitionWith(ActivationType.CHANNELLED), true));
        assertTrue(AbilityMenuScreen.informationalGateApplies(
                definitionWith(ActivationType.TOGGLE), true));
    }

    @Test
    void informationalGateNeverAppliesOnOrdinaryTabs() {
        // the gate is a property of the tab, not the row: an ordinary tab
        // passes every activation type straight through to slotting
        assertFalse(AbilityMenuScreen.informationalGateApplies(
                definitionWith(ActivationType.INSTANT), false));
        assertFalse(AbilityMenuScreen.informationalGateApplies(
                definitionWith(ActivationType.NONE), false));
        assertFalse(AbilityMenuScreen.informationalGateApplies(
                definitionWith(ActivationType.UNKNOWN), false));
    }

    // ---- the owner's drag gate and locked-mastery hide ----

    @Test
    void assignmentGateRefusesLockedAndPassiveRows() {
        // owner's brief: locked abilities and NONE/UNKNOWN never reach a
        // hotbar slot - neither click nor drag; the press is consumed
        assertFalse(AbilityMenuScreen.assignmentGateAllows(
                definitionWith(ActivationType.INSTANT), true),
                "a locked row never reaches a slot");
        assertFalse(AbilityMenuScreen.assignmentGateAllows(
                definitionWith(ActivationType.NONE), false),
                "a passive mastery never reaches a slot");
        assertFalse(AbilityMenuScreen.assignmentGateAllows(
                definitionWith(ActivationType.UNKNOWN), false),
                "undetermined never reaches a slot");
        assertTrue(AbilityMenuScreen.assignmentGateAllows(
                definitionWith(ActivationType.INSTANT), false),
                "an unlocked caster click- and drag-assigns as before");
        assertTrue(AbilityMenuScreen.assignmentGateAllows(
                definitionWith(ActivationType.CHARGED), false),
                "charging rows assign exactly as before");
    }

    @Test
    void lockedPassiveMasteryRowsHideAndNothingElseDoes() {
        // owner's hide brief: only a greyed NONE row (a locked passive
        // mastery) hides; unknown unlock state never does (never-guess)
        ResourceLocation mastery = ResourceLocation.fromNamespaceAndPath(
                "tensura", "hide_fixture_mastery");
        ResourceLocation caster = ResourceLocation.fromNamespaceAndPath(
                "tensura", "hide_fixture_caster");
        AbilityUnlocks.declare(mastery, (player, id) -> Optional.of(Boolean.FALSE));
        AbilityUnlocks.declare(caster, (player, id) -> Optional.of(Boolean.FALSE));
        try {
            assertTrue(AbilityMenuScreen.hiddenLockedMastery(mastery,
                    definitionWith(ActivationType.NONE), null),
                    "a greyed passive mastery hides");
            assertFalse(AbilityMenuScreen.hiddenLockedMastery(caster,
                    definitionWith(ActivationType.INSTANT), null),
                    "a locked caster stays listed (grey only)");
        } finally {
            AbilityUnlocks.remove(mastery);
            AbilityUnlocks.remove(caster);
        }

        AbilityUnlocks.declare(mastery, (player, id) -> Optional.of(Boolean.TRUE));
        try {
            assertFalse(AbilityMenuScreen.hiddenLockedMastery(mastery,
                    definitionWith(ActivationType.NONE), null),
                    "an unlocked mastery lists");
        } finally {
            AbilityUnlocks.remove(mastery);
        }
        assertFalse(AbilityMenuScreen.hiddenLockedMastery(
                ResourceLocation.fromNamespaceAndPath("tensura", "hide_fixture_unknown"),
                definitionWith(ActivationType.NONE), null),
                "unknown unlock state never hides - never guess");
    }

}
