package org.bitwisemadness.abilityhotbar.client;

/**
 * Seam for toggling ability mode from the Right Ctrl keybind (REQ-HOT-16).
 *
 * <p>The key is registered and rebindable now (hotbar#20); integration with
 * Tensura's native mode state lands with hotbar#17, which will implement this
 * method. Kept as a named seam so the input handler never has to change when
 * the mode flow exists.</p>
 */
public final class AbilityMode {

    private AbilityMode() {
    }

    /** Toggles ability mode. No-op until hotbar#17 implements it. */
    public static void toggle() {
        // Intentionally empty until hotbar#17.
    }
}
