package org.bitwisemadness.abilityhotbar.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.UnlockRule;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilityhotbar.state.LoadoutPreset;
import org.bitwisemadness.abilityhotbar.state.LoadoutPresets;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.slf4j.Logger;

/**
 * Client-to-server preset recall (hotbar#29): applies the named preset to
 * its saved bar through {@link LoadoutPresets} - a pure state transition
 * with zero casts and zero mode-change events (REQ-HOT-22's invariant):
 * stored entries land verbatim (inert ids preserved), clamped to the bar's
 * current slot count, after name/assignability validation. Diagnostics
 * (shape notes, inert entries, entries the standing unlock sweep will clear)
 * are logged server-side and sent to the player; the authoritative snapshot
 * echoes through the normal reconciled path.
 *
 * @param name the candidate preset name (raw client input)
 */
public record RecallPresetPayload(String name) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<RecallPresetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "recall_preset"));

    public static final StreamCodec<ByteBuf, RecallPresetPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(RecallPresetPayload::new, RecallPresetPayload::name);

    @Override
    public Type<? extends RecallPresetPayload> type() {
        return TYPE;
    }

    /** Server handler: validate, apply, diagnose, echo. */
    public static void handle(RecallPresetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BarState current = player.getData(BarStateAttachments.BAR_STATE);
                String name = LoadoutPreset.validName(payload.name());
                if (name == null) {
                    BarStateSync.sendSnapshot(player);
                    return;
                }
                LoadoutPresets.RecallResult result = LoadoutPresets.recall(current, name);
                if (result == null) {
                    BarStateSync.sendSnapshot(player);
                    return;
                }
                BarStateSync.acceptAssignment(player, result.state());
                for (String line : diagnostics(player, name, result)) {
                    LOGGER.info("Loadout recall {}: {}", name, line);
                    player.sendSystemMessage(Component.literal(line)
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        });
    }

    /**
     * The recall's player-facing notes: shape notes from the apply, entries
     * kept inert (provider absent), and entries the standing unlock sweep
     * clears at this very echo (hotbar#147) - so "stored but locked" is
     * never a silent surprise.
     */
    private static List<String> diagnostics(ServerPlayer player, String name,
            LoadoutPresets.RecallResult result) {
        List<String> lines = new ArrayList<>(result.notes());
        SlotAssignment[] row = result.state().assignmentsOf(result.barId());
        if (row != null) {
            lines.addAll(LoadoutPresets.inertNotes(row));
            for (int i = 0; i < row.length; i++) {
                ResourceLocation id = row[i].abilityId();
                if (id != null && UnlockRule.provenUnlearned(
                        AbilityUnlocks.isLearned(player, id))) {
                    lines.add("slot " + (i + 1) + " holds " + id
                            + " locked (not unlocked) - it is cleared from the bar by the"
                            + " unlock sweep and never auto-casts");
                }
            }
        }
        return lines;
    }
}
