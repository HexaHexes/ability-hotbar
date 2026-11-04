package org.bitwisemadness.abilityhotbar.content;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;

/**
 * Per-bar content-provider registration (hotbar#83, ADR-012 §3).
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
 */
public final class HotbarContentProviders {

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
