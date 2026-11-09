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
import org.bitwisemadness.abilityhotbar.state.Bar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.jetbrains.annotations.Nullable;

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
                // Unset instances follow the live projection, so a cycle
                // would move all of them at once (owner's report): freeze
                // them at what they showed - only the cycled slot moves
                String before = ModeContract.currentMode(player, assignment.abilityId())
                        .orElse(null);
                var result = ModeChangeHandlers.run(
                        new ModeChangeRequest(assignment.abilityId(),
                                payload.slot(), payload.barId()), player);
                if (result.outcome() != ActivationOutcome.ACTED) {
                    BarStateSync.sendSnapshot(player);
                    return;
                }
                BarState next = frozenUnsetModes(current, assignment.abilityId(), before);
                // the SLOT-scoped read (core#133): the cycle stepped this
                // slot's own preset position, so store that slot's mode -
                // never another slot's (owner: each slot sets its mode)
                BarState cycled = ModeContract
                        .currentMode(player, assignment.abilityId(), payload.slot())
                        .map(mode -> next.withSlotMode(
                                payload.barId(), payload.slot(), mode))
                        .orElse(next);
                BarStateSync.acceptAssignment(player, cycled);
            }
        });
    }

    /**
     * The state with every <b>still-unset</b> slot of {@code abilityId}
     * holding {@code mode} (the pre-cycle live value): those slots were
     * displaying it through the hydration fill, so freezing them keeps
     * their display exactly as it was while the cycled slot moves. Null
     * mode (no projection) changes nothing. Package-private for the
     * fixture (the handler itself needs a ServerPlayer).
     */
    static BarState frozenUnsetModes(BarState state, ResourceLocation abilityId,
            @Nullable String mode) {
        if (mode == null) {
            return state;
        }
        BarState out = state;
        for (Bar bar : state.bars()) {
            SlotAssignment[] row = bar.assignments();
            SlotAssignment[] next = null;
            for (int slot = 0; slot < row.length; slot++) {
                SlotAssignment assignment = row[slot];
                if (assignment != null && assignment.isAssigned()
                        && abilityId.equals(assignment.abilityId())
                        && assignment.mode() == null) {
                    if (next == null) {
                        next = row.clone();
                    }
                    next[slot] = assignment.withMode(mode);
                }
            }
            if (next != null) {
                out = out.withRow(bar.id(), next);
            }
        }
        return out;
    }
}
