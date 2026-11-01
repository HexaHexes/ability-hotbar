package org.bitwisemadness.abilityhotbar.client;

/**
 * Seam for opening the ability management menu from the K keybind (REQ-HOT-12).
 *
 * <p>The key is registered and rebindable now (hotbar#20); the menu flow itself
 * lands with hotbar#27, which will implement this method. Kept as a named seam
 * so the input handler never has to change when the menu exists.</p>
 */
public final class AbilityMenu {

    private AbilityMenu() {
    }

    /** Opens the ability management menu. No-op until hotbar#27 implements it. */
    public static void open() {
        // Intentionally empty until hotbar#27.
    }
}
