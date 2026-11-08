package org.bitwisemadness.abilityhotbar.state;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The per-player ability bar state (REQ-HOT-14/22, ADR-011/012).
 *
 * <p>Schema v3 holds N {@link Bar}s - each with a stable id, its own slot set
 * and its own selected slot - plus the <b>active bar id</b>. The single-bar
 * accessors ({@link #assignments()}, {@link #selectedSlot()},
 * {@link #assignment(int)}, {@link #withAssignment(int, ResourceLocation)},
 * {@link #withSelectedSlot(int)}) operate on the <b>active</b> bar, so every
 * existing consumer keeps working unchanged.</p>
 *
 * <p>The active id may name a bar this state does not store - a registered
 * non-assignable bar (e.g. Pseudocore curios) carries no attachment state
 * because its content belongs to the registering mod (ADR-012). The views then
 * <b>fail closed</b>: empty slots, no selection, mutations are no-ops. Inert
 * preservation extends to identity: a stored id whose registration is gone is
 * kept, never remapped onto a later registrant.</p>
 *
 * @param bars          the bars; index = storage position, never empty
 * @param activeBarId   id of the active bar (server-owned; may be unregistered or
 *                      registered-non-assignable, in which case the views fail closed)
 * @param schemaVersion format version for forward migration
 * @param presets       named per-player loadout snapshots (hotbar#29, v4)
 */
public record BarState(Bar[] bars, ResourceLocation activeBarId, int schemaVersion,
        LoadoutPreset[] presets) {

    public static final int CURRENT_SCHEMA_VERSION = 4;

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Codec<List<Bar>> BARS_CODEC = Bar.CODEC.listOf();

    public static final Codec<BarState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BARS_CODEC.fieldOf("bars").forGetter(BarState::barsList),
            ResourceLocation.CODEC.fieldOf("active_bar").forGetter(BarState::activeBarId),
            Codec.INT.optionalFieldOf("schema_version", CURRENT_SCHEMA_VERSION)
                    .forGetter(BarState::schemaVersion),
            LoadoutPreset.CODEC.listOf().optionalFieldOf("presets", java.util.List.of())
                    .forGetter(BarState::presetsList)
    ).apply(instance, BarState::fromParts));

    /** Network codec for snapshot/delta sync (ADR-003). */
    public static final StreamCodec<ByteBuf, BarState> STREAM_CODEC = StreamCodec.composite(
            Bar.STREAM_CODEC.apply(ByteBufCodecs.list()), BarState::barsList,
            ResourceLocation.STREAM_CODEC, BarState::activeBarId,
            ByteBufCodecs.VAR_INT, BarState::schemaVersion,
            LoadoutPreset.STREAM_CODEC.apply(ByteBufCodecs.list()), BarState::presetsList,
            BarState::fromParts
    );

    /** Codec adapter: List -> array. */
    private static List<Bar> barsList(BarState state) {
        return Arrays.asList(state.bars());
    }

    /** Codec adapter: array -> List. */
    private static List<LoadoutPreset> presetsList(BarState state) {
        return Arrays.asList(state.presets());
    }

    /** Codec adapter: List -> BarState. */
    private static BarState fromParts(List<Bar> bars, ResourceLocation activeBarId,
            int schemaVersion, List<LoadoutPreset> presets) {
        return new BarState(bars.toArray(new Bar[0]), activeBarId, schemaVersion,
                presets.toArray(new LoadoutPreset[0]));
    }

    public BarState {
        if (bars == null || bars.length == 0) {
            throw new IllegalArgumentException("bars must contain at least one bar");
        }
        if (activeBarId == null) {
            throw new IllegalArgumentException("activeBarId must not be null");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1, got " + schemaVersion);
        }
        if (presets == null) {
            throw new IllegalArgumentException("presets must not be null");
        }
        for (int i = 0; i < bars.length; i++) {
            if (bars[i] == null) {
                throw new IllegalArgumentException("bars[" + i + "] must not be null");
            }
        }
        bars = bars.clone();
        presets = presets.clone();
    }

    /**
     * Defensive copy on every access: the array is mutable, so the record must
     * not hand out its internal one (the {@link Bar} elements are immutable).
     */
    @Override
    public Bar[] bars() {
        return bars.clone();
    }

    /** @return the storage index of the active bar, or -1 when it is not stored */
    private int indexOf(ResourceLocation id) {
        for (int i = 0; i < bars.length; i++) {
            if (bars[i].id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The active bar's slot assignments - empty when the active bar is not
     * stored (a registered non-assignable bar), so consumers fail closed.
     */
    public SlotAssignment[] assignments() {
        int index = indexOf(activeBarId);
        return index >= 0 ? bars[index].assignments() : new SlotAssignment[0];
    }

    /**
     * The active bar's selected slot - 0 when the active bar is not stored
     * (consumers pair this with the empty {@link #assignments()}, so no
     * downstream path can act on the phantom selection).
     */
    public int selectedSlot() {
        int index = indexOf(activeBarId);
        return index >= 0 ? bars[index].selectedSlot() : 0;
    }

    /**
     * @param slot the slot index within the active bar
     * @return the assignment, or an empty sentinel when out of range or when
     *         the active bar is not stored
     */
    @Nullable
    public SlotAssignment assignment(int slot) {
        int index = indexOf(activeBarId);
        return index >= 0 ? bars[index].assignment(slot) : SlotAssignment.empty();
    }

    /**
     * Sets a slot on the <b>active</b> bar.
     *
     * @return this state with the active bar updated; an out-of-range slot or
     *         an unstored active bar is a no-op (the caller's bounds check is
     *         the fail-closed gate)
     */
    public BarState withAssignment(int slot, @Nullable ResourceLocation abilityId) {
        int index = indexOf(activeBarId);
        if (index < 0) {
            return this;
        }
        return withBar(index, bars[index].withAssignment(slot, abilityId));
    }

    /**
     * Sets a slot on a <b>named</b> bar - the menu's path, which targets any
     * assignable bar rather than only the active one (ADR-012 id targeting,
     * hotbar#27). The caller validates the bar's existence, assignability and
     * slot range; those failures are a no-op here.
     *
     * @param barId       the bar to write into
     * @param slot        the target slot index
     * @param abilityId   the ability to store, or null to clear the slot
     * @return this state with that bar updated; an unstored bar is a no-op
     */
    public BarState withAssignment(ResourceLocation barId, int slot,
            @Nullable ResourceLocation abilityId) {
        int index = indexOf(barId);
        if (index < 0) {
            return this;
        }
        return withBar(index, bars[index].withAssignment(slot, abilityId));
    }

    /**
     * Selects a slot on the <b>active</b> bar; every other bar keeps its own
     * selection.
     *
     * @return this state with the active bar's selection updated
     * @throws IllegalArgumentException when the slot is out of the active bar's
     *                                  range, or the active bar is not stored
     */
    public BarState withSelectedSlot(int slot) {
        int index = indexOf(activeBarId);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "active bar " + activeBarId + " is not stored in this state");
        }
        return withBar(index, bars[index].withSelectedSlot(slot));
    }

    /**
     * Makes another bar active. Every single-bar accessor then reads and writes
     * that bar instead; no slot data moves between bars.
     *
     * <p>No storage or registry validation here: the server validates switch
     * targets against its own registry and ceiling at the payload boundary
     * (ADR-012), and a registered non-assignable bar is a legitimate active id
     * this state does not store.</p>
     *
     * @param barId the id of the bar to activate
     * @return this state with a different active bar
     * @throws IllegalArgumentException when the id is null
     */
    public BarState withActiveBar(ResourceLocation barId) {
        if (barId == null) {
            throw new IllegalArgumentException("activeBarId must not be null");
        }
        return new BarState(bars, barId, schemaVersion, presets);
    }

    /** Replaces one bar in storage, keeping position, active id and version. */
    private BarState withBar(int index, Bar next) {
        Bar[] copy = bars.clone();
        copy[index] = next;
        return new BarState(copy, activeBarId, schemaVersion, presets);
    }

    /** Defensive copy on access - the array is mutable, the record must not hand it out. */
    @Override
    public LoadoutPreset[] presets() {
        return presets.clone();
    }

    /** The named preset, or null (the caller maps an unknown name to a refusal). */
    @Nullable
    public LoadoutPreset presetNamed(String name) {
        for (LoadoutPreset preset : presets) {
            if (preset.name().equals(name)) {
                return preset;
            }
        }
        return null;
    }

    /** The stored assignment row of a named bar, or null when it is not stored. */
    @Nullable
    public SlotAssignment[] assignmentsOf(ResourceLocation barId) {
        int index = indexOf(barId);
        return index < 0 ? null : bars[index].assignments();
    }

    /**
     * The state with only its preset list replaced (hotbar#29's save/delete)
     * - bars, selection and active id untouched.
     */
    public BarState withPresets(LoadoutPreset[] next) {
        if (next == null) {
            throw new IllegalArgumentException("presets must not be null");
        }
        return new BarState(bars, activeBarId, schemaVersion, next);
    }

    /**
     * The state with a <b>stored</b> bar's whole assignment row replaced
     * (preset recall's write); an unstored bar or null row is a no-op (the
     * caller validated assignability and slot shape first).
     */
    public BarState withRow(ResourceLocation barId, SlotAssignment[] row) {
        int index = indexOf(barId);
        if (index < 0 || row == null) {
            return this;
        }
        Bar[] copy = bars.clone();
        copy[index] = new Bar(copy[index].id(), row, copy[index].selectedSlot());
        return new BarState(copy, activeBarId, schemaVersion, presets);
    }

    /**
     * Sets one slot's mode on the <b>active</b> bar (the ability-mode
     * keybind's server-side echo, hotbar#29 + tensura-iss-integration#51):
     * the pairing keeps its ability; an unstored active bar or an
     * out-of-range/unassigned slot is a no-op.
     */
    public BarState withSlotMode(int slot, String mode) {
        int index = indexOf(activeBarId);
        if (index < 0) {
            return this;
        }
        return withBar(index, bars[index].withMode(slot, mode));
    }

    /**
     * Sets one slot's mode on a <b>named stored bar</b> (the menu's row
     * pill cycles the bar its tab shows, which may not be the active one -
     * play-test round): the pairing keeps its ability; an unstored bar or
     * an out-of-range/unassigned slot is a no-op.
     */
    public BarState withSlotMode(ResourceLocation barId, int slot, String mode) {
        int index = indexOf(barId);
        if (index < 0) {
            return this;
        }
        return withBar(index, bars[index].withMode(slot, mode));
    }

    /**
     * Reconciles this state with the live hotbar registry (ADR-012): appends a
     * fresh empty bar for every <b>assignable</b> registration not yet stored
     * (sized like this state's own first bar - the serializer reads no config),
     * and re-anchors an active id that is neither stored nor registered to the
     * first stored bar, with a log line.
     *
     * <p>Never removes anything: a stored bar without a live registration is
     * inert-preserved (kept, not cyclable, not remappable), and a registered
     * non-assignable active id is kept even though it has no stored state.</p>
     *
     * @param registrations the registry snapshot, typically {@link HotbarRegistry#all()}
     * @return this state when nothing changed, otherwise the reconciled state
     * @throws IllegalArgumentException when registrations is null
     */
    public BarState reconciledWith(List<HotbarRegistration> registrations) {
        if (registrations == null) {
            throw new IllegalArgumentException("registrations must not be null");
        }
        List<Bar> grown = appendedMissingBars(registrations);
        ResourceLocation active = reanchoredActive(registrations);
        if (grown == null && active.equals(activeBarId)) {
            return this;
        }
        return new BarState(grown != null ? grown.toArray(new Bar[0]) : bars,
                active, schemaVersion, presets);
    }

    /** Appends a fresh empty bar for each assignable registration not yet stored. */
    private List<Bar> appendedMissingBars(List<HotbarRegistration> registrations) {
        List<Bar> grown = null;
        for (HotbarRegistration registration : registrations) {
            if (!registration.assignable() || indexOf(registration.id()) >= 0) {
                continue;
            }
            if (grown == null) {
                grown = new ArrayList<>(Arrays.asList(bars));
            }
            grown.add(Bar.empty(registration.id(), bars[0].slotCount()));
            LOGGER.info("BarState appended registered bar {}", registration.id());
        }
        return grown;
    }

    /** The active id, re-anchored to the first stored bar when it names nothing known. */
    private ResourceLocation reanchoredActive(List<HotbarRegistration> registrations) {
        if (indexOf(activeBarId) >= 0
                || registrations.stream().anyMatch(r -> r.id().equals(activeBarId))) {
            return activeBarId;
        }
        LOGGER.warn("BarState active bar {} is neither stored nor registered"
                + " - re-anchoring to {}", activeBarId, bars[0].id());
        return bars[0].id();
    }

    /** A single-bar state with the given slot count; built-in bar 0 is active. */
    public static BarState empty(int slotCount) {
        return empty(slotCount, 1);
    }

    /** A state with {@code barCount} empty built-in bars of {@code slotCount} slots each. */
    public static BarState empty(int slotCount, int barCount) {
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be >= 1, got " + barCount);
        }
        Bar[] bars = new Bar[barCount];
        for (int i = 0; i < barCount; i++) {
            bars[i] = Bar.empty(HotbarRegistry.builtinBarId(i), slotCount);
        }
        return new BarState(bars, HotbarRegistry.builtinBarId(0), CURRENT_SCHEMA_VERSION,
                new LoadoutPreset[0]);
    }
}
