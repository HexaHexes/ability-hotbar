package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilityhotbar.client.HotbarInputHandler.HeldActivation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The press/release pairing behind the native-parity release channel
 * (core#159): a press records its hold under the key index, the key-up
 * claims it exactly once, and a second press drained in one tick flushes
 * the displaced record first so press/release pairs stay 1:1 on the wire
 * (a multi-tap is press, release, press - never press, press, two
 * releases out of order).
 */
class HoldTrackingTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final HeldActivation FIRST =
            new HeldActivation(FIREBALL, ActivationSource.DIRECT_SLOT, 3, null);
    private static final HeldActivation SECOND =
            new HeldActivation(FIREBALL, ActivationSource.DIRECT_SLOT, 4, "burst");

    @BeforeEach
    void reset() {
        HotbarInputHandler.clearHolds();
    }

    @Test
    void keyUpClaimsTheHoldExactlyOnce() {
        assertEquals(null, HotbarInputHandler.recordHold(0, FIRST),
                "the first press displaces nothing");
        assertNotNull(HotbarInputHandler.claimHold(0), "the key-up finds its hold");
        assertNull(HotbarInputHandler.claimHold(0),
                "a duplicated key-up never sends a second release");
    }

    @Test
    void secondPressFlushesTheDisplacedHold() {
        HotbarInputHandler.recordHold(0, FIRST);
        HeldActivation displaced = HotbarInputHandler.recordHold(0, SECOND);

        assertEquals(FIRST, displaced,
                "the earlier press must be released before the new one records");
        assertEquals(SECOND, HotbarInputHandler.claimHold(0),
                "the newest press owns the key afterwards");
        assertNull(HotbarInputHandler.claimHold(0));
    }

    @Test
    void keysAreIndependentHolds() {
        HotbarInputHandler.recordHold(0, FIRST);
        HeldActivation selected = new HeldActivation(FIREBALL,
                ActivationSource.SELECTED_SLOT, 7, null);
        assertEquals(null, HotbarInputHandler.recordHold(
                HotbarInputHandler.SELECTED_KEY, selected));

        assertEquals(FIRST, HotbarInputHandler.claimHold(0));
        assertNull(HotbarInputHandler.claimHold(0),
                "releasing one key must not touch another");
        assertEquals(selected, HotbarInputHandler.claimHold(
                HotbarInputHandler.SELECTED_KEY));
    }

    @Test
    void clearHoldsDropsEverythingWithoutClaiming() {
        HotbarInputHandler.recordHold(0, FIRST);
        HotbarInputHandler.clearHolds();
        assertNull(HotbarInputHandler.claimHold(0),
                "a dropped record (no connection to notify) releases nothing");
    }
}
