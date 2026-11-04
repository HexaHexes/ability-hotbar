package org.bitwisemadness.abilityhotbar.registry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Ability-to-bar scope declarations (hotbar#90).
 *
 * <p>A scoped ability belongs to exactly one registered bar: it is rejected
 * for assignment on any other bar and refused activation outside it.
 * Pseudocore's {@code press} is the first consumer - its slot-to-core mapping
 * ({@code ActivationRequest.slotContext}) only means anything on the curios
 * bar it is seeded into (#89), so the declaration keeps it off every other
 * bar. Declared at mod setup like every other registration here, with the
 * same loud-failure vocabulary; <b>undeclared abilities are unaffected</b>
 * (no scope = today's behaviour, everywhere).</p>
 *
 * <p>Common code, no client imports (pinned by the distribution fixture):
 * the queries feed the server-side assignment check, the eligibility rule
 * (through the core compat bridge) and - when #27 lands - the menu's list
 * filter, which omits a scoped ability for every out-of-scope target bar.</p>
 */
public final class AbilityBarScopes {

    /** ability id -> the one bar it belongs to; one scope per ability. */
    private static final Map<ResourceLocation, ResourceLocation> SCOPES = new ConcurrentHashMap<>();

    private AbilityBarScopes() {
    }

    /**
     * Declares that an ability belongs to exactly one registered bar. Call
     * during mod setup.
     *
     * @param abilityId the scoped ability
     * @param barId     the registered bar the ability belongs to
     * @throws IllegalArgumentException on a null id or a bar id that is not a registered hotbar
     * @throws IllegalStateException    when the ability is already scoped - the same bar
     *                                  again is a duplicate, a different bar is contradictory
     */
    public static void register(ResourceLocation abilityId, ResourceLocation barId) {
        if (abilityId == null) {
            throw new IllegalArgumentException("ability id must not be null");
        }
        if (barId == null) {
            throw new IllegalArgumentException("bar id must not be null for " + abilityId);
        }
        if (HotbarRegistry.get(barId).isEmpty()) {
            // Built-ins are deliberately unreachable here: scoping to a
            // registered bar only is the #90 non-goal made structural.
            throw new IllegalArgumentException("bar id is not a registered hotbar: " + barId);
        }
        ResourceLocation existing = SCOPES.putIfAbsent(abilityId, barId);
        if (existing != null) {
            if (existing.equals(barId)) {
                throw new IllegalStateException("ability already scoped to this bar: " + abilityId);
            }
            throw new IllegalStateException("contradictory scope for " + abilityId
                    + ": already " + existing + ", not " + barId);
        }
    }

    /** @return the bar this ability belongs to, when it is scoped */
    public static Optional<ResourceLocation> scopeOf(ResourceLocation abilityId) {
        return abilityId == null ? Optional.empty() : Optional.ofNullable(SCOPES.get(abilityId));
    }

    /**
     * The scope query every consumer filters with: an unscoped ability passes
     * everywhere (today's behaviour); a scoped one only on its own bar.
     *
     * @param abilityId the ability being assigned, listed or activated
     * @param targetBar the bar in question; null never matches a scope
     * @return true when the ability may live on the target bar
     */
    public static boolean isInScope(ResourceLocation abilityId, @Nullable ResourceLocation targetBar) {
        ResourceLocation scope = abilityId == null ? null : SCOPES.get(abilityId);
        return scope == null || scope.equals(targetBar);
    }

    /** Drops every declaration. Test-only - declarations are setup-time code. */
    public static void reset() {
        SCOPES.clear();
    }
}
