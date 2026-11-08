package org.bitwisemadness.abilityhotbar.client;

import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ResourceCost;

/**
 * The definition-side representative cost (issue #122, reshaped by the
 * owner's no-badge brief): the FIRST declared cost the menu's row line
 * and pane lead with - or null when the ability declares none. Replaces
 * the core {@code AbilityDefinition.cost()} accessor removed in alpha.34
 * (its {@code orElse} argument was evaluated eagerly, so the old call
 * fired for every resolvable slot, not only unknown-cost tuples). The
 * old overlay badge is gone: costs render as localised text in the menu,
 * never as a strip over the world hotbar's icons.
 */
final class RepresentativeCost {

    private RepresentativeCost() {
    }

    static ResourceCost of(AbilityDefinition definition) {
        var costs = definition.costs();
        return costs.isEmpty() ? null : costs.get(0);
    }
}
