package org.bitwisemadness.abilityhotbar.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.compat.CoreMasteryCompat;

/**
 * Client-only lifecycle hooks for the bar state cache and the mastery
 * snapshot.
 *
 * <p>Registered for the physical client only, so the server never touches
 * {@link ClientBarState} or the snapshot's client-side copy.</p>
 */
@EventBusSubscriber(modid = AbilityHotbar.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientHotbarEvents {

    private ClientHotbarEvents() {
    }

    /**
     * Drop the cached bar and clear the mastery snapshot when leaving a world
     * so neither outlives its session: the next world must never read the old
     * world's levels as proven unlock verdicts (Technomagy PR #13 review).
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBarState.clear();
        CoreMasteryCompat.reset();
    }
}
