package org.bitwisemadness.abilityhotbar.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.config.HotbarServerConfig;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilityhotbar.state.BarSwitching;

/**
 * Client-to-server bar-switch request (REQ-HOT-22, ADR-012).
 *
 * <p>Carries the client's <em>intent</em> to make another bar active, named
 * <b>by id</b> - never by index - so a desynced window can never land on the
 * wrong bar. The server validates against its own registry and ceiling
 * through {@link BarSwitching}; an accepted switch persists and echoes the
 * authoritative state.</p>
 *
 * <p>Unlike the other requests, a <b>rejected</b> switch also echoes the
 * current state (sending nothing new - just the state as it stands). The
 * client applies its switch optimistically so the HUD moves without a
 * round trip; this echo is what reconciles that optimism within one round
 * trip when the server disagreed. Switching never activates anything: it
 * only moves the active bar.</p>
 *
 * @param barId the requested bar's id
 */
public record SwitchBarPayload(ResourceLocation barId) implements CustomPacketPayload {

    public static final Type<SwitchBarPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "switch_bar"));

    public static final StreamCodec<ByteBuf, SwitchBarPayload> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(SwitchBarPayload::new, SwitchBarPayload::barId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Server handler: validate, persist on accept, and always echo so an
     * optimistically-applied client view converges on the server's answer.
     */
    public static void handle(SwitchBarPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BarState current = player.getData(BarStateAttachments.BAR_STATE);
                BarSwitching.switchTo(current, payload.barId(), HotbarServerConfig.MAX_HOTBAR_COUNT.get())
                        .ifPresentOrElse(
                                next -> BarStateSync.acceptSwitch(player, next),
                                () -> BarStateSync.sendSnapshot(player));
            }
        });
    }
}
