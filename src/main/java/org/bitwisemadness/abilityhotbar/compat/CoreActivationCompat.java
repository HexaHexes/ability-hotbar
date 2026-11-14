package org.bitwisemadness.abilityhotbar.compat;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.sync.ActivateAbilityPayload;
import org.bitwisemadness.abilitycore.sync.ReleaseAbilityPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Core activation routing (REQ-HOT-11, ADR-003).
 *
 * <p>When {@code ability_core} is present, forwards a resolved activation to
 * core's serverbound {@link ActivateAbilityPayload}, which the server resolves
 * through the authoritative activation pipeline. The key-up of a held
 * activation rides {@link ReleaseAbilityPayload} (core#159's native-parity
 * release channel). The client never holds activation authority - it sends
 * intent only.</p>
 *
 * <p>An activation may carry a <em>target hint</em> (hotbar#94): the entity
 * the player is aiming at, in core's {@code TargetContext} wire vocabulary
 * (kind {@code entity}, params {@code entity=<uuid>}). A null hint writes
 * blank kind/params, which core's {@code toRequest} reads as "no target
 * intent" - byte-identical to the request this class sent before hints
 * existed, so untargeted activations are unchanged on the wire.</p>
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
     * @param target    the entity the player aims at, or null for no hint
     *                  (only ever non-null for {@code targeted}-tagged
     *                  abilities - see {@code TargetHints})
     */
    public static void activate(ResourceLocation abilityId, ActivationSource source, int slot,
            @Nullable UUID target, @Nullable String mode) {
        PacketDistributor.sendToServer(
                payload(abilityId, source, slot, System.currentTimeMillis(), target, mode));
    }

    /**
     * Sends a release intent (key-up) to the server through core - the
     * wire twin of {@link #activate} that ends whatever the press started
     * (native-parity release channel, core#159). Callers must gate on
     * {@link #isPresent()}. Deliberately carries <b>no target hint</b>: the
     * crosshair may have moved while the key was held, and a stop must
     * never fail on targeting.
     *
     * @param abilityId the ability whose hold to end
     * @param source    the source of the original press (wire-mapped by core)
     * @param slot      the hotbar slot the press resolved from, or -1
     * @param mode      the slot's validated mode setting at press, or null
     */
    public static void release(ResourceLocation abilityId, ActivationSource source, int slot,
            @Nullable String mode) {
        PacketDistributor.sendToServer(
                releasePayload(abilityId, source, slot, System.currentTimeMillis(), mode));
    }

    /**
     * Builds the release wire payload - the testable half of
     * {@link #release} (the fixtures assert the request core builds from
     * it without a connection).
     *
     * @param mode the press's mode context, or null: blank writes core's
     *             no-mode wire form exactly like the press half
     */
    static ReleaseAbilityPayload releasePayload(ResourceLocation abilityId, ActivationSource source,
            int slot, long clientStamp, @Nullable String mode) {
        return new ReleaseAbilityPayload(abilityId.toString(), source.wireName(), slot, clientStamp,
                mode != null ? mode : "");
    }

    /**
     * Builds the wire payload - the testable half of {@link #activate}
     * (hotbar#94 fixtures assert the hint's bytes here without a connection).
     *
     * @param target the aim hint, or null: null writes blank kind/params,
     *               core's absent-hint wire form
     * @param mode   the slot's validated mode setting (hotbar#29), or null:
     *               blank writes core's no-mode-context wire form
     */
    static ActivateAbilityPayload payload(ResourceLocation abilityId, ActivationSource source,
            int slot, long clientStamp, @Nullable UUID target, @Nullable String mode) {
        String kind = target == null ? "" : "entity";
        String params = target == null ? "" : "entity=" + target;
        return new ActivateAbilityPayload(abilityId.toString(), source.wireName(), slot,
                clientStamp, kind, params, mode != null ? mode : "");
    }
}
