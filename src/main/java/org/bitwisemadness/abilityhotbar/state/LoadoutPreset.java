package org.bitwisemadness.abilityhotbar.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One named loadout preset (hotbar#29): a per-player, named snapshot of
 * <b>one bar's slot assignments</b> - ids, inert ids included, and their
 * per-slot modes (schema v4). Presets live in the same attachment as the bar
 * schema and are pure display/state data: recall writes them back through
 * the server-validated assignment path and never casts or drives mode
 * machinery (REQ-HOT-22's invariant).
 *
 * <p>Preset names are display data: length-limited and non-blank by this
 * record's contract, which the server enforces first at the payload
 * boundary.</p>
 *
 * @param name        display name (1..{@value #MAX_NAME_LENGTH} chars, non-blank)
 * @param barId       the bar this preset was saved from (recall targets it)
 * @param assignments the saved slot assignments, in slot order
 */
public record LoadoutPreset(String name, ResourceLocation barId,
        SlotAssignment[] assignments) {

    /** Server-side name ceiling (validated at the payload boundary first). */
    public static final int MAX_NAME_LENGTH = 32;

    public LoadoutPreset {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("preset name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "preset name must be <= " + MAX_NAME_LENGTH + " chars, got " + name.length());
        }
        if (barId == null) {
            throw new IllegalArgumentException("barId must not be null");
        }
        if (assignments == null) {
            throw new IllegalArgumentException("assignments must not be null");
        }
        assignments = assignments.clone();
    }

    /** JSON codec (attachment-adjacent test forms); NBT has its own layout. */
    public static final Codec<LoadoutPreset> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(LoadoutPreset::name),
                    ResourceLocation.CODEC.fieldOf("bar_id").forGetter(LoadoutPreset::barId),
                    SlotAssignment.CODEC.listOf().fieldOf("assignments")
                            .forGetter(p -> Arrays.asList(p.assignments()))
            ).apply(instance, (name, barId, assignments) ->
                    new LoadoutPreset(name, barId, assignments.toArray(new SlotAssignment[0]))));

    /** Network codec for the snapshot's preset list (ADR-003, display data). */
    public static final StreamCodec<ByteBuf, LoadoutPreset> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, LoadoutPreset::name,
            ResourceLocation.STREAM_CODEC, LoadoutPreset::barId,
            SlotAssignment.STREAM_CODEC.apply(ByteBufCodecs.list()),
            p -> Arrays.asList(p.assignments()),
            LoadoutPreset::fromParts
    );

    private static LoadoutPreset fromParts(String name, ResourceLocation barId,
            java.util.List<SlotAssignment> assignments) {
        return new LoadoutPreset(name, barId, assignments.toArray(new SlotAssignment[0]));
    }

    /** Defensive copy on access - the array is mutable, the record must not hand it out. */
    @Override
    public SlotAssignment[] assignments() {
        return assignments.clone();
    }

    /**
     * Server-side name validation (the payload boundary): trimmed length
     * within {@value #MAX_NAME_LENGTH} and non-blank after trimming.
     *
     * @param raw the candidate name (nullable)
     * @return the trimmed name, or null when the candidate is not acceptable
     */
    @Nullable
    public static String validName(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            return null;
        }
        return trimmed;
    }
}
