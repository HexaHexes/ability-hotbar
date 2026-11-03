package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The switch notification's visibility window (REQ-HOT-22, #28 AC 4): it
 * appears when a switch is recorded, stays for its brief duration, and
 * expires without anything needing to clear it. The clock is injected, so no
 * test sleeps or depends on wall time beyond a millisecond race.
 */
class SwitchToastTest {

    @BeforeEach
    @AfterEach
    void resetToast() {
        SwitchToast.reset();
    }

    @Test
    void nothingIsVisibleBeforeAnySwitch() {
        assertFalse(SwitchToast.isVisible(System.currentTimeMillis()));
        assertEquals("", SwitchToast.message().getString());
    }

    @Test
    void toastAppearsOnShowAndExpiresAfterItsDuration() {
        SwitchToast.show(HotbarRegistry.builtinBarId(1));
        long shown = SwitchToast.shownAt();

        assertTrue(SwitchToast.isVisible(shown), "visible the moment it is shown");
        assertTrue(SwitchToast.isVisible(shown + SwitchToast.DURATION_MS - 1),
                "still visible just inside the window");
        assertFalse(SwitchToast.isVisible(shown + SwitchToast.DURATION_MS),
                "gone at the window's end - self-expiring, nothing to clear");
        assertFalse(SwitchToast.isVisible(shown - 1),
                "a time from before the show never shows a stale toast");
    }

    @Test
    void messageNamesTheBarThatWasSwitchedTo() {
        SwitchToast.show(HotbarRegistry.builtinBarId(3));
        Component message = SwitchToast.message();

        assertTrue(message.getContents() instanceof TranslatableContents);
        assertEquals(AbilityHotbar.MOD_ID + ".bar_3",
                ((TranslatableContents) message.getContents()).getKey());
    }

    @Test
    void resetForgetsTheRecordedSwitch() {
        SwitchToast.show(HotbarRegistry.builtinBarId(0));
        SwitchToast.reset();

        assertFalse(SwitchToast.isVisible(System.currentTimeMillis()));
        assertEquals("", SwitchToast.message().getString());
    }

    @Test
    void showingAgainUpdatesTheRecordedBarAndClock() {
        SwitchToast.show(HotbarRegistry.builtinBarId(0));
        long firstShown = SwitchToast.shownAt();
        SwitchToast.show(HotbarRegistry.builtinBarId(4));
        long secondShown = SwitchToast.shownAt();

        assertTrue(secondShown >= firstShown, "the second show moves the clock forward");
        assertTrue(SwitchToast.isVisible(secondShown),
                "the freshly shown toast is visible");
        assertEquals(AbilityHotbar.MOD_ID + ".bar_4",
                ((TranslatableContents) SwitchToast.message().getContents()).getKey(),
                "the message names the latest bar, not the first");
    }
}
