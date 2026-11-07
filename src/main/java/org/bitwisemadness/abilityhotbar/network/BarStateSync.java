package org.bitwisemadness.abilityhotbar.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.abilityhotbar.UnlockReconciler;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;

/**
 * Server-side sync actions for the bar state (REQ-HOT-14, ADR-003).
 *
 * <p>Centralises the two send points: a lifecycle snapshot on the boundaries
 * core signals, and the authoritative echo after an accepted assignment.
 * Every path sweeps through {@link UnlockReconciler} first (hotbar#147), so
 * a slot whose ability is provably unlearned is cleared - saved and echoed -
 * on the next snapshot or apply without a tick loop.</p>
 */
public final class BarStateSync {

    private BarStateSync() {
    }

    /** Sends the player's current bar state to their client (unlock-reconciled first). */
    public static void sendSnapshot(ServerPlayer player) {
        BarState current = player.getData(BarStateAttachments.BAR_STATE);
        BarState effective = UnlockReconciler.reconcile(player, current).orElse(current);
        if (effective != current) {
            player.setData(BarStateAttachments.BAR_STATE, effective);
        }
        PacketDistributor.sendToPlayer(player, new BarStateSnapshotPayload(effective));
    }

    /** Persists an accepted assignment and echoes the authoritative state back. */
    public static void acceptAssignment(ServerPlayer player, BarState next) {
        persistAndEcho(player, reconciled(player, next));
    }

    /** Persists an accepted selection and echoes the authoritative state back. */
    public static void acceptSelection(ServerPlayer player, BarState next) {
        persistAndEcho(player, reconciled(player, next));
    }

    /** Persists an accepted bar switch and echoes the authoritative state back. */
    public static void acceptSwitch(ServerPlayer player, BarState next) {
        persistAndEcho(player, reconciled(player, next));
    }

    private static void persistAndEcho(ServerPlayer player, BarState effective) {
        player.setData(BarStateAttachments.BAR_STATE, effective);
        PacketDistributor.sendToPlayer(player, new BarStateSnapshotPayload(effective));
    }

    /** The sweep applied to every accepted apply (hotbar#147) - unchanged states pass through. */
    private static BarState reconciled(ServerPlayer player, BarState next) {
        return UnlockReconciler.reconcile(player, next).orElse(next);
    }
}
