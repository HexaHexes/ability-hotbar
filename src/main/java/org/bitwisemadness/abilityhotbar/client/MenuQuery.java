package org.bitwisemadness.abilityhotbar.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.VisibilityHandlers;
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
     * Every distinct provider namespace present in the pool, sorted for
     * stable cycling - the id namespace is the provider (hotbar#142), so
     * the screen's provider button steps through these plus a "no filter"
     * position.
     *
     * @param entries the id-aware pool
     * @return the distinct namespaces in sorted order (empty for an empty pool)
     */
    public static List<String> providers(Map<ResourceLocation, AbilityDefinition> entries) {
        return entries.keySet().stream()
                .map(ResourceLocation::getNamespace)
                .distinct()
                .sorted()
                .toList();
    }

    /** The tag prefix marking a subcategory (the open convention, hotbar#142). */
    public static final String SUBCATEGORY_PREFIX = "type:";

    /**
     * Every distinct {@code type:} subcategory tag present under one
     * category, sorted for stable cycling (hotbar#142) - the subdivision
     * Tensura's skills/magic use ({@code type:unique} and friends); a pool
     * or category without such tags answers empty.
     *
     * @param entries  the id-aware pool
     * @param category the category whose subcategories to list
     * @return the subcategory tags in sorted order
     */
    public static List<String> subCategories(Map<ResourceLocation, AbilityDefinition> entries,
            ResourceLocation category) {
        return entries.entrySet().stream()
                .filter(candidate -> candidate.getValue().category().equals(category))
                .flatMap(candidate -> candidate.getValue().tags().stream())
                .filter(tag -> tag.startsWith(SUBCATEGORY_PREFIX))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * One step of the hierarchical category cycle (hotbar#142): {@code null}
     * (no filter), then each category, then each of its {@code type:}
     * subcategories before the next category begins - so the cycle reads
     * "any" -&gt; "Skill" -&gt; "Skill / Unique" -&gt; ...
     *
     * @param category the required category, or null for "any"
     * @param subTag   the required subcategory tag (only with a category), or null
     */
    public record CategoryStep(@Nullable ResourceLocation category, @Nullable String subTag) {
    }

    /**
     * Every distinct category present in the pool, sorted for stable
     * cycling - the foundation of {@link #categorySteps}.
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
     * The category cycle as flat steps: the "any" position, then every
     * category with its subcategory steps (sorted) - the screen's category
     * button walks this list and stores the selected step's pair.
     *
     * @param entries the id-aware pool
     * @return the cycle in order (at least the "any" step)
     */
    public static List<CategoryStep> categorySteps(
            Map<ResourceLocation, AbilityDefinition> entries) {
        List<CategoryStep> steps = new ArrayList<>();
        steps.add(new CategoryStep(null, null));
        for (ResourceLocation category : categories(entries)) {
            steps.add(new CategoryStep(category, null));
            for (String sub : subCategories(entries, category)) {
                steps.add(new CategoryStep(category, sub));
            }
        }
        return List.copyOf(steps);
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
     * only on its own bar) and by the live search/category/subcategory/tag/
     * provider filters. Blank text and a null filter each mean "no filter";
     * text matches the resolved display name case-insensitively; category,
     * subTag and tag are the open vocabulary on {@link AbilityDefinition}
     * and provider is the id namespace (hotbar#142).
     *
     * <p>Rows return in the deterministic <b>default order: provider
     * -&gt; category -&gt; lowest tag -&gt; id</b> - stable across reloads
     * regardless of registration/map order (hotbar#142).</p>
     *
     * @param entries    the id-aware pool ({@code AbilityDiscovery.entries()})
     * @param targetBar  the bar section being listed
     * @param searchText the search box text; null/blank = no search
     * @param category   required category, or null for any
     * @param subTag     required {@code type:} subcategory tag, or null for any
     * @param tag        required tag, or null for any
     * @param provider   required id namespace (provider), or null for any
     * @param nameOf     resolves a definition to its display text (the screen
     *                   resolves translation keys; tests pass literals)
     * @return the visible rows, in the default order
     */
    public static List<Entry> visibleEntries(Map<ResourceLocation, AbilityDefinition> entries,
            ResourceLocation targetBar, @Nullable String searchText,
            @Nullable ResourceLocation category, @Nullable String subTag,
            @Nullable String tag, @Nullable String provider,
            Function<AbilityDefinition, Component> nameOf) {
        return visibleEntries(entries, targetBar, searchText, category, subTag, tag, provider,
                id -> true, nameOf);
    }

    /**
     * The predicate-filtered variant: ids the predicate rejects never
     * reach the list (generic narrowing; the menu's listing decisions are
     * visibility via {@link #visiblePool} and the draw-time unlock grey -
     * never this gate).
     *
     * @param unlocked whether an id may appear at all (the never-guess gate)
     * @return the visible rows, in the default order
     */
    public static List<Entry> visibleEntries(Map<ResourceLocation, AbilityDefinition> entries,
            ResourceLocation targetBar, @Nullable String searchText,
            @Nullable ResourceLocation category, @Nullable String subTag,
            @Nullable String tag, @Nullable String provider,
            Predicate<ResourceLocation> unlocked,
            Function<AbilityDefinition, Component> nameOf) {
        return visibleEntries(entries, targetBar, searchText, category, subTag, tag, provider,
                unlocked, null, nameOf);
    }

    /**
     * The tab-filtered variant (hotbar#154): a registered tab's membership
     * narrows the pool alongside every other filter - null = the All tab's
     * "no filter". Composition stays AND across scope, visibility predicate,
     * provider, tab, category, subcategory, tag and search.
     *
     * @param tabFilter the selected tab's membership, or null for All
     * @return the visible rows, in the default order
     */
    public static List<Entry> visibleEntries(Map<ResourceLocation, AbilityDefinition> entries,
            ResourceLocation targetBar, @Nullable String searchText,
            @Nullable ResourceLocation category, @Nullable String subTag,
            @Nullable String tag, @Nullable String provider,
            Predicate<ResourceLocation> unlocked,
            BiPredicate<ResourceLocation, AbilityDefinition> tabFilter,
            Function<AbilityDefinition, Component> nameOf) {
        String needle = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>(entries.size());
        for (Map.Entry<ResourceLocation, AbilityDefinition> candidate : entries.entrySet()) {
            AbilityDefinition definition = candidate.getValue();
            if (!AbilityBarScopes.isInScope(candidate.getKey(), targetBar)) {
                continue;
            }
            if (!unlocked.test(candidate.getKey())) {
                continue;
            }
            if (provider != null && !candidate.getKey().getNamespace().equals(provider)) {
                continue;
            }
            if (tabFilter != null && !tabFilter.test(candidate.getKey(), definition)) {
                continue;
            }
            if (category != null && !definition.category().equals(category)) {
                continue;
            }
            if (subTag != null && !definition.tags().contains(subTag)) {
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
        out.sort(DEFAULT_ORDER);
        return List.copyOf(out);
    }

    /** provider -> category -> lowest tag -> id: the deterministic default order (#142). */
    private static final Comparator<Entry> DEFAULT_ORDER = Comparator
            .comparing((Entry entry) -> entry.id().getNamespace())
            .thenComparing(entry -> entry.definition().category())
            .thenComparing(entry -> entry.definition().tags().stream().sorted()
                    .findFirst().orElse(""))
            .thenComparing(Entry::id);

    /**
     * The pool after core's menu-visibility contract (core#124,
     * hotbar#153): ids whose handler hides them drop out, undeclared ids
     * stay (unknown never hides). The screen feeds this to every menu
     * query - rows and the category/tag/provider chips can then never
     * disagree about what exists.
     */
    public static Map<ResourceLocation, AbilityDefinition> visiblePool(
            Map<ResourceLocation, AbilityDefinition> entries, @Nullable Player player) {
        Map<ResourceLocation, AbilityDefinition> out = new LinkedHashMap<>(entries.size());
        entries.forEach((id, definition) -> {
            if (VisibilityHandlers.visible(player, id)) {
                out.put(id, definition);
            }
        });
        return out;
    }
}
