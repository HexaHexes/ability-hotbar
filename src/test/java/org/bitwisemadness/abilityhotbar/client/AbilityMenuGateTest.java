package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The menu-open gate (hotbar#107): with no provider-registered ability the
 * menu key is a silent no-op instead of an empty screen; one registered
 * ability opens normally; the not-yet-ready registry reads as empty rather
 * than crashing (provider presence IS registration presence, REQ-CORE-03).
 */
class AbilityMenuGateTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");

    private Registry<AbilityDefinition> previousRegistry;

    @BeforeEach
    void rememberRegistry() {
        previousRegistry = AbilityRegistries.registry();
    }

    @AfterEach
    void restore() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
    }

    private static AbilityDefinition definition() {
        return new AbilityDefinition("tensura.skill.gate", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, java.util.List.of(),
                ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of());
    }

    private static Registry<AbilityDefinition> emptyFixture() {
        return new MappedRegistry<>(AbilityRegistries.ABILITY_REGISTRY_KEY,
                Lifecycle.stable(), false);
    }

    @Test
    void noRegisteredAbilityGatesTheMenuClosed() {
        AbilityRegistries.installRegistryForTests(emptyFixture());

        assertFalse(AbilityMenu.hasManageableAbilities(),
                "an empty registry means no provider -> nothing to manage");
    }

    @Test
    void oneRegisteredAbilityOpensTheGate() {
        var fixture = emptyFixture();
        Registry.register(fixture, FIREBALL, definition());
        AbilityRegistries.installRegistryForTests(fixture);

        assertTrue(AbilityMenu.hasManageableAbilities(),
                "one provider-registered ability is enough to manage");
    }

    @Test
    void notYetReadyRegistryGatesClosedWithoutCrashing() {
        AbilityRegistries.installRegistryForTests(null);

        assertFalse(AbilityMenu.hasManageableAbilities(),
                "pre-login state reads as empty, never a crash");
    }
}
