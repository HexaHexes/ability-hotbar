package org.bitwisemadness.abilityhotbar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The shared label fallbacks: the sentence form is the menu's original
 * casing, the title form is what a provider's registration-time prose
 * (ability-iss's school names) needs - one implementation, both
 * behaviours pinned.
 */
class TextLabelsTest {

    @Test
    void sentenceCapitalisesTheFirstLetterOnly() {
        assertEquals("Dark blood", TextLabels.sentence("dark_blood"),
                "the menu fallback keeps its original casing");
        assertEquals("Technomagy spell", TextLabels.sentence("technomagy/spell"),
                "path separators become spaces");
        assertEquals("Ability hotbar", TextLabels.sentence("ability:hotbar"),
                "namespace separators become spaces");
    }

    @Test
    void titleCapitalisesEveryWord() {
        assertEquals("Dark Blood", TextLabels.title("dark_blood"),
                "each underscore segment capitalises");
        assertEquals("Eldritch", TextLabels.title("eldritch"),
                "a single word capitalises once");
    }

    @Test
    void emptyAndNullAnswerEmpty() {
        assertEquals("", TextLabels.sentence(null), "null answers empty, never an error");
        assertEquals("", TextLabels.sentence(""), "empty answers empty");
        assertEquals("", TextLabels.title(null), "null answers empty, never an error");
        assertEquals("", TextLabels.title(""), "empty answers empty");
    }
}
