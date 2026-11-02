package org.bitwisemadness.abilityhotbar.config;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * How the {@link BarAnchor#ABOVE_STATUS} anchor resolves the height of the
 * vanilla status cluster (REQ-HOT-07).
 *
 * <p>Only read when {@code display.anchor} is {@code aboveStatus}; every other
 * anchor ignores it.</p>
 */
public enum StatusAnchorMode implements TranslatableEnum {
    /**
     * Always the vanilla baseline cluster height. The bar never moves, but it
     * can overlap the armor row for players with more than one row of hearts.
     */
    FIXED,

    /**
     * Cluster height tracked from the player's heart rows, armor and oxygen.
     * Never overlaps; the bar shifts when those change.
     */
    DYNAMIC;

    /**
     * Shows this mode's translated name on the config screen's cycle button
     * instead of the raw enum constant (e.g. {@code DYNAMIC}).
     */
    @Override
    public Component getTranslatedName() {
        return Component.translatable(translationKey());
    }

    /** {@return the language key naming this mode, e.g. {@code ability_hotbar.status_anchor_mode.dynamic}} */
    public String translationKey() {
        return "ability_hotbar.status_anchor_mode." + name().toLowerCase(Locale.ROOT);
    }
}
