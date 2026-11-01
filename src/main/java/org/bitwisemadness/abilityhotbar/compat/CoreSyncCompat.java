package org.bitwisemadness.abilityhotbar.compat;

import net.neoforged.neoforge.common.NeoForge;
import org.bitwisemadness.abilitycore.sync.AbilitySyncNeededEvent;
import org.bitwisemadness.abilityhotbar.network.BarStateSync;

/**
 * Core integration for the bar snapshot (REQ-HOT-14, ADR-003).
 *
 * <p>Loads only when {@code ability_core} is present: {@link #register()} is
 * called behind a {@code ModList.isLoaded} guard, so when core is absent this
 * class is never loaded and its reference to
 * {@link AbilitySyncNeededEvent} never resolves. That keeps the mod's
 * optional-runtime invariant intact.</p>
 */
public final class CoreSyncCompat {

    private CoreSyncCompat() {
    }

    /** Subscribes the snapshot sender to core's lifecycle sync signal. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(CoreSyncCompat::onSyncNeeded);
    }

    private static void onSyncNeeded(AbilitySyncNeededEvent event) {
        BarStateSync.sendSnapshot(event.player());
    }
}
