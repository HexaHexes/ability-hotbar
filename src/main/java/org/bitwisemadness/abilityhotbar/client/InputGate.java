package org.bitwisemadness.abilityhotbar.client;

import net.minecraft.client.Minecraft;

/**
 * The shared input-suppression invariant (REQ-HOT-18).
 *
 * <p>Every hotbar control - the general bar, the future spellbook bar, and the
 * menu/mode keys - consults this one gate before acting, so the "never steal
 * vanilla or chat input" rule lives in a single place instead of drifting
 * across handlers (the drift-is-regression concern the issue calls out).</p>
 *
 * <p>A control may act only while <b>no screen is open</b> (chat, inventory,
 * any GUI) and a player is loaded; modifier-gated controls additionally require
 * their modifier to be held. The decision is deliberately an explicit,
 * unit-testable function rather than an assumption about vanilla's implicit
 * conflict-context suppression or NeoForge's screen-gated scroll event - those
 * could change under us, and the invariant must not.</p>
 *
 * <p>Loads only on the physical client.</p>
 */
public final class InputGate {

    private InputGate() {
    }

    /**
     * Pure decision: may a hotbar control act right now?
     *
     * <p>This is the invariant in one testable place. The live overloads read
     * Minecraft state and defer here.</p>
     *
     * @param screenOpen       true when any screen (chat, inventory, GUI) is open
     * @param hasPlayer        true when a local player is loaded
     * @param modifierHeld     current state of the control's modifier key
     * @param modifierRequired whether the control in question needs that modifier
     * @return true only when the screen is closed, a player is present, and any
     *         required modifier is held
     */
    public static boolean capturable(boolean screenOpen, boolean hasPlayer,
                                     boolean modifierHeld, boolean modifierRequired) {
        if (screenOpen || !hasPlayer) {
            return false;
        }
        return !modifierRequired || modifierHeld;
    }

    /**
     * Live gate for a plain, non-modifier control (G, K, Right Ctrl): the screen
     * must be closed and a player present; no modifier is consulted.
     *
     * @return true when the control may act
     */
    public static boolean isInputCapturable() {
        Minecraft mc = Minecraft.getInstance();
        return capturable(mc.screen != null, mc.player != null, true, false);
    }

    /**
     * Live gate for a modifier-gated control (Ctrl+wheel, Ctrl+number): the screen
     * must be closed, a player present, and the modifier held.
     *
     * @param modifierHeld the control's modifier state (e.g. {@code Screen.hasControlDown()})
     * @return true when the control may act
     */
    public static boolean isInputCapturable(boolean modifierHeld) {
        Minecraft mc = Minecraft.getInstance();
        return capturable(mc.screen != null, mc.player != null, modifierHeld, true);
    }

    /** @return the live screen-open flag, for callers that branch on it directly. */
    public static boolean isScreenOpen() {
        return Minecraft.getInstance().screen != null;
    }
}
