package org.bitwisemadness.abilityhotbar.client;

import net.minecraft.client.gui.GuiGraphics;
import org.bitwisemadness.abilityhotbar.registry.MenuStyle;

/**
 * The ability menu's drawing primitives (hotbar#27): the Tensura RPG
 * System-inspired chrome - backdrop, chamfered glass surfaces, meters -
 * written as our own code from the reference's visual language (design
 * comment on the issue; MIT). Every color resolves through the passed
 * {@link MenuStyle}, so no palette literal lives here.
 */
final class MenuChrome {

    /** The corner cut for capsules/cells (their chamfer, staircase-filled). */
    static final int CHAMFER = 3;

    private MenuChrome() {
    }

    /**
     * The backdrop: the style's world dim, the cyan hairline frame, the
     * fading header band, the corner accent ticks and the header rule.
     */
    static void backdrop(GuiGraphics gui, int width, int height, MenuStyle style) {
        gui.fill(0, 0, width, height, MenuStyle.alpha(style.background(), style.dimPercent()));
        int hairline = MenuStyle.alpha(style.accent(), 30);
        gui.fill(0, 0, width, 1, hairline);
        gui.fill(0, height - 1, width, height, hairline);
        gui.fill(0, 0, 1, height, hairline);
        gui.fill(width - 1, 0, width, height, hairline);
        // the 32px header band, fading downward (their header gradient)
        gui.fillGradient(0, 0, width, 32,
                MenuStyle.alpha(style.background(), 55),
                MenuStyle.alpha(style.background(), 0));
        // accent ticks: top-left and bottom-right (the bracket-frame look)
        int tick = MenuStyle.alpha(style.accent(), 80);
        gui.fill(2, 2, 16, 4, tick);
        gui.fill(2, 2, 4, 11, tick);
        gui.fill(width - 16, height - 4, width - 2, height - 2, tick);
        gui.fill(width - 4, height - 11, width - 2, height - 2, tick);
        // the rule under the header, above the filter strip
        gui.fill(8, 36, width - 8, 37, MenuStyle.alpha(style.accent(), 45));
    }

    /**
     * A chamfered (cut-corner) fill: the core rectangle plus staircase rows
     * at the top and bottom edges, narrowing to the corner - their panel
     * silhouette, approximated at pixel granularity.
     */
    static void chamferFill(GuiGraphics gui, int x, int y, int w, int h, int ch, int color) {
        int cut = Math.max(0, Math.min(ch, Math.min(w, h) / 2));
        gui.fill(x + cut, y, x + w - cut, y + h, color);
        for (int i = 0; i < cut; i++) {
            int inset = cut - i;
            gui.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            gui.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** A glass capsule at the default chamfer: outline + translucent fill. */
    static void glass(GuiGraphics gui, int x, int y, int w, int h, MenuStyle style,
            boolean highlight) {
        glass(gui, x, y, w, h, CHAMFER, style, highlight);
    }

    /**
     * The tooltip panel's surface (hotbar#155): an opaque-strength fill so
     * covered rows cannot bleed through and interleave with the panel's own
     * lines. The decorative glass (background at 60%) left them legible
     * underneath - verified on the owner's screenshots by the covered
     * icon's crushed saturation (245 -> 161 proves the fill covered; the
     * remaining legibility is the alpha) - while the style's hairline
     * outline keeps the chrome.
     */
    static void tooltip(GuiGraphics gui, int x, int y, int w, int h, MenuStyle style) {
        int outline = MenuStyle.alpha(style.accent(), 40);
        chamferFill(gui, x, y, w, h, CHAMFER, outline);
        chamferFill(gui, x + 1, y + 1, w - 2, h - 2, Math.max(0, CHAMFER - 1),
                MenuStyle.alpha(style.background(), 94));
    }

    /**
     * A chamfered glass surface: cyan hairline outline over a translucent
     * fill; {@code highlight} raises both to the accent (selection/hover).
     */
    static void glass(GuiGraphics gui, int x, int y, int w, int h, int ch, MenuStyle style,
            boolean highlight) {
        int outline = MenuStyle.alpha(style.accent(), highlight ? 55 : 30);
        chamferFill(gui, x, y, w, h, ch, outline);
        int fill = highlight ? MenuStyle.alpha(style.accent(), 35)
                : MenuStyle.alpha(style.background(), 60);
        chamferFill(gui, x + 1, y + 1, w - 2, h - 2, Math.max(0, ch - 1), fill);
    }

    /** A meter: dark track, accent fill, and a top highlight line. */
    static void meter(GuiGraphics gui, int x, int y, int w, int h, float fraction,
            MenuStyle style) {
        meter(gui, x, y, w, h, fraction, style, style.accent(),
                MenuStyle.alpha(style.background(), 75));
    }

    /** A meter with an explicit fill - the row bar's state colour. */
    static void meter(GuiGraphics gui, int x, int y, int w, int h, float fraction,
            MenuStyle style, int fill) {
        meter(gui, x, y, w, h, fraction, style, fill,
                MenuStyle.alpha(style.background(), 75));
    }

    /**
     * A meter with an explicit fill <b>and track</b>: the row bar tints
     * both with its state colour, so the state reads even where progress
     * is a sliver (owner: never the same colour in every state).
     */
    static void meter(GuiGraphics gui, int x, int y, int w, int h, float fraction,
            MenuStyle style, int fill, int track) {
        chamferFill(gui, x, y, w, h, Math.min(2, h / 2), track);
        int fillWidth = meterFillWidth(w, fraction);
        if (fillWidth > 0) {
            chamferFill(gui, x + 1, y + 1, fillWidth, h - 2, 0, fill);
            gui.fill(x + 1, y + 1, x + 1 + fillWidth, y + 2,
                    MenuStyle.alpha(style.text(), 45));
        }
    }

    /**
     * The filled width of a meter track: pure and clamped to [0, width - 2]
     * (a 1px track border each side), for the geometry fixture.
     */
    static int meterFillWidth(int width, float fraction) {
        int usable = Math.max(0, width - 2);
        float clamped = Math.max(0f, Math.min(1f, fraction));
        return Math.min(usable, Math.round(usable * clamped));
    }
}
