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
 * Client-to-server preset deletion (hotbar#29): drops the named preset from
 * the attachment; bars, slots and modes are never touched. An unknown name
 * or invalid input changes nothing and echoes the authoritative snapshot
 * (ADR-003: every rejection re-sends the state).
 *
 * @param name the candidate preset name (raw client input)
 */
public record DeletePresetPayload(String name) implements CustomPacketPayload {

    public static final Type<DeletePresetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "delete_preset"));

    public static final StreamCodec<ByteBuf, DeletePresetPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(DeletePresetPayload::new, DeletePresetPayload::name);

    @Override
    public Type<? extends DeletePresetPayload> type() {
        return TYPE;
    }

    /** Server handler: validate the name, delete, echo. */
    public static void handle(DeletePresetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BarState current = player.getData(BarStateAttachments.BAR_STATE);
                String name = LoadoutPreset.validName(payload.name());
                BarState next = name == null ? null : LoadoutPresets.delete(current, name);
                if (next != null) {
                    BarStateSync.acceptAssignment(player, next);
                } else {
                    BarStateSync.sendSnapshot(player);
                }
            }
        });
    }
}
