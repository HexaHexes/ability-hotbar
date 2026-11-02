package org.bitwisemadness.abilityhotbar.config;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * Named screen positions for the ability bar (REQ-HOT-07).
 *
 * <p>Every constant is a complete two-axis placement rule, so any anchor is
 * valid with either {@code display.horizontal} value: the anchor decides
 * <em>where</em> the bar sits, the orientation decides whether it is a row or
 * a column. There is deliberately no anchor-plus-orientation validation.</p>
 *
 * <p>The depths are vanilla HUD geometry for Minecraft 1.21.1, read from
 * {@code net.minecraft.client.gui.Gui}: the hotbar occupies the bottom 22 px
 * and the experience bar tops out 29 px above the bottom of the screen.</p>
 */
public enum BarAnchor implements TranslatableEnum {
    /**
     * Centred horizontally, slot-grid bottom resting on the vanilla hotbar's
     * top edge. This is the historical placement and the default.
     */
    ABOVE_HOTBAR,

    /**
     * Centred horizontally, slot-grid bottom resting on the vanilla
     * experience bar's top edge.
     */
    ABOVE_XP,

    /**
     * Centred horizontally, slot-grid bottom above the armor / health /
     * hunger / oxygen cluster. Its height is resolved per-frame from
     * {@code display.statusAnchorMode}.
     */
    ABOVE_STATUS,

    /** Inset from the left screen border, vertically centred. */
    LEFT_EDGE,

    /** Inset from the right screen border, vertically centred. */
    RIGHT_EDGE;

    /**
     * Shows this anchor's translated name on the config screen's cycle button
     * instead of the raw enum constant (e.g. {@code RIGHT_EDGE}).
     */
    @Override
    public Component getTranslatedName() {
        return Component.translatable(translationKey());
    }

    /** {@return the language key naming this anchor, e.g. {@code ability_hotbar.anchor.right_edge}} */
    public String translationKey() {
        return "ability_hotbar.anchor." + name().toLowerCase(Locale.ROOT);
    }
}
