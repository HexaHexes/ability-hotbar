package org.bitwisemadness.abilityhotbar.content;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
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
 * registers the bar first (so {@code AbilityBarScopes} calls stay compatible
 * afterwards - scopes validate the bar is registered), then constructs the
 * provider <b>only on the client</b>, so a consumer's client-only provider
 * class is never loaded on a dedicated server (the dist-cleaner trap this
 * helper exists to remove). Identical re-registration is a no-op; a live id
 * with a different contract fails loudly with the id.</p>
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
     * One-call registration for a content-owned bar (hotbar#114): registers
     * the bar as non-assignable, then - <b>only on the client</b> - constructs
     * and registers the provider. The order is the load-bearing one: the bar
     * must exist before {@code AbilityBarScopes.register} (which validates it)
     * and before {@link #register} (which validates it); the supplier is never
     * invoked on a dedicated server, so a client-only provider class cannot
     * trip the dist cleaner.
     *
     * <p>Idempotent: an identical second call is a no-op (mod setup may run
     * more than once); the same id with a different name key, or an
     * assignable bar, fails loudly with the id. Every misuse message carries
     * {@code barId}.</p>
     *
     * @param barId           the content bar's id (reserved-namespace and
     *                        duplicate rules follow {@link HotbarRegistry#register})
     * @param displayNameKey  non-blank translation key for the bar's name
     * @param providerSupplier constructs the provider; invoked only on {@code Dist.CLIENT}
     * @throws IllegalArgumentException on nulls/blank key or a supplier that
     *         returns null - nothing registered
     * @throws IllegalStateException    when the id is live with a different contract
     */
    public static void registerContentBar(ResourceLocation barId, String displayNameKey,
            Supplier<HotbarContentProvider> providerSupplier) {
        registerContentBar(barId, displayNameKey, providerSupplier,
                FMLEnvironment.dist == Dist.CLIENT);
    }

    /**
     * Dist-injected core so fixtures can pin both distributions without a
     * game instance (hotbar#114 AC1).
     */
    static void registerContentBar(ResourceLocation barId, String displayNameKey,
            Supplier<HotbarContentProvider> providerSupplier, boolean clientSide) {
        if (barId == null) {
            throw new IllegalArgumentException("bar id must not be null");
        }
        if (displayNameKey == null || displayNameKey.isBlank()) {
            throw new IllegalArgumentException("display name key must not be blank for " + barId);
        }
        if (providerSupplier == null) {
            throw new IllegalArgumentException("provider supplier must not be null for " + barId);
        }
        HotbarRegistration existing = HotbarRegistry.get(barId).orElse(null);
        if (existing != null) {
            if (existing.assignable() || !displayNameKey.equals(existing.displayNameKey())) {
                throw new IllegalStateException(
                        "content bar already registered with a different contract: " + barId);
            }
            if (clientSide && PROVIDERS.containsKey(barId)) {
                LOGGER.debug("registerContentBar: {} already registered - idempotent no-op", barId);
                return;
            }
            if (clientSide) {
                register(barId, requireProvider(providerSupplier, barId));
                LOGGER.debug("Content bar {} registered via one-call helper (provider registered: true)",
                        barId);
            } else {
                LOGGER.debug("Content bar {} registered via one-call helper (provider registered: false)",
                        barId);
            }
            return;
        }
        HotbarRegistry.register(barId, displayNameKey, false);
        boolean providerRegistered = false;
        if (clientSide) {
            register(barId, requireProvider(providerSupplier, barId));
            providerRegistered = true;
        }
        LOGGER.debug("Content bar {} registered via one-call helper (provider registered: {})",
                barId, providerRegistered);
    }

    /** The supplier's answer, with the id on a null (misuse is loud). */
    private static HotbarContentProvider requireProvider(
            Supplier<HotbarContentProvider> providerSupplier, ResourceLocation barId) {
        HotbarContentProvider provider = providerSupplier.get();
        if (provider == null) {
            throw new IllegalArgumentException(
                    "provider supplier returned null for " + barId);
        }
        return provider;
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
