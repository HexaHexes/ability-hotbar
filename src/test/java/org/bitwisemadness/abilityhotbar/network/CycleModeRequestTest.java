package org.bitwisemadness.abilityhotbar.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ModeChangeHandlers;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The shared cycle-request builder (owner directive 2026-11-15: the menu
 * pill commits like the keybind - one construction site for both). The
 * request is bar-scoped because the menu's tab may name a bar other than
 * the active one; everything else mirrors {@code AbilityMode.requestFor}'s
 * client-side knowledge (assignment + registered adapter, server re-checks).
 */
class CycleModeRequestTest {

    private static final ResourceLocation CYCLEABLE =
            ResourceLocation.fromNamespaceAndPath("tensura", "freeze");
    private static final ResourceLocation NO_ADAPTER =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spark");
    private static final ResourceLocation FOREIGN_BAR =
            ResourceLocation.fromNamespaceAndPath("ability_hotbar", "bar_other");

    @AfterEach
    void tearDown() {
        ModeChangeHandlers.remove(CYCLEABLE);
    }

    @Test
    void requestNamesTheBarsOwnSlot() {
        ModeChangeHandlers.register(CYCLEABLE, (request, player) -> ActivationResult.ACTED);
        BarState state = BarState.empty(3, 1)
                .withAssignment(0, CYCLEABLE)
                .withAssignment(1, CYCLEABLE);

        var request = CycleModePayload.request(state, state.activeBarId(), 1);
        assertTrue(request.isPresent(), "an assigned slot with an adapter cycles");
        assertEquals(state.activeBarId(), request.get().barId(),
                "the menu pill names the bar its tab shows");
        assertEquals(1, request.get().slot(), "the request carries the cycled slot");
    }

    @Test
    void requestIsANoopWithoutStateBarSlotOrAdapter() {
        assertFalse(CycleModePayload.request(null,
                ResourceLocation.fromNamespaceAndPath("ability_hotbar", "bar_0"), 0).isPresent(),
                "no state must produce no request");
        assertFalse(CycleModePayload.request(BarState.empty(3, 1), null, 0).isPresent(),
                "a null bar must produce no request");

        BarState state = BarState.empty(3, 1).withAssignment(0, CYCLEABLE);
        assertFalse(CycleModePayload.request(state, state.activeBarId(), -1).isPresent(),
                "a negative slot must produce no request");
        assertFalse(CycleModePayload.request(state, state.activeBarId(), 9).isPresent(),
                "an out-of-range slot must produce no request");
        assertFalse(CycleModePayload.request(state, FOREIGN_BAR, 0).isPresent(),
                "a bar the state does not carry must produce no request");
        assertFalse(CycleModePayload.request(BarState.empty(3, 1),
                BarState.empty(3, 1).activeBarId(), 0).isPresent(),
                "an unassigned slot must not send");

        BarState foreign = BarState.empty(3, 1).withAssignment(0, NO_ADAPTER);
        assertFalse(CycleModePayload.request(foreign, foreign.activeBarId(), 0).isPresent(),
                "an id with no registered adapter must not send (iss/curios slots)");
    }
}
