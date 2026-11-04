package org.bitwisemadness.abilityhotbar.client;

import net.minecraft.client.Minecraft;

/**
 * Seam for opening the ability management menu from the menu keybind
 * (REQ-HOT-12, hotbar#27 - the grave accent key by default).
 *
 * <p>The key is registered and rebindable (hotbar#20); this opens the
 * actual setup screen. Kept as a named seam so the input handler never
 * needs to know the screen's type, and so opening stays one line if the
 * screen ever changes.</p>
 */
public final class AbilityMenu {

    private AbilityMenu() {
    }

    /** Opens the ability management menu. */
    public static void open() {
        Minecraft.getInstance().setScreen(new AbilityMenuScreen());
    }
}
