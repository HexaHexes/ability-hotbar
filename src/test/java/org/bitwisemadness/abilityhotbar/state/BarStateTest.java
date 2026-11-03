package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
                .withActiveBar(1)
                .withAssignment(2, HEAL)
                .withSelectedSlot(1)
                .withActiveBar(2)
                .withSelectedSlot(1);

        assertEquals(3, state.bars().length);
        assertEquals(2, state.activeBar());

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
        BarState onSecond = two.withActiveBar(1);

        assertTrue(onSecond.assignment(0).isEmpty(), "bar 1 must not see bar 0's slot");
        assertEquals(0, onSecond.selectedSlot());
        // the original is untouched (immutability) and still points at bar 0
        assertEquals(0, two.activeBar());
        assertEquals(FIREBALL, two.assignment(0).abilityId());
    }

    @Test
    void outOfRangeActiveBarIsRejected() {
        BarState one = BarState.empty(3);
        assertThrows(IllegalArgumentException.class, () -> one.withActiveBar(1));
        assertThrows(IllegalArgumentException.class, () -> one.withActiveBar(-1));
        BarState two = BarState.empty(3, 2);
        assertThrows(IllegalArgumentException.class, () -> two.withActiveBar(2));
    }

    @Test
    void emptyWithBarCountAllocatesTheRequestedBars() {
        BarState state = BarState.empty(4, 5);
        assertEquals(5, state.bars().length);
        assertEquals(0, state.activeBar());
        for (Bar bar : state.bars()) {
            assertEquals(4, bar.assignments().length);
            assertEquals(0, bar.selectedSlot());
        }
        assertThrows(IllegalArgumentException.class, () -> BarState.empty(4, 0));
    }

    @Test
    void dfuCodecRoundTripsBarsActiveBarAndSchema() {
        BarState original = BarState.empty(3, 2)
                .withAssignment(1, FIREBALL)
                .withSelectedSlot(2)
                .withActiveBar(1)
                .withAssignment(0, HEAL)
                .withSelectedSlot(1);

        JsonElement json = BarState.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .result().orElseThrow();
        BarState decoded = BarState.CODEC.parse(JsonOps.INSTANCE, json)
                .result().orElseThrow();

        assertEquals(2, decoded.bars().length);
        assertEquals(1, decoded.activeBar());
        assertEquals(BarState.CURRENT_SCHEMA_VERSION, decoded.schemaVersion());
        assertEquals(HEAL, decoded.assignment(0).abilityId());
        assertEquals(1, decoded.selectedSlot());
        assertEquals(FIREBALL, decoded.bars()[0].assignment(1).abilityId());
        assertEquals(2, decoded.bars()[0].selectedSlot());
    }
}
