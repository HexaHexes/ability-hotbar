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
 * tensura-iss-integration#51): carries the <b>bar and slot</b> - the menu
 * edits a bar that may not be the active one (its tab/chip selection is
 * client-side), so the slot alone could name a different bar's row
 * (play-test round). The server resolves the ability from its own state
 * (never a client-authoritative id or mode value), routes one cycle
 * through core's mode-change contract, and on ACTED reflects the
 * provider's new live mode into that slot's stored setting before
 * echoing the snapshot (the round-trip the UI reads). No adapter, an
 * unstored bar, an empty slot, or any refusal changes nothing and
 * echoes. A mode change never casts, resets selections, or drives
 * anything else - core's mode contract is charge-free by construction.
 *
 * @param barId the bar whose slot cycles (the keybind sends the active bar)
 * @param slot  the selected slot on that bar
 */
public record CycleModePayload(ResourceLocation barId, int slot)
        implements CustomPacketPayload {

    public static final Type<CycleModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "cycle_mode"));

    public static final StreamCodec<ByteBuf, CycleModePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, CycleModePayload::barId,
                    ByteBufCodecs.VAR_INT, CycleModePayload::slot,
                    CycleModePayload::new);

    @Override
    public Type<? extends CycleModePayload> type() {
        return TYPE;
    }

    /** Server handler: resolve from server state, run one cycle, echo. */
    public static void handle(CycleModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BarState current = player.getData(BarStateAttachments.BAR_STATE);
                SlotAssignment[] row = current.assignmentsOf(payload.barId());
                SlotAssignment assignment = row == null || payload.slot() < 0
                        || payload.slot() >= row.length ? null : row[payload.slot()];
                if (assignment == null || assignment.abilityId() == null
                        || !ModeChangeHandlers.has(assignment.abilityId())) {
                    BarStateSync.sendSnapshot(player);
                    return;
                }
                var result = ModeChangeHandlers.run(
                        new ModeChangeRequest(assignment.abilityId(),
                                payload.slot(), payload.barId()), player);
                if (result.outcome() != ActivationOutcome.ACTED) {
                    BarStateSync.sendSnapshot(player);
                    return;
                }
                // the SLOT-scoped read (core#133): the cycle stepped this
                // slot's own preset position, so store that slot's mode -
                // never another slot's (owner: each slot sets its mode)
                BarState next = ModeContract
                        .currentMode(player, assignment.abilityId(), payload.slot())
                        .map(mode -> current.withSlotMode(
                                payload.barId(), payload.slot(), mode))
                        .orElse(current);
                BarStateSync.acceptAssignment(player, next);
            }
        });
    }
}
