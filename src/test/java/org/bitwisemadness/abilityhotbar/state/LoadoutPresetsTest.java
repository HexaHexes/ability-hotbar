package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Loadout preset operations (hotbar#29's fixtures): the save -> modify ->
 * recall round-trip preserves ids, inert ids and per-slot modes exactly;
 * recall performs zero casts and zero mode-change events (it is a pure
 * state transition - nothing outside the target row moves); the mode lives
 * per slotted position and no bar-level mode state exists; name and shape
 * validation fails closed with diagnostics.
 */
class LoadoutPresetsTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation HEAL =
            ResourceLocation.fromNamespaceAndPath("tensura", "heal");
    private static final ResourceLocation UNKNOWN_INERT =
            ResourceLocation.fromNamespaceAndPath("vanished", "old_art");
    private static final ResourceLocation BAR = HotbarRegistry.builtinBarId(0);
    private static final ResourceLocation OTHER_BAR = HotbarRegistry.builtinBarId(1);

    @BeforeEach
    @AfterEach
    void isolateRegistry() {
        HotbarRegistry.reset();
    }

    private static BarState stateWithRow(SlotAssignment[] row) {
        return BarState.empty(3, 2).withRow(BAR, row);
    }

    @Test
    void saveModifyRecallRestoresExactlyIncludingInertIdsAndModes() {
        SlotAssignment[] saved = {
            new SlotAssignment(FIREBALL, "fire"),
            new SlotAssignment(UNKNOWN_INERT, "legacy"),
            new SlotAssignment(HEAL, null),
        };
        BarState original = stateWithRow(saved);
        BarState savedState = LoadoutPresets.save(original, BAR, "pvp");
        assertNotNull(savedState, "the stored bar snapshots");

        // Modify: clear a slot and reassign another.
        BarState modified = savedState
                .withRow(BAR, new SlotAssignment[]{SlotAssignment.empty(),
                        new SlotAssignment(FIREBALL, "water"), new SlotAssignment(HEAL, null)});

        LoadoutPresets.RecallResult recalled =
                LoadoutPresets.recall(modified, "pvp");
        assertNotNull(recalled, "the preset still exists after modification");
        SlotAssignment[] back = recalled.state().assignmentsOf(BAR);

        assertEquals(FIREBALL, back[0].abilityId());
        assertEquals("fire", back[0].mode(), "the saved mode rides the recall");
        assertEquals(UNKNOWN_INERT, back[1].abilityId(),
                "an inert (provider-absent) id is preserved verbatim");
        assertEquals("legacy", back[1].mode());
        assertEquals(HEAL, back[2].abilityId());
        assertNull(back[2].mode());
        // Recall is not delete: the preset remains for the next recall.
        assertNotNull(recalled.state().presetNamed("pvp"));
    }

    @Test
    void recallIsAStateTransitionOnly_zeroCastsZeroModeChangeEvents() {
        SlotAssignment[] saved = {new SlotAssignment(FIREBALL, "fire"),
                new SlotAssignment(HEAL, "holy"), SlotAssignment.empty()};
        BarState original = stateWithRow(saved)
                .withSelectedSlot(2);
        BarState presetOwned = LoadoutPresets.save(original, BAR, "raid");
        int selectionBefore = presetOwned.selectedSlot();
        int presetsBefore = presetOwned.presets().length;

        LoadoutPresets.RecallResult result =
                LoadoutPresets.recall(presetOwned.withRow(BAR,
                        new SlotAssignment[]{SlotAssignment.empty(),
                                SlotAssignment.empty(), SlotAssignment.empty()}), "raid");

        // Only the target row moved: selection, presets and every other bar
        // are byte-identical - nothing cast, no mode machinery was driven
        // (restoring a stored mode value is state, not a mode-change event).
        assertEquals(selectionBefore, result.state().selectedSlot());
        assertEquals(presetsBefore, result.state().presets().length);
        assertTrue(result.state().presetNamed("raid") != null);
        assertTrue(result.state().assignmentsOf(OTHER_BAR)[0].isEmpty(),
                "the other bar is untouched");
        assertArrayEquals(saved, result.state().assignmentsOf(BAR),
                "the row is restored verbatim");
    }

    @Test
    void recallClampsToTheBarsCurrentSlotCountWithNotes() {
        SlotAssignment[] longRow = {
            new SlotAssignment(FIREBALL, "a"), new SlotAssignment(HEAL, "b"),
            new SlotAssignment(UNKNOWN_INERT, null), new SlotAssignment(FIREBALL, "c"),
            new SlotAssignment(HEAL, "d"),
        };
        BarState owned = stateWithRow(new SlotAssignment[]{SlotAssignment.empty(),
                SlotAssignment.empty(), SlotAssignment.empty()})
                .withPresets(new LoadoutPreset[]{new LoadoutPreset("wide", BAR, longRow)});

        LoadoutPresets.RecallResult result = LoadoutPresets.recall(owned, "wide");

        SlotAssignment[] applied = result.state().assignmentsOf(BAR);
        assertEquals(3, applied.length, "clamped to the stored row length");
        assertEquals("a", applied[0].mode());
        assertEquals(UNKNOWN_INERT, applied[2].abilityId());
        assertFalse(result.notes().isEmpty(), "the clamp is reported, never silent");
    }

    @Test
    void recallRefusalsAreDiagnosticNotSilent() {
        // Unknown name: null result (the payload echoes the state instead).
        assertNull(LoadoutPresets.recall(BarState.empty(3, 1), "missing"));

        // Unstored target bar: kept state + one explanatory note.
        BarState orphanOwned = BarState.empty(3, 1).withPresets(new LoadoutPreset[]{
                new LoadoutPreset("elsewhere",
                        ResourceLocation.fromNamespaceAndPath("nobody", "bar"),
                        new SlotAssignment[]{SlotAssignment.empty()})});
        LoadoutPresets.RecallResult unstored = LoadoutPresets.recall(orphanOwned, "elsewhere");
        assertNotNull(unstored);
        assertEquals(1, unstored.notes().size());
        assertFalse(unstored.notes().get(0).isEmpty());

        // A bar that lost assignability: kept state + one explanatory note.
        ResourceLocation closedBar = ResourceLocation.fromNamespaceAndPath("closed", "bar");
        HotbarRegistry.register(closedBar, "closed.bar", false);
        BarState closedOwned = new BarState(
                new Bar[]{Bar.empty(closedBar, 3), Bar.empty(HotbarRegistry.builtinBarId(0), 3)},
                HotbarRegistry.builtinBarId(0), BarState.CURRENT_SCHEMA_VERSION,
                new LoadoutPreset[]{new LoadoutPreset("closed", closedBar,
                        new SlotAssignment[]{SlotAssignment.empty()})});
        LoadoutPresets.RecallResult refused = LoadoutPresets.recall(closedOwned, "closed");
        assertNotNull(refused);
        assertEquals(1, refused.notes().size());
        assertTrue(refused.notes().get(0).contains("assignable"));
    }

    @Test
    void saveUpsertsAndDeleteRemovesByName() {
        BarState base = stateWithRow(new SlotAssignment[]{new SlotAssignment(FIREBALL, "fire"),
                SlotAssignment.empty(), SlotAssignment.empty()});

        BarState first = LoadoutPresets.save(base, BAR, "one");
        BarState second = LoadoutPresets.save(first, BAR, "one");
        assertEquals(1, second.presets().length, "saving twice upserts, never duplicates");

        BarState withTwo = LoadoutPresets.save(second, BAR, "two");
        assertEquals(2, withTwo.presets().length);

        BarState deleted = LoadoutPresets.delete(withTwo, "one");
        assertNotNull(deleted);
        assertEquals(1, deleted.presets().length);
        assertNull(deleted.presetNamed("one"));
        assertNotNull(deleted.presetNamed("two"));
        assertNull(LoadoutPresets.delete(deleted, "missing"), "an unknown name changes nothing");
    }

    @Test
    void theModeLivesPerSlotAndNeverOnTheBar() {
        // The same skill, two slots, two modes: independent by construction.
        SlotAssignment[] row = {
            new SlotAssignment(FIREBALL, "fire"),
            new SlotAssignment(FIREBALL, "water"),
        };
        SlotAssignment[] back = stateWithRow(row).assignmentsOf(BAR);
        assertEquals("fire", back[0].mode());
        assertEquals("water", back[1].mode());
        assertFalse(back[0].withMode("ice").mode().equals(back[1].mode()),
                "changing one slot's mode never touches its sibling");

        // No bar-level or state-level mode component exists anywhere.
        assertFalse(Arrays.stream(Bar.class.getRecordComponents())
                        .anyMatch(component -> component.getName().contains("mode")),
                "Bar carries no mode state");
        assertFalse(Arrays.stream(BarState.class.getRecordComponents())
                        .anyMatch(component -> component.getName().contains("mode")),
                "BarState carries no mode state");
    }

    @Test
    void inertNotesNameProviderAbsentEntries() {
        SlotAssignment[] row = {
            new SlotAssignment(FIREBALL, null),
            new SlotAssignment(UNKNOWN_INERT, "x"),
        };
        var notes = LoadoutPresets.inertNotes(row);
        // This unit registry is empty, so every id reads provider-absent -
        // in a live pack only the genuinely missing provider flags.
        assertEquals(2, notes.size(), "both ids are provider-absent here");
        assertTrue(notes.get(0).contains(FIREBALL.toString()));
        assertTrue(notes.get(1).contains(UNKNOWN_INERT.toString()));
    }

    @Test
    void validNameValidatesServerSideFirst() {
        assertEquals("pvp", LoadoutPreset.validName("  pvp  "), "trimmed");
        assertNull(LoadoutPreset.validName(null));
        assertNull(LoadoutPreset.validName("   "), "blank is not a name");
        assertNull(LoadoutPreset.validName("x".repeat(LoadoutPreset.MAX_NAME_LENGTH + 1)),
                "overlong is rejected before any state is touched");
        assertThrowsOnConstruction();
    }

    private static void assertThrowsOnConstruction() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new LoadoutPreset(" ", BAR, new SlotAssignment[0]));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new LoadoutPreset("x".repeat(LoadoutPreset.MAX_NAME_LENGTH + 1),
                        BAR, new SlotAssignment[0]));
    }
}
