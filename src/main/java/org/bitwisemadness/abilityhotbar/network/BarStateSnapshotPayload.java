package org.bitwisemadness.abilityhotbar.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.client.ClientBarState;
import org.bitwisemadness.abilityhotbar.state.BarState;

/**
 * Server-to-client full bar-state snapshot (REQ-HOT-14, ADR-003).
 *
 * <p>Sent on the sync boundaries core signals (login, respawn, dimension
 * change) and echoed after a server-accepted assignment. The client cache is
 * replaced wholesale - the server is the only source of truth.</p>
 *
 * @param state the authoritative bar state
 */
public record BarStateSnapshotPayload(BarState state) implements CustomPacketPayload {

    public static final Type<BarStateSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "bar_state_snapshot"));

    public static final StreamCodec<ByteBuf, BarStateSnapshotPayload> STREAM_CODEC =
            BarState.STREAM_CODEC.map(BarStateSnapshotPayload::new, BarStateSnapshotPayload::state);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client handler: store the snapshot. Only ever invoked on a physical client. */
    public static void handle(BarStateSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBarState.set(payload.state()));
    }
}
