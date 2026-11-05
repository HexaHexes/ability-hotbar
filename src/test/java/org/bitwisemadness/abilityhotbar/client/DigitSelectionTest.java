package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Ctrl+digit's selection half (hotbar#108): an in-range index that is not
 * already selected yields a request for exactly that slot; already selected,
 * beyond the drawn bar, or negative yields none (vanilla parity - digits
 * past the bar are inert - and no redundant packets). Config-free by
 * design: the drawn count is a parameter, mirroring the scroll path's
 * clamp; the server half stays fail-closed in {@code BarSelection.select}.
 */
class DigitSelectionTest {

    @Test
    void inRangeDifferentIndexRequestsThatSlot() {
        var payload = HotbarInputHandler.digitSelection(3, 0, 9);

        assertTrue(payload.isPresent(), "an in-range digit parks the cursor");
        assertEquals(3, payload.get().slot(), "exactly the pressed index");
    }

    @Test
    void alreadySelectedIndexSendsNothing() {
        assertTrue(HotbarInputHandler.digitSelection(5, 5, 9).isEmpty(),
                "the cursor is already there - no redundant packet");
    }

    @Test
    void indicesBeyondTheDrawnBarAreInert() {
        assertTrue(HotbarInputHandler.digitSelection(9, 0, 9).isEmpty(),
                "renderCount is exclusive - the digit past the bar does nothing");
        assertTrue(HotbarInputHandler.digitSelection(12, 0, 9).isEmpty());
        assertTrue(HotbarInputHandler.digitSelection(0, 0, 0).isEmpty(),
                "nothing drawn -> nothing to park on");
    }

    @Test
    void negativeIndexFailsClosed() {
        assertTrue(HotbarInputHandler.digitSelection(-1, 0, 9).isEmpty());
    }
}
