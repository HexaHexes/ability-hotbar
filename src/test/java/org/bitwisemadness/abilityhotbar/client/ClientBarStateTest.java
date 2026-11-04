package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.bitwisemadness.abilityhotbar.state.BarState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The client convergence half of the rejection re-send contract (core#19
 * AC5, ADR-003/ADR-011): the client's view is written <b>only</b> from
 * received snapshots, so applying the server's answer - accepted, rejected
 * or lifecycle - always lands on the server's state, and a session ends with
 * the cache dropped so a stale bar never outlives it.
 */
class ClientBarStateTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ClientBarState.clear();
    }

    @Test
    void aReceivedSnapshotReplacesAnyDivergedView() {
        BarState diverged = BarState.empty(3).withSelectedSlot(2);
        BarState serverAnswer = BarState.empty(3).withSelectedSlot(0);

        ClientBarState.set(diverged); // optimistic view before the answer arrived
        ClientBarState.set(serverAnswer);

        assertSame(serverAnswer, ClientBarState.get(),
                "the server's echo must converge the client's view, not blend with it");
    }

    @Test
    void noSnapshotMeansNoGuessAndClearDropsTheSession() {
        assertNull(ClientBarState.get(), "before any snapshot the client must render nothing");

        BarState view = BarState.empty(2);
        ClientBarState.set(view);
        assertEquals(view, ClientBarState.get());

        ClientBarState.clear();
        assertNull(ClientBarState.get(), "a stale bar must never outlive its session");
    }
}
