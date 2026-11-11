package org.bitwisemadness.abilityhotbar.client;

import java.util.List;
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
 *
 * <p>The list overload exists for the row's LIVE answer
 * ({@code LiveCosts.forDisplay}): the pane and the row must lead with
 * the same cost from the same list - repriced by
 * {@code tensura_iron_spells} when a projection is in force.</p>
 */
final class RepresentativeCost {

    private RepresentativeCost() {
    }

    static ResourceCost of(AbilityDefinition definition) {
        return of(definition.costs());
    }

    /** The first of the given costs, or null when the list is empty. */
    static ResourceCost of(List<ResourceCost> costs) {
        return costs.isEmpty() ? null : costs.get(0);
    }
}
