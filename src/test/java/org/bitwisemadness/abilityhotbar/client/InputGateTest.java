package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Input-suppression invariant (REQ-HOT-18): a hotbar control acts only with no
 * screen open and a player loaded, and modifier-gated controls only while their
 * modifier is held. The pure decision is what every handler routes through.
 */
class InputGateTest {

    @Test
    void actsInGameWithNoScreen() {
        assertTrue(InputGate.capturable(false, true, true, false), "plain control in-game");
        assertTrue(InputGate.capturable(false, true, true, true), "modifier control in-game, modifier held");
    }

    @Test
    void neverActsWithAnyScreenOpen() {
        assertFalse(InputGate.capturable(true, true, true, false), "chat/inventory/GUI open");
        assertFalse(InputGate.capturable(true, true, true, true), "screen open even with modifier held");
    }

    @Test
    void neverActsWithoutAPlayer() {
        assertFalse(InputGate.capturable(false, false, true, false));
        assertFalse(InputGate.capturable(false, false, true, true));
    }

    @Test
    void modifierControlRequiresTheModifier() {
        // modifier required but not held -> suppressed; plain wheel/keys stay vanilla
        assertFalse(InputGate.capturable(false, true, false, true), "Ctrl+wheel without Ctrl");
        // modifier held -> allowed (and only one reaction: the caller consumes the event)
        assertTrue(InputGate.capturable(false, true, false, false), "plain control ignores the modifier");
    }

    @Test
    void screenAndPlayerBeatTheModifier() {
        // Even with the modifier held, an open screen or a missing player suppresses.
        assertFalse(InputGate.capturable(true, true, true, true));
        assertFalse(InputGate.capturable(false, false, true, true));
    }
}
