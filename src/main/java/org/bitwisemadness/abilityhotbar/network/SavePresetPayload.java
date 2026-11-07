package org.bitwisemadness.abilityhotbar.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilityhotbar.state.LoadoutPreset;
import org.bitwisemadness.abilityhotbar.state.LoadoutPresets;

/**
 * Client-to-server preset save (hotbar#29): snapshots the stored active
 * bar's row under a display name. The name is validated server-side first
 * (trim, 1..{@value LoadoutPreset#MAX_NAME_LENGTH} chars); an invalid name
 * or an unstored bar changes nothing and the authoritative snapshot is
 * echoed back (ADR-003: every rejection re-sends the state).
 *
 * @param name the candidate preset name (raw client input)
 */
public record SavePresetPayload(String name) implements CustomPacketPayload {

    public static final Type<SavePresetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "save_preset"));

    public static final StreamCodec<ByteBuf, SavePresetPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(SavePresetPayload::new, SavePresetPayload::name);

    @Override
    public Type<? extends SavePresetPayload> type() {
        return TYPE;
    }

    /** Server handler: validate the name, snapshot the active bar, echo. */
    public static void handle(SavePresetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BarState current = player.getData(BarStateAttachments.BAR_STATE);
                String name = LoadoutPreset.validName(payload.name());
                if (name == null) {
                    BarStateSync.sendSnapshot(player);
                    return;
                }
                BarState next = LoadoutPresets.save(current, current.activeBarId(), name);
                if (next != null) {
                    BarStateSync.acceptAssignment(player, next);
                } else {
                    BarStateSync.sendSnapshot(player);
                }
            }
        });
    }
}
