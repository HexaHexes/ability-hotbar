package org.bitwisemadness.abilityhotbar.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.jetbrains.annotations.Nullable;

/**
 * The menu's live cost answer - the fleet's provider pattern on the
 * display side: a provider whose costs change <b>after</b> the registry
 * phase closes declares a supplier here, and the detail pane shows its
 * answer instead of the definition's baked list.
 *
 * <p>The motivating case (owner's directive): {@code tensura_iron_spells}
 * reprices Iron's Spells through a mixin on {@code AbstractSpell}, and its
 * config loads <b>after</b> every {@code RegisterEvent} - so a quote baked
 * at registration can only ever carry the native number. A supplier is
 * read at <b>display time</b>, when the repricing is in force.</p>
 *
 * <p>Fail-soft by construction: a supplier that answers {@code null} or
 * throws falls back to the definition's baked costs, so a broken
 * projection costs one row's number, never the pane.</p>
 */
public final class LiveCosts {

    private static final Map<ResourceLocation, Supplier<List<ResourceCost>>> PROJECTIONS =
            new ConcurrentHashMap<>();

    private LiveCosts() {
    }

    /**
     * Declares an ability's current-cost answer (client display only).
     *
     * @param id     the ability whose costs the pane should quote
     * @param costs  the live answer; null (or a thrown read) falls back
     *               to the definition's baked list
     * @throws IllegalArgumentException on null arguments
     */
    public static void declare(ResourceLocation id, Supplier<List<ResourceCost>> costs) {
        if (id == null || costs == null) {
            throw new IllegalArgumentException("ability id and cost supplier must not be null");
        }
        PROJECTIONS.put(id, costs);
    }

    /**
     * What the pane shows: the live answer when the ability has one,
     * else the definition's own (baked, overlay-merged) list.
     *
     * @param id         the ability being detailed (nullable)
     * @param definition the registry's definition for it
     * @return the costs to draw - never null
     */
    public static List<ResourceCost> forDisplay(@Nullable ResourceLocation id,
            AbilityDefinition definition) {
        Supplier<List<ResourceCost>> projection = id == null ? null : PROJECTIONS.get(id);
        if (projection != null) {
            try {
                List<ResourceCost> live = projection.get();
                if (live != null) {
                    return live;
                }
            } catch (RuntimeException unreadable) {
                // fall through to the baked list: one row's number, not the pane
            }
        }
        return definition.costs();
    }

    /** Test isolation / reload paths. */
    public static boolean remove(ResourceLocation id) {
        return id != null && PROJECTIONS.remove(id) != null;
    }
}
