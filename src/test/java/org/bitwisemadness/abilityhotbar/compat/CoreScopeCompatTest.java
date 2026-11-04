package org.bitwisemadness.abilityhotbar.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilityhotbar.registry.AbilityBarScopes;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The bar-scope activation guard (hotbar#90 AC4): activating a scoped ability
 * outside its scope bar answers REFUSED_UNAVAILABLE, on the scope bar it is
 * permitted, unscoped abilities never refuse, and registration lands exactly
 * one rule on core's canonical chain.
 */
class CoreScopeCompatTest {

    private static final ResourceLocation PRESS =
            ResourceLocation.fromNamespaceAndPath("pseudocore_curios", "press");
    private static final ResourceLocation OTHER_VERB =
            ResourceLocation.fromNamespaceAndPath("pseudocore_curios", "other");
    private static final ResourceLocation CURIOS =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");

    @BeforeEach
    @AfterEach
    void isolate() {
        AbilityBarScopes.reset();
        HotbarRegistry.reset();
    }

    @Test
    void outOfScopeActivationIsRefusedUnavailable() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        AbilityBarScopes.register(PRESS, CURIOS);

        var refusal = CoreScopeCompat.refusalFor(PRESS, HotbarRegistry.builtinBarId(0));

        assertTrue(refusal.isPresent(), "press on another bar must be refused");
        assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, refusal.get().outcome());
    }

    @Test
    void activationOnTheScopeBarIsPermitted() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        AbilityBarScopes.register(PRESS, CURIOS);

        assertTrue(CoreScopeCompat.refusalFor(PRESS, CURIOS).isEmpty(),
                "the scope bar itself is always permitted");
    }

    @Test
    void unscopedAbilitiesNeverRefuse() {
        assertTrue(CoreScopeCompat.refusalFor(OTHER_VERB, HotbarRegistry.builtinBarId(0)).isEmpty());
        assertTrue(CoreScopeCompat.refusalFor(null, null).isEmpty());
    }

    @Test
    void registerAddsOneRuleToTheCanonicalChain() {
        int before = AbilityCore.ELIGIBILITY.size();

        CoreScopeCompat.register();

        assertEquals(before + 1, AbilityCore.ELIGIBILITY.size(),
                "exactly one scope rule per registration call");
    }
}
