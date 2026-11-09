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
    private static final ResourceLocation EXTRA =
            ResourceLocation.fromNamespaceAndPath("otherpack", "extra");

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
        // non-assignable bar - the assignable gate refuses it outright.
        // Player rejection is unchanged; #89's owner write never comes here.
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        AbilityBarScopes.register(PRESS, CURIOS);
        BarState onCurios = BarState.empty(3).withActiveBar(CURIOS);

        assertFalse(BarAssignments.assign(onCurios, 0, "pseudocore_curios:press").isPresent());
        assertFalse(BarAssignments.assign(onCurios, 0, "").isPresent(),
                "clearing its cells is rejected too");
    }

    @Test
    void namedTargetBarReceivesTheAssignmentEvenWhenNotActive() {
        // #27 AC9: the menu names its target - any assignable bar, not only
        // the active one (ADR-012 id targeting).
        BarState base = BarState.empty(3, 3);

        BarState result = BarAssignments.assign(base, HotbarRegistry.builtinBarId(1), 2,
                "tensura:fireball").orElseThrow();

        assertEquals(base.activeBarId(), result.activeBarId(), "assigning never switches bars");
        assertEquals(FIREBALL, result.bars()[1].assignment(2).abilityId(), "the named bar got it");
        assertTrue(result.bars()[0].assignment(2).isEmpty(), "the active bar is untouched");
        assertEquals(0, result.bars()[1].selectedSlot(), "selections do not move either");
    }

    @Test
    void nonexistentTargetBarIsRejected() {
        // A registered assignable bar this state does not store (reconciliation
        // would have appended it on load; a direct-built state may lack it) and
        // any unknown id are both rejected before a write is attempted.
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);
        BarState base = BarState.empty(3);

        assertFalse(BarAssignments.assign(base, EXTRA, 0, "tensura:fireball").isPresent());
        assertFalse(BarAssignments.assign(base, HotbarRegistry.builtinBarId(9), 0, "tensura:fireball").isPresent());
        assertFalse(BarAssignments.assign(base,
                ResourceLocation.fromNamespaceAndPath("nobody", "bar"), 0, "tensura:fireball").isPresent());
        assertTrue(base.bars()[0].assignment(0).isEmpty(), "nothing was written");
    }

    @Test
    void nonAssignableTargetBarIsRejectedEvenWhenStored() {
        // The content-owned bar may well be stored (owner-seeded, #89) - the
        // assignable gate still refuses a player write aimed straight at it.
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        BarState seeded = SeededAssignments.seed(BarState.empty(3), CURIOS, 0, "pseudocore_curios:press");

        assertFalse(BarAssignments.assign(seeded, CURIOS, 1, "tensura:fireball").isPresent());
        assertFalse(BarAssignments.assign(seeded, CURIOS, 0, "").isPresent());
        assertEquals(PRESS, SeededAssignments.storedBar(seeded, CURIOS).orElseThrow()
                .assignment(0).abilityId(), "the seed survives the rejected write");
    }

    @Test
    void slotRangeIsValidatedAgainstTheNamedBar() {
        // Each bar owns its own slot count: bar 1 has three slots here, so
        // slot 3 fails even though another bar might be longer.
        BarState base = BarState.empty(3, 2);

        assertFalse(BarAssignments.assign(base, HotbarRegistry.builtinBarId(1), 3, "tensura:fireball").isPresent());
        assertFalse(BarAssignments.assign(base, HotbarRegistry.builtinBarId(1), -1, "tensura:fireball").isPresent());
        assertTrue(BarAssignments.assign(base, HotbarRegistry.builtinBarId(1), 2, "tensura:fireball").isPresent(),
                "an in-range slot on the named bar is accepted");
    }

    @Test
    void storedContentOwnedBarIsNeverPlayerWritable() {
        // #89/#27: once the owner has seeded a content-owned bar the bar IS
        // stored - the assignable gate refuses the player payload outright,
        // where the range check used to reject it implicitly.
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

    @Test
    void swapExchangesWholeAssignmentsWithTheirModes() {
        // A reorder moves whole cells: the stored mode travels with its
        // ability, so dragging never resets a mode the player chose.
        BarState base = BarState.empty(3);
        SlotAssignment[] row = base.bars()[0].assignments().clone();
        row[0] = new SlotAssignment(FIREBALL, "burst");
        row[2] = new SlotAssignment(PRESS, "held");
        BarState filled = base.withRow(base.activeBarId(), row);

        BarState swapped = BarAssignments.swap(filled, filled.activeBarId(), 0, 2).orElseThrow();

        assertEquals(PRESS, swapped.assignment(0).abilityId(), "the target moved to the source");
        assertEquals("held", swapped.assignment(0).mode(), "and brought its mode");
        assertEquals(FIREBALL, swapped.assignment(2).abilityId(), "the source moved to the target");
        assertEquals("burst", swapped.assignment(2).mode(), "and brought its mode");
        assertTrue(swapped.assignment(1).isEmpty(), "the middle cell is untouched");
        assertEquals(FIREBALL, filled.assignment(0).abilityId(), "the original is untouched");
    }

    @Test
    void swapWithAnEmptyCellMovesEmptinessNotAbilities() {
        BarState base = BarState.empty(3).withAssignment(1, FIREBALL);

        BarState swapped = BarAssignments.swap(base, base.activeBarId(), 0, 1).orElseThrow();

        assertEquals(FIREBALL, swapped.assignment(0).abilityId(),
                "the filled cell took the dragged slot's place");
        assertTrue(swapped.assignment(1).isEmpty(), "the empty cell travelled to the target");
    }

    @Test
    void swapIsValidatedFailClosed() {
        BarState base = BarState.empty(3);

        assertFalse(BarAssignments.swap(base, base.activeBarId(), 1, 1).isPresent(),
                "a self-swap is refused");
        assertFalse(BarAssignments.swap(base, base.activeBarId(), -1, 1).isPresent(),
                "an out-of-range source is refused");
        assertFalse(BarAssignments.swap(base, base.activeBarId(), 0, 3).isPresent(),
                "an out-of-range target is refused");
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        assertFalse(BarAssignments.swap(base, CURIOS, 0, 1).isPresent(),
                "a non-assignable bar is refused");
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);
        assertFalse(BarAssignments.swap(base, EXTRA, 0, 1).isPresent(),
                "an unstored bar is refused");
        assertTrue(base.assignment(0).isEmpty() && base.assignment(1).isEmpty(),
                "nothing was written");
    }
}
