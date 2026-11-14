package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The badge's declared level bonus (owner's brief: the shown mastery
 * level also counts Tensura's states - Learned +1, Mastered +2): a
 * declared source wins, and anything it cannot answer (no declaration,
 * null id/player, a throw, a negative) reads 0 - the badge stays the
 * plain core level, byte-identical for undeclared ids.
 */
class MasteryLevelBonusTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");

    @AfterEach
    void isolate() {
        MasteryLevelBonus.remove(ABILITY);
    }

    @Test
    void declaredSourceWins() {
        MasteryLevelBonus.declare(ABILITY, (player, id) -> 2);

        assertEquals(2, MasteryLevelBonus.bonusFor(null, ABILITY),
                "the declared answer is what the badge adds");
    }

    @Test
    void undeclaredIdsAndNullIdsAddNothing() {
        assertEquals(0, MasteryLevelBonus.bonusFor(null, ABILITY),
                "no declaration: the badge stays the core level");
        assertEquals(0, MasteryLevelBonus.bonusFor(null, null),
                "a null id never throws");
    }

    @Test
    void aThrowingSourceNeverBreaksTheBadge() {
        MasteryLevelBonus.declare(ABILITY, (player, id) -> {
            throw new IllegalStateException("upstream unreadable");
        });

        assertEquals(0, MasteryLevelBonus.bonusFor(null, ABILITY),
                "a broken source costs one row's badge, not the screen");
    }

    @Test
    void negativeAnswersClampToZero() {
        MasteryLevelBonus.declare(ABILITY, (player, id) -> -5);

        assertEquals(0, MasteryLevelBonus.bonusFor(null, ABILITY),
                "a badge never subtracts levels");
    }

    @Test
    void declarationsAreExclusiveAndRemovable() {
        assertThrows(IllegalArgumentException.class, () -> MasteryLevelBonus.declare(null, (p, i) -> 1));
        assertThrows(IllegalArgumentException.class, () -> MasteryLevelBonus.declare(ABILITY, null));

        MasteryLevelBonus.declare(ABILITY, (player, id) -> 1);
        MasteryLevelBonus.remove(ABILITY);
        assertEquals(0, MasteryLevelBonus.bonusFor(null, ABILITY),
                "after removal the plain core level returns");
    }
}
