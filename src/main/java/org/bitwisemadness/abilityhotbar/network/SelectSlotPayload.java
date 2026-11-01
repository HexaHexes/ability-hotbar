package org.bitwisemadness.abilityhotbar.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.state.BarSelection;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;

/**
 * Client-to-server selection request (REQ-HOT-09/10).
 *
 * <p>Carries the client's <em>intent</em> to select a slot (Ctrl+wheel). The
 * server resolves it through {@link BarSelection} - an out-of-range slot is
 * dropped fail-closed - then echoes the authoritative state back. Selecting
 * never fires an ability; it only moves the selected-slot cursor.</p>
 *
 * @param slot the requested slot index
 */
public record SelectSlotPayload(int slot) implements CustomPacketPayload {

    public static final Type<SelectSlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "select_slot"));

    public static final StreamCodec<ByteBuf, SelectSlotPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(SelectSlotPayload::new, SelectSlotPayload::slot);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server handler: resolve, mutate, echo. An out-of-range request is dropped. */
    public static void handle(SelectSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BarState current = player.getData(BarStateAttachments.BAR_STATE);
                BarSelection.select(current, payload.slot())
                        .ifPresent(next -> BarStateSync.acceptSelection(player, next));
            }
        });
    }
}
