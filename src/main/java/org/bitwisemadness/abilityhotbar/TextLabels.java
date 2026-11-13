package org.bitwisemadness.abilityhotbar;

/**
 * Player-facing fallbacks for identifier-shaped text: the label a
 * translation key does not cover. Pure and common-side, so every caller
 * on either side shares one implementation - the menu's client labels
 * use {@link #sentence}, and a provider building registration-time
 * prose (for example ability-iss's school names) uses {@link #title}.
 */
public final class TextLabels {

    private TextLabels() {
    }

    /**
     * Separators to spaces, first letter capitalised: {@code dark_blood}
     * becomes {@code Dark blood}. The menu's original fallback casing.
     *
     * @param raw the identifier to prettify; null or empty answers empty
     * @return the sentence-cased label
     */
    public static String sentence(String raw) {
        String words = words(raw);
        if (words.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /**
     * Separators to spaces, every word capitalised: {@code dark_blood}
     * becomes {@code Dark Blood}. The title form.
     *
     * @param raw the identifier to prettify; null or empty answers empty
     * @return the title-cased label
     */
    public static String title(String raw) {
        String words = words(raw);
        if (words.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(words.length());
        boolean wordStart = true;
        for (char c : words.toCharArray()) {
            out.append(wordStart ? Character.toUpperCase(c) : c);
            wordStart = c == ' ';
        }
        return out.toString();
    }

    /** The shared normalisation: path and namespace separators become spaces. */
    private static String words(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace('/', ' ').replace(':', ' ').replace('_', ' ');
    }
}
