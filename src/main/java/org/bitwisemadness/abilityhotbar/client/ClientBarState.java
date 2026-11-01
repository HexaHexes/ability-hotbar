package org.bitwisemadness.abilityhotbar.client;

import org.bitwisemadness.abilityhotbar.state.BarState;
import org.jetbrains.annotations.Nullable;

/**
 * The client's last-known copy of the local player's bar state (ADR-003).
 *
 * <p>Server-authoritative: this is only ever written from a received snapshot
 * payload, never edited locally. {@code null} means no snapshot has arrived
 * yet (or the session ended), and consumers must render nothing rather than a
 * guess.</p>
 *
 * <p>Loads only on the physical client; the payload handler that calls
 * {@link #set} is never invoked on a dedicated server.</p>
 */
public final class ClientBarState {

    @Nullable
    private static volatile BarState current;

    private ClientBarState() {
    }

    /** Stores a snapshot received from the server. */
    public static void set(BarState state) {
        current = state;
    }

    /** @return the last snapshot, or null when none has been received. */
    @Nullable
    public static BarState get() {
        return current;
    }

    /** Drops the cache (on disconnect) so a stale bar never outlives its session. */
    public static void clear() {
        current = null;
    }
}
