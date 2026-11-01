package org.bitwisemadness.abilityhotbar.compat;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.sync.ActivateAbilityPayload;

/**
 * Core activation routing (REQ-HOT-11, ADR-003).
 *
 * <p>When {@code ability_core} is present, forwards a resolved activation to
 * core's serverbound {@link ActivateAbilityPayload}, which the server resolves
 * through the authoritative activation pipeline. The client never holds
 * activation authority - it sends intent only.</p>
 *
 * <p>Loaded only behind a {@link #isPresent()} guard at the call site, so when
 * core is absent this class is never asked to activate and its reference to
 * core payload types never resolves - the mod's optional-runtime invariant
 * holds.</p>
 */
public final class CoreActivationCompat {

    private CoreActivationCompat() {
    }

    /** @return true when ability_core is present and activation can be routed through it. */
    public static boolean isPresent() {
        return ModList.get().isLoaded("ability_core");
    }

    /**
     * Sends an activation intent to the server through core. Callers must gate
     * on {@link #isPresent()}.
     *
     * @param abilityId the ability to activate
     * @param source    where the activation originates (wire-mapped by core)
     * @param slot      the hotbar slot the ability was resolved from, or -1
     */
    public static void activate(ResourceLocation abilityId, ActivationSource source, int slot) {
        PacketDistributor.sendToServer(new ActivateAbilityPayload(
                abilityId.toString(), source.wireName(), slot, System.currentTimeMillis()));
    }
}
