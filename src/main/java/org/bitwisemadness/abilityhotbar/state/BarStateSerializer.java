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
 * schema-version upgrade ladder (ADR-004, ADR-011).
 *
 * <p>Schema v2 layout: {@code schema_version}, {@code active_bar} and
 * {@code bar_count} at the root, with each bar in its own {@code bar_N}
 * compound holding that bar's {@code slot_count}, {@code selected_slot} and
 * {@code slot_M} keys.</p>
 *
 * <p>Data-preservation rules:</p>
 * <ul>
 *   <li>The saved slot count is written explicitly per bar and read back, so a
 *       temporary config change never truncates stored data. When the count is
 *       absent (pre-v1 data) it is inferred from the highest slot key present.
 *       The bar count is written and inferred the same way, so a saved bar
 *       beyond the current {@code hotbar.maxBars} ceiling is preserved, never
 *       truncated (inert-preservation, ADR-011).</li>
 *   <li>Assignments whose ids cannot be parsed are logged (naming the bar and
 *       slot) and left empty rather than crashing the load; valid-but-unresolvable
 *       ids are preserved raw so the slot renders as diagnosed inert (REQ-HOT-21).</li>
 *   <li>A future schema version loads a valid empty state instead of throwing;
 *       an out-of-range {@code active_bar} falls back to bar 0 with a log line.</li>
 *   <li>An older schema version upgrades in memory on load - v0/v1 flat data
 *       becomes bar 0 with {@code active_bar = 0} - and the upgrade is logged
 *       with its from/to versions; the file is rewritten at the current version
 *       on the next natural save (REQ-HOT-15).</li>
 * </ul>
 *
 * <p>This class deliberately does not read config: it must be usable from the
 * attachment plumbing before/without a loaded config, and the attachment
 * default supplier already applies the server ceilings (slots per bar, bar
 * count).</p>
 */
