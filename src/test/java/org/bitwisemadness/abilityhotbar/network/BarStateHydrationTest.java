package org.bitwisemadness.abilityhotbar.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityModes;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The snapshot's hydration half (play-test round): a cycleable slot's
 * mode is filled from the provider's live projection so the menu's slot
 * cells and row pill show the ability's current mode from the get-go.
 * A slot whose stored setting is still declared keeps <b>its own</b>
 * value - the projection is the ability's one live answer and never
 * overwrites what the player set on a slot (per-slot modes), while a
 * stale setting the ability no longer declares is re-projected, an
 * absent projection keeps the stored one, an undeclared id never reaches
 * the wire, modeless abilities and unassigned slots are never touched,
 * and every stored bar hydrates - the menu edits a bar that may not be
 * the active one. Hydration is display-only: the input state is never
 * mutated.
 */
class BarStateHydrationTest {

    private static final ResourceLocation CYCLEABLE =
            ResourceLocation.fromNamespaceAndPath("hydration_test", "cycleable");
    private static final ResourceLocation MODELESS =
            ResourceLocation.fromNamespaceAndPath("hydration_test", "modeless");

    @BeforeAll
    static void declareModes() {
        AbilityModes.declare(CYCLEABLE, List.of(
                new AbilityModes.Mode("m1", "hydration_test.cycleable.m1"),
                new AbilityModes.Mode("m2", "hydration_test.cycleable.m2")));
    }

    private static BarState withCycleable(int slot) {
        return BarState.empty(3, 1).withAssignment(slot, CYCLEABLE);
    }

    @Test
    void hydrationFillsTheProjectedModeWithoutPersistingIt() {
        BarState state = withCycleable(0);

        BarState hydrated = BarStateSync.hydrated(state, id -> Optional.of("m2"));

        assertEquals("m2", hydrated.assignment(0).mode(),
                "the projected mode reaches the wire copy");
        assertNull(state.assignment(0).mode(),
                "hydration never mutates the persisted state");
    }

    @Test
    void hydrationKeepsAStoredModeTheAbilityStillDeclares() {
        // owner: each slot sets its own mode - the projection is the
        // ability's ONE live value, so writing it over a declared setting
        // would collapse every slot holding that ability onto one mode
        BarState state = withCycleable(0).withSlotMode(0, "m1");

        BarState hydrated = BarStateSync.hydrated(state, id -> Optional.of("m2"));

        assertEquals("m1", hydrated.assignment(0).mode(),
                "the slot's own setting wins over the live projection");
    }

    @Test
    void hydrationReprojectsAStaleStoredMode() {
        // a setting the ability no longer declares is not a setting any
        // more: the projection may fill it (or leave it, if undeclared)
        BarState state = withCycleable(0).withSlotMode(0, "m0");

        BarState hydrated = BarStateSync.hydrated(state, id -> Optional.of("m2"));

        assertEquals("m2", hydrated.assignment(0).mode(),
                "a stale mode is replaced by the declared projection");
    }

    @Test
    void hydrationKeepsEachSlotsOwnModeAcrossSlots() {
        // two slots of the SAME ability, two settings: neither may drift
        // onto the other's value (the play-test regression this rule
        // exists for)
        BarState state = BarState.empty(3, 1)
                .withAssignment(0, CYCLEABLE).withSlotMode(0, "m1")
                .withAssignment(1, CYCLEABLE).withSlotMode(1, "m2");

        BarState hydrated = BarStateSync.hydrated(state, id -> Optional.of("m2"));

        assertEquals("m1", hydrated.assignment(0).mode(),
                "slot 0 keeps its own mode");
        assertEquals("m2", hydrated.assignment(1).mode(),
                "slot 1 keeps its own mode");
    }

    @Test
    void hydrationKeepsTheStoredValueWithoutAProjection() {
        BarState state = withCycleable(0).withSlotMode(0, "m1");

        BarState hydrated = BarStateSync.hydrated(state, id -> Optional.empty());

        assertEquals("m1", hydrated.assignment(0).mode(),
                "an absent projection keeps what the slot stores");
    }

    @Test
    void hydrationNeverWritesAnUndeclaredMode() {
        BarState state = withCycleable(0).withSlotMode(0, "m1");

        BarState hydrated = BarStateSync.hydrated(state, id -> Optional.of("m9"));

        assertEquals("m1", hydrated.assignment(0).mode(),
                "an id outside the declared cycle never reaches the wire");
    }

    @Test
    void hydrationSkipsModelessAbilitiesAndEmptySlots() {
        // a lying projection for every id: only the declared cycleable
        // pairing may ever be written, the rest stays exactly as it was
        BarState state = BarState.empty(3, 1).withAssignment(0, MODELESS);

        BarState hydrated = BarStateSync.hydrated(state, id -> Optional.of("m2"));

        assertNull(hydrated.assignment(0).mode(),
                "a modeless ability never gains a mode");
        assertNull(hydrated.assignment(1).mode(),
                "an unassigned slot is never touched");
    }

    @Test
    void hydrationCoversEveryStoredBarNotJustTheActiveOne() {
        BarState state = BarState.empty(3, 2)
                .withAssignment(HotbarRegistry.builtinBarId(1), 0, CYCLEABLE);

        BarState hydrated = BarStateSync.hydrated(state, id -> Optional.of("m2"));

        SlotAssignment[] secondRow =
                hydrated.assignmentsOf(HotbarRegistry.builtinBarId(1));
        assertEquals("m2", secondRow[0].mode(),
                "the menu's tab bar hydrates even when it is not active");
        assertNull(hydrated.assignment(0).mode(),
                "the active bar keeps its own untouched slot");
    }
}
