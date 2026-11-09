package org.bitwisemadness.abilityhotbar.registry;

/**
 * The ability menu's visual style (issue #129): palette and rendering
 * parameters as data, so the screen draws through one resolved style instead
 * of one-off hex literals.
 *
 * <p>{@link #TRS_SAODARK} ships by default, its values <b>synced</b> from
 * Tensura RPG System's default {@code InterfaceTheme.SAO_DARK} (their enum,
 * MIT - recorded here with provenance; a future palette change is a
 * deliberate re-sync, never an automatic coupling):
 * background {@code 0x131D2C}, raised {@code 0x22354A}, text
 * {@code 0xEAF4FF}, muted {@code 0x95ACC2}, accent {@code 0x67DDED},
 * action {@code 0xFFC679}, world dim {@code 15%}, panel opacity
 * {@code 78%}.</p>
 *
 * @param background     panel/glass base color
 * @param raised         hovered/raised surface color
 * @param text           primary text
 * @param muted          secondary text
 * @param accent         the cyan interactive accent (underlines, markers)
 * @param action         the orange action/highlight color
 * @param dimPercent     world dimming behind the screen (0..100)
 * @param opacityPercent panel glass opacity (0..100)
 */
public record MenuStyle(int background, int raised, int text, int muted, int accent,
        int action, int dimPercent, int opacityPercent) {

    /** Tensura RPG System's default palette (their SAO_DARK theme). */
    public static final MenuStyle TRS_SAODARK = new MenuStyle(
            0x131D2C, 0x22354A, 0xEAF4FF, 0x95ACC2, 0x67DDED, 0xFFC679, 15, 78);

    /** The shipped default: the TRS-synced style. */
    public static final MenuStyle DEFAULT = TRS_SAODARK;

    /**
     * Their opacity formula, kept exact: the percentage baked into the alpha
     * byte over the low 24 bits.
     */
    public static int alpha(int rgb, int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        return clamped * 255 / 100 << 24 | rgb & 0xFFFFFF;
    }

    /** The glass fill for a surface: the color at the style's panel opacity. */
    public int glass(int color) {
        return alpha(color, opacityPercent);
    }

    /** The colour's RGB triple with the alpha byte stripped (TextColor's shape). */
    public static int rgb(int argb) {
        return argb & 0xFFFFFF;
    }
}
