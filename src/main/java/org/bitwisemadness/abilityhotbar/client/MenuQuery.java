package org.bitwisemadness.abilityhotbar.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilityhotbar.registry.AbilityBarScopes;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * The ability management menu's query layer (hotbar#27 AC1/AC7/AC8).
 *
 * <p>Pure and headless: definitions, ids and resolved names go in, filtered
 * lists come out - no screen, no discovery calls, no config. The screen
 * supplies its inputs ({@code AbilityDiscovery.entries()} for the pool, the
 * live search box and filter state, and a name resolver over translation
 * keys); the fixtures supply synthetic ones.</p>
 *
 * <p>Two queries: {@link #manageableBars} decides which bars the screen
 * offers (the built-in window plus registered <b>assignable</b> bars -
 * non-assignable registrations excluded, empty bars never hidden, and the
 * skip-empty option is a <em>cycling</em> rule that does not apply here),
 * and {@link #visibleEntries} narrows one bar's pool by scope, search,
 * category and tag (the open vocabulary on {@link AbilityDefinition}).</p>
 */
public final class MenuQuery {

    private MenuQuery() {
    }

    /**
     * Every distinct category present in the pool, sorted for stable
     * cycling - the screen's category button steps through these plus a
     * "no filter" position.
     *
     * @param entries the id-aware pool
     * @return the distinct categories in sorted order (empty for an empty pool)
     */
    public static List<ResourceLocation> categories(Map<ResourceLocation, AbilityDefinition> entries) {
        return entries.values().stream()
                .map(AbilityDefinition::category)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Every distinct tag present in the pool, sorted for stable cycling -
     * the open tag vocabulary the screen's tag button steps through.
     *
     * @param entries the id-aware pool
     * @return the distinct tags in sorted order (empty for an empty pool)
     */
    public static List<String> tags(Map<ResourceLocation, AbilityDefinition> entries) {
        return entries.values().stream()
                .flatMap(definition -> definition.tags().stream())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * One menu row: the id the payload will send, with its definition.
     */
    public record Entry(ResourceLocation id, AbilityDefinition definition) {
    }

    /**
     * The bars the screen manages (AC7/AC8): the built-in window, then every
     * registered assignable bar in registration order. Registered
     * non-assignable bars are excluded (ADR-012 - the assignment screen
     * never lists them); there is deliberately no emptiness input, so an
     * empty bar can never be hidden here.
     *
     * @param window        the built-in window size ({@code display.hotbarCount} clamped)
     * @param registrations the live registrations, in registration order
     * @return the manageable bars, in cycle order
     */
    public static List<ResourceLocation> manageableBars(int window,
            List<HotbarRegistration> registrations) {
        List<ResourceLocation> bars = new ArrayList<>(Math.max(window, 0) + registrations.size());
        for (int i = 0; i < window; i++) {
            bars.add(HotbarRegistry.builtinBarId(i));
        }
        for (HotbarRegistration registration : registrations) {
            if (registration.assignable()) {
                bars.add(registration.id());
            }
        }
        return List.copyOf(bars);
    }

    /**
     * One bar's visible rows (AC1): the id-aware pool narrowed by the
     * ability's scope ({@link AbilityBarScopes} - a scoped ability shows
     * only on its own bar) and by the live search/category/tag filters.
     * Blank text, a null category and a null tag each mean "no filter";
     * text matches the resolved display name case-insensitively; category
     * and tag are the open vocabulary on {@link AbilityDefinition}. Input
     * order is preserved.
     *
     * @param entries    the id-aware pool ({@code AbilityDiscovery.entries()})
     * @param targetBar  the bar section being listed
     * @param searchText the search box text; null/blank = no search
     * @param category   required category, or null for any
     * @param tag        required tag, or null for any
     * @param nameOf     resolves a definition to its display text (the screen
     *                   resolves translation keys; tests pass literals)
     * @return the visible rows, in input order
     */
    public static List<Entry> visibleEntries(Map<ResourceLocation, AbilityDefinition> entries,
            ResourceLocation targetBar, @Nullable String searchText,
            @Nullable ResourceLocation category, @Nullable String tag,
            Function<AbilityDefinition, Component> nameOf) {
        String needle = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>(entries.size());
        for (Map.Entry<ResourceLocation, AbilityDefinition> candidate : entries.entrySet()) {
            AbilityDefinition definition = candidate.getValue();
            if (!AbilityBarScopes.isInScope(candidate.getKey(), targetBar)) {
                continue;
            }
            if (category != null && !definition.category().equals(category)) {
                continue;
            }
            if (tag != null && !definition.tags().contains(tag)) {
                continue;
            }
            if (!needle.isEmpty()
                    && !nameOf.apply(definition).getString().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            out.add(new Entry(candidate.getKey(), definition));
        }
        return List.copyOf(out);
    }
}
