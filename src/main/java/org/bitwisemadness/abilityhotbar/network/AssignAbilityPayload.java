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
 * Client-to-server assignment request (REQ-HOT-14).
 *
 * <p>Carries the client's <em>intent</em> to assign or clear a slot. The
 * server resolves it through {@link BarAssignments} - out-of-range slots and
 * malformed ids are dropped fail-closed - then echoes the authoritative state
 * back. A refused request changes nothing and sends nothing.</p>
 *
 * @param slot        the target slot index
 * @param abilityId   the requested ability id, blank to clear the slot
 */
public record AssignAbilityPayload(int slot, String abilityId) implements CustomPacketPayload {

    public static final Type<AssignAbilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "assign_ability"));

    public static final StreamCodec<ByteBuf, AssignAbilityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AssignAbilityPayload::slot,
            ByteBufCodecs.STRING_UTF8, AssignAbilityPayload::abilityId,
            AssignAbilityPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server handler: resolve, mutate, echo. Malformed input is dropped silently. */
    public static void handle(AssignAbilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BarState current = player.getData(BarStateAttachments.BAR_STATE);
                BarAssignments.assign(current, payload.slot(), payload.abilityId())
                        .ifPresent(next -> BarStateSync.acceptAssignment(player, next));
            }
        });
    }
}
