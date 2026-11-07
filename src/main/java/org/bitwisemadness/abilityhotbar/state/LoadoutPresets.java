package org.bitwisemadness.abilityhotbar.state;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * Named loadout preset operations (hotbar#29) - pure state transitions, the
 * fixture-testable half: save snapshots a stored bar's row, recall writes a
 * preset's row back through a validated apply, delete drops it by name. The
 * payloads do validation, echo and diagnostics around these.
 *
 * <p>Recall invariants (REQ-HOT-22's): zero casts, zero mode-change events -
 * recall only writes stored state; a preset entry is raw data, so inert
 * (provider-absent) ids are preserved verbatim (the #24 round-trip rule),
 * while the standing {@code UnlockReconciler} sweep applies its own ruling
 * to provably-unlearned entries at the very same echo (hotbar#147 - a
 * standing rule this issue predates; see the recall result's notes).
 * Per-slot modes ride the saved {@link SlotAssignment}s verbatim - restoring
 * stored state is not a mode-change event.</p>
 */
public final class LoadoutPresets {

    private LoadoutPresets() {
    }

    /** The outcome of a recall: next state, the applied bar, player-facing notes. */
    public record RecallResult(BarState state, ResourceLocation barId, List<String> notes) {
    }

    /**
     * Saves the stored bar's row under a (trimmed, non-blank) name - an
     * existing name is replaced (upsert: save twice = one preset).
     *
     * @param state  the player's current state
     * @param barId  the bar to snapshot (must be stored for a result)
     * @param name   the already-validated display name
     * @return the next state, or null when the bar is not stored (nothing to save)
     */
    @Nullable
    public static BarState save(BarState state, ResourceLocation barId, String name) {
        SlotAssignment[] row = state.assignmentsOf(barId);
        if (row == null) {
            return null;
        }
        LoadoutPreset[] current = state.presets();
        List<LoadoutPreset> next = new ArrayList<>(current.length + 1);
        boolean replaced = false;
        for (LoadoutPreset preset : current) {
            if (preset.name().equals(name)) {
                next.add(new LoadoutPreset(name, barId, row));
                replaced = true;
            } else {
                next.add(preset);
            }
        }
        if (!replaced) {
            next.add(new LoadoutPreset(name, barId, row));
        }
        return state.withPresets(next.toArray(new LoadoutPreset[0]));
    }

    /**
     * Deletes the named preset.
     *
     * @return the next state, or null when no preset carries the name
     */
    @Nullable
    public static BarState delete(BarState state, String name) {
        LoadoutPreset[] current = state.presets();
        List<LoadoutPreset> next = new ArrayList<>(current.length);
        for (LoadoutPreset preset : current) {
            if (!preset.name().equals(name)) {
                next.add(preset);
            }
        }
        if (next.size() == current.length) {
            return null;
        }
        return state.withPresets(next.toArray(new LoadoutPreset[0]));
    }

    /**
     * Applies the named preset to its saved bar: the bar must still be stored
     * <b>and assignable</b>, and the row is clamped to the bar's current slot
     * count (a config shrink drops the tail with a note; a grow pads empty -
     * never renumbers or crashes). Everything else in the state is untouched.
     *
     * @param state the player's current state
     * @param name  the preset name (already validated non-blank)
     * @return the recall outcome, or null when no preset carries the name
     */
    @Nullable
    public static RecallResult recall(BarState state, String name) {
        LoadoutPreset preset = state.presetNamed(name);
        if (preset == null) {
            return null;
        }
        ResourceLocation barId = preset.barId();
        SlotAssignment[] current = state.assignmentsOf(barId);
        if (current == null) {
            return new RecallResult(state, barId, List.of(
                    "preset '" + name + "' names a bar this state does not store - nothing applied"));
        }
        if (!HotbarRegistry.isAssignable(barId)) {
            return new RecallResult(state, barId, List.of(
                    "preset '" + name + "' targets a bar that is no longer assignable - nothing applied"));
        }

        SlotAssignment[] saved = preset.assignments();
        List<String> notes = new ArrayList<>();
        SlotAssignment[] applied = new SlotAssignment[current.length];
        for (int i = 0; i < current.length; i++) {
            applied[i] = i < saved.length ? saved[i] : SlotAssignment.empty();
        }
        if (saved.length != current.length) {
            notes.add("preset '" + name + "' was saved with " + saved.length
                    + " slots; applied to " + current.length + " (entries "
                    + (saved.length > current.length ? "beyond the last slot dropped"
                            : "missing filled empty") + ")");
        }
        BarState next = state.withRow(barId, applied);
        return new RecallResult(next, barId, notes);
    }

    /**
     * Server-side diagnostics for entries that will not activate as stored:
     * provider-absent ids render inert (preserved by design), and provably
     * unlearned ids are swept by the standing reconciliation at this very
     * echo (hotbar#147). Pure over the row; the caller supplies the row.
     *
     * @param row the recalled assignment row
     * @return one line per noteworthy entry, empty when all are clean
     */
    public static List<String> inertNotes(SlotAssignment[] row) {
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < row.length; i++) {
            ResourceLocation id = row[i].abilityId();
            if (id != null && !org.bitwisemadness.abilitycore.discovery.AbilityDiscovery
                    .get(id).isPresent()) {
                notes.add("slot " + (i + 1) + " keeps unknown ability " + id
                        + " (renders inert until its provider exists)");
            }
        }
        return notes;
    }
}
