package org.bitwisemadness.abilityhotbar;

import java.util.Optional;

/**
 * The never-guess read of core's unlock contract (ability-hotbar#135):
 * <b>provably unlearned</b> - only an explicit {@code false} blocks.
 * {@code Optional.empty} (undeclared id, abstaining provider, or a
 * contract-violating provider read) is <i>unknown</i>, and unknown never
 * refuses an assignment and never dims a menu row (REQ-HOT-08: unknown is
 * not a wrong verdict - the failure mode of guessing would be locking
 * players out of abilities they hold).
 *
 * <p>Consumers: the assignment payload refuses only on proven-unlearned;
 * the menu dims only proven-unlearned rows. Both ask core
 * ({@code AbilityUnlocks.isLearned}) - this rule is the shared mapping.</p>
 */
public final class UnlockRule {

    private UnlockRule() {
    }

    /**
     * @param learned core's answer for one ability (empty = unknown)
     * @return true only when core explicitly answered {@code false}
     */
    public static boolean provenUnlearned(Optional<Boolean> learned) {
        return learned.isPresent() && !learned.get();
    }
}
