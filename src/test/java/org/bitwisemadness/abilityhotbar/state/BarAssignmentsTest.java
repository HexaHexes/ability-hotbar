package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Server-side assignment resolution (REQ-HOT-14/21): valid requests mutate,
 * malformed or out-of-range requests are dropped fail-closed.
 */
class BarAssignmentsTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");

    @Test
    void validRequestAssignsTheRawId() {
        BarState base = BarState.empty(3);
        Optional<BarState> result = BarAssignments.assign(base, 1, "tensura:fireball");

        assertTrue(result.isPresent());
        assertEquals(FIREBALL, result.get().assignment(1).abilityId());
        // the original is untouched (immutable update)
        assertTrue(base.assignment(1).isEmpty());
    }

    @Test
    void blankOrNullClearsTheSlot() {
        BarState assigned = BarState.empty(3).withAssignment(0, FIREBALL);

        assertTrue(BarAssignments.assign(assigned, 0, "").orElseThrow().assignment(0).isEmpty());
        assertTrue(BarAssignments.assign(assigned, 0, "   ").orElseThrow().assignment(0).isEmpty());
        assertTrue(BarAssignments.assign(assigned, 0, null).orElseThrow().assignment(0).isEmpty());
    }

    @Test
    void outOfRangeSlotIsRejected() {
        BarState base = BarState.empty(3);

        assertFalse(BarAssignments.assign(base, -1, "tensura:fireball").isPresent());
        assertFalse(BarAssignments.assign(base, 3, "tensura:fireball").isPresent());
        assertFalse(BarAssignments.assign(base, 999, "tensura:fireball").isPresent());
    }

    @Test
    void malformedIdIsRejectedAndChangesNothing() {
        BarState base = BarState.empty(3);

        assertFalse(BarAssignments.assign(base, 0, "not a valid id").isPresent());
        assertFalse(BarAssignments.assign(base, 0, ":::").isPresent());
    }

    @Test
    void assignedIdSurvivesEvenWhenNoProviderResolvesIt() {
        // REQ-HOT-21: the raw id is stored verbatim; inertness is a render concern.
        BarState result = BarAssignments.assign(BarState.empty(2), 0, "unknownpack:ghost").orElseThrow();
        assertEquals(ResourceLocation.fromNamespaceAndPath("unknownpack", "ghost"),
                result.assignment(0).abilityId());
        assertTrue(result.assignment(0).isAssigned());
    }
}
