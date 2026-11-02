package org.bitwisemadness.abilityhotbar.client;

/**
 * Height of the vanilla status cluster, in pixels from the bottom of the screen
 * up to the cluster's top edge (REQ-HOT-07).
 *
 * <p>Mirrors the row bookkeeping in {@code net.minecraft.client.gui.Gui} for
 * Minecraft 1.21.1: hearts and food start 39 px above the bottom of the screen,
 * one heart row per 20 HP of {@code max(maxHealth, health) + absorption}, the
 * armor row adds 10 px when armor is worn, and the oxygen row adds 10 px while
 * it is shown.</p>
 *
 * <p>Pure arithmetic - no player or GUI types - so the placement rule can be
 * unit-tested without a client. The render layer extracts the player values and
 * passes them in.</p>
 */
public final class StatusCluster {

    /**
     * Depth of the vanilla baseline cluster: one heart row plus the armor and
     * oxygen rows above it. This is the {@code FIXED} anchor depth.
     */
    public static final int BASELINE_DEPTH = 49;

    /** Depth of the hearts / food baseline row's top edge. */
    private static final int BASELINE_ROW = 39;

    /** Vanilla's nominal heart-row step, in pixels. */
    private static final int ROW_STEP = 10;

    /** Pixels one extra row (armor or oxygen) adds above the baseline. */
    private static final int EXTRA_ROW = 10;

    private StatusCluster() {
    }

    /**
     * The number of heart rows vanilla draws for a health pool, from
     * {@code Gui.renderHealthLevel}: {@code ceil((max(maxHealth, health) + absorption) / 20)}.
     *
     * @param maxHealth   the player's maximum health, in HP
     * @param health      the player's current health, in HP
     * @param absorption  the player's absorption, in HP
     * @return at least {@code 1}
     */
    public static int heartRows(double maxHealth, double health, double absorption) {
        double container = Math.max(maxHealth, health);
        return Math.max(1, (int) Math.ceil((container + absorption) / 20.0));
    }

    /**
     * Depth of the status cluster's top edge for a given player state.
     *
     * <p>Uses the nominal 10 px row step rather than vanilla's compressed
     * {@code max(12 - rows, 3)}, which only differs from 3 heart rows upward.
     * Our value is therefore never smaller than vanilla's own, so the bar sits
     * at or above the real cluster top and cannot overlap it.</p>
     *
     * @param heartRows      heart rows drawn, see {@link #heartRows}
     * @param hasArmor       whether an armor row is drawn ({@code armorValue > 0})
     * @param oxygenVisible  whether the oxygen row is drawn
     * @return depth in pixels from the bottom of the screen
     */
    public static int depth(int heartRows, boolean hasArmor, boolean oxygenVisible) {
        int rows = Math.max(1, heartRows);
        int left = BASELINE_ROW + (rows - 1) * ROW_STEP + (hasArmor ? EXTRA_ROW : 0);
        int right = BASELINE_ROW + (oxygenVisible ? EXTRA_ROW : 0);
        return Math.max(left, right);
    }
}
