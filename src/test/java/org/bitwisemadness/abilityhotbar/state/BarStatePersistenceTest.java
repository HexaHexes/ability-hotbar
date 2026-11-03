package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        BarState upgraded;
        try (LogCapture logs = LogCapture.forLogger(BarStateSerializer.class)) {
            upgraded = serializer.read(null, legacy, null);

            assertEquals(BarState.CURRENT_SCHEMA_VERSION, upgraded.schemaVersion());
            assertEquals(3, upgraded.assignments().length, "slot count inferred from the highest key");
            assertEquals(FIREBALL, upgraded.assignment(0).abilityId());
            assertTrue(upgraded.assignment(1).isEmpty());
            assertEquals(HEAL, upgraded.assignment(2).abilityId());
            assertEquals(0, upgraded.selectedSlot());

            // REQ-HOT-15 AC 1: the upgrade is logged with its from/to versions.
            assertTrue(logs.messages().stream()
                            .anyMatch(m -> m.contains("schema upgraded from 0 to "
                                    + BarState.CURRENT_SCHEMA_VERSION)),
                    "upgrade must be logged, got: " + logs.messages());
        }
    }

    @Test
    void currentVersionCompoundDoesNotLogAnUpgrade() {
        // The upgrade line must fire only on a real version step - a normal
        // relog of current-version data stays quiet (no log spam per login).
        CompoundTag tag = serializer.write(BarState.empty(3), null);

        try (LogCapture logs = LogCapture.forLogger(BarStateSerializer.class)) {
            serializer.read(null, tag, null);

            assertTrue(logs.messages().stream().noneMatch(m -> m.contains("schema upgraded")),
                    "current-version data must not log an upgrade, got: " + logs.messages());
        }
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

        BarState loaded;
        try (LogCapture logs = LogCapture.forLogger(BarStateSerializer.class)) {
            loaded = assertDoesNotThrow(() -> serializer.read(null, tag, null));

            // REQ-HOT-15 AC 2: the bad key is named in a log line, per key.
            assertTrue(logs.messages().stream()
                            .anyMatch(m -> m.contains("slot 0")
                                    && m.contains("###not-a-valid-id###")),
                    "corrupt slot must be logged with its key and raw value, got: "
                            + logs.messages());
        }

        assertTrue(loaded.assignment(0).isEmpty(), "corrupt id leaves the slot empty, not crashing");
        assertEquals(HEAL, loaded.assignment(1).abilityId(), "valid neighbours still load");
    }

    @Test
    void outOfRangeSelectedSlotInNbtIsClampedToZero() {
        CompoundTag tag = serializer.write(BarState.empty(3), null);
        // v2 keeps each bar's keys inside its bar_N compound.
        tag.getCompound("bar_0").putInt("selected_slot", 99);

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

    @Test
    void versionOneFlatCompoundMigratesToBarZero() {
        // #28 AC 2: a shipped v1 save (flat single-bar keys) upgrades to the v2
        // multi-bar layout as bar 0, active bar 0, values and selection intact.
        CompoundTag v1 = new CompoundTag();
        v1.putInt("schema_version", 1);
        v1.putInt("slot_count", 3);
        v1.putInt("selected_slot", 2);
        v1.putString("slot_0", FIREBALL.toString());
        v1.putString("slot_2", HEAL.toString());

        BarState upgraded;
        try (LogCapture logs = LogCapture.forLogger(BarStateSerializer.class)) {
            upgraded = serializer.read(null, v1, null);

            assertEquals(BarState.CURRENT_SCHEMA_VERSION, upgraded.schemaVersion());
            assertEquals(1, upgraded.bars().length, "flat data becomes exactly one bar");
            assertEquals(0, upgraded.activeBar(), "flat data activates bar 0");
            assertEquals(FIREBALL, upgraded.assignment(0).abilityId());
            assertTrue(upgraded.assignment(1).isEmpty());
            assertEquals(HEAL, upgraded.assignment(2).abilityId());
            assertEquals(2, upgraded.selectedSlot());

            // REQ-HOT-15 AC 1: the upgrade is logged with its from/to versions.
            assertTrue(logs.messages().stream()
                            .anyMatch(m -> m.contains("schema upgraded from 1 to "
                                    + BarState.CURRENT_SCHEMA_VERSION)),
                    "v1 -> v2 upgrade must be logged, got: " + logs.messages());
        }

        // The migrated state rewrites at the current version with the flat keys
        // nested under bar_0 - the ladder never reads them as flat again.
        CompoundTag rewritten = serializer.write(upgraded, null);
        assertEquals(BarState.CURRENT_SCHEMA_VERSION, rewritten.getInt("schema_version"));
        assertEquals(0, rewritten.getInt("active_bar"));
        assertEquals(1, rewritten.getInt("bar_count"));
        CompoundTag bar0 = rewritten.getCompound("bar_0");
        assertEquals(3, bar0.getInt("slot_count"));
        assertEquals(2, bar0.getInt("selected_slot"));
        assertEquals(FIREBALL.toString(), bar0.getString("slot_0"));
        BarState reloaded = serializer.read(null, rewritten, null);
        assertEquals(HEAL, reloaded.assignment(2).abilityId());
    }

    @Test
    void versionTwoRoundTripsMultipleBarsSelectionsAndActiveBar() {
        BarState original = BarState.empty(4, 3)
                .withAssignment(0, FIREBALL)
                .withSelectedSlot(3)
                .withActiveBar(1)
                .withAssignment(2, HEAL)
                .withSelectedSlot(1)
                .withActiveBar(2)
                .withSelectedSlot(2);

        CompoundTag tag = serializer.write(original, null);
        assertEquals(3, tag.getInt("bar_count"));
        assertEquals(2, tag.getInt("active_bar"));

        BarState restored = serializer.read(null, tag, null);

        assertEquals(3, restored.bars().length);
        assertEquals(2, restored.activeBar());
        assertEquals(BarState.CURRENT_SCHEMA_VERSION, restored.schemaVersion());
        // Active-bar view: bar 2, selection 2, no assignments.
        assertEquals(2, restored.selectedSlot());
        assertTrue(restored.assignment(0).isEmpty());
        // Each bar keeps its own slots and selection.
        Bar[] bars = restored.bars();
        assertEquals(FIREBALL, bars[0].assignment(0).abilityId());
        assertEquals(3, bars[0].selectedSlot());
        assertEquals(HEAL, bars[1].assignment(2).abilityId());
        assertEquals(1, bars[1].selectedSlot());
        assertEquals(2, bars[2].selectedSlot());
        assertTrue(bars[2].assignment(0).isEmpty());
    }

    @Test
    void outOfRangeActiveBarFallsBackToBarZeroWithoutLosingBars() {
        BarState original = BarState.empty(3, 2).withAssignment(0, FIREBALL);
        CompoundTag tag = serializer.write(original, null);
        tag.putInt("active_bar", 5);

        BarState loaded;
        try (LogCapture logs = LogCapture.forLogger(BarStateSerializer.class)) {
            loaded = serializer.read(null, tag, null);

            assertEquals(0, loaded.activeBar());
            assertEquals(2, loaded.bars().length, "the bars themselves are kept");
            assertEquals(FIREBALL, loaded.assignment(0).abilityId());
            assertTrue(logs.messages().stream()
                            .anyMatch(m -> m.contains("active bar 5 out of range")),
                    "the fallback must be logged, got: " + logs.messages());
        }
    }

    @Test
    void savedBarCountSurvivesACeilingReduction() {
        // Inert preservation (ADR-011): the serializer never reads config, so a
        // state saved with more bars than a later-lowered ceiling keeps them all.
        BarState original = BarState.empty(2, 4).withAssignment(1, HEAL);
        CompoundTag tag = serializer.write(original, null);

        BarState restored = serializer.read(null, tag, null);
        assertEquals(4, restored.bars().length);
        assertEquals(HEAL, restored.bars()[0].assignment(1).abilityId());
    }
}
