package org.bitwisemadness.abilityhotbar.client;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * The display name for a hotbar id - what the switch notification shows
 * (REQ-HOT-22, #28 AC 4).
 *
 * <p>Resolution order:</p>
 * <ul>
 *   <li>a registered bar shows its registration's display-name key (the
 *       registering mod owns the text);</li>
 *   <li>a built-in {@code ability_hotbar:bar_N} shows its own lang entry
 *       {@code ability_hotbar.bar_N} - "Hotbar 1" .. "Hotbar 10", matching
 *       the Alt+digit labels;</li>
 *   <li>anything else - an unidentified or unknown id - shows the raw id as a
 *       literal: diagnosed, never an invented name.</li>
 * </ul>
 *
 * <p>Client-only (it produces display text), but reads only registry data.</p>
 */
public final class BarName {

    private BarName() {
    }

    /**
     * @param id the bar's id; null yields an empty component
     * @return the bar's display name as a translatable component (or a
     *         literal raw id for identities the registry does not know)
     */
    public static Component of(@Nullable ResourceLocation id) {
        if (id == null) {
            return Component.empty();
        }
        Optional<HotbarRegistration> registration = HotbarRegistry.get(id);
        if (registration.isPresent()) {
            return Component.translatable(registration.get().displayNameKey());
        }
        if (HotbarRegistry.isBuiltin(id)) {
            // ability_hotbar:bar_N -> ability_hotbar.bar_N (see en_us.json)
            return Component.translatable(AbilityHotbar.MOD_ID + "." + id.getPath());
        }
        return Component.literal(id.toString());
    }
}
