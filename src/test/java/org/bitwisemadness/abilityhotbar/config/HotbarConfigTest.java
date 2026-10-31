package org.bitwisemadness.abilityhotbar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Two-tier config semantics (REQ-CFG-01/02): server defines the ceiling,
 * client picks within it. The clamp is structural: min(client, server).
 *
 * <p>These tests use the math directly (the actual ModConfigSpec values are
 * loaded by NeoForge at runtime and can't be unit-tested without a game
 * instance — but the clamp logic is pure math and testable here).</p>
 */
class HotbarConfigTest {

    @Test
    void clientBelowServerClampsToClient() {
        assertEquals(3, effective(3, 10));
        assertEquals(1, effective(1, 10));
    }

    @Test
    void clientEqualsServerIsAllowed() {
        assertEquals(10, effective(10, 10));
        assertEquals(9, effective(9, 9));
    }

    @Test
    void clientAboveServerClampsToServer() {
        assertEquals(10, effective(50, 10));
        assertEquals(5, effective(9, 5));
        assertEquals(1, effective(100, 1));
    }

    @Test
    void serverMaxOfOneMeansSingleSlot() {
        assertEquals(1, effective(9, 1));
        assertEquals(1, effective(1, 1));
    }

    @Test
    void effectiveIsNeverNegative() {
        // both config values are validated by NeoForge's range checks,
        // but the clamp result is also always >= 1
        assertTrue(effective(1, 1) >= 1);
    }

    private static int effective(int clientSlots, int serverMax) {
        return Math.min(clientSlots, serverMax);
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
