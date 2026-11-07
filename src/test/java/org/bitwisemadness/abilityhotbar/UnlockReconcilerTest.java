package org.bitwisemadness.abilityhotbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilityhotbar.state.Bar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.LogCapture;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The unlock reconciliation sweep (hotbar#147): only proven-unlearned slots
 * clear (unknown/undeclared round-trip untouched), the unchanged state
 * answers empty so callers skip the write, and logging happens once per
 * cleared id - a cleared slot holds nothing next sweep, so re-runs never
 * re-log.
 */
class UnlockReconcilerTest {

    private static final ResourceLocation BAR0 =
            ResourceLocation.fromNamespaceAndPath("tensura", "bar_0");
    private static final ResourceLocation BAR1 =
            ResourceLocation.fromNamespaceAndPath("tensura", "bar_1");
    private static final ResourceLocation LEARNED =
            ResourceLocation.fromNamespaceAndPath("tensura", "learned_skill");
    private static final ResourceLocation UNLEARNED =
            ResourceLocation.fromNamespaceAndPath("tensura", "unlearned_skill");
    private static final ResourceLocation UNKNOWN =
            ResourceLocation.fromNamespaceAndPath("tensura", "undeclared_skill");

    @AfterEach
    void isolate() {
        AbilityUnlocks.remove(LEARNED);
        AbilityUnlocks.remove(UNLEARNED);
    }

    private static void answer(ResourceLocation abilityId, boolean learned) {
        AbilityUnlocks.declare(abilityId, (player, id) -> Optional.of(learned));
    }

    private static Bar bar(ResourceLocation barId, ResourceLocation... abilities) {
        SlotAssignment[] assignments = new SlotAssignment[abilities.length];
        for (int i = 0; i < abilities.length; i++) {
            assignments[i] = abilities[i] == null
                    ? SlotAssignment.empty()
                    : new SlotAssignment(abilities[i]);
        }
        return new Bar(barId, assignments, 0);
    }

    private static BarState state(Bar... bars) {
        return new BarState(bars, bars[0].id(), BarState.CURRENT_SCHEMA_VERSION,
                new org.bitwisemadness.abilityhotbar.state.LoadoutPreset[0]);
    }

    @Test
    void clearsOnlyProvenUnlearnedSlots() {
        answer(UNLEARNED, false);
        answer(LEARNED, true);
        // UNKNOWN stays undeclared: unknown never clears

        var input = state(bar(BAR0, UNLEARNED, LEARNED, UNKNOWN));
        var output = UnlockReconciler.reconcile(null, input).orElseThrow();

        var swept = output.bars()[0];
        assertNull(swept.assignment(0).abilityId(), "the proven-unlearned slot clears");
        assertEquals(LEARNED, swept.assignment(1).abilityId(), "learned keeps its slot");
        assertEquals(UNKNOWN, swept.assignment(2).abilityId(),
                "unknown never clears - a pool without declarations round-trips");
        assertEquals(BAR0, output.activeBarId(), "the sweep changes slots only");
        assertEquals(BarState.CURRENT_SCHEMA_VERSION, output.schemaVersion());
        assertEquals(input.bars().length, output.bars().length, "no bar is dropped");
    }

    @Test
    void unchangedAndNullStatesAnswerEmpty() {
        answer(LEARNED, true);
        var input = state(bar(BAR0, LEARNED, UNKNOWN));
        assertTrue(UnlockReconciler.reconcile(null, input).isEmpty(),
                "nothing proven unlearned -> empty, callers skip the write");
        assertTrue(UnlockReconciler.reconcile(null, null).isEmpty(),
                "a null state is never invented");
    }

    @Test
    void logsOncePerClearedIdAndNeverAgain() {
        answer(UNLEARNED, false);
        var input = state(bar(BAR0, UNLEARNED), bar(BAR1, UNLEARNED));

        try (LogCapture logs = LogCapture.forLogger(UnlockReconciler.class)) {
            var first = UnlockReconciler.reconcile(null, input).orElseThrow();

            assertNull(first.bars()[0].assignment(0).abilityId(), "bar 0 cleared");
            assertNull(first.bars()[1].assignment(0).abilityId(), "bar 1 cleared");
            long lines = logs.messages().stream()
                    .filter(m -> m.contains(UNLEARNED.toString()))
                    .count();
            assertEquals(1, lines,
                    "one line per distinct cleared id, got: " + logs.messages());

            // re-running over the reconciled state: nothing left to clear
            assertTrue(UnlockReconciler.reconcile(null, first).isEmpty(),
                    "an already-cleared state never churns again");
            assertEquals(1, logs.messages().stream()
                            .filter(m -> m.contains(UNLEARNED.toString())).count(),
                    "re-reconciling never re-logs (no log spam)");
        }
    }
}
