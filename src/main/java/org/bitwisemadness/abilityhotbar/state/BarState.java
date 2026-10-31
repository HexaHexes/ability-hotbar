package org.bitwisemadness.abilityhotbar.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The per-player ability bar state (REQ-HOT-14).
 *
 * @param assignments   slot -> ability id, or null for empty slots (array length = effectiveSlotsPerBar)
 * @param selectedSlot  the currently selected slot index (0-based)
 * @param schemaVersion format version for forward migration
 */
public record BarState(SlotAssignment[] assignments, int selectedSlot, int schemaVersion) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final Codec<List<SlotAssignment>> ASSIGNMENTS_CODEC =
            SlotAssignment.CODEC.listOf();

    public static final Codec<BarState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ASSIGNMENTS_CODEC.fieldOf("assignments").forGetter(BarState::toList),
            Codec.INT.fieldOf("selected_slot").forGetter(BarState::selectedSlot),
            Codec.INT.optionalFieldOf("schema_version", CURRENT_SCHEMA_VERSION).forGetter(BarState::schemaVersion)
    ).apply(instance, BarState::fromList));

    /** Codec adapter: List -> array. */
    private static List<SlotAssignment> toList(BarState state) {
        return Arrays.asList(state.assignments());
    }

    /** Codec adapter: List -> BarState. */
    private static BarState fromList(List<SlotAssignment> assignments, int selectedSlot, int schemaVersion) {
        return new BarState(assignments.toArray(new SlotAssignment[0]), selectedSlot, schemaVersion);
    }

    public BarState {
        if (assignments == null) {
            throw new IllegalArgumentException("assignments must not be null");
        }
        if (selectedSlot < 0 || selectedSlot >= assignments.length) {
            throw new IllegalArgumentException(
                    "selectedSlot must be in [0, " + assignments.length + "), got " + selectedSlot);
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1, got " + schemaVersion);
        }
        assignments = assignments.clone();
    }

    public static BarState empty(int slotCount) {
        SlotAssignment[] slots = new SlotAssignment[slotCount];
        for (int i = 0; i < slotCount; i++) {
            slots[i] = SlotAssignment.empty();
        }
        return new BarState(slots, 0, CURRENT_SCHEMA_VERSION);
    }

    @Nullable
    public SlotAssignment assignment(int slot) {
        if (slot < 0 || slot >= assignments.length) {
            return SlotAssignment.empty();
        }
        return assignments[slot];
    }

    public BarState withAssignment(int slot, @Nullable ResourceLocation abilityId) {
        SlotAssignment[] copy = assignments.clone();
        if (slot >= 0 && slot < copy.length) {
            copy[slot] = abilityId == null ? SlotAssignment.empty() : new SlotAssignment(abilityId);
        }
        return new BarState(copy, selectedSlot, schemaVersion);
    }

    public BarState withSelectedSlot(int slot) {
        return new BarState(assignments, slot, schemaVersion);
    }
}
