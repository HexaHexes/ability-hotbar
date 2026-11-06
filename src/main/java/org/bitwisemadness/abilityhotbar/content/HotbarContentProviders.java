package org.bitwisemadness.abilityhotbar.content;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.slf4j.Logger;

/**
 * Per-bar content-provider registration (hotbar#83, ADR-012 A3), plus the
 * one-call content-bar ritual (hotbar#114).
 *
 * <p>The registering mod declares, at mod setup, what its content-owned bar
 * shows: {@code register(barId, provider)} with the same loud-failure style
 * as {@link HotbarRegistry#register} - an unknown bar id, an <b>assignable</b>
 * bar (assignable bars keep attachment-owned state; providers exist only for
 * content-owned bars), or a duplicate provider for the same bar all fail
 * loudly rather than shadow or silently ignore.</p>
 *
 * <p>Registration is setup-time code, callable on both distributions - the
 * registry and the interface use common classes only. <b>Invocation is
 * client-only</b>: only the GUI layer extracts a {@link ContentPlan} and only
 * the client's input handler asks {@link ContentPlan#hasContent}, so a
 * dedicated server never runs a provider (code-review invariant, pinned by
 * the distribution fixture).</p>
 *
 * <p>{@link #registerContentBar} is the DX wrapper over that ritual: it
 * registers the bar (on both distributions) with the order, idempotency and
 * loud-misuse rules below. It deliberately takes <b>no provider supplier</b>
 * (hotbar#120): a supplier argument is evaluated - and its target class
 * linked - at the call site on <i>both</i> distributions, so a client-reading
 * provider class dies with a {@code BootstrapMethodError} on a dedicated
 * server before this helper runs a single line. The provider half stays in
 * the caller's dist-gated {@link #register} call, whose call site never
 * links on the server.</p>
 */
public final class HotbarContentProviders {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Bar id to provider. A concurrent map: registration is a setup-time
     * write, extraction a per-frame read, and CopyOnWrite/CHM give the reads
     * lock-free safe publication without a monitor (SpotBugs JLM).
     */
    private static final Map<ResourceLocation, HotbarContentProvider> PROVIDERS = new ConcurrentHashMap<>();

    private HotbarContentProviders() {
    }

    /**
     * Declares what {@code barId}'s content-owned bar shows. Call during mod
     * setup, for a <b>non-assignable</b> registered bar.
     *
     * @param barId    a registered, non-assignable bar id
     * @param provider the bar's content provider
     * @throws IllegalArgumentException on a null id/provider, an id that is
     *         not a registered hotbar, or an assignable bar
     * @throws IllegalStateException    when the bar already has a provider
     */
    public static void register(ResourceLocation barId, HotbarContentProvider provider) {
        if (barId == null) {
            throw new IllegalArgumentException("bar id must not be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("content provider must not be null for " + barId);
        }
        HotbarRegistration registration = HotbarRegistry.get(barId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "bar id is not a registered hotbar: " + barId));
        if (registration.assignable()) {
            throw new IllegalArgumentException(
                    "assignable bars keep attachment-owned state; providers are for content-owned bars only: "
                            + barId);
        }
        if (PROVIDERS.putIfAbsent(barId, provider) != null) {
            throw new IllegalStateException("content provider already registered for: " + barId);
        }
        LOGGER.debug("Content provider registered for bar {}", barId);
    }

    /**
     * One-call registration for a content-owned bar (hotbar#114, reshaped by
     * hotbar#120): registers the bar as non-assignable, on both
     * distributions, with the load-bearing order (the bar exists before
     * {@code AbilityBarScopes.register} and before {@link #register}, which
     * both validate it).
     *
     * <p><b>No provider-supplier parameter, by design</b> (hotbar#120): a
     * supplier argument links its target class at the call site on both
     * distributions - a client-reading provider would crash a dedicated
     * server with a {@code BootstrapMethodError} before this method runs.
     * Register the provider through the dist-gated {@link #register} call
     * instead; that call site sits behind the consumer's dist guard, so the
     * provider class is never linked on the server.</p>
     *
     * <p>Idempotent: an identical second call is a no-op (mod setup may run
     * more than once); the same id with a different name key, or an
     * assignable bar, fails loudly with the id. Every misuse message carries
     * {@code barId}.</p>
     *
     * @param barId          the content bar's id (reserved-namespace and
     *                       duplicate rules follow {@link HotbarRegistry#register})
     * @param displayNameKey non-blank translation key for the bar's name
     * @throws IllegalArgumentException on null id, or a null/blank name key
     * @throws IllegalStateException    when the id is live with a different contract
     */
    public static void registerContentBar(ResourceLocation barId, String displayNameKey) {
        if (barId == null) {
            throw new IllegalArgumentException("bar id must not be null");
        }
        if (displayNameKey == null || displayNameKey.isBlank()) {
            throw new IllegalArgumentException("display name key must not be blank for " + barId);
        }
        HotbarRegistration existing = HotbarRegistry.get(barId).orElse(null);
        if (existing != null) {
            if (existing.assignable() || !displayNameKey.equals(existing.displayNameKey())) {
                throw new IllegalStateException(
                        "content bar already registered with a different contract: " + barId);
            }
            LOGGER.debug("registerContentBar: {} already registered - idempotent no-op", barId);
            return;
        }
        HotbarRegistry.register(barId, displayNameKey, false);
        LOGGER.debug("Content bar {} registered via one-call helper", barId);
    }

    /**
     * @param barId the bar's id; null yields empty
     * @return the bar's provider, when the registering mod declared one
     */
    public static Optional<HotbarContentProvider> get(ResourceLocation barId) {
        return barId == null ? Optional.empty() : Optional.ofNullable(PROVIDERS.get(barId));
    }

    /** Drops every provider. Test-only - providers are setup-time code. */
    public static void reset() {
        PROVIDERS.clear();
    }
}
