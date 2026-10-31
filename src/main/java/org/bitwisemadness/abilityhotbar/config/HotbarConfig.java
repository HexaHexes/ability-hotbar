package org.bitwisemadness.abilityhotbar.config;

/**
 * Resolved configuration values (REQ-CFG-02 precedence: safety > client).
 *
 * <p>The effective slot count is {@code min(client.slotsPerBar, server.maxSlots)}.
 * This class reads the loaded config values; it must only be accessed after
 * config loading (during gameplay, not during mod construction).</p>
 */
public final class HotbarConfig {

    private HotbarConfig() {
    }

    /** The effective number of slots per bar (client preference clamped by server ceiling). */
    public static int effectiveSlotsPerBar() {
        int clientPref = HotbarClientConfig.SLOTS_PER_BAR.get();
        int serverMax = HotbarServerConfig.MAX_HOTBAR_SLOTS.get();
        return Math.min(clientPref, serverMax);
    }

    /** The effective number of cyclable hotbars (client preference clamped by server ceiling). */
    public static int effectiveHotbarCount() {
        // client has no count preference yet; the server ceiling is the only constraint
        // until REQ-HOT-22 (multi-hotbar stretch) adds a client preference
        return HotbarServerConfig.MAX_HOTBAR_COUNT.get();
    }

    /** The server's hard ceiling for slots (non-bypassable). */
    public static int serverSlotCeiling() {
        return HotbarServerConfig.MAX_HOTBAR_SLOTS.get();
    }
}
