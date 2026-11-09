package org.bitwisemadness.abilityhotbar.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.state.BarAssignments;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;

/**
 * Client-to-server slot reorder (owner: drag one hotbar slot onto another).
 *
 * <p>Carries only the <em>intent</em> - bar, source, target: the server
 * swaps the two cells from its own state, so the stored modes travel with
 * their abilities and nothing the client claims about the cells is
 * trusted. Every answer is a snapshot - accepted requests echo the mutated
 * state, rejected ones echo current truth - the same convergence rule as
 * assignment (hotbar#27 AC4). A non-assignable bar, an out-of-range index
 * and a self-swap refuse fail-closed through {@link BarAssignments}.</p>
 *
 * @param barId  the bar being reordered
 * @param from   the dragged slot index
 * @param to     the drop-target slot index
 */
public record ReorderSlotsPayload(ResourceLocation barId, int from, int to)
        implements CustomPacketPayload {

    public static final Type<ReorderSlotsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "reorder_slots"));

    public static final StreamCodec<ByteBuf, ReorderSlotsPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ReorderSlotsPayload::barId,
            ByteBufCodecs.VAR_INT, ReorderSlotsPayload::from,
            ByteBufCodecs.VAR_INT, ReorderSlotsPayload::to,
            ReorderSlotsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server handler: swap fail-closed, then always echo the authoritative state. */
    public static void handle(ReorderSlotsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BarState current = player.getData(BarStateAttachments.BAR_STATE);
                BarAssignments.swap(current, payload.barId(), payload.from(), payload.to())
                        .ifPresentOrElse(
                                applied -> BarStateSync.acceptAssignment(player, applied),
                                () -> BarStateSync.sendSnapshot(player));
            }
        });
    }
}
