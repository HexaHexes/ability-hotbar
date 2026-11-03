package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.Test;

/**
 * Client-side activation resolution (REQ-HOT-11/19/20): an assigned slot resolves
 * to an activation intent, while an empty or out-of-range slot resolves to nothing
 * so the client never sends a request for it.
 *
 * <p>Multi-bar (REQ-HOT-22): the slot is always read from the <b>active</b>
 * bar's view - another bar's assignment never resolves, and an unstored active
 * bar resolves to nothing at all.</p>
 */
class ActivationResolverTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation HEAL =
            ResourceLocation.fromNamespaceAndPath("tensura", "heal");

    @Test
    void emptySlotResolvesToNothing() {
        BarState base = BarState.empty(3);

        assertTrue(ActivationResolver.resolve(base, 1, ActivationSource.DIRECT_SLOT).isEmpty());
    }

    @Test
    void assignedSlotResolvesToItsAbility() {
        BarState base = BarState.empty(3).withAssignment(1, FIREBALL);

        ActivationResolver.Activation a =
                ActivationResolver.resolve(base, 1, ActivationSource.DIRECT_SLOT).orElseThrow();
        assertEquals(FIREBALL, a.abilityId());
        assertEquals(ActivationSource.DIRECT_SLOT, a.source());
        assertEquals(1, a.slot());
    }

    @Test
    void selectedSlotSourceIsPreserved() {
        BarState base = BarState.empty(2).withAssignment(0, FIREBALL);

        ActivationResolver.Activation a =
                ActivationResolver.resolve(base, 0, ActivationSource.SELECTED_SLOT).orElseThrow();
        assertEquals(ActivationSource.SELECTED_SLOT, a.source());
    }

    @Test
    void outOfRangeSlotResolvesToNothing() {
        BarState base = BarState.empty(2).withAssignment(0, FIREBALL);

        assertFalse(ActivationResolver.resolve(base, 2, ActivationSource.DIRECT_SLOT).isPresent());
        assertFalse(ActivationResolver.resolve(base, -1, ActivationSource.DIRECT_SLOT).isPresent());
    }

    @Test
    void resolveTargetsTheActiveBar() {
        // #28: slot lookups read the active bar's view - bar 0's assignment must
        // not resolve while another bar is active, and an active bar this state
        // does not store resolves nothing at all.
        BarState state = BarState.empty(3, 3)
                .withAssignment(0, FIREBALL)
                .withActiveBar(HotbarRegistry.builtinBarId(1));

        assertTrue(ActivationResolver.resolve(state, 0, ActivationSource.DIRECT_SLOT).isEmpty(),
                "bar 0's assignment must not resolve while another bar is active");

        // the active bar's own assignment resolves
        BarState activeAssigned = state.withAssignment(1, HEAL);
        ActivationResolver.Activation a = ActivationResolver
                .resolve(activeAssigned, 1, ActivationSource.DIRECT_SLOT).orElseThrow();
        assertEquals(HEAL, a.abilityId());

        // an unstored (non-assignable) active bar fails closed
        BarState unstored = BarState.empty(3)
                .withActiveBar(ResourceLocation.fromNamespaceAndPath("pseudocore", "curios"));
        assertTrue(ActivationResolver.resolve(unstored, 0, ActivationSource.DIRECT_SLOT).isEmpty());
    }

    @Test
    void nullStateResolvesToNothing() {
        assertFalse(ActivationResolver.resolve(null, 0, ActivationSource.DIRECT_SLOT).isPresent());
    }
}
