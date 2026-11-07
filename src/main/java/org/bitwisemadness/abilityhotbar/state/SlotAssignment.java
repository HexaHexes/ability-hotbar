package org.bitwisemadness.abilityhotbar.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One hotbar slot's assignment (REQ-HOT-14/21), extended by the per-slot
 * ability mode (hotbar#29, schema v4): the mode is a property of the
 * <b>slotted pairing</b>, so the same skill in two slots of one bar holds two
 * independent modes and no bar-level mode state exists anywhere.
 *
 * <p>The raw ability id is preserved even when the provider is absent - the
 * slot renders as diagnosed inert, never silently replaced or dropped. An
 * empty slot is represented by a null {@code abilityId}; a null or blank
 * {@code mode} means "no mode context" (the ability's default applies).</p>
 */
public record SlotAssignment(@Nullable ResourceLocation abilityId, @Nullable String mode) {

    /** Mode-less construction: every pre-v4 call site and a cleared pairing. */
    public SlotAssignment(@Nullable ResourceLocation abilityId) {
        this(abilityId, null);
    }

    public SlotAssignment {
        if (mode != null && mode.isBlank()) {
            mode = null; // blank = no mode context (the wire's own reading)
        }
    }

    /**
     * JSON codec: two optional strings (the v3 single-string id shape is a
     * test-facing form; NBT persistence lives in {@link BarStateSerializer}
     * with its own legacy-safe layout).
     */
    public static final Codec<SlotAssignment> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.optionalFieldOf("ability", "").forGetter(a -> {
                        ResourceLocation id = a.abilityId();
                        return id != null ? id.toString() : "";
                    }),
                    Codec.STRING.optionalFieldOf("mode", "").forGetter(a -> {
                        String mode = a.mode();
                        return mode != null ? mode : "";
                    })
            ).apply(instance, (id, mode) -> id.isBlank()
                    ? empty()
                    : new SlotAssignment(ResourceLocation.parse(id), mode)));

    /** Network codec: presence flag, the id when assigned, then the mode. */
    public static final StreamCodec<ByteBuf, SlotAssignment> STREAM_CODEC = StreamCodec.of(
            (buf, assignment) -> {
                ResourceLocation id = assignment.abilityId();
                buf.writeBoolean(id != null);
                if (id != null) {
                    ResourceLocation.STREAM_CODEC.encode(buf, id);
                    String mode = assignment.mode();
                    ByteBufCodecs.STRING_UTF8.encode(buf, mode != null ? mode : "");
                }
            },
            buf -> buf.readBoolean()
                    ? new SlotAssignment(ResourceLocation.STREAM_CODEC.decode(buf),
                            nullableMode(ByteBufCodecs.STRING_UTF8.decode(buf)))
                    : empty()
    );

    private static @Nullable String nullableMode(String raw) {
        return raw == null || raw.isBlank() ? null : raw;
    }

    /** Sentinel for an empty slot (no ability assigned). */
    public static SlotAssignment empty() {
        return new SlotAssignment(null, null);
    }

    /** The same pairing with a different mode (the cycle's own write path). */
    public SlotAssignment withMode(@Nullable String newMode) {
        return new SlotAssignment(abilityId, newMode);
    }

    /** True when this slot has no ability assigned. */
    public boolean isEmpty() {
        return abilityId == null;
    }

    /** True when this slot has an ability assigned (may be inert if the provider is absent). */
    public boolean isAssigned() {
        return abilityId != null;
    }
}
