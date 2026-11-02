package org.bitwisemadness.abilityhotbar.config;

/**
 * How the {@link BarAnchor#ABOVE_STATUS} anchor resolves the height of the
 * vanilla status cluster (REQ-HOT-07).
 *
 * <p>Only read when {@code display.anchor} is {@code aboveStatus}; every other
 * anchor ignores it.</p>
 */
public enum StatusAnchorMode {
    /**
     * Always the vanilla baseline cluster height. The bar never moves, but it
     * can overlap the armor row for players with more than one row of hearts.
     */
    FIXED,

    /**
     * Cluster height tracked from the player's heart rows, armor and oxygen.
     * Never overlaps; the bar shifts when those change.
     */
    DYNAMIC
}
