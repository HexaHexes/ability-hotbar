package org.bitwisemadness.abilityhotbar.network;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.UnlockRule;
import org.bitwisemadness.abilityhotbar.state.BarAssignments;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;

/**
 * Client-to-server assignment request (REQ-HOT-14, hotbar#27).
 *
 * <p>Carries the menu's <em>intent</em>: assign, replace or clear a slot of
 * a <b>named</b> bar (ADR-012 id targeting - the screen may manage any
 * assignable bar, not only the active one). The server resolves it through
 * {@link BarAssignments} - a nonexistent or non-assignable bar, an
 * out-of-range slot, a scoped ability on the wrong bar and a malformed id
 * are all dropped fail-closed - then echoes the authoritative state back.</p>
 *
 * <p><b>Every</b> answer is a snapshot: accepted requests echo the mutated
 * state, rejected ones echo the current state (hotbar#27 AC4 - a stale menu
 * converges on server truth instead of keeping a state the server refused).
 * Assignment never activates anything; a blank id clears the slot and
 * destroys nothing else.</p>
 *
 * @param slot        the target slot index
 * @param abilityId   the requested ability id, blank to clear the slot
 * @param barId       the requested target bar
 */
public record AssignAbilityPayload(int slot, String abilityId, ResourceLocation barId)
        implements CustomPacketPayload {

    public static final Type<AssignAbilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "assign_ability"));

    public static final StreamCodec<ByteBuf, AssignAbilityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AssignAbilityPayload::slot,
            ByteBufCodecs.STRING_UTF8, AssignAbilityPayload::abilityId,
            ResourceLocation.STREAM_CODEC, AssignAbilityPayload::barId,
            AssignAbilityPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Server handler: the unlock gate (ability-hotbar#135), then resolve,
     * then always echo the authoritative state. A proven-unlearned ability
     * never lands in a slot (crafted payloads included); unknown answers
     * pass through - core's rule, never a guess.
     */
    public static void handle(AssignAbilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BarState current = player.getData(BarStateAttachments.BAR_STATE);
                ResourceLocation abilityId = ResourceLocation.tryParse(payload.abilityId());
                Optional<BarState> next = abilityId == null
                        || !UnlockRule.provenUnlearned(
                                AbilityUnlocks.isLearned(player, abilityId))
                                ? BarAssignments.assign(current, payload.barId(),
                                        payload.slot(), payload.abilityId())
                                : Optional.empty();
                next.ifPresentOrElse(
                        applied -> BarStateSync.acceptAssignment(player, applied),
                        () -> BarStateSync.sendSnapshot(player));
            }
        });
    }
}