public final class BarStateSerializer implements IAttachmentSerializer<CompoundTag, BarState> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String KEY_SCHEMA = "schema_version";
    private static final String KEY_ACTIVE_BAR = "active_bar";
    private static final String KEY_BAR_COUNT = "bar_count";
    private static final String KEY_BAR_PREFIX = "bar_";
    private static final String KEY_SLOT_COUNT = "slot_count";
    private static final String KEY_SELECTED = "selected_slot";
    private static final String KEY_SLOT_PREFIX = "slot_";

    @Override
    public BarState read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
        // Absent schema_version = data written before versioning existed = schema 0,
        // so the load below counts as an upgrade and is logged as one (REQ-HOT-15).
        int version = tag.contains(KEY_SCHEMA) ? tag.getInt(KEY_SCHEMA) : 0;
        if (version > BarState.CURRENT_SCHEMA_VERSION) {
            LOGGER.warn("BarState schema version {} is newer than supported {} - loading as empty",
                    version, BarState.CURRENT_SCHEMA_VERSION);
            return BarState.empty(1);
        }

        BarState state;
        if (version <= 1) {
            // v0 and v1 share the flat single-bar layout (v1 only added the
            // explicit count keys) -> migrate it to bar 0 of the v2 shape.
            state = new BarState(new Bar[]{readBar(tag, 0)}, 0,
                    BarState.CURRENT_SCHEMA_VERSION);
        } else {
            state = readVersionedBars(tag);
        }

        if (version < BarState.CURRENT_SCHEMA_VERSION) {
            LOGGER.info("BarState schema upgraded from {} to {} - values retained",
                    version, BarState.CURRENT_SCHEMA_VERSION);
        }

        return state;
    }

    /** Reads the schema-v2 layout: root bar keys plus one compound per bar. */
    private static BarState readVersionedBars(CompoundTag tag) {
        int barCount = resolveBarCount(tag);
        Bar[] bars = new Bar[barCount];
        for (int i = 0; i < barCount; i++) {
            // A missing bar_N reads as an empty compound -> an empty single-slot
            // bar: corrupt/partial data fails closed, never crashes (ADR-004).
            bars[i] = readBar(tag.getCompound(KEY_BAR_PREFIX + i), i);
        }

        int activeBar = tag.contains(KEY_ACTIVE_BAR) ? tag.getInt(KEY_ACTIVE_BAR) : 0;
        if (activeBar < 0 || activeBar >= barCount) {
            LOGGER.warn("BarState active bar {} out of range for {} bars - using bar 0",
                    activeBar, barCount);
            activeBar = 0;
        }

        return new BarState(bars, activeBar, BarState.CURRENT_SCHEMA_VERSION);
    }

    /**
     * Reads one bar's keys from the given compound. The pre-v2 flat root is
     * itself such a compound, so v0/v1 data flows through the same reader as a
     * v2 {@code bar_N}.
     *
     * @param barTag   the compound holding this bar's keys
     * @param barIndex the bar number, for diagnostic messages
     */
    private static Bar readBar(CompoundTag barTag, int barIndex) {
        int slotCount = resolveSlotCount(barTag);

        List<SlotAssignment> assignmentList = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            String raw = barTag.getString(KEY_SLOT_PREFIX + i);
            if (raw.isBlank()) {
                assignmentList.add(SlotAssignment.empty());
                continue;
            }
            try {
                assignmentList.add(new SlotAssignment(ResourceLocation.parse(raw)));
            } catch (Exception e) {
                LOGGER.warn("BarState bar {} slot {}: unparseable ability id '{}' - leaving slot empty",
                        barIndex, i, raw);
                assignmentList.add(SlotAssignment.empty());
            }
        }

        int selectedSlot = barTag.contains(KEY_SELECTED) ? barTag.getInt(KEY_SELECTED) : 0;
        if (selectedSlot < 0 || selectedSlot >= slotCount) {
            selectedSlot = 0;
        }

        return new Bar(assignmentList.toArray(new SlotAssignment[0]), selectedSlot);
    }

    /**
     * The number of slots to read: the explicitly stored count, or - for data
     * written before the count was tracked - the highest assigned slot index
     * plus one. Defaults to a single slot for a fully empty compound.
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
        // Clamp to at least one slot: a fully empty compound must load as a
        // valid single-slot empty bar, not a zero-length bar that the Bar
        // invariant rejects (ADR-004: corrupt/empty data fails closed, never crashes).
        return Math.max(1, highest + 1);
    }

    /**
     * The number of bars to read: the explicitly stored count, or - for data
     * written without one - the highest {@code bar_N} key plus one. At least
     * one bar always loads.
     */
    private static int resolveBarCount(CompoundTag tag) {
        if (tag.contains(KEY_BAR_COUNT)) {
            return Math.max(1, tag.getInt(KEY_BAR_COUNT));
        }
        int highest = -1;
        for (String key : tag.getAllKeys()) {
            if (!key.startsWith(KEY_BAR_PREFIX)) {
                continue;
            }
            try {
                highest = Math.max(highest, Integer.parseInt(key.substring(KEY_BAR_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                // not a bar index key (e.g. a stray "bar_count" spelling)
            }
        }
        return Math.max(1, highest + 1);
    }

    @Override
    public CompoundTag write(BarState state, HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_SCHEMA, BarState.CURRENT_SCHEMA_VERSION);
        tag.putInt(KEY_ACTIVE_BAR, state.activeBar());

        Bar[] bars = state.bars();
        tag.putInt(KEY_BAR_COUNT, bars.length);
        for (int i = 0; i < bars.length; i++) {
            CompoundTag barTag = new CompoundTag();
            SlotAssignment[] slots = bars[i].assignments();
            barTag.putInt(KEY_SLOT_COUNT, slots.length);
            barTag.putInt(KEY_SELECTED, bars[i].selectedSlot());
            for (int s = 0; s < slots.length; s++) {
                ResourceLocation id = slots[s].abilityId();
                if (id != null) {
                    barTag.putString(KEY_SLOT_PREFIX + s, id.toString());
                }
            }
            tag.put(KEY_BAR_PREFIX + i, barTag);
        }
        return tag;
    }
}
