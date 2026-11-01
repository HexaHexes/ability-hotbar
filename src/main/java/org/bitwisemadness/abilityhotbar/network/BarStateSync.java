package org.bitwisemadness.abilityhotbar.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;

/**
 * Server-side sync actions for the bar state (REQ-HOT-14, ADR-003).
 *
 * <p>Centralises the two send points: a lifecycle snapshot on the boundaries
 * core signals, and the authoritative echo after an accepted assignment.</p>
 */
public final class BarStateSync {

    private BarStateSync() {
    }

    /** Sends the player's current bar state to their client. */
    public static void sendSnapshot(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new BarStateSnapshotPayload(player.getData(BarStateAttachments.BAR_STATE)));
    }

    /** Persists an accepted assignment and echoes the authoritative state back. */
    public static void acceptAssignment(ServerPlayer player, BarState next) {
        player.setData(BarStateAttachments.BAR_STATE, next);
        PacketDistributor.sendToPlayer(player, new BarStateSnapshotPayload(next));
    }
}
