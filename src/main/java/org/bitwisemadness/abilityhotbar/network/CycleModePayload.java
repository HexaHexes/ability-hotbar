package org.bitwisemadness.abilityhotbar.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ModeChangeHandlers;
import org.bitwisemadness.abilitycore.api.ModeChangeRequest;
import org.bitwisemadness.abilitycore.api.ModeContract;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;

/**
 * Client-to-server ability-mode cycle request (REQ-HOT-16, tracked as
 * tensura-iss-integration#51): carries only the <b>slot</b> - the server
 * resolves the ability from its own state (never a client-authoritative id
 * or mode value), routes one cycle through core's mode-change contract, and
 * on ACTED reflects the provider's new live mode into that slot's stored
 * setting before echoing the snapshot (the round-trip the UI reads). No
 * adapter, an empty slot, or any refusal changes nothing and echoes.
 * A mode change never casts, resets selections, or drives anything else -
 * core's mode contract is charge-free by construction.
 *
 * @param slot the selected slot on the player's active bar
 */
public record CycleModePayload(int slot) implements CustomPacketPayload {

    public static final Type<CycleModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "cycle_mode"));

    public static final StreamCodec<ByteBuf, CycleModePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(CycleModePayload::new, CycleModePayload::slot);

    @Override
    public Type<? extends CycleModePayload> type() {
        return TYPE;
    }

    /** Server handler: resolve from server state, run one cycle, echo. */
    public static void handle(CycleModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BarState current = player.getData(BarStateAttachments.BAR_STATE);
                SlotAssignment assignment = current.assignment(payload.slot());
                if (assignment == null || assignment.abilityId() == null
                        || !ModeChangeHandlers.has(assignment.abilityId())) {
                    BarStateSync.sendSnapshot(player);
                    return;
                }
                var result = ModeChangeHandlers.run(
                        new ModeChangeRequest(assignment.abilityId(), payload.slot()), player);
                if (result.outcome() != ActivationOutcome.ACTED) {
                    BarStateSync.sendSnapshot(player);
                    return;
                }
                BarState next = ModeContract.currentMode(player, assignment.abilityId())
                        .map(mode -> current.withSlotMode(payload.slot(), mode))
                        .orElse(current);
                BarStateSync.acceptAssignment(player, next);
            }
        });
    }
}
