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
 * action {@code 0xFFC679}, mastery {@code 0xB48CF2} (the org's own
 * addition for the generic-mastery chrome), world dim {@code 15%}, panel
 * opacity {@code 78%}.</p>
 *
 * @param background     panel/glass base color
 * @param raised         hovered/raised surface color
 * @param text           primary text
 * @param muted          secondary text
 * @param accent         the cyan interactive accent (underlines, markers)
 * @param action         the orange action/highlight color
 * @param mastery        the violet generic-mastery color (the row's level
 *                       badge and core bar - deliberately distinct from
 *                       the domain presentation meters)
 * @param dimPercent     world dimming behind the screen (0..100)
 * @param opacityPercent panel glass opacity (0..100)
 */
public record MenuStyle(int background, int raised, int text, int muted, int accent,
        int action, int mastery, int dimPercent, int opacityPercent) {

    /** Tensura RPG System's default palette (their SAO_DARK theme). */
    public static final MenuStyle TRS_SAODARK = new MenuStyle(
            0x131D2C, 0x22354A, 0xEAF4FF, 0x95ACC2, 0x67DDED, 0xFFC679, 0xB48CF2, 15, 78);

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

    /**
     * The colour with an absent alpha byte forced opaque: palette triples
     * carry no alpha ({@code 0x67DDED}), so a fill drawn straight from one
     * would be fully transparent - text already treats a zero alpha as
     * opaque, and a fill must not disagree with it.
     */
    public static int opaque(int rgb) {
        return (rgb >>> 24) == 0 ? rgb | 0xFF000000 : rgb;
    }
}
