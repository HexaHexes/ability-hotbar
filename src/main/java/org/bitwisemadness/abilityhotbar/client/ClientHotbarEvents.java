package org.bitwisemadness.abilityhotbar.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;

/**
 * Client-only lifecycle hooks for the bar state cache.
 *
 * <p>Registered for the physical client only, so the server never touches
 * {@link ClientBarState}.</p>
 */
@EventBusSubscriber(modid = AbilityHotbar.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientHotbarEvents {

    private ClientHotbarEvents() {
    }

    /** Drop the cached bar when leaving a world so it never outlives its session. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBarState.clear();
    }
}
