package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.AbilityBarScopes;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Server-side assignment resolution (REQ-HOT-14/21): valid requests mutate,
 * malformed or out-of-range requests are dropped fail-closed, and a scoped
 * ability (#90) is rejected on every bar but its own.
 */
class BarAssignmentsTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation PRESS =
            ResourceLocation.fromNamespaceAndPath("pseudocore_curios", "press");
    private static final ResourceLocation CURIOS =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");

    @BeforeEach
    @AfterEach
    void isolateScopes() {
        AbilityBarScopes.reset();
        HotbarRegistry.reset();
    }

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

    @Test
    void scopedAbilityIsRejectedOnAnotherBar() {
        // #90 AC3: press belongs to the curios bar - any other active bar
        // refuses it fail-closed, with nothing mutated.
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        AbilityBarScopes.register(PRESS, CURIOS);
        BarState onBuiltIn = BarState.empty(3);

        assertFalse(BarAssignments.assign(onBuiltIn, 0, "pseudocore_curios:press").isPresent());
        assertTrue(onBuiltIn.assignment(0).isEmpty(), "a refusal never mutates the bar");
    }

    @Test
    void playerPayloadNeverWritesEvenTheScopeBarItself() {
        // #90 AC5: with the scope satisfied the payload still cannot write a
        // non-assignable bar - its empty view fails the range check first.
        // Player rejection is unchanged; #89's owner write never comes here.
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        AbilityBarScopes.register(PRESS, CURIOS);
        BarState onCurios = BarState.empty(3).withActiveBar(CURIOS);

        assertFalse(BarAssignments.assign(onCurios, 0, "pseudocore_curios:press").isPresent());
        assertFalse(BarAssignments.outOfScope(onCurios, "pseudocore_curios:press"),
                "the scope is satisfied here - this is the range gate, not a scope refusal");
    }

    @Test
    void outOfScopeDetectsOnlyScopeRejections() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        AbilityBarScopes.register(PRESS, CURIOS);
        BarState onBuiltIn = BarState.empty(3);

        assertTrue(BarAssignments.outOfScope(onBuiltIn, "pseudocore_curios:press"),
                "the payload resyncs only for scope refusals");
        assertFalse(BarAssignments.outOfScope(onBuiltIn, "tensura:fireball"),
                "an unscoped ability keeps the silent-drop path");
        assertFalse(BarAssignments.outOfScope(onBuiltIn, "not a valid id"),
                "a malformed id keeps the silent-drop path");
        assertFalse(BarAssignments.outOfScope(onBuiltIn, ""), "blank clears, never a scope");
        assertFalse(BarAssignments.outOfScope(onBuiltIn, null));
        assertFalse(BarAssignments.outOfScope(null, "pseudocore_curios:press"));

        BarState onCurios = BarState.empty(3).withActiveBar(CURIOS);
        assertFalse(BarAssignments.outOfScope(onCurios, "pseudocore_curios:press"),
                "its own bar is never out of scope");
    }

    @Test
    void storedContentOwnedBarIsNeverPlayerWritable() {
        // #89: once the owner has seeded a content-owned bar, the bar IS
        // stored - without the assignable gate the player payload would slip
        // through the range check that used to reject it implicitly.
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        BarState seeded = SeededAssignments.seed(BarState.empty(3), CURIOS, 0, "pseudocore_curios:press");
        BarState active = seeded.withActiveBar(CURIOS);

        assertFalse(BarAssignments.assign(active, 1, "tensura:fireball").isPresent(),
                "writing to a content-owned bar is rejected");
        assertFalse(BarAssignments.assign(active, 0, "").isPresent(),
                "clearing its cells is rejected too");
        assertFalse(BarAssignments.assign(active, 0, "pseudocore_curios:press").isPresent(),
                "even re-writing the same seeded id belongs to the owner, not the player");
    }
}
