package org.bitwisemadness.abilityhotbar.config;

/**
 * Resolved configuration values (REQ-CFG-02 precedence: server ceiling > client preference).
 *
 * <p>The effective display size is {@code min(client.slotsPerBar, server.maxSlots)}.
 * These values may only be read after config loading (during gameplay, not
 * during mod construction).</p>
 *
 * <p><b>Sidedness:</b> {@link #effectiveSlotsPerBar()} reads the CLIENT config
 * and is therefore valid only on a physical client. Server-side code must use
 * {@link #serverSlotCeiling()} - a dedicated server never loads the client
 * config, and reading it throws "Cannot get config value before config is
 * loaded".</p>
 */
public final class HotbarConfig {

    private HotbarConfig() {
    }

    /**
     * The effective number of slots the client renders per bar: the player's
     * preference clamped by the server ceiling.
     *
     * <p>Physical client only.</p>
     */
    public static int effectiveSlotsPerBar() {
        int clientPref = HotbarClientConfig.SLOTS_PER_BAR.get();
        int serverMax = HotbarServerConfig.MAX_HOTBAR_SLOTS.get();
        return Math.min(clientPref, serverMax);
    }

    /**
     * The number of slots the bar actually draws for a given synced state:
     * {@link #effectiveSlotsPerBar()} clamped to the state's own length (saved
     * state may be shorter than the current config).
     *
     * <p>This is the single source of truth for both the renderer and the
     * Ctrl+wheel selection, so the scroll can never reach a slot the bar does
     * not draw (REQ-CFG-02 + REQ-HOT-10).</p>
     *
     * <p>Physical client only.</p>
     */
    public static int effectiveRenderCount(int stateLength) {
        return Math.min(effectiveSlotsPerBar(), stateLength);
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
