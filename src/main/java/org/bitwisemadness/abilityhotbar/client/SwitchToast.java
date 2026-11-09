package org.bitwisemadness.abilityhotbar.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.config.ConfigDiagnostics;
import org.bitwisemadness.abilityhotbar.config.HotbarClientConfig;
import org.jetbrains.annotations.Nullable;

/**
 * The brief bar-name notification shown after a switch (REQ-HOT-22, #28 AC 4:
 * "optional brief hotbar-name notification on switch (CLIENT preference)" -
 * the toggle is {@code display.switchToast}).
 *
 * <p>Recorded by the input handler at the moment a switch is applied
 * optimistically, so the toast follows the <em>player's intent</em>: the
 * server's echo confirms it silently, and a rejected switch never
 * double-toasts on the way back. Read by the render layer each frame; the
 * window is wall-clock based and self-expiring, so nothing ever needs to
 * clear it and it can outlive neither a disconnect nor its own duration.</p>
 *
 * <p>Client-only. The visibility decision takes the clock as a parameter so
 * it is unit-testable without a game instance.</p>
 */
public final class SwitchToast {

    /**
     * How long the name stays on screen by default - "brief" per the
     * requirement; the live value is {@code display.switchToastDurationMs}.
     */
    public static final long DURATION_MS = 2500L;

    @Nullable
    private static volatile ResourceLocation barId;
    private static volatile long shownAt;

    private SwitchToast() {
    }

    /**
     * Records that {@code id}'s name should show. The CLIENT toggle
     * ({@code display.switchToast}) is the caller's gate, not this class's.
     *
     * @param id the bar that was switched to
     */
    public static void show(ResourceLocation id) {
        shownAt = System.currentTimeMillis();
        barId = id;
    }

    /**
     * @param now the current time in milliseconds (injected so tests need no
     *            game instance or sleeping)
     * @return whether the toast is on screen at {@code now}
     */
    public static boolean isVisible(long now) {
        ResourceLocation id = barId;
        if (id == null || now < shownAt) {
            return false;
        }
        // live display.switchToastDurationMs; the shipped default when the
        // spec is not loaded (headless fixtures), 0 = never shows
        long duration = ConfigDiagnostics.readOrDefault("display.switchToastDurationMs",
                () -> HotbarClientConfig.SWITCH_TOAST_DURATION_MS.get(), (int) DURATION_MS);
        return now - shownAt < duration;
    }

    /**
     * @return the name to draw - {@link BarName} resolved for the recorded
     *         bar, or an empty component when nothing was shown
     */
    public static Component message() {
        ResourceLocation id = barId;
        return id == null ? Component.empty() : BarName.of(id);
    }

    /**
     * @return the time of the last {@link #show} - package-private so the
     *         window fixtures can anchor on the exact clock instead of racing
     *         wall time
     */
    static long shownAt() {
        return shownAt;
    }

    /** Forgets any recorded switch - test-only, so fixtures start clean. */
    public static void reset() {
        barId = null;
        shownAt = 0L;
    }
}
