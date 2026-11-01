package org.bitwisemadness.abilityhotbar.state;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.slf4j.Logger;

/**
 * Serialises {@link BarState} to/from player NBT with a forward-only
 * schema-version upgrade ladder (ADR-004).
 *
 * <p>Data-preservation rules:</p>
 * <ul>
 *   <li>The saved slot count is written explicitly and read back, so a
 *       temporary config change never truncates stored data. When the count is
 *       absent (pre-v1 data) it is inferred from the highest slot key present.</li>
 *   <li>Assignments whose ids cannot be parsed are logged and left empty rather
 *       than crashing the load; valid-but-unresolvable ids are preserved raw so
 *       the slot renders as diagnosed inert (REQ-HOT-21).</li>
 *   <li>A future schema version loads a valid empty state instead of throwing.</li>
 * </ul>
 *
 * <p>This class deliberately does not read config: it must be usable from the
 * attachment plumbing before/without a loaded config, and the attachment
 * default supplier already applies {@code effectiveSlotsPerBar()}.</p>
 */
public final class BarStateSerializer implements IAttachmentSerializer<CompoundTag, BarState> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String KEY_SCHEMA = "schema_version";
    private static final String KEY_SLOT_COUNT = "slot_count";
    private static final String KEY_SELECTED = "selected_slot";
    private static final String KEY_SLOT_PREFIX = "slot_";

    @Override
    public BarState read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
        int version = tag.contains(KEY_SCHEMA) ? tag.getInt(KEY_SCHEMA) : 1;
        if (version > BarState.CURRENT_SCHEMA_VERSION) {
            LOGGER.warn("BarState schema version {} is newer than supported {} - loading as empty",
                    version, BarState.CURRENT_SCHEMA_VERSION);
            return BarState.empty(1);
        }

        int slotCount = resolveSlotCount(tag);

        List<SlotAssignment> assignmentList = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            String raw = tag.getString(KEY_SLOT_PREFIX + i);
            if (raw.isBlank()) {
                assignmentList.add(SlotAssignment.empty());
                continue;
            }
            try {
                assignmentList.add(new SlotAssignment(ResourceLocation.parse(raw)));
            } catch (Exception e) {
                LOGGER.warn("BarState slot {}: unparseable ability id '{}' - leaving slot empty", i, raw);
                assignmentList.add(SlotAssignment.empty());
            }
        }

        int selectedSlot = tag.contains(KEY_SELECTED) ? tag.getInt(KEY_SELECTED) : 0;
        if (selectedSlot < 0 || selectedSlot >= slotCount) {
            selectedSlot = 0;
        }

        return new BarState(assignmentList.toArray(new SlotAssignment[0]), selectedSlot,
                BarState.CURRENT_SCHEMA_VERSION);
    }

    /**
     * The number of slots to read: the explicitly stored count, or - for data
     * written before the count was tracked - the highest assigned slot index
     * plus one. Defaults to a single slot for a fully empty legacy state.
     */
    private static int resolveSlotCount(CompoundTag tag) {
        if (tag.contains(KEY_SLOT_COUNT)) {
            return Math.max(1, tag.getInt(KEY_SLOT_COUNT));
        }
        int highest = -1;
        for (String key : tag.getAllKeys()) {
            if (!key.startsWith(KEY_SLOT_PREFIX)) {
                continue;
            }
            try {
                highest = Math.max(highest, Integer.parseInt(key.substring(KEY_SLOT_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                // not a slot index key
            }
        }
        // Clamp to at least one slot: a fully empty legacy compound must load as a
        // valid single-slot empty state, not a zero-length bar that the BarState
        // invariant rejects (ADR-004: corrupt/empty data fails closed, never crashes).
        return Math.max(1, highest + 1);
    }

    @Override
    public CompoundTag write(BarState state, HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_SCHEMA, BarState.CURRENT_SCHEMA_VERSION);
        tag.putInt(KEY_SLOT_COUNT, state.assignments().length);
        tag.putInt(KEY_SELECTED, state.selectedSlot());
        for (int i = 0; i < state.assignments().length; i++) {
            SlotAssignment assignment = state.assignments()[i];
            if (assignment.isAssigned()) {
                tag.putString(KEY_SLOT_PREFIX + i, assignment.abilityId().toString());
            }
        }
        return tag;
    }
}
