package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.Test;

/**
 * Server-authoritative slot selection (REQ-HOT-09/10): valid selections move the
 * cursor, out-of-range requests are dropped fail-closed, and the Ctrl+wheel scroll
 * math wraps within the bar.
 *
 * <p>Multi-bar (REQ-HOT-22): a selection always lands on the <b>active</b> bar
 * and leaves every other bar's own selection untouched; an active id that is
 * not stored fails closed instead of selecting on a stranger's bar.</p>
 */
class BarSelectionTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");

    @Test
    void validSelectUpdatesSelectedSlot() {
        BarState base = BarState.empty(3);
        Optional<BarState> result = BarSelection.select(base, 2);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().selectedSlot());
        // the original is untouched (immutable update)
        assertEquals(0, base.selectedSlot());
    }

    @Test
    void outOfRangeSelectionIsRejected() {
        BarState base = BarState.empty(3);

        assertFalse(BarSelection.select(base, -1).isPresent());
        assertFalse(BarSelection.select(base, 3).isPresent());
        assertFalse(BarSelection.select(base, 999).isPresent());
    }

    @Test
    void selectionPreservesAssignments() {
        BarState assigned = BarState.empty(2).withAssignment(1, FIREBALL);
        BarState moved = BarSelection.select(assigned, 1).orElseThrow();

        assertEquals(1, moved.selectedSlot());
        assertEquals(FIREBALL, moved.assignment(1).abilityId());
        assertTrue(moved.assignment(0).isEmpty());
    }

    @Test
    void selectTargetsTheActiveBarAndLeavesOtherBarsSelectionsAlone() {
        // #28: each bar owns its selected slot - a selection moves the active
        // bar's cursor only, never bar 0's by position.
        BarState state = BarState.empty(3, 3)
                .withSelectedSlot(1)
                .withActiveBar(HotbarRegistry.builtinBarId(1));

        BarState moved = BarSelection.select(state, 2).orElseThrow();

        assertEquals(HotbarRegistry.builtinBarId(1), moved.activeBarId());
        assertEquals(2, moved.selectedSlot(), "the active bar's cursor moves");
        assertEquals(2, moved.bars()[1].selectedSlot(), "the active bar itself moved");
        assertEquals(1, moved.bars()[0].selectedSlot(), "bar 0 keeps its own selection");
    }

    @Test
    void selectFailsClosedWhenTheActiveBarIsNotStored() {
        // A non-assignable bar active with no stored state: every request is
        // rejected rather than selecting on some other bar's slots.
        BarState state = BarState.empty(3)
                .withActiveBar(ResourceLocation.fromNamespaceAndPath("pseudocore", "curios"));

        assertTrue(BarSelection.select(state, 0).isEmpty());
        assertTrue(BarSelection.select(state, 2).isEmpty());
    }

    @Test
    void scrollWrapsWithinTheBar() {
        assertEquals(1, BarSelection.slotAfterScroll(0, 1, 3));
        assertEquals(0, BarSelection.slotAfterScroll(2, 1, 3));
        assertEquals(2, BarSelection.slotAfterScroll(0, -1, 3));
        assertEquals(1, BarSelection.slotAfterScroll(2, -1, 3));
    }

    @Test
    void scrollDirectionMatchesVanillaHotbar() {
        // Scroll up (positive delta) moves selection LEFT (toward slot 1);
        // scroll down moves it RIGHT. This is the vanilla convention - an
        // earlier build inverted it.
        assertEquals(-1, BarSelection.scrollDirection(1.0));
        assertEquals(-1, BarSelection.scrollDirection(0.1));
        assertEquals(1, BarSelection.scrollDirection(-1.0));
        assertEquals(1, BarSelection.scrollDirection(-0.1));
        assertEquals(0, BarSelection.scrollDirection(0.0));
    }

    @Test
    void scrollUpDecrementsAndDownIncrementsThroughVanillaMapping() {
        // End-to-end: scroll up from slot 0 wraps to the last slot (left), scroll
        // down advances one (right) - the vanilla hotbar behaviour.
        int up = BarSelection.slotAfterScroll(0, BarSelection.scrollDirection(1.0), 3);
        int down = BarSelection.slotAfterScroll(0, BarSelection.scrollDirection(-1.0), 3);
        assertEquals(2, up, "scroll up from slot 0 wraps left to the last slot");
        assertEquals(1, down, "scroll down from slot 0 advances right");
    }

    @Test
    void scrollOnSingleSlotBarStaysPut() {
        assertEquals(0, BarSelection.slotAfterScroll(0, 1, 1));
        assertEquals(0, BarSelection.slotAfterScroll(0, -1, 1));
    }
}
