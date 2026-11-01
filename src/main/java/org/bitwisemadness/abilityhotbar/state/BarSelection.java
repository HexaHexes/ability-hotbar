package org.bitwisemadness.abilityhotbar.state;

import java.util.Optional;

/**
 * Server-authoritative slot selection (REQ-HOT-09/10, ADR-003).
 *
 * <p>Selection is independent of activation: selecting a slot never fires an
 * ability, and the server is the only place a selection is resolved. An
 * out-of-range request is rejected fail-closed (empty result, no state change).</p>
 */
public final class BarSelection {

    private BarSelection() {
    }

    /**
     * Resolves a selection request against the current state.
     *
     * @param current the player's current bar state
     * @param slot    the requested slot index
     * @return the state with the new selection, or empty when the request is invalid
     */
    public static Optional<BarState> select(BarState current, int slot) {
        if (current == null || slot < 0 || slot >= current.assignments().length) {
            return Optional.empty();
        }
        return Optional.of(current.withSelectedSlot(slot));
    }

    /**
     * Pure scroll-to-selection math for the client's Ctrl+wheel routing: wraps
     * one step within {@code [0, slotCount)}. The server re-validates the
     * resulting index, so this never widens authority.
     *
     * <p>Vanilla parity: the caller maps scroll-up to a negative direction
     * (move left toward slot 1) and scroll-down to positive (move right toward
     * the last slot) - see {@link #scrollDirection(double)}.</p>
     *
     * @param current   the currently selected slot
     * @param direction step direction: negative = left (lower index), positive = right (higher index)
     * @param slotCount the number of slots in the bar
     * @return the slot one step away, wrapping at the ends
     */
    public static int slotAfterScroll(int current, int direction, int slotCount) {
        if (slotCount <= 1) {
            return 0;
        }
        int step = direction > 0 ? 1 : -1;
        return Math.floorMod(current + step, slotCount);
    }

    /**
     * Maps a wheel scroll delta to a selection step, matching the vanilla
     * hotbar: scrolling up moves the selection left (toward slot 1), scrolling
     * down moves it right. A zero delta maps to zero (no movement).
     *
     * @param scrollDeltaY the scroll wheel's vertical delta (positive = scroll up)
     * @return -1 for scroll up (left), +1 for scroll down (right), 0 for none
     */
    public static int scrollDirection(double scrollDeltaY) {
        if (scrollDeltaY == 0.0) {
            return 0;
        }
        return scrollDeltaY > 0 ? -1 : 1;
    }
}
