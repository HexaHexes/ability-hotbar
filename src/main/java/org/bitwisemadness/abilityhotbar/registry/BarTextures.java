package org.bitwisemadness.abilityhotbar.registry;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Optional per-bar sprite overrides (hotbar#125): the two textures the bar
 * layer draws - the background strip and the selection frame. Null components
 * mean "keep the vanilla sprite" ({@code hud/hotbar} /
 * {@code hud/hotbar_selection}), so a registration without overrides renders
 * byte-identically to before this record existed.
 *
 * <p>The ids must resolve in the GUI sprite atlas ({@code blitSprite}):
 * ship {@code assets/<namespace>/gui/sprites/<path>.png} and add a directory
 * source for your namespace in {@code assets/<namespace>/atlases/gui.json} -
 * the data-driven registration vanilla's own sprites use.</p>
 *
 * @param background the bar's background strip sprite, or null for vanilla
 * @param selection  the slot selection frame sprite, or null for vanilla
 */
public record BarTextures(@Nullable ResourceLocation background,
        @Nullable ResourceLocation selection) {

    /** No overrides: the layer draws the vanilla sprites. */
    public static final BarTextures DEFAULT = new BarTextures(null, null);

    /** The effective background sprite: the override when set, else {@code fallback}. */
    public ResourceLocation backgroundOr(ResourceLocation fallback) {
        return background != null ? background : fallback;
    }

    /** The effective selection sprite: the override when set, else {@code fallback}. */
    public ResourceLocation selectionOr(ResourceLocation fallback) {
        return selection != null ? selection : fallback;
    }
}
