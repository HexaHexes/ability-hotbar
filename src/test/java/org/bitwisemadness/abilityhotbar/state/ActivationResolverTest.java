package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.junit.jupiter.api.Test;

/**
 * Client-side activation resolution (REQ-HOT-11/19/20): an assigned slot resolves
 * to an activation intent, while an empty or out-of-range slot resolves to nothing
 * so the client never sends a request for it.
 */
class ActivationResolverTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");

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
    void nullStateResolvesToNothing() {
        assertFalse(ActivationResolver.resolve(null, 0, ActivationSource.DIRECT_SLOT).isPresent());
    }
}
