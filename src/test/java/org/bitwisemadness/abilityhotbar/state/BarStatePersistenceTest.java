package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Persistence fixtures for the versioned NBT upgrade ladder (REQ-HOT-14/15/21,
 * ADR-004), closing the legacy-migration half of hotbar#24.
 *
 * <p>{@code BarStateTest} already covers the current-version round-trip, inert-id
 * preservation and the newer-schema safety net. These fixtures cover the paths a
 * real pack update takes: a compound written before {@code schema_version} /
 * {@code slot_count} were tracked must upgrade forward with its values retained,
 * and a corrupt (unparseable) slot id must never crash the load.</p>
 *
 * <p>The serializer ignores its {@code IAttachmentHolder} and
 * {@code HolderLookup.Provider}, so the tests pass {@code null} for both.</p>
 */
class BarStatePersistenceTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation HEAL =
            ResourceLocation.fromNamespaceAndPath("tensura", "heal");

    private final BarStateSerializer serializer = new BarStateSerializer();

    @Test
    void legacyCompoundWithoutVersionOrCountUpgradesForward() {
        // Pre-v1 data: no schema_version, no slot_count - only the slot keys.
        CompoundTag legacy = new CompoundTag();
        legacy.putString("slot_0", FIREBALL.toString());
        legacy.putString("slot_2", HEAL.toString());

        BarState upgraded = serializer.read(null, legacy, null);

        assertEquals(BarState.CURRENT_SCHEMA_VERSION, upgraded.schemaVersion());
        assertEquals(3, upgraded.assignments().length, "slot count inferred from the highest key");
        assertEquals(FIREBALL, upgraded.assignment(0).abilityId());
        assertTrue(upgraded.assignment(1).isEmpty());
        assertEquals(HEAL, upgraded.assignment(2).abilityId());
        assertEquals(0, upgraded.selectedSlot());
    }

    @Test
    void legacySlotCountInferredFromHighestKeyRetainsSparseSlots() {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("slot_0", FIREBALL.toString());
        legacy.putString("slot_4", HEAL.toString());

        BarState upgraded = serializer.read(null, legacy, null);

        assertEquals(5, upgraded.assignments().length);
        assertEquals(FIREBALL, upgraded.assignment(0).abilityId());
        assertTrue(upgraded.assignment(3).isEmpty());
        assertEquals(HEAL, upgraded.assignment(4).abilityId());
    }

    @Test
    void corruptSlotIdLoadsEmptyAndDoesNotCrash() {
        // An unparseable id cannot be preserved as a ResourceLocation, so the slot
        // is emptied and logged rather than failing the whole load (REQ-HOT-15).
        CompoundTag tag = new CompoundTag();
        tag.putInt("slot_count", 2);
        tag.putString("slot_0", "###not-a-valid-id###");
        tag.putString("slot_1", HEAL.toString());

        BarState loaded = assertDoesNotThrow(() -> serializer.read(null, tag, null));

        assertTrue(loaded.assignment(0).isEmpty(), "corrupt id leaves the slot empty, not crashing");
        assertEquals(HEAL, loaded.assignment(1).abilityId(), "valid neighbours still load");
    }

    @Test
    void outOfRangeSelectedSlotInNbtIsClampedToZero() {
        CompoundTag tag = serializer.write(BarState.empty(3), null);
        tag.putInt("selected_slot", 99);

        BarState loaded = serializer.read(null, tag, null);

        assertEquals(0, loaded.selectedSlot());
        assertEquals(3, loaded.assignments().length);
    }

    @Test
    void fullyEmptyLegacyStateDefaultsToSingleSlot() {
        // No schema_version, no slot_count, no slot keys: a valid, non-throwing
        // single-slot empty state rather than a crash or a zero-length bar.
        BarState loaded = serializer.read(null, new CompoundTag(), null);

        assertTrue(loaded.assignments().length >= 1);
        assertTrue(loaded.assignment(0).isEmpty());
        assertEquals(0, loaded.selectedSlot());
    }
}
