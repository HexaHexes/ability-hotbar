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
 * The per-player ability bar state (REQ-HOT-14, REQ-HOT-22, ADR-011).
 *
 * <p>Schema v2 holds N {@link Bar}s - each with its own slot set and its own
 * selected slot - plus the server-owned active bar. The single-bar accessors
 * ({@link #assignments()}, {@link #selectedSlot()}, {@link #assignment(int)},
 * {@link #withAssignment(int, ResourceLocation)},
 * {@link #withSelectedSlot(int)}) operate on the <b>active</b> bar, so every
 * existing consumer keeps working unchanged while only bar 0 is ever active
 * (the switch flow lands with REQ-HOT-22's later slices).</p>
 *
 * <p>Inert preservation applies per bar: a saved bar beyond the current
 * {@code hotbar.maxBars} ceiling is kept, never truncated (ADR-011).</p>
 *
 * @param bars          the bars; index = bar number, never empty
 * @param activeBar     index of the bar the single-bar accessors operate on (server-owned)
 * @param schemaVersion format version for forward migration
 */
public record BarState(Bar[] bars, int activeBar, int schemaVersion) {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    private static final Codec<List<Bar>> BARS_CODEC = Bar.CODEC.listOf();

    public static final Codec<BarState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BARS_CODEC.fieldOf("bars").forGetter(BarState::barsList),
            Codec.INT.fieldOf("active_bar").forGetter(BarState::activeBar),
            Codec.INT.optionalFieldOf("schema_version", CURRENT_SCHEMA_VERSION)
                    .forGetter(BarState::schemaVersion)
    ).apply(instance, BarState::fromBars));

    /** Network codec for snapshot/delta sync (ADR-003). */
    public static final StreamCodec<ByteBuf, BarState> STREAM_CODEC = StreamCodec.composite(
            Bar.STREAM_CODEC.apply(ByteBufCodecs.list()), BarState::barsList,
            ByteBufCodecs.VAR_INT, BarState::activeBar,
            ByteBufCodecs.VAR_INT, BarState::schemaVersion,
            BarState::fromBars
    );

    /** Codec adapter: List -> array. */
    private static List<Bar> barsList(BarState state) {
        return Arrays.asList(state.bars());
    }

    /** Codec adapter: List -> BarState. */
    private static BarState fromBars(List<Bar> bars, int activeBar, int schemaVersion) {
        return new BarState(bars.toArray(new Bar[0]), activeBar, schemaVersion);
    }

    public BarState {
        if (bars == null) {
            throw new IllegalArgumentException("bars must not be null");
        }
        if (activeBar < 0 || activeBar >= bars.length) {
            throw new IllegalArgumentException(
                    "activeBar must be in [0, " + bars.length + "), got " + activeBar);
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1, got " + schemaVersion);
        }
        for (int i = 0; i < bars.length; i++) {
            if (bars[i] == null) {
                throw new IllegalArgumentException("bars[" + i + "] must not be null");
            }
        }
        bars = bars.clone();
    }

    /**
     * Defensive copy on every access: the array is mutable, so the record must
     * not hand out its internal one (the {@link Bar} elements are immutable).
     */
    @Override
    public Bar[] bars() {
        return bars.clone();
    }

    /** The active bar's slot assignments (the pre-v2 single-bar view). */
    public SlotAssignment[] assignments() {
        return bars[activeBar].assignments();
    }

    /** The active bar's selected slot (the pre-v2 single-bar view). */
    public int selectedSlot() {
        return bars[activeBar].selectedSlot();
    }

    /**
     * @param slot the slot index within the active bar
     * @return the assignment, or an empty sentinel when out of range
     */
    @Nullable
    public SlotAssignment assignment(int slot) {
        return bars[activeBar].assignment(slot);
    }

    /**
     * Sets a slot on the <b>active</b> bar.
     *
     * @return this state with the active bar updated; an out-of-range slot is a
     *         no-op (the caller's bounds check is the fail-closed gate)
     */
    public BarState withAssignment(int slot, @Nullable ResourceLocation abilityId) {
        return withActiveBarContent(bars[activeBar].withAssignment(slot, abilityId));
    }

    /**
     * Selects a slot on the <b>active</b> bar; every other bar keeps its own
     * selection.
     *
     * @return this state with the active bar's selection updated
     * @throws IllegalArgumentException when the slot is out of the active bar's range
     */
    public BarState withSelectedSlot(int slot) {
        return withActiveBarContent(bars[activeBar].withSelectedSlot(slot));
    }

    /**
     * Makes another bar active. Every single-bar accessor then reads and writes
     * that bar instead; no slot data moves between bars.
     *
     * @param bar the bar index to activate
     * @return this state with a different active bar
     * @throws IllegalArgumentException when the index is out of range
     */
    public BarState withActiveBar(int bar) {
        if (bar < 0 || bar >= bars.length) {
            throw new IllegalArgumentException(
                    "activeBar must be in [0, " + bars.length + "), got " + bar);
        }
        return new BarState(bars, bar, schemaVersion);
    }

    /** Replaces the active bar, leaving every other bar untouched. */
    private BarState withActiveBarContent(Bar next) {
        Bar[] copy = bars.clone();
        copy[activeBar] = next;
        return new BarState(copy, activeBar, schemaVersion);
    }

    /** A single-bar state with the given slot count; selection starts at slot 0. */
    public static BarState empty(int slotCount) {
        return empty(slotCount, 1);
    }

    /** A state with {@code barCount} empty bars of {@code slotCount} slots each. */
    public static BarState empty(int slotCount, int barCount) {
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be >= 1, got " + barCount);
        }
        Bar[] bars = new Bar[barCount];
        for (int i = 0; i < barCount; i++) {
            bars[i] = Bar.empty(slotCount);
        }
        return new BarState(bars, 0, CURRENT_SCHEMA_VERSION);
    }
}
