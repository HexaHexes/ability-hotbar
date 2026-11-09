package org.bitwisemadness.abilityhotbar.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.junit.jupiter.api.Test;

/**
 * The cycle's freeze (owner's report: cycling one instance changed every
 * instance of that ability). A slot never given a mode displays the live
 * projection, so the cycle would move all of them at once - the handler
 * freezes every still-unset slot of that ability at the PRE-cycle value
 * first, and only the cycled slot moves. Fixtures need no ServerPlayer:
 * the freeze itself is pure.
 */
class CycleModeFreezeTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("freeze_test", "art");
    private static final ResourceLocation OTHER =
            ResourceLocation.fromNamespaceAndPath("freeze_test", "other");

    @Test
    void everyUnsetSlotOfTheAbilityKeepsWhatItWasShowing() {
        BarState state = BarState.empty(3, 2)
                .withAssignment(0, ABILITY)
                .withAssignment(HotbarRegistry.builtinBarId(1), 1, ABILITY)
                .withAssignment(HotbarRegistry.builtinBarId(1), 2, OTHER);

        BarState frozen = CycleModePayload.frozenUnsetModes(state, ABILITY, "m1");

        assertEquals("m1", frozen.assignment(0).mode(),
                "the unset instance on the active bar keeps what it showed");
        assertEquals("m1",
                frozen.assignmentsOf(HotbarRegistry.builtinBarId(1))[1].mode(),
                "an unset instance on ANOTHER bar keeps it too");
        assertNull(frozen.assignmentsOf(HotbarRegistry.builtinBarId(1))[2].mode(),
                "another ability is never touched");
    }

    @Test
    void aSlotThatWasGivenAModeKeepsItsOwn() {
        BarState state = BarState.empty(3, 1)
                .withAssignment(0, ABILITY).withSlotMode(0, "mine")
                .withAssignment(1, ABILITY);

        BarState frozen = CycleModePayload.frozenUnsetModes(state, ABILITY, "m1");

        assertEquals("mine", frozen.assignment(0).mode(),
                "a set mode is the slot's own - the freeze never overwrites it");
        assertEquals("m1", frozen.assignment(1).mode(),
                "only the unset follower freezes");
    }

    @Test
    void noProjectionChangesNothing() {
        BarState state = BarState.empty(3, 1).withAssignment(0, ABILITY);

        BarState frozen = CycleModePayload.frozenUnsetModes(state, ABILITY, null);

        assertNull(frozen.assignment(0).mode(),
                "without a pre-cycle value there is nothing to freeze at");
        assertEquals(state, frozen, "the state passes through untouched");
    }
}
