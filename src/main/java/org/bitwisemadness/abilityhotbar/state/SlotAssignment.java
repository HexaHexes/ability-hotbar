package org.bitwisemadness.abilityhotbar.state;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One hotbar slot's assignment (REQ-HOT-14/21).
 *
 * <p>The raw ability id is preserved even when the provider is absent - the
 * slot renders as diagnosed inert, never silently replaced or dropped. An
 * empty slot is represented by a null {@code abilityId}.</p>
 */
public record SlotAssignment(@Nullable ResourceLocation abilityId) {

    /** String-based codec: blank string = empty slot (handles null cleanly). */
    public static final Codec<SlotAssignment> CODEC = Codec.STRING.xmap(
            s -> s.isBlank() ? empty() : new SlotAssignment(ResourceLocation.parse(s)),
            a -> a.abilityId() != null ? a.abilityId().toString() : ""
    );

    /** Sentinel for an empty slot (no ability assigned). */
    public static SlotAssignment empty() {
        return new SlotAssignment(null);
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
