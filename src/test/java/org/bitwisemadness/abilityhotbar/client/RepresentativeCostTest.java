package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.junit.jupiter.api.Test;

/**
 * The representative definition-side cost (issue #122): first declared entry,
 * null when the ability declares none - the overlay contract's rule, standing
 * in for the core accessor removed in alpha.34.
 */
class RepresentativeCostTest {

    private static final ResourceLocation CATEGORY =
            ResourceLocation.fromNamespaceAndPath("tensura", "skill");

    private static AbilityDefinition definition(List<ResourceCost> costs) {
        return new AbilityDefinition("tensura.skill.rep", null, CATEGORY,
                ActivationType.INSTANT, costs, ConsumptionStrategies.ALL,
                AmountResolvers.STATIC, Set.of(), Map.of());
    }

    @Test
    void emptyCostsBadgeNothing() {
        assertNull(RepresentativeCost.of(definition(List.of())),
                "an ability with no declared cost renders no badge");
    }

    @Test
    void singleEntryIsTheRepresentative() {
        var only = new ResourceCost("tensura:aura", 5.0);
        assertEquals(only, RepresentativeCost.of(definition(List.of(only))));
    }

    @Test
    void firstEntryRepresentsAMultiCostAbility() {
        var first = new ResourceCost("tensura:magicule", 25.0);
        var second = new ResourceCost("tensura:aura", 5.0);
        assertEquals(first, RepresentativeCost.of(definition(List.of(first, second))),
                "the overlay contract's single representative entry is the first declared cost");
    }

    @Test
    void theListOverloadLeadsWithTheFirstDeclaredEntry() {
        var first = new ResourceCost("tensura:magicule", 30.0);
        var second = new ResourceCost("tensura:aura", 7.0);
        assertNull(RepresentativeCost.of(List.of()),
                "an empty list names no cost");
        assertEquals(first, RepresentativeCost.of(List.of(first, second)),
                "the first declared entry represents the list");
        assertEquals(RepresentativeCost.of(definition(List.of(first))),
                RepresentativeCost.of(List.of(first)),
                "the definition path and the list path agree on the same costs");
    }
}
