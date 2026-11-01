package org.bitwisemadness.abilityhotbar.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;

/**
 * The hotbar's network channel (REQ-HOT-14, ADR-003).
 *
 * <p>Own payloads on the org-idiomatic {@code versioned("1").optional()}
 * registrar: {@code optional()} tolerates channel absence (mod on one side
 * only) but never version skew, so codec changes require a version bump
 * shipped to both sides together - the same contract core uses.</p>
 */
@EventBusSubscriber(modid = AbilityHotbar.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class HotbarNetwork {

    private HotbarNetwork() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AbilityHotbar.MOD_ID)
                .versioned("1")
                .optional();

        registrar.playToClient(
                BarStateSnapshotPayload.TYPE,
                BarStateSnapshotPayload.STREAM_CODEC,
                BarStateSnapshotPayload::handle
        );
        registrar.playToServer(
                AssignAbilityPayload.TYPE,
                AssignAbilityPayload.STREAM_CODEC,
                AssignAbilityPayload::handle
        );
    }
}
