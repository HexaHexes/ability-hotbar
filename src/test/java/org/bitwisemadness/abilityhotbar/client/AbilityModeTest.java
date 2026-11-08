package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ModeChangeHandlers;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The ability-mode keybind's halves (REQ-HOT-16, tensura-iss-integration#51):
 * the client sends one bar-scoped request when - and only when - the
 * selected slot holds an ability with a registered mode-change adapter;
 * the server-side echo's state transition keeps every other slot, the
 * selection and the pairings untouched (one press, one step, one slot).
 */
class AbilityModeTest {

    private static final ResourceLocation CYCLEABLE =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation NO_ADAPTER =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spark");

    @AfterEach
    void tearDown() {
        ModeChangeHandlers.remove(CYCLEABLE);
    }

    private static BarState twoAssignedSlots() {
        return BarState.empty(3, 1)
                .withAssignment(0, CYCLEABLE)
                .withAssignment(1, CYCLEABLE);
    }

    @Test
    void requestForCarriesTheActiveBarAndSelectedSlot() {
        ModeChangeHandlers.register(CYCLEABLE, (request, player) -> ActivationResult.ACTED);

        var first = AbilityMode.requestFor(twoAssignedSlots());
        assertTrue(first.isPresent(), "an assigned slot with an adapter cycles");
        assertEquals(twoAssignedSlots().activeBarId(), first.get().barId(),
                "the keybind names the active bar (the menu pill names its tab's)");
        assertEquals(0, first.get().slot(), "the request carries the selected slot");

        var second = AbilityMode.requestFor(twoAssignedSlots().withSelectedSlot(1));
        assertTrue(second.isPresent());
        assertEquals(1, second.get().slot(), "only the selected slot is targeted (AC)");
    }

    @Test
    void requestForIsANoopWithoutStateSlotOrAdapter() {
        assertTrue(AbilityMode.requestFor(null).isEmpty(),
                "null state must produce no request");
        assertFalse(AbilityMode.requestFor(BarState.empty(3, 1)).isPresent(),
                "an unassigned selected slot must not send");

        BarState foreign = BarState.empty(3, 1).withAssignment(0, NO_ADAPTER);
        assertFalse(AbilityMode.requestFor(foreign).isPresent(),
                "an id with no registered adapter must not send (iss/curios slots)");
    }

    @Test
    void withSlotModeTouchesOnlyThatPairing() {
        BarState original = twoAssignedSlots().withSelectedSlot(2);
        BarState next = original.withSlotMode(1, "fire");

        assertEquals("fire", next.assignment(1).mode(), "the cycled slot's echo lands");
        assertNull(next.assignment(0).mode(), "its sibling keeps its mode");
        assertEquals(CYCLEABLE, next.assignment(0).abilityId());
        assertEquals(CYCLEABLE, next.assignment(1).abilityId(),
                "a mode never replaces the pairing");
        assertEquals(2, next.selectedSlot(), "the selection never moves");
        assertEquals(original.presets().length, next.presets().length,
                "presets are untouched");
    }

    @Test
    void withSlotModeFailsClosed() {
        BarState base = BarState.empty(3, 1).withAssignment(0, CYCLEABLE);

        BarState outOfRange = base.withSlotMode(9, "fire");
        assertNull(outOfRange.assignment(0).mode(), "out of range is a no-op");

        BarState unassigned = base.withSlotMode(1, "fire");
        assertNull(unassigned.assignment(1).mode(),
                "a mode never conjures an assignment");
    }
}
