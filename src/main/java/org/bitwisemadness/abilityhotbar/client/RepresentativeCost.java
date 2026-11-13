package org.bitwisemadness.abilityhotbar.client;

import java.util.List;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ResourceCost;

/**
 * The definition-side representative cost (issue #122, reshaped by the
 * owner's no-badge brief): the FIRST declared cost, or null when the
 * ability declares none. The world hotbar's slot badge still leads with
 * it ({@code SlotRender}); the menu's row no longer does - it prints
 * every non-zero declared entry through {@code costText(List)} (issue
 * #222: all of a spell's prices show). Replaces the core
 * {@code AbilityDefinition.cost()} accessor removed in alpha.34 (its
 * {@code orElse} argument was evaluated eagerly, so the old call fired
 * for every resolvable slot, not only unknown-cost tuples). The old
 * overlay badge is gone: costs render as localised text in the menu,
 * never as a strip over the world hotbar's icons.
 *
 * <p>The list overload is the first-of-list step the definition path
 * delegates to; the row's live answer is joined by {@code costText(List)}
 * instead.</p>
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
