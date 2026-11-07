package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    /**
     * {@code read()} reconciles against the live static registry (ADR-012), so
     * every test here starts from - and leaves behind - an empty registry:
     * leaks would append phantom bars to other tests' reads.
     */
    @BeforeEach
    @AfterEach
    void isolateRegistry() {
        HotbarRegistry.reset();
    }

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
        // #28 AC 2 / #77 AC 3: a shipped v1 save (flat single-bar keys) upgrades
        // to the current multi-bar layout as built-in bar 0, active bar 0,
        // values and selection intact.
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
            assertEquals(HotbarRegistry.builtinBarId(0), upgraded.activeBarId(),
                    "flat data activates built-in bar 0");
            assertEquals(FIREBALL, upgraded.assignment(0).abilityId());
            assertTrue(upgraded.assignment(1).isEmpty());
            assertEquals(HEAL, upgraded.assignment(2).abilityId());
            assertEquals(2, upgraded.selectedSlot());

            // REQ-HOT-15 AC 1: the upgrade is logged with its from/to versions.
            assertTrue(logs.messages().stream()
                            .anyMatch(m -> m.contains("schema upgraded from 1 to "
                                    + BarState.CURRENT_SCHEMA_VERSION)),
                    "v1 -> current upgrade must be logged, got: " + logs.messages());
        }

        // The migrated state rewrites at the current version with the flat keys
        // nested under bar_0 (carrying its bar_id) - the ladder never reads
        // them as flat again.
        CompoundTag rewritten = serializer.write(upgraded, null);
        assertEquals(BarState.CURRENT_SCHEMA_VERSION, rewritten.getInt("schema_version"));
        assertEquals(HotbarRegistry.builtinBarId(0).toString(),
                rewritten.getString("active_bar"));
        assertEquals(1, rewritten.getInt("bar_count"));
        CompoundTag bar0 = rewritten.getCompound("bar_0");
        assertEquals(HotbarRegistry.builtinBarId(0).toString(), bar0.getString("bar_id"));
        assertEquals(3, bar0.getInt("slot_count"));
        assertEquals(2, bar0.getInt("selected_slot"));
        assertEquals(FIREBALL.toString(), bar0.getString("slot_0"));
        BarState reloaded = serializer.read(null, rewritten, null);
        assertEquals(HEAL, reloaded.assignment(2).abilityId());
    }

    @Test
    void versionTwoNestedCompoundMigratesToBuiltInIds() {
        // #77 AC 3: a v2 save (nested bars, integer active bar, no bar_id keys)
        // upgrades - every v2 bar was a built-in, so positions become
        // ability_hotbar:bar_N and the integer active bar becomes that id.
        CompoundTag v2 = new CompoundTag();
        v2.putInt("schema_version", 2);
        v2.putInt("active_bar", 1);
        v2.putInt("bar_count", 2);
        CompoundTag bar0 = new CompoundTag();
        bar0.putInt("slot_count", 3);
        bar0.putInt("selected_slot", 2);
        bar0.putString("slot_0", FIREBALL.toString());
        v2.put("bar_0", bar0);
        CompoundTag bar1 = new CompoundTag();
        bar1.putInt("slot_count", 3);
        bar1.putInt("selected_slot", 1);
        bar1.putString("slot_2", HEAL.toString());
        v2.put("bar_1", bar1);

        BarState upgraded;
        try (LogCapture logs = LogCapture.forLogger(BarStateSerializer.class)) {
            upgraded = serializer.read(null, v2, null);

            assertEquals(BarState.CURRENT_SCHEMA_VERSION, upgraded.schemaVersion());
            assertEquals(2, upgraded.bars().length);
            assertEquals(HotbarRegistry.builtinBarId(0), upgraded.bars()[0].id());
            assertEquals(HotbarRegistry.builtinBarId(1), upgraded.bars()[1].id());
            assertEquals(HotbarRegistry.builtinBarId(1), upgraded.activeBarId(),
                    "integer active bar 1 becomes built-in id bar_1");
            // values retained on both bars
            assertEquals(FIREBALL, upgraded.bars()[0].assignment(0).abilityId());
            assertEquals(2, upgraded.bars()[0].selectedSlot());
            assertEquals(HEAL, upgraded.bars()[1].assignment(2).abilityId());
            assertEquals(1, upgraded.bars()[1].selectedSlot());

            assertTrue(logs.messages().stream()
                            .anyMatch(m -> m.contains("schema upgraded from 2 to "
                                    + BarState.CURRENT_SCHEMA_VERSION)),
                    "v2 -> current upgrade must be logged, got: " + logs.messages());
        }

        // The rewrite now persists bar_id per bar, so a re-read is identity-stable.
        CompoundTag rewritten = serializer.write(upgraded, null);
        assertEquals(HotbarRegistry.builtinBarId(1).toString(),
                rewritten.getCompound("bar_1").getString("bar_id"));
        BarState reloaded = serializer.read(null, rewritten, null);
        assertEquals(HotbarRegistry.builtinBarId(1), reloaded.activeBarId());
        assertEquals(HEAL, reloaded.bars()[1].assignment(2).abilityId());
    }

    @Test
    void roundTripsMultipleBarsSelectionsAndActiveId() {
        BarState original = BarState.empty(4, 3)
                .withAssignment(0, FIREBALL)
                .withSelectedSlot(3)
                .withActiveBar(HotbarRegistry.builtinBarId(1))
                .withAssignment(2, HEAL)
                .withSelectedSlot(1)
                .withActiveBar(HotbarRegistry.builtinBarId(2))
                .withSelectedSlot(2);

        CompoundTag tag = serializer.write(original, null);
        assertEquals(3, tag.getInt("bar_count"));
        assertEquals(HotbarRegistry.builtinBarId(2).toString(), tag.getString("active_bar"));
        assertEquals(HotbarRegistry.builtinBarId(1).toString(),
                tag.getCompound("bar_1").getString("bar_id"));

        BarState restored = serializer.read(null, tag, null);

        assertEquals(3, restored.bars().length);
        assertEquals(HotbarRegistry.builtinBarId(2), restored.activeBarId());
        assertEquals(BarState.CURRENT_SCHEMA_VERSION, restored.schemaVersion());
        // Active-bar view: bar 2, selection 2, no assignments.
        assertEquals(2, restored.selectedSlot());
        assertTrue(restored.assignment(0).isEmpty());
        // Each bar keeps its own identity, slots and selection.
        Bar[] bars = restored.bars();
        assertEquals(HotbarRegistry.builtinBarId(0), bars[0].id());
        assertEquals(FIREBALL, bars[0].assignment(0).abilityId());
        assertEquals(3, bars[0].selectedSlot());
        assertEquals(HotbarRegistry.builtinBarId(1), bars[1].id());
        assertEquals(HEAL, bars[1].assignment(2).abilityId());
        assertEquals(1, bars[1].selectedSlot());
        assertEquals(HotbarRegistry.builtinBarId(2), bars[2].id());
        assertEquals(2, bars[2].selectedSlot());
        assertTrue(bars[2].assignment(0).isEmpty());
    }

    @Test
    void unknownActiveBarIdFallsBackToFirstBarWithoutLosingBars() {
        // #77 AC 6: an active id that is neither stored nor registered is
        // re-anchored on load, with a log line, and no bar data is lost.
        BarState original = BarState.empty(3, 2).withAssignment(0, FIREBALL);
        CompoundTag tag = serializer.write(original, null);
        tag.putString("active_bar", "missing:bar");

        BarState loaded;
        try (LogCapture logs = LogCapture.forLogger(BarState.class)) {
            loaded = serializer.read(null, tag, null);

            assertEquals(HotbarRegistry.builtinBarId(0), loaded.activeBarId());
            assertEquals(2, loaded.bars().length, "the bars themselves are kept");
            assertEquals(FIREBALL, loaded.assignment(0).abilityId());
            assertTrue(logs.messages().stream()
                            .anyMatch(m -> m.contains("neither stored nor registered")),
                    "the fallback must be logged, got: " + logs.messages());
        }
    }

    @Test
    void malformedActiveBarIdFallsBackToFirstBar() {
        // Corrupt type/value in active_bar: the read fails closed to the first
        // stored bar with a log line, never crashing the load (ADR-004).
        BarState original = BarState.empty(3, 2).withAssignment(0, FIREBALL);
        CompoundTag tag = serializer.write(original, null);
        tag.putInt("active_bar", 5);

        BarState loaded;
        try (LogCapture logs = LogCapture.forLogger(BarStateSerializer.class)) {
            loaded = serializer.read(null, tag, null);

            assertEquals(HotbarRegistry.builtinBarId(0), loaded.activeBarId());
            assertEquals(2, loaded.bars().length, "the bars themselves are kept");
            assertEquals(FIREBALL, loaded.assignment(0).abilityId());
            assertTrue(logs.messages().stream()
                            .anyMatch(m -> m.contains("using the first stored bar")),
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

    @Test
    void loadAppendsRegisteredAssignableBarAndSkipsNonAssignable() {
        // #77 AC 5 (load half): reading a save that predates a registration
        // appends the assignable bar with its registration id, sized like the
        // state's own bars; a non-assignable registration gets no state.
        HotbarRegistry.register(ResourceLocation.fromNamespaceAndPath("otherpack", "extra"),
                "otherpack.bar.extra", true);
        HotbarRegistry.register(ResourceLocation.fromNamespaceAndPath("pseudocore", "curios"),
                "pseudocore.bar.curios", false);

        CompoundTag tag = serializer.write(BarState.empty(3, 2), null);
        BarState loaded = serializer.read(null, tag, null);

        assertEquals(3, loaded.bars().length, "assignable registration appended");
        assertEquals(ResourceLocation.fromNamespaceAndPath("otherpack", "extra"),
                loaded.bars()[2].id());
        assertEquals(3, loaded.bars()[2].assignments().length, "sized like the state's own bars");
        assertTrue(loaded.bars()[2].assignment(0).isEmpty());
        // non-assignable left no trace in the attachment
        assertEquals(HotbarRegistry.builtinBarId(0), loaded.activeBarId());

        // idempotent: a second read does not append again
        BarState again = serializer.read(null, tag, null);
        assertEquals(3, again.bars().length);
    }

    @Test
    void loadKeepsUnregisteredStoredBarAndNeverRemapsIt() {
        // #77 AC 4: a stored bar whose registration is gone survives the load
        // untouched, and a *different* registrant gets fresh state rather than
        // inheriting the orphan's slots (identity inert-preservation).
        ResourceLocation orphanId = ResourceLocation.fromNamespaceAndPath("gone", "bar");
        BarState saved = new BarState(
                new Bar[]{
                        Bar.empty(HotbarRegistry.builtinBarId(0), 3),
                        Bar.empty(orphanId, 3).withAssignment(1, FIREBALL)},
                HotbarRegistry.builtinBarId(0), BarState.CURRENT_SCHEMA_VERSION,
                new LoadoutPreset[0]);
        CompoundTag tag = serializer.write(saved, null);

        HotbarRegistry.register(ResourceLocation.fromNamespaceAndPath("newcomer", "bar"),
                "newcomer.bar", true);
        BarState loaded = serializer.read(null, tag, null);

        assertEquals(3, loaded.bars().length);
        // orphan kept in place, data intact
        assertEquals(orphanId, loaded.bars()[1].id());
        assertEquals(FIREBALL, loaded.bars()[1].assignment(1).abilityId());
        // newcomer appended with fresh empty state - no remapping happened
        assertEquals(ResourceLocation.fromNamespaceAndPath("newcomer", "bar"),
                loaded.bars()[2].id());
        assertTrue(loaded.bars()[2].assignment(1).isEmpty());
    }

    @Test
    void versionThreeCompoundUpgradesToV4WithoutValueConversion() {
        // hotbar#29's ladder step: v3 data has no mode keys and no preset
        // keys - it must load as schema 4 with modeless slots and an empty
        // preset list, values retained, no per-version conversion.
        CompoundTag v3 = new CompoundTag();
        v3.putInt("schema_version", 3);
        v3.putInt("bar_count", 1);
        CompoundTag bar = new CompoundTag();
        bar.putString("bar_id", HotbarRegistry.builtinBarId(0).toString());
        bar.putInt("slot_count", 2);
        bar.putInt("selected_slot", 1);
        bar.putString("slot_0", FIREBALL.toString());
        v3.put("bar_0", bar);

        BarState loaded = serializer.read(null, v3, null);

        assertEquals(BarState.CURRENT_SCHEMA_VERSION, loaded.schemaVersion(),
                "v3 must upgrade to the current schema in memory");
        assertEquals(0, loaded.presets().length, "pre-v4 data has no presets");
        assertEquals(FIREBALL, loaded.assignment(0).abilityId());
        assertNull(loaded.assignment(0).mode(), "pre-v4 slots are modeless");
        assertTrue(loaded.assignment(1).isEmpty());
        assertEquals(1, loaded.selectedSlot());
    }

    @Test
    void roundTripsPerSlotModesAndPresetsAtV4() {
        SlotAssignment[] row = {
            new SlotAssignment(FIREBALL, "fire"),
            SlotAssignment.empty(),
            new SlotAssignment(HEAL, null),
        };
        BarState original = BarState.empty(3, 1)
                .withRow(HotbarRegistry.builtinBarId(0), row)
                .withPresets(new LoadoutPreset[]{
                        new LoadoutPreset("pvp", HotbarRegistry.builtinBarId(0), row)});

        CompoundTag tag = serializer.write(original, null);
        assertEquals(4, tag.getInt("schema_version"));
        assertEquals("fire", tag.getCompound("bar_0").getString("mode_0"), "mode rides mode_i");
        assertEquals(1, tag.getInt("preset_count"));
        assertEquals("pvp", tag.getCompound("preset_0").getString("name"));

        BarState restored = serializer.read(null, tag, null);

        SlotAssignment[] back = restored.assignmentsOf(HotbarRegistry.builtinBarId(0));
        assertEquals("fire", back[0].mode());
        assertTrue(back[1].isEmpty());
        assertNull(back[2].mode());
        assertEquals(FIREBALL, back[0].abilityId());
        assertEquals(1, restored.presets().length);
        LoadoutPreset preset = restored.presets()[0];
        assertEquals("pvp", preset.name());
        assertEquals(HotbarRegistry.builtinBarId(0), preset.barId());
        assertEquals("fire", preset.assignments()[0].mode());
        assertTrue(preset.assignments()[1].isEmpty());
    }
}
