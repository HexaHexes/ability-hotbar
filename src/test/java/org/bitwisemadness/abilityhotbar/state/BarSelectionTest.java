package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Server-authoritative slot selection (REQ-HOT-09/10): valid selections move the
 * cursor, out-of-range requests are dropped fail-closed, and the Ctrl+wheel scroll
 * math wraps within the bar.
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
    void scrollWrapsWithinTheBar() {
        assertEquals(1, BarSelection.slotAfterScroll(0, 1, 3));
        assertEquals(0, BarSelection.slotAfterScroll(2, 1, 3));
        assertEquals(2, BarSelection.slotAfterScroll(0, -1, 3));
        assertEquals(1, BarSelection.slotAfterScroll(2, -1, 3));
    }

    @Test
    void scrollOnSingleSlotBarStaysPut() {
        assertEquals(0, BarSelection.slotAfterScroll(0, 1, 1));
        assertEquals(0, BarSelection.slotAfterScroll(0, -1, 1));
    }
}
