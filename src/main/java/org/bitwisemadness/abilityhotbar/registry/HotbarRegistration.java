package org.bitwisemadness.abilityhotbar.registry;

import net.minecraft.resources.ResourceLocation;

/**
 * A hotbar some mod declared at setup (ADR-012, hotbar#77).
 *
 * @param id             stable identity; the reserved {@code ability_hotbar:} namespace is rejected at registration
 * @param displayNameKey translation key for the bar's name (assignment screens, switch toast)
 * @param assignable     whether players may assign abilities to this bar; {@code false} for
 *                       content-owned bars (e.g. Pseudocore curios) that carry no attachment state
 * @param textures       optional sprite overrides (hotbar#125); {@link BarTextures#DEFAULT}
 *                       when the addon defines none
 */
public record HotbarRegistration(ResourceLocation id, String displayNameKey, boolean assignable,
        BarTextures textures) {

    /** The pre-texture shape: no overrides (existing callers and fixtures). */
    public HotbarRegistration(ResourceLocation id, String displayNameKey, boolean assignable) {
        this(id, displayNameKey, assignable, BarTextures.DEFAULT);
    }

    public HotbarRegistration {
        if (textures == null) {
            textures = BarTextures.DEFAULT;
        }
    }
}
