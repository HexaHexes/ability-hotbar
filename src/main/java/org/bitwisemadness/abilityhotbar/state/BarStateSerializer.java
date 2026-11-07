package org.bitwisemadness.abilityhotbar.state;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.slf4j.Logger;

/**
 * Serialises {@link BarState} to/from player NBT with a forward-only
 * schema-version upgrade ladder (ADR-004, ADR-011/012).
 *
 * <p>Schema v4 layout: {@code schema_version}, {@code active_bar} (the active
 * bar's <b>id</b>, not an index) and {@code bar_count} at the root, with each
 * bar in its own {@code bar_N} compound carrying that bar's {@code bar_id},
 * {@code slot_count}, {@code selected_slot} and {@code slot_M} keys (plus the
 * optional per-slot {@code mode_M} added by v4, hotbar#29). v4 also stores the
 * root {@code preset_count} and {@code preset_N} compounds (name, bar_id,
 * slot row) for named loadout presets. Upgrades are key-absence based: data
 * written before v4 simply has no mode or preset keys, so it loads with
 * modeless slots and an empty preset list under the generic upgrade log -
 * no per-version value conversion.</p>
 *
 * <p>Data-preservation rules:</p>
 * <ul>
 *   <li>Identity is inert: a stored {@code bar_id} whose registration is gone
 *       is kept, never remapped onto a later registrant. A v3 compound with a
 *       missing or unparseable {@code bar_id} keeps its data under
 *       {@link HotbarRegistry#unidentifiedBarId(int)} - preserved but never
 *       wrongly adopted (ADR-012).</li>
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
 *       an unparseable {@code active_bar} falls back to the first stored bar
 *       with a log line.</li>
 *   <li>An older schema version upgrades in memory on load - v0/v1 flat data
 *       becomes built-in bar 0, v2's positional bars gain built-in ids and its
 *       integer active bar becomes an id - and the upgrade is logged with its
 *       from/to versions; the file is rewritten at the current version on the
 *       next natural save (REQ-HOT-15).</li>
 *   <li>Every load reconciles the result with the live registry
 *       ({@link BarState#reconciledWith}): missing registered-assignable bars
 *       are appended, an active id that is neither stored nor registered
 *       re-anchors to the first stored bar (ADR-012).</li>
 * </ul>
 *
 * <p>This class deliberately does not read config: it must be usable from the
 * attachment plumbing before/without a loaded config, and the attachment
 * default supplier already applies the server ceilings (slots per bar, bar
 * count). It does read the hotbar registry - registrations are code, fixed
 * before any world loads, not configuration.</p>
 */
