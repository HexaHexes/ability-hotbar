package org.bitwisemadness.abilityhotbar.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.sync.MasteryQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The row mastery display contract (REQ-CORE-12): the badge/bar gates
 * open only for an answered snapshot echoing THIS client's request, the
 * level/XP/progress reads answer SERVER truth from the snapshot's wire
 * entries (and never run the curve locally), an unanswered or
 * foreign-echo reply leaves the rows silent instead of guessing, and the
 * throttle coalesces resize re-inits into one exchange per interval.
 *
 * <p>The entries are built in the exact shape core's
 * {@code MasteryQueryResponse} ships (kind/id/xp/level/progress), so a
 * wire-vocabulary drift on either side fails here rather than blanking
 * the rows in-game.</p>
 */
class CoreMasteryCompatTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("test", "mastery_display");
    private static final UUID TARGET = UUID.fromString(
            "0b7f4e3a-1f2c-4d5e-9a8b-7c6d5e4f3a2b");

    @BeforeEach
    void cleanExchange() {
        CoreMasteryCompat.reset();
    }

    @Test
    void unansweredPaneOpensNoSection() {
        assertFalse(CoreMasteryCompat.answered(),
                "before any answer the pane must omit the section");
    }

    @Test
    void throttleCoalescesBurstsIntoOneExchange() {
        assertTrue(CoreMasteryCompat.shouldRequest(1_000L), "first request is due");
        CoreMasteryCompat.markRequested(TARGET, 1_000L);
        assertFalse(CoreMasteryCompat.shouldRequest(
                        1_000L + CoreMasteryCompat.REQUEST_INTERVAL_MS - 1L),
                "resize re-inits inside the interval must coalesce");
        assertTrue(CoreMasteryCompat.shouldRequest(
                        1_000L + CoreMasteryCompat.REQUEST_INTERVAL_MS),
                "the next open after the interval may ask again");
    }

    @Test
    void foreignEchoIsDropped() {
        CoreMasteryCompat.markRequested(TARGET, 0L);
        CoreMasteryCompat.accept(new MasteryQueryResponse(
                UUID.randomUUID().toString(),
                List.of(abilityEntry(ABILITY, 9.0, 1, 0.9, List.of()))));
        assertFalse(CoreMasteryCompat.answered(),
                "a reply for another player must never seed the pane");
    }

    @Test
    void answeredOwnEchoExposesServerEntry() {
        CoreMasteryCompat.markRequested(TARGET, 0L);
        CoreMasteryCompat.accept(new MasteryQueryResponse(
                TARGET.toString(),
                List.of(abilityEntry(ABILITY, 45.5, 3, 0.4,
                        List.of(new MasteryQueryResponse.Bonus("COST", 0.95, 0.02))))));

        assertTrue(CoreMasteryCompat.answered(), "our echo must open the gate");
        assertEquals(3, CoreMasteryCompat.levelOf(ABILITY));
        assertEquals(45.5, CoreMasteryCompat.xpOf(ABILITY));
        assertEquals(0.4, CoreMasteryCompat.progressOf(ABILITY),
                "the bar fill comes from the server's progress field");
        var bonuses = CoreMasteryCompat.bonusesOf(ABILITY);
        assertEquals(1, bonuses.size(), "the pane section reads the server's bonuses");
        assertEquals("COST", bonuses.get(0).axis());
        assertEquals(0.02, bonuses.get(0).masteryBonus(),
                "the mastery slice rides the snapshot to the pane");
        assertEquals(0.95, bonuses.get(0).multiplier(), 1e-9,
                "the multiplier is the server's live answer, never re-derived");
    }

    @Test
    void answeredSnapshotWithoutTheAbilityReadsZero() {
        CoreMasteryCompat.markRequested(TARGET, 0L);
        CoreMasteryCompat.accept(new MasteryQueryResponse(
                TARGET.toString(), List.of()));
        assertTrue(CoreMasteryCompat.answered());

        // absence in an ANSWERED snapshot is the server saying "never
        // earned" - a true zero, not a guess
        assertEquals(0, CoreMasteryCompat.levelOf(ABILITY));
        assertEquals(0.0, CoreMasteryCompat.xpOf(ABILITY));
        assertEquals(0.0, CoreMasteryCompat.progressOf(ABILITY));
        assertTrue(CoreMasteryCompat.bonusesOf(ABILITY).isEmpty(),
                "no entry, no bonuses: the pane section stays hidden");
    }

    @Test
    void xpTextKeepsOnlyRealFractions() {
        assertEquals("45", CoreMasteryCompat.formatXp(45.0));
        assertEquals("0", CoreMasteryCompat.formatXp(0.0));
        assertEquals("45.5", CoreMasteryCompat.formatXp(45.5));
    }

    @Test
    void xpTextClampsToTwoDecimals() {
        // owner's brief: the live pool's tail stops at the eye's
        // precision - rounded, never padded, never locale-shaped
        assertEquals("45.57", CoreMasteryCompat.formatXp(45.567),
                "two decimals, rounded");
        assertEquals("45.56", CoreMasteryCompat.formatXp(45.564),
                "never past two decimals");
        assertEquals("45.2", CoreMasteryCompat.formatXp(45.2),
                "a zero second decimal never pads the tail");
        assertEquals("46", CoreMasteryCompat.formatXp(45.999),
                "a rounding whole reads whole");
        assertEquals("-45.57", CoreMasteryCompat.formatXp(-45.567),
                "negatives clamp the same way");
    }

    @Test
    void declaredGatesOnCoreDeclarations() {
        assertFalse(CoreMasteryCompat.declared(ABILITY),
                "undeclared abilities never render the section");
        MasteryDeclarations.declare(ABILITY, MasterySpec.gained(1.0));
        try {
            assertTrue(CoreMasteryCompat.declared(ABILITY));
        } finally {
            MasteryDeclarations.remove(ABILITY);
        }
    }

    /** One ABILITY entry in core's wire vocabulary (kind, id, xp, level, progress, bonuses). */
    private static MasteryQueryResponse.Entry abilityEntry(ResourceLocation id, double xp,
            int level, double progress, List<MasteryQueryResponse.Bonus> bonuses) {
        return new MasteryQueryResponse.Entry("ABILITY", id.toString(), xp, level, progress,
                bonuses);
    }
}
