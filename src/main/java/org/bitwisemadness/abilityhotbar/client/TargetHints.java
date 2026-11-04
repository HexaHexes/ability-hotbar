package org.bitwisemadness.abilityhotbar.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.jetbrains.annotations.Nullable;

/**
 * Crosshair entity hints for slot activation (hotbar#94).
 *
 * <p>An ability opts in with the {@link #TARGETED_TAG} tag on its definition:
 * only a tagged ability ever sends a hint, and only when the crosshair
 * resolves to an entity. An untagged ability therefore activates with exactly
 * the bytes it sent before this class existed, and an unknown definition
 * (not yet synced) never hints - fail-closed, like every other client-side
 * gate.</p>
 *
 * <p>Client-only: {@link #crosshairEntity()} reads {@code Minecraft.hitResult},
 * so this class must never load on a dedicated server. It lives in the client
 * package behind the input handler's {@code Dist.CLIENT} subscriber, and the
 * source-tree scan in {@code TargetHintsTest} pins that placement.</p>
 */
public final class TargetHints {

    /** The definition tag that opts an ability into crosshair hints. */
    public static final String TARGETED_TAG = "targeted";

    private TargetHints() {
    }

    /**
     * Resolves the hint for an activation of the given ability against the
     * live crosshair. The crosshair read is a plain field access, so the
     * decision itself stays in the pure {@link #hintFor} half.
     *
     * @param abilityId the ability being activated
     * @return the aimed entity's id when the ability is tagged
     *         {@value #TARGETED_TAG} and the crosshair is on an entity;
     *         otherwise null (no hint is attached)
     */
    public static @Nullable UUID forActivation(ResourceLocation abilityId) {
        var definition = AbilityDiscovery.get(abilityId).orElse(null);
        return hintFor(definition, crosshairEntity());
    }

    /**
     * The pure half of the decision, fixture-tested: the aim rides along only
     * for a tagged definition, and only when there is an aim at all.
     *
     * @param definition  the ability's definition, or null when unknown
     * @param aimedEntity the entity under the crosshair, or null
     * @return the hint to attach, or null for none
     */
    static @Nullable UUID hintFor(@Nullable AbilityDefinition definition, @Nullable UUID aimedEntity) {
        if (definition == null || aimedEntity == null) {
            return null;
        }
        return definition.tags().contains(TARGETED_TAG) ? aimedEntity : null;
    }

    /** The entity under the crosshair, or null. Client-only ({@code Minecraft.hitResult}). */
    private static @Nullable UUID crosshairEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        HitResult hit = minecraft.hitResult;
        return hit instanceof EntityHitResult entityHit ? entityHit.getEntity().getUUID() : null;
    }
}