public final class BarStateSerializer implements IAttachmentSerializer<CompoundTag, BarState> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String KEY_SCHEMA = "schema_version";
    private static final String KEY_ACTIVE_BAR = "active_bar";
    private static final String KEY_BAR_COUNT = "bar_count";
    private static final String KEY_BAR_PREFIX = "bar_";
    private static final String KEY_BAR_ID = "bar_id";
    private static final String KEY_SLOT_COUNT = "slot_count";
    private static final String KEY_SELECTED = "selected_slot";
    private static final String KEY_SLOT_PREFIX = "slot_";
    private static final String KEY_MODE_PREFIX = "mode_";
    private static final String KEY_PRESET_COUNT = "preset_count";
    private static final String KEY_PRESET_PREFIX = "preset_";
    private static final String KEY_NAME = "name";

    @Override
    public BarState read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
        // Absent schema_version = data written before versioning existed = schema 0,
        // so the load below counts as an upgrade and is logged as one (REQ-HOT-15).
        int version = tag.contains(KEY_SCHEMA) ? tag.getInt(KEY_SCHEMA) : 0;
        if (version > BarState.CURRENT_SCHEMA_VERSION) {
            LOGGER.warn("BarState schema version {} is newer than supported {} - loading as empty",
                    version, BarState.CURRENT_SCHEMA_VERSION);
            return BarState.empty(1).reconciledWith(HotbarRegistry.all());
        }

        BarState state;
        if (version <= 1) {
            // v0 and v1 share the flat single-bar layout (v1 only added the
            // explicit count keys). Registration did not exist then, so the
            // single bar is built-in bar 0.
            state = readFlat(tag);
        } else if (version == 2) {
            // v2 had nested bars but an integer active bar and no bar_id keys;
            // every v2 bar was a built-in, so positional ids are correct.
            state = readVersion2(tag);
        } else {
            state = readVersion3(tag);
        }

        if (version < BarState.CURRENT_SCHEMA_VERSION) {
            LOGGER.info("BarState schema upgraded from {} to {} - values retained",
                    version, BarState.CURRENT_SCHEMA_VERSION);
        }

        return state.reconciledWith(HotbarRegistry.all());
    }

    /** Reads the pre-v2 flat layout as built-in bar 0. */
    private static BarState readFlat(CompoundTag tag) {
        Bar bar = readBar(tag, HotbarRegistry.builtinBarId(0));
        return new BarState(new Bar[]{bar}, bar.id(), BarState.CURRENT_SCHEMA_VERSION,
                readPresets(tag));
    }

    /** Reads the schema-v2 layout: nested bars, integer active bar, no bar_id. */
    private static BarState readVersion2(CompoundTag tag) {
        int barCount = resolveBarCount(tag);
        Bar[] bars = new Bar[barCount];
        for (int i = 0; i < barCount; i++) {
            // v2 bars were all built-ins, so position i is ability_hotbar:bar_i.
            // A missing bar_N reads as an empty compound -> an empty single-slot
            // bar: corrupt/partial data fails closed, never crashes (ADR-004).
            bars[i] = readBar(tag.getCompound(KEY_BAR_PREFIX + i),
                    HotbarRegistry.builtinBarId(i));
        }

        int activeIndex = tag.contains(KEY_ACTIVE_BAR) ? tag.getInt(KEY_ACTIVE_BAR) : 0;
        if (activeIndex < 0 || activeIndex >= barCount) {
            LOGGER.warn("BarState active bar {} out of range for {} bars - using bar 0",
                    activeIndex, barCount);
            activeIndex = 0;
        }

        return new BarState(bars, bars[activeIndex].id(), BarState.CURRENT_SCHEMA_VERSION,
                readPresets(tag));
    }

    /** Reads the schema-v3 layout: per-bar {@code bar_id} and an id active bar. */
    private static BarState readVersion3(CompoundTag tag) {
        int barCount = resolveBarCount(tag);
        Bar[] bars = new Bar[barCount];
        for (int i = 0; i < barCount; i++) {
            CompoundTag barTag = tag.getCompound(KEY_BAR_PREFIX + i);
            bars[i] = readBar(barTag, readBarId(barTag, i));
        }
        return new BarState(bars, readActiveId(tag, bars), BarState.CURRENT_SCHEMA_VERSION,
                readPresets(tag));
    }

    /**
     * Reads the v4 preset list from the root (hotbar#29); pre-v4 data has no
     * preset keys and loads an empty list - the generic upgrade log covers it.
     * A malformed preset is skipped with a log line (corrupt data fails
     * closed, never crashes the load - ADR-004).
     */
    private static LoadoutPreset[] readPresets(CompoundTag tag) {
        if (!tag.contains(KEY_PRESET_COUNT)) {
            return new LoadoutPreset[0];
        }
        int count = Math.max(0, tag.getInt(KEY_PRESET_COUNT));
        List<LoadoutPreset> presets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CompoundTag presetTag = tag.getCompound(KEY_PRESET_PREFIX + i);
            String name = presetTag.getString(KEY_NAME);
            try {
                ResourceLocation barId = ResourceLocation.parse(presetTag.getString(KEY_BAR_ID));
                SlotAssignment[] row = readRow(presetTag, resolveSlotCount(presetTag),
                        "preset " + name);
                presets.add(new LoadoutPreset(name, barId, row));
            } catch (Exception e) {
                LOGGER.warn("BarState preset {} ({}) unreadable - skipped: {}", i, name, e.toString());
            }
        }
        return presets.toArray(new LoadoutPreset[0]);
    }

    /**
     * Resolves one bar's identity from its {@code bar_id}: parsed when valid,
     * otherwise {@link HotbarRegistry#unidentifiedBarId(int)} so the data is
     * preserved under an identity that can collide with neither a built-in nor
     * a registration (ADR-012 inert-by-identity).
     *
     * @param barTag   the compound holding this bar's keys
     * @param barIndex the bar's storage position, for the unidentified fallback
     */
    private static ResourceLocation readBarId(CompoundTag barTag, int barIndex) {
        String raw = barTag.getString(KEY_BAR_ID);
        if (raw.isBlank()) {
            LOGGER.warn("BarState bar {} has no bar_id - keeping its data as unidentified",
                    barIndex);
            return HotbarRegistry.unidentifiedBarId(barIndex);
        }
        try {
            return ResourceLocation.parse(raw);
        } catch (Exception e) {
            LOGGER.warn("BarState bar {} has an unparseable bar_id '{}' - keeping its data as unidentified",
                    barIndex, raw);
            return HotbarRegistry.unidentifiedBarId(barIndex);
        }
    }

    /**
     * Resolves the active bar id from a v3 tag. An id that parses but names
     * nothing this state knows is left alone here - {@link BarState#reconciledWith}
     * re-anchors it (with its own log line) once the registry is consulted.
     */
    private static ResourceLocation readActiveId(CompoundTag tag, Bar[] bars) {
        String raw = tag.getString(KEY_ACTIVE_BAR);
        if (raw.isBlank()) {
            LOGGER.warn("BarState active bar id is missing - using the first stored bar");
            return bars[0].id();
        }
        try {
            return ResourceLocation.parse(raw);
        } catch (Exception e) {
            LOGGER.warn("BarState active bar id '{}' is not valid - using the first stored bar",
                    raw);
            return bars[0].id();
        }
    }

    /**
     * Reads one bar's slot keys from the given compound. The pre-v2 flat root
     * is itself such a compound, so v0/v1 data flows through the same reader as
     * a v2/v3 {@code bar_N}.
     *
     * @param barTag the compound holding this bar's keys
     * @param barId  the bar's resolved identity
     */
    private static Bar readBar(CompoundTag barTag, ResourceLocation barId) {
        SlotAssignment[] row = readRow(barTag, resolveSlotCount(barTag), "bar " + barId);
        int slotCount = row.length;

        int selectedSlot = barTag.contains(KEY_SELECTED) ? barTag.getInt(KEY_SELECTED) : 0;
        if (selectedSlot < 0 || selectedSlot >= slotCount) {
            selectedSlot = 0;
        }

        return new Bar(barId, row, selectedSlot);
    }

    /**
     * Reads a {@code slot_i} (+ v4 {@code mode_i}) row - bars and presets
     * share the layout; a missing mode key is pre-v4 data (mode = null).
     *
     * @param tag       the compound holding the row's keys
     * @param slotCount how many slots to read
     * @param where     log context naming the owner (bar/preset)
     */
    private static SlotAssignment[] readRow(CompoundTag tag, int slotCount, String where) {
        List<SlotAssignment> assignmentList = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            String raw = tag.getString(KEY_SLOT_PREFIX + i);
            if (raw.isBlank()) {
                assignmentList.add(SlotAssignment.empty());
                continue;
            }
            try {
                String mode = tag.getString(KEY_MODE_PREFIX + i);
                assignmentList.add(new SlotAssignment(ResourceLocation.parse(raw),
                        mode.isBlank() ? null : mode));
            } catch (Exception e) {
                LOGGER.warn("BarState {} slot {}: unparseable ability id '{}' - leaving slot empty",
                        where, i, raw);
                assignmentList.add(SlotAssignment.empty());
            }
        }
        return assignmentList.toArray(new SlotAssignment[0]);
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
        tag.putString(KEY_ACTIVE_BAR, state.activeBarId().toString());

        Bar[] bars = state.bars();
        tag.putInt(KEY_BAR_COUNT, bars.length);
        for (int i = 0; i < bars.length; i++) {
            Bar bar = bars[i];
            CompoundTag barTag = new CompoundTag();
            barTag.putString(KEY_BAR_ID, bar.id().toString());
            barTag.putInt(KEY_SELECTED, bar.selectedSlot());
            writeRow(barTag, bar.assignments());
            tag.put(KEY_BAR_PREFIX + i, barTag);
        }

        LoadoutPreset[] presets = state.presets();
        tag.putInt(KEY_PRESET_COUNT, presets.length);
        for (int i = 0; i < presets.length; i++) {
            CompoundTag presetTag = new CompoundTag();
            presetTag.putString(KEY_NAME, presets[i].name());
            presetTag.putString(KEY_BAR_ID, presets[i].barId().toString());
            writeRow(presetTag, presets[i].assignments());
            tag.put(KEY_PRESET_PREFIX + i, presetTag);
        }
        return tag;
    }

    /**
     * Writes a row's {@code slot_i} (+ v4 {@code mode_i}) keys - bars and
     * presets share the layout. Absent keys mean empty/none, exactly as all
     * pre-v4 readers expect.
     */
    private static void writeRow(CompoundTag parent, SlotAssignment[] row) {
        parent.putInt(KEY_SLOT_COUNT, row.length);
        for (int s = 0; s < row.length; s++) {
            ResourceLocation id = row[s].abilityId();
            if (id != null) {
                parent.putString(KEY_SLOT_PREFIX + s, id.toString());
            }
            String mode = row[s].mode();
            if (mode != null) {
                parent.putString(KEY_MODE_PREFIX + s, mode);
            }
        }
    }
}
