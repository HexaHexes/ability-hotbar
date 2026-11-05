package org.bitwisemadness.abilityhotbar.client;

import net.minecraft.client.Minecraft;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;

/**
 * Seam for opening the ability management menu from the menu keybind
 * (REQ-HOT-12, hotbar#27 - the grave accent key by default).
 *
 * <p>The key is registered and rebindable (hotbar#20); this opens the
 * actual setup screen. Kept as a named seam so the input handler never
 * needs to know the screen's type, and so opening stays one line if the
 * screen ever changes.</p>
 *
 * <p>Opening is gated (hotbar#107): when no provider has registered any
 * ability there is nothing to manage, so the keybind is a silent no-op
 * instead of an empty screen. Provider presence IS registration presence
 * (REQ-CORE-03) - a bare instance naturally reads as "no abilities", and
 * a not-yet-ready registry reads the same way (empty, never a crash).</p>
 */
public final class AbilityMenu {

    private AbilityMenu() {
    }

    /** Opens the ability management menu when there is anything to manage. */
    public static void open() {
        if (!hasManageableAbilities()) {
            return;
        }
        Minecraft.getInstance().setScreen(new AbilityMenuScreen());
    }

    /**
     * The open gate: at least one provider has registered an ability - the
     * menu's only reason to exist (bars are always present, abilities are
     * the variable). Package-private for the fixtures.
     *
     * @return true when the menu has something to manage
     */
    static boolean hasManageableAbilities() {
        return !AbilityDiscovery.entries().isEmpty();
    }
}
