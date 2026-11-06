package org.bitwisemadness.abilityhotbar.registry;

import net.minecraft.resources.ResourceLocation;

/**
 * A named hotbar style (issue #127): a mod-defined texture set a bar can
 * wear. Registered at setup like bars; the display name is the
 * player-facing label in the style cycler.
 *
 * @param id             stable identity, namespaced (the registering mod's namespace)
 * @param displayNameKey translation key for the style's name
 * @param textures       the background/selection sprites the style applies
 */
public record HotbarStyle(ResourceLocation id, String displayNameKey, BarTextures textures) {
}
