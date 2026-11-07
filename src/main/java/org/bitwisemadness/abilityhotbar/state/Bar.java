package org.bitwisemadness.abilityhotbar.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One bar's identity, slot set and its own selection (REQ-HOT-22, ADR-011/012).
 *
 * <p>The id is the bar's stable identity across saves: built-in bars carry
 * positional ids ({@code ability_hotbar:bar_N}), registered bars carry their
 * registration id. Inert preservation extends to identity - a stored id whose
 * registration is gone is kept, never remapped onto a later registrant
 * (ADR-012).</p>
 *
 * <p>Immutable like the rest of the state model: the slot array is copied on
 * construction and on access, and every mutation returns a new {@code Bar}.</p>
 *
 * @param id           stable identity of this bar
 * @param assignments  slot -> ability id, or null for empty slots (array length = slots in this bar)
 * @param selectedSlot the selected slot index within this bar (0-based)
 */
public record Bar(ResourceLocation id, SlotAssignment[] assignments, int selectedSlot) {

    private static final Codec<List<SlotAssignment>> ASSIGNMENTS_CODEC =
            SlotAssignment.CODEC.listOf();

    /** DFU codec: identity, this bar's slots, plus its own selection. */
    public static final Codec<Bar> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("bar_id").forGetter(Bar::id),
            ASSIGNMENTS_CODEC.fieldOf("assignments").forGetter(Bar::toList),
            Codec.INT.fieldOf("selected_slot").forGetter(Bar::selectedSlot)
    ).apply(instance, Bar::fromParts));

    /** Network codec for one bar inside the snapshot (ADR-003). */
    public static final StreamCodec<ByteBuf, Bar> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, Bar::id,
            SlotAssignment.STREAM_CODEC.apply(ByteBufCodecs.list()), Bar::toList,
            ByteBufCodecs.VAR_INT, Bar::selectedSlot,
            Bar::fromParts
    );

    /** Codec adapter: List -> array. */
    private static List<SlotAssignment> toList(Bar bar) {
        return Arrays.asList(bar.assignments);
    }

    /** Codec adapter: List -> Bar. */
    private static Bar fromParts(ResourceLocation id, List<SlotAssignment> assignments,
            int selectedSlot) {
        return new Bar(id, assignments.toArray(new SlotAssignment[0]), selectedSlot);
    }

    public Bar {
        if (id == null) {
            throw new IllegalArgumentException("bar id must not be null");
        }
        if (assignments == null) {
            throw new IllegalArgumentException("assignments must not be null");
        }
        if (selectedSlot < 0 || selectedSlot >= assignments.length) {
            throw new IllegalArgumentException(
                    "selectedSlot must be in [0, " + assignments.length + "), got " + selectedSlot);
        }
        assignments = assignments.clone();
    }

    /**
     * Defensive copy on every access: the array is mutable, so the record must
     * not hand out its internal one.
     */
    @Override
    public SlotAssignment[] assignments() {
        return assignments.clone();
    }

    /** @return how many slots this bar holds (no copy) */
    public int slotCount() {
        return assignments.length;
    }

    /** An empty bar with the given identity and slot count; selection starts at slot 0. */
    public static Bar empty(ResourceLocation id, int slotCount) {
        SlotAssignment[] slots = new SlotAssignment[slotCount];
        for (int i = 0; i < slotCount; i++) {
            slots[i] = SlotAssignment.empty();
        }
        return new Bar(id, slots, 0);
    }

    /**
     * @param slot the slot index
     * @return the assignment, or an empty sentinel when out of range
     */
    @Nullable
    public SlotAssignment assignment(int slot) {
        if (slot < 0 || slot >= assignments.length) {
            return SlotAssignment.empty();
        }
        return assignments[slot];
    }

    /**
     * @return this bar with the slot set; an out-of-range slot is a no-op
     *         (the caller's bounds check is the fail-closed gate)
     */
    public Bar withAssignment(int slot, @Nullable ResourceLocation abilityId) {
        if (slot < 0 || slot >= assignments.length) {
            return this;
        }
        SlotAssignment[] copy = assignments.clone();
        copy[slot] = abilityId == null ? SlotAssignment.empty() : new SlotAssignment(abilityId);
        return new Bar(id, copy, selectedSlot);
    }

    /**
     * @return this bar with a new selected slot
     * @throws IllegalArgumentException when the slot is out of this bar's range
     */
    public Bar withSelectedSlot(int slot) {
        return new Bar(id, assignments, slot);
    }

    /**
     * Replaces one slot's mode, keeping its pairing (hotbar#29/#51's
     * server-side echo): an out-of-range slot or an unassigned pairing is a
     * no-op - a mode never conjures an assignment.
     *
     * @param slot the slot index
     * @param mode the new mode value (blank clears the context)
     */
    public Bar withMode(int slot, String mode) {
        if (slot < 0 || slot >= assignments.length || assignments[slot].abilityId() == null) {
            return this;
        }
        SlotAssignment[] copy = assignments.clone();
        copy[slot] = copy[slot].withMode(mode);
        return new Bar(id, copy, selectedSlot);
    }
}
