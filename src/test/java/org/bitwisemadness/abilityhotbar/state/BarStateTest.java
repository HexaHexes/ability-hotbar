package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Bar state model + NBT serializer contract (REQ-HOT-14/21, ADR-004).
 *
 * <p>The serializer takes an {@code IAttachmentHolder} and a
 * {@code HolderLookup.Provider}; its implementation does not use either, so
 * the tests pass {@code null} for both (they are only consulted by NeoForge's
 * attachment plumbing, which is exercised by the GameTest).</p>
 */
class BarStateTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation HEAL =
            ResourceLocation.fromNamespaceAndPath("tensura", "heal");

    /**
     * The serializer's default form reads the live static registry on load
     * (ADR-012), so start from - and leave behind - an empty one: a leaked
     * registration would append phantom bars to a round-trip's read.
     */
    @BeforeEach
    @AfterEach
    void isolateRegistry() {
        HotbarRegistry.reset();
    }

    @Test
    void emptyStateHasOnlyEmptySlots() {
        BarState state = BarState.empty(5);
        assertEquals(5, state.assignments().length);
        assertEquals(0, state.selectedSlot());
        assertEquals(BarState.CURRENT_SCHEMA_VERSION, state.schemaVersion());
        for (int i = 0; i < 5; i++) {
            assertTrue(state.assignment(i).isEmpty(), "slot " + i + " should be empty");
        }
    }

    @Test
    void assignmentAndSelectionAreImmutableUpdates() {
        BarState base = BarState.empty(4);
        BarState updated = base.withAssignment(2, FIREBALL).withSelectedSlot(3);

        // original untouched
        assertTrue(base.assignment(2).isEmpty());
        assertEquals(0, base.selectedSlot());
        // updated carries the change
        assertEquals(FIREBALL, updated.assignment(2).abilityId());
        assertEquals(3, updated.selectedSlot());
    }

    @Test
    void outOfBoundsAccessAndSelectedSlotAreRejected() {
        BarState state = BarState.empty(3);
        assertThrows(IllegalArgumentException.class, () -> state.withSelectedSlot(3));
        assertThrows(IllegalArgumentException.class, () -> state.withSelectedSlot(-1));
        // out-of-range assignment is a no-op, not an exception
        assertTrue(state.withAssignment(9, FIREBALL).assignment(9).isEmpty());
    }

    @Test
    void serializerRoundTripsAssignmentsAndSelection() {
        BarState original = BarState.empty(3)
                .withAssignment(0, FIREBALL)
                .withAssignment(2, HEAL)
                .withSelectedSlot(1);

        BarStateSerializer serializer = new BarStateSerializer();
        CompoundTag tag = serializer.write(original, null);
        BarState restored = serializer.read(null, tag, null);

        assertEquals(original.assignments().length, restored.assignments().length);
        assertEquals(FIREBALL, restored.assignment(0).abilityId());
        assertTrue(restored.assignment(1).isEmpty());
        assertEquals(HEAL, restored.assignment(2).abilityId());
        assertEquals(1, restored.selectedSlot());
        assertEquals(BarState.CURRENT_SCHEMA_VERSION, restored.schemaVersion());
    }

    @Test
    void serializerPreservesSlotCountAcrossConfigShrink() {
        // A state saved with 5 slots must survive even if the effective config
        // is later reduced - the persisted count wins so data is never truncated.
        BarState original = BarState.empty(5).withAssignment(4, HEAL);
        BarStateSerializer serializer = new BarStateSerializer();
        CompoundTag tag = serializer.write(original, null);

        BarState restored = serializer.read(null, tag, null);
        assertEquals(5, restored.assignments().length);
        assertEquals(HEAL, restored.assignment(4).abilityId());
    }

    @Test
    void serializerPreservesRawIdForInertSlot() {
        // Simulate a provider being absent: the id is valid but nothing resolves
        // it. The raw id must be kept, not replaced or dropped (REQ-HOT-21).
        BarState original = BarState.empty(2).withAssignment(0, FIREBALL);
        BarStateSerializer serializer = new BarStateSerializer();
        CompoundTag tag = serializer.write(original, null);

        BarState restored = serializer.read(null, tag, null);
        assertEquals(FIREBALL, restored.assignment(0).abilityId());
        assertTrue(restored.assignment(0).isAssigned());
        assertFalse(restored.assignment(1).isAssigned());
    }

    @Test
    void newerSchemaVersionLoadsEmptyInsteadOfCrashing() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", BarState.CURRENT_SCHEMA_VERSION + 1);
        BarState restored = new BarStateSerializer().read(null, tag, null);
        // falls back to a valid empty state
        assertTrue(restored.assignments().length >= 1);
        assertEquals(0, restored.selectedSlot());
    }

    @Test
    void multipleBarsKeepIndependentAssignmentsAndSelections() {
        // #28 AC 1 fixture: bar N's slots and selection never leak into bar M.
        BarState state = BarState.empty(3, 3)
                .withAssignment(0, FIREBALL)
                .withSelectedSlot(2)
                .withActiveBar(HotbarRegistry.builtinBarId(1))
                .withAssignment(2, HEAL)
                .withSelectedSlot(1)
                .withActiveBar(HotbarRegistry.builtinBarId(2))
                .withSelectedSlot(1);

        assertEquals(3, state.bars().length);
        assertEquals(HotbarRegistry.builtinBarId(2), state.activeBarId());

        // Single-bar accessors target the active bar (2): empty and selected 1.
        assertEquals(1, state.selectedSlot());
        assertTrue(state.assignment(0).isEmpty());

        Bar[] bars = state.bars();
        assertEquals(FIREBALL, bars[0].assignment(0).abilityId());
        assertEquals(2, bars[0].selectedSlot());
        assertTrue(bars[0].assignment(1).isEmpty());
        assertEquals(HEAL, bars[1].assignment(2).abilityId());
        assertEquals(1, bars[1].selectedSlot());
        assertTrue(bars[1].assignment(0).isEmpty());
        assertEquals(1, bars[2].selectedSlot());
        assertTrue(bars[2].assignment(0).isEmpty());
    }

    @Test
    void activeBarSwitchesWhichBarTheSingleBarAccessorsTarget() {
        BarState two = BarState.empty(2, 2).withAssignment(0, FIREBALL);
        BarState onSecond = two.withActiveBar(HotbarRegistry.builtinBarId(1));

        assertTrue(onSecond.assignment(0).isEmpty(), "bar 1 must not see bar 0's slot");
        assertEquals(0, onSecond.selectedSlot());
        // the original is untouched (immutability) and still points at bar 0
        assertEquals(HotbarRegistry.builtinBarId(0), two.activeBarId());
        assertEquals(FIREBALL, two.assignment(0).abilityId());
    }

    @Test
    void nullActiveBarIdIsRejected() {
        // Range/registry validation of a switch target lives at the server
        // payload boundary (ADR-012); the model only rejects a missing id.
        assertThrows(IllegalArgumentException.class, () -> BarState.empty(3).withActiveBar(null));
    }

    @Test
    void unstoredActiveBarIdFailsViewsClosed() {
        // A registered non-assignable bar (or a diverged id) is a legitimate
        // active id this state does not store: every view must fail closed
        // rather than read another bar's data (ADR-012).
        ResourceLocation ghost = ResourceLocation.fromNamespaceAndPath("other", "ghost_bar");
        BarState state = BarState.empty(3).withActiveBar(ghost);

        assertEquals(0, state.assignments().length);
        assertEquals(0, state.selectedSlot());
        assertTrue(state.assignment(0).isEmpty());
        // mutations are no-ops / rejected, never redirected to a stored bar
        assertEquals(state, state.withAssignment(0, FIREBALL));
        assertThrows(IllegalArgumentException.class, () -> state.withSelectedSlot(0));
    }

    @Test
    void emptyWithBarCountAllocatesTheRequestedBars() {
        BarState state = BarState.empty(4, 5);
        assertEquals(5, state.bars().length);
        assertEquals(HotbarRegistry.builtinBarId(0), state.activeBarId());
        for (int i = 0; i < 5; i++) {
            assertEquals(HotbarRegistry.builtinBarId(i), state.bars()[i].id());
            assertEquals(4, state.bars()[i].assignments().length);
            assertEquals(0, state.bars()[i].selectedSlot());
        }
        assertThrows(IllegalArgumentException.class, () -> BarState.empty(4, 0));
    }

    @Test
    void dfuCodecRoundTripsBarsActiveBarAndSchema() {
        BarState original = BarState.empty(3, 2)
                .withAssignment(1, FIREBALL)
                .withSelectedSlot(2)
                .withActiveBar(HotbarRegistry.builtinBarId(1))
                .withAssignment(0, HEAL)
                .withSelectedSlot(1);

        JsonElement json = BarState.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .result().orElseThrow();
        BarState decoded = BarState.CODEC.parse(JsonOps.INSTANCE, json)
                .result().orElseThrow();

        assertEquals(2, decoded.bars().length);
        assertEquals(HotbarRegistry.builtinBarId(1), decoded.activeBarId());
        assertEquals(BarState.CURRENT_SCHEMA_VERSION, decoded.schemaVersion());
        assertEquals(HEAL, decoded.assignment(0).abilityId());
        assertEquals(1, decoded.selectedSlot());
        assertEquals(FIREBALL, decoded.bars()[0].assignment(1).abilityId());
        assertEquals(2, decoded.bars()[0].selectedSlot());
        assertEquals(HotbarRegistry.builtinBarId(0), decoded.bars()[0].id());
    }

    // --- reconciledWith (ADR-012) ------------------------------------------

    private static HotbarRegistration registration(String id, boolean assignable) {
        return new HotbarRegistration(ResourceLocation.parse(id), "test.bar", assignable);
    }

    @Test
    void reconcileAppendsMissingAssignableBarsAfterBuiltIns() {
        BarState fresh = BarState.empty(3, 2);
        BarState reconciled = fresh.reconciledWith(List.of(
                registration("otherpack:extra", true),
                registration("otherpack:readonly", false)));

        // assignable appended with its registration id, sized like the state's own bars
        assertEquals(3, reconciled.bars().length);
        assertEquals(ResourceLocation.fromNamespaceAndPath("otherpack", "extra"),
                reconciled.bars()[2].id());
        assertEquals(3, reconciled.bars()[2].assignments().length);
        // non-assignable registrations get no attachment state at all
        assertEquals(HotbarRegistry.builtinBarId(0), reconciled.activeBarId());
        // idempotent: a second reconcile changes nothing
        assertSame(reconciled, reconciled.reconciledWith(List.of(
                registration("otherpack:extra", true),
                registration("otherpack:readonly", false))));
    }

    @Test
    void reconcileKeepsStoredBarWithoutRegistration() {
        // Inert by identity: a stored bar whose registration is gone is kept,
        // never remapped onto a later registrant (ADR-012).
        ResourceLocation orphanId = ResourceLocation.fromNamespaceAndPath("gone", "bar");
        BarState stored = new BarState(
                new Bar[]{
                        Bar.empty(HotbarRegistry.builtinBarId(0), 3),
                        Bar.empty(orphanId, 3).withAssignment(1, FIREBALL)},
                HotbarRegistry.builtinBarId(0), BarState.CURRENT_SCHEMA_VERSION);

        BarState reconciled = stored.reconciledWith(List.of(
                registration("newcomer:bar", true)));

        assertEquals(3, reconciled.bars().length);
        // orphan intact, in place, with its data
        assertEquals(orphanId, reconciled.bars()[1].id());
        assertEquals(FIREBALL, reconciled.bars()[1].assignment(1).abilityId());
        // newcomer got its own fresh bar - the orphan's state was not handed over
        assertEquals(ResourceLocation.fromNamespaceAndPath("newcomer", "bar"),
                reconciled.bars()[2].id());
        assertTrue(reconciled.bars()[2].assignment(1).isEmpty());
    }

    @Test
    void reconcileReanchorsUnknownActiveIdToFirstStoredBar() {
        BarState state = BarState.empty(3)
                .withActiveBar(ResourceLocation.fromNamespaceAndPath("missing", "bar"));

        try (LogCapture logs = LogCapture.forLogger(BarState.class)) {
            BarState reconciled = state.reconciledWith(List.of());

            assertEquals(HotbarRegistry.builtinBarId(0), reconciled.activeBarId());
            assertTrue(logs.messages().stream()
                            .anyMatch(m -> m.contains("neither stored nor registered")),
                    "re-anchor must be logged, got: " + logs.messages());
        }
    }

    @Test
    void reconcileKeepsRegisteredNonAssignableActiveId() {
        // The active id of a live non-assignable registration is legitimate
        // even though this state stores no bar for it - kept, views fail closed.
        ResourceLocation curios = ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");
        BarState state = BarState.empty(3).withActiveBar(curios);

        BarState reconciled = state.reconciledWith(List.of(registration("pseudocore:curios", false)));

        assertEquals(curios, reconciled.activeBarId());
        assertEquals(1, reconciled.bars().length, "non-assignable bars get no state");
        assertEquals(0, reconciled.assignments().length);
    }
}
