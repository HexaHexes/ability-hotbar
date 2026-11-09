package org.bitwisemadness.abilityhotbar.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.junit.jupiter.api.Test;

/**
 * The snapshot's ceiling padding (owner's report: both configs said 18
 * but the bar drew nine). A save written under an older, smaller
 * `hotbar.maxSlots` keeps its shorter rows forever, and
 * `effectiveRenderCount` clamps to that length - so the bar could never
 * grow. Padding grants the empty slots the current ceiling allows; the
 * render still clamps to the client preference.
 */
class BarStatePaddingTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("padding_test", "art");

    @Test
    void aShortRowGrowsToTheCeilingWithEmptySlots() {
        // the unloaded-spec default is 18 - the shape a9-slot save must reach
        BarState shortSave = BarState.empty(9, 1).withAssignment(0, ABILITY);

        BarState padded = BarStateSync.paddedToCeiling(shortSave);

        assertEquals(18, padded.assignmentsOf(HotbarRegistry.builtinBarId(0)).length,
                "the row reaches the ceiling");
        assertEquals(ABILITY, padded.assignment(0).abilityId(),
                "the existing assignment survives the grow");
        for (int slot = 9; slot < 18; slot++) {
            assertEquals(false, padded.assignment(slot).isAssigned(),
                    "the granted slots start empty: " + slot);
        }
    }

    @Test
    void paddingIsIdempotent() {
        BarState already = BarState.empty(18, 1).withAssignment(3, ABILITY);

        BarState padded = BarStateSync.paddedToCeiling(already);

        assertSame(already, padded, "a row at the ceiling passes through untouched");
        assertEquals(ABILITY, padded.assignment(3).abilityId(), "and keeps its slots");
    }
}
