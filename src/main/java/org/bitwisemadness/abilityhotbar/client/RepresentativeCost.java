package org.bitwisemadness.abilityhotbar.client;

import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ResourceCost;

/**
 * The definition-side cost badge for one slot (issue #122): the overlay
 * contract's "single representative entry" - the first declared cost, or
 * null when the ability declares none. Replaces the core
 * {@code AbilityDefinition.cost()} accessor removed in alpha.34 (its
 * {@code orElse} argument was evaluated eagerly, so the old call fired for
 * every resolvable slot, not only unknown-cost tuples).
 */
final class RepresentativeCost {

    private RepresentativeCost() {
    }

    static ResourceCost of(AbilityDefinition definition) {
        var costs = definition.costs();
        return costs.isEmpty() ? null : costs.get(0);
    }
}
