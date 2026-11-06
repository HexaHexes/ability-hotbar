package org.bitwisemadness.abilityhotbar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The never-guess unlock read (ability-hotbar#135): only an explicit false
 * blocks or dims; unknown never does (REQ-HOT-08 - guessing would lock
 * players out of abilities they hold).
 */
class UnlockRuleTest {

    @Test
    void explicitFalseIsProvenUnlearned() {
        assertTrue(UnlockRule.provenUnlearned(Optional.of(Boolean.FALSE)),
                "an explicit false must block");
    }

    @Test
    void learnedPasses() {
        assertFalse(UnlockRule.provenUnlearned(Optional.of(Boolean.TRUE)),
                "a learned ability must never block");
    }

    @Test
    void unknownNeverBlocks() {
        assertFalse(UnlockRule.provenUnlearned(Optional.empty()),
                "unknown (undeclared/abstaining/violating) must never block - never guess");
    }
}
