package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The pane's live cost answer (the owner's directive: tensura_iron_spells
 * reprices Iron's Spells after the registry phase closes, so the baked
 * quote can be stale): a declared supplier wins, and anything it cannot
 * answer (null, a throw) falls back to the definition's baked list - a
 * broken projection costs one row's number, never the pane.
 */
class LiveCostsTest {

    private static final ResourceLocation CATEGORY =
            ResourceLocation.fromNamespaceAndPath("tensura", "skill");
    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("iss", "acid_orb");

    private static final ResourceCost BAKED = new ResourceCost("irons_spellbooks:mana", 30.0);
    private static final ResourceCost LIVE = new ResourceCost("irons_spellbooks:mana", 75.0);

    private static AbilityDefinition definition() {
        return new AbilityDefinition("tensura.skill.live", null, CATEGORY,
                ActivationType.INSTANT, List.of(BAKED), ConsumptionStrategies.ALL,
                AmountResolvers.STATIC, Set.of(), Map.of());
    }

    @AfterEach
    void isolate() {
        LiveCosts.remove(ABILITY);
    }

    @Test
    void declaredSupplierWinsOverTheBakedList() {
        LiveCosts.declare(ABILITY, () -> List.of(LIVE));

        assertEquals(List.of(LIVE), LiveCosts.forDisplay(ABILITY, definition()),
                "the repriced quote is what the pane shows");
    }

    @Test
    void nullAnswerFallsBackToTheBakedList() {
        LiveCosts.declare(ABILITY, () -> null);

        assertEquals(List.of(BAKED), LiveCosts.forDisplay(ABILITY, definition()),
                "a projection that cannot tell keeps the definition's number");
    }

    @Test
    void aThrowingSupplierNeverBreaksThePane() {
        LiveCosts.declare(ABILITY, () -> {
            throw new IllegalStateException("upstream unreadable");
        });

        assertEquals(List.of(BAKED), LiveCosts.forDisplay(ABILITY, definition()),
                "a broken projection costs one row's number, not the pane");
    }

    @Test
    void undeclaredAbilitiesShowTheirOwnCosts() {
        assertEquals(List.of(BAKED),
                LiveCosts.forDisplay(
                        ResourceLocation.fromNamespaceAndPath("iss", "nope"), definition()),
                "no declaration: the baked (overlay-merged) list stands");
        assertEquals(List.of(BAKED), LiveCosts.forDisplay(null, definition()),
                "a null id never throws");
    }

    @Test
    void declarationsAreExclusiveAndRemovable() {
        assertThrows(IllegalArgumentException.class, () -> LiveCosts.declare(null, List::of));
        assertThrows(IllegalArgumentException.class, () -> LiveCosts.declare(ABILITY, null));

        LiveCosts.declare(ABILITY, () -> List.of(LIVE));
        LiveCosts.remove(ABILITY);
        assertEquals(List.of(BAKED), LiveCosts.forDisplay(ABILITY, definition()),
                "after removal the baked answer returns");
    }
}
