package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitycore.api.VisibilityHandlers;
import org.bitwisemadness.abilityhotbar.registry.AbilityBarScopes;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.registry.MenuTabs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The menu's query layer (hotbar#27 AC1/AC7/AC8, hotbar#142): which bars
 * the screen offers, and how one bar's pool narrows by scope, search,
 * category, subcategory, tag and provider across the open vocabulary - and
 * the deterministic default order (provider -> category -> tag -> id).
 */
class MenuQueryTest {

    private static final ResourceLocation SKILL = ResourceLocation.fromNamespaceAndPath("tensura", "skill");
    private static final ResourceLocation MAGIC = ResourceLocation.fromNamespaceAndPath("tensura", "magic");
    private static final ResourceLocation SPELL = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell");
    private static final ResourceLocation WILL = ResourceLocation.fromNamespaceAndPath("tensura", "battlewill");

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation HEAL =
            ResourceLocation.fromNamespaceAndPath("tensura", "heal");
    private static final ResourceLocation FLASH =
            ResourceLocation.fromNamespaceAndPath("tensura", "flash");
    private static final ResourceLocation FIREBOLT =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "firebolt");
    private static final ResourceLocation PRESS =
            ResourceLocation.fromNamespaceAndPath("pseudocore_curios", "press");
    private static final ResourceLocation CURIOS =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");
    private static final ResourceLocation EXTRA =
            ResourceLocation.fromNamespaceAndPath("otherpack", "extra");
    private static final ResourceLocation RAGNAROK =
            ResourceLocation.fromNamespaceAndPath("tensura", "ragnarok");

    private static AbilityDefinition def(String displayKey, ResourceLocation category, Set<String> tags) {
        return new AbilityDefinition(displayKey, null, category, ActivationType.INSTANT, java.util.List.of(),
                ConsumptionStrategies.ALL, AmountResolvers.STATIC, tags, Map.of());
    }

    /** Resolved names the way the screen resolves them: key -> display text. */
    private static final Function<AbilityDefinition, Component> NAMES = definition -> {
        Map<String, String> friendly = Map.of(
                "tensura.skill.fireball", "Fireball",
                "tensura.skill.heal", "Greater Heal",
                "tensura.magic.flash", "Flash Step",
                "irons_spellbooks.firebolt", "Firebolt",
                "pseudocore_curios.press", "Release Core");
        return Component.literal(friendly.getOrDefault(definition.displayNameKey(),
                definition.displayNameKey()));
    };

    @BeforeEach
    @AfterEach
    void isolate() {
        AbilityBarScopes.reset();
        HotbarRegistry.reset();
    }

    private static Map<ResourceLocation, AbilityDefinition> pool() {
        Map<ResourceLocation, AbilityDefinition> entries = new LinkedHashMap<>();
        entries.put(FIREBALL, def("tensura.skill.fireball", SKILL, Set.of("active")));
        entries.put(HEAL, def("tensura.skill.heal", SKILL, Set.of("passive")));
        entries.put(FLASH, def("tensura.magic.flash", MAGIC, Set.of("active")));
        entries.put(FIREBOLT, def("irons_spellbooks.firebolt", SPELL, Set.of()));
        entries.put(PRESS, def("pseudocore_curios.press", WILL, Set.of("active")));
        return entries;
    }

    /** The pool plus one skill carrying a Tensura-style subcategory tag (#142). */
    private static Map<ResourceLocation, AbilityDefinition> poolWithTypeTags() {
        Map<ResourceLocation, AbilityDefinition> entries = pool();
        entries.put(RAGNAROK, def("tensura.skill.ragnarok", SKILL,
                Set.of("active", "type:unique")));
        return entries;
    }

    @Test
    void emptyTabsAreDroppedFromTheStrip() {
        // owner's brief: empty tabs are not displayed - a tab survives
        // only when its pool holds an entry its membership accepts, and
        // the registration order is the strip's order
        try {
            MenuTabs.register(SKILL, "tensura.tab.skills", (id, definition) -> true);
            MenuTabs.register(MAGIC, "tensura.tab.magic", (id, definition) -> false);
            Map<ResourceLocation, AbilityDefinition> full = pool();
            List<MenuTabs.MenuTab> withEntries = MenuQuery.nonEmptyTabs(MenuTabs.all(),
                    tab -> tab.id().equals(SKILL) ? full : Map.of());
            assertEquals(List.of(SKILL),
                    withEntries.stream().map(tab -> tab.id()).toList(),
                    "a tab with entries survives, in strip order");

            List<MenuTabs.MenuTab> acceptsNothing = MenuQuery.nonEmptyTabs(MenuTabs.all(),
                    tab -> full);
            assertEquals(1, acceptsNothing.size(),
                    "membership decides: accepts-nothing drops even a full pool");
            assertEquals(SKILL, acceptsNothing.get(0).id(),
                    "the accepting tab is the survivor");
            assertTrue(MenuQuery.nonEmptyTabs(List.of(), tab -> full).isEmpty(),
                    "no tabs, no strip");
        } finally {
            MenuTabs.remove(SKILL);
            MenuTabs.remove(MAGIC);
        }
    }

    @Test
    void subTypeTagsHonoursDeclaredOrderThenAlphabetical() {
        Map<ResourceLocation, AbilityDefinition> entries = new LinkedHashMap<>();
        entries.put(FIREBALL, def("tensura.skill.fireball", SKILL,
                Set.of("type:common", "type:unique", "type:resistance")));
        entries.put(HEAL, def("tensura.skill.heal", SKILL,
                Set.of("type:intrinsic", "type:extra")));

        // the owner's reorder brief: declared tags first, in order; a
        // reserved slot the pool never has ('type:ultimate') simply skips
        assertEquals(java.util.List.of("type:resistance", "type:intrinsic",
                        "type:common", "type:extra", "type:unique"),
                MenuQuery.subTypeTags(entries, java.util.List.of("type:resistance",
                        "type:intrinsic", "type:common", "type:extra",
                        "type:unique", "type:ultimate")),
                "declared order, absent slot skipped");

        assertEquals(java.util.List.of("type:common", "type:extra", "type:intrinsic",
                        "type:resistance", "type:unique"),
                MenuQuery.subTypeTags(entries, java.util.List.of()),
                "no declaration keeps plain alphabetical");

        assertEquals(java.util.List.of("type:unique", "type:common", "type:extra",
                        "type:intrinsic", "type:resistance"),
                MenuQuery.subTypeTags(entries, java.util.List.of("type:unique")),
                "declared tag first, the rest alphabetical behind it");
    }

    @Test
    void manageableBarsListsTheWindowThenAssignableRegistrations() {
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        List<HotbarRegistration> registrations = List.of(
                new HotbarRegistration(EXTRA, "otherpack.bar.extra", true),
                new HotbarRegistration(CURIOS, "pseudocore.bar.curios", false));

        List<ResourceLocation> bars = MenuQuery.manageableBars(3, registrations);

        assertEquals(List.of(HotbarRegistry.builtinBarId(0), HotbarRegistry.builtinBarId(1),
                HotbarRegistry.builtinBarId(2), EXTRA), bars,
                "window first, then assignable registrations in order");
        assertFalse(bars.contains(CURIOS), "non-assignable bars are never listed (ADR-012)");
    }

    @Test
    void manageableBarsHasNoEmptinessInputSoEmptyBarsAreNeverHidden() {
        // AC7: unlike cycling, the screen's bar list cannot skip anything -
        // there is no content parameter to skip with. Same inputs, same list.
        List<HotbarRegistration> registrations = List.of(
                new HotbarRegistration(EXTRA, "otherpack.bar.extra", true));

        assertEquals(MenuQuery.manageableBars(3, registrations),
                MenuQuery.manageableBars(3, registrations),
                "the query is content-blind by construction");
        assertEquals(List.of(EXTRA), MenuQuery.manageableBars(0, registrations),
                "a zero window still lists the registered assignable bars");
    }

    @Test
    void visibleEntriesNarrowsBySearchCategoryAndTag() {
        Map<ResourceLocation, AbilityDefinition> entries = pool();
        ResourceLocation bar = HotbarRegistry.builtinBarId(0);

        assertEquals(List.of(FIREBOLT, FIREBALL),
                ids(MenuQuery.visibleEntries(entries, bar, "fire", null, null, null, null, NAMES)),
                "search matches the resolved name case-insensitively, in default order");
        assertEquals(List.of(FIREBALL, HEAL),
                ids(MenuQuery.visibleEntries(entries, bar, null, SKILL, null, null, null, NAMES)),
                "category narrows to one kind");
        assertEquals(List.of(HEAL),
                ids(MenuQuery.visibleEntries(entries, bar, null, SKILL, null, "passive", null, NAMES)),
                "tag narrows inside the category");
        assertEquals(5, MenuQuery.visibleEntries(entries, bar, "", null, null, null, null, NAMES).size(),
                "blank text and null filters pass everything");
        assertTrue(MenuQuery.visibleEntries(entries, bar, "zzz-nothing", null, null, null, null, NAMES).isEmpty(),
                "a search with no match empties the list");
        assertTrue(MenuQuery.visibleEntries(Map.of(), bar, null, null, null, null, null, NAMES).isEmpty(),
                "an empty pool stays empty");
    }

    @Test
    void visibleEntriesNarrowsByProviderAndSubcategory() {
        // hotbar#142: the provider tab and the type: subdivision both narrow
        Map<ResourceLocation, AbilityDefinition> entries = poolWithTypeTags();
        ResourceLocation bar = HotbarRegistry.builtinBarId(0);

        assertEquals(List.of(FIREBOLT),
                ids(MenuQuery.visibleEntries(entries, bar, null, null, null, null,
                        "irons_spellbooks", NAMES)),
                "the provider filter keeps only that namespace");
        assertEquals(4, MenuQuery.visibleEntries(entries, bar, null, null, null, null,
                "tensura", NAMES).size(), "every tensura entry survives its tab");
        assertEquals(List.of(RAGNAROK),
                ids(MenuQuery.visibleEntries(entries, bar, null, SKILL, "type:unique", null, null, NAMES)),
                "the subcategory narrows inside the category");
        assertTrue(MenuQuery.visibleEntries(entries, bar, null, MAGIC, "type:unique", null, null, NAMES)
                .isEmpty(), "a subcategory outside its category matches nothing");
    }

    @Test
    void visibleEntriesAppliesTheAbilityScopeGate() {
        // #90: a scoped ability shows only on its own bar - for pseudocore's
        // press that means it appears in no listed bar at all (the curios bar
        // is non-assignable and never listed).
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        AbilityBarScopes.register(PRESS, CURIOS);
        Map<ResourceLocation, AbilityDefinition> entries = pool();
        ResourceLocation builtIn = HotbarRegistry.builtinBarId(0);

        assertFalse(ids(MenuQuery.visibleEntries(entries, builtIn, null, null, null, null, null, NAMES))
                .contains(PRESS), "press is out of scope on a built-in bar");
        assertTrue(ids(MenuQuery.visibleEntries(entries, CURIOS, null, null, null, null, null, NAMES))
                .contains(PRESS), "its own bar keeps it");
        assertEquals(4, MenuQuery.visibleEntries(entries, builtIn, null, null, null, null, null, NAMES).size(),
                "every unscoped ability is still listed");
    }

    @Test
    void visibleEntriesShipsInTheDefaultOrderProviderCategoryTagId() {
        // hotbar#142: deterministic regardless of registration/map order -
        // namespace, then category, then lowest tag, then id.
        Map<ResourceLocation, AbilityDefinition> entries = pool();
        ResourceLocation bar = HotbarRegistry.builtinBarId(0);

        assertEquals(List.of(FIREBOLT, PRESS, FLASH, FIREBALL, HEAL),
                ids(MenuQuery.visibleEntries(entries, bar, null, null, null, null, null, NAMES)),
                "irons < pseudocore < tensura; magic < skill; active < passive");

        // same content, reversed insertion order -> identical output
        Map<ResourceLocation, AbilityDefinition> reversed = new LinkedHashMap<>();
        List<ResourceLocation> ids = List.copyOf(entries.keySet());
        for (int i = ids.size() - 1; i >= 0; i--) {
            reversed.put(ids.get(i), entries.get(ids.get(i)));
        }
        assertEquals(ids(MenuQuery.visibleEntries(entries, bar, null, null, null, null, null, NAMES)),
                ids(MenuQuery.visibleEntries(reversed, bar, null, null, null, null, null, NAMES)),
                "input order cannot leak into the list");
    }

    @Test
    void tabMembershipNarrowsAlongsideEveryOtherFilter() {
        // hotbar#154: a registered tab's predicate composes AND with the
        // rest; null (the All tab) keeps the whole pool
        Map<ResourceLocation, AbilityDefinition> entries = pool();
        ResourceLocation bar = HotbarRegistry.builtinBarId(0);
        java.util.function.BiPredicate<ResourceLocation, AbilityDefinition> tensuraOnly =
                (id, definition) -> id.getNamespace().equals("tensura");

        assertEquals(3, MenuQuery.visibleEntries(entries, bar, null, null, null, null, null,
                        id -> true, tensuraOnly, NAMES).size(),
                "the tab narrows to its membership");
        assertEquals(2, MenuQuery.visibleEntries(entries, bar, null, SKILL, null, null, null,
                        id -> true, tensuraOnly, NAMES).size(),
                "tab membership and the category filter compose (both tensura skills)");
        assertEquals(5, MenuQuery.visibleEntries(entries, bar, null, null, null, null, null,
                        id -> true, null, NAMES).size(),
                "null membership (All) passes everything - byte-for-byte the plain pool");
        assertTrue(MenuQuery.visibleEntries(entries, bar, null, null, null, null, null,
                        id -> true, (id, definition) -> false, NAMES).isEmpty(),
                "reject-all membership empties the list");
    }

    @Test
    void visibleEntriesHidesIdsTheUnlockPredicateRejects() {
        // The predicate overload is generic narrowing (the never-guess
        // gate shape); the screen's listing decisions are visibility
        // (visiblePool) and the draw-time unlock grey, not this gate.
        Map<ResourceLocation, AbilityDefinition> entries = pool();
        ResourceLocation bar = HotbarRegistry.builtinBarId(0);

        assertEquals(List.of(FIREBOLT, PRESS),
                ids(MenuQuery.visibleEntries(entries, bar, null, null, null, null, null,
                        id -> !id.getNamespace().equals("tensura"), NAMES)),
                "rejected ids vanish; the rest keep their default order");
        assertEquals(5, MenuQuery.visibleEntries(entries, bar, null, null, null, null, null,
                id -> true, NAMES).size(), "an accept-all predicate matches the plain overload");
        assertTrue(MenuQuery.visibleEntries(entries, bar, null, null, null, null, null,
                id -> false, NAMES).isEmpty(), "reject-all empties the list");
        assertEquals(ids(MenuQuery.visibleEntries(entries, bar, null, null, null, null, null, NAMES)),
                ids(MenuQuery.visibleEntries(entries, bar, null, null, null, null, null,
                        id -> true, NAMES)),
                "the delegating overload is byte-for-byte the same list");
    }

    @Test
    void visiblePoolAppliesTheMenuVisibilityContract() {
        // core#124 / hotbar#153: a declared NEVER id drops out of the
        // pool every menu query reads; an undeclared id stays - unknown
        // never hides.
        var hidden = ResourceLocation.fromNamespaceAndPath("tensura", "hidden_probe");
        Map<ResourceLocation, AbilityDefinition> entries = new LinkedHashMap<>();
        entries.put(SKILL, def("tensura.skill.a", SKILL, Set.of()));
        entries.put(hidden, def("tensura.skill.b", SKILL, Set.of()));
        VisibilityHandlers.declare(hidden, VisibilityHandlers.NEVER);
        try {
            Map<ResourceLocation, AbilityDefinition> visible = MenuQuery.visiblePool(entries, null);
            assertFalse(visible.containsKey(hidden), "a NEVER id must drop out of the pool");
            assertTrue(visible.containsKey(SKILL), "an undeclared id must stay - unknown never hides");
        } finally {
            VisibilityHandlers.remove(hidden);
        }
    }

    @Test
    void filterOptionsComeFromThePoolInStableOrder() {
        // The screen's cycle buttons step through these: distinct, sorted -
        // deterministic regardless of registration or iteration order.
        Map<ResourceLocation, AbilityDefinition> entries = pool();

        assertEquals(List.of(WILL, MAGIC, SKILL, SPELL), MenuQuery.categories(entries),
                "sorted distinct categories (ResourceLocation orders path-first)");
        assertEquals(List.of("active", "passive"), MenuQuery.tags(entries),
                "sorted distinct tags (FIREBOLT carries none)");
        assertTrue(MenuQuery.categories(Map.of()).isEmpty(), "an empty pool has no categories");
        assertTrue(MenuQuery.tags(Map.of()).isEmpty(), "an empty pool has no tags");
        assertEquals(List.of("irons_spellbooks", "pseudocore_curios", "tensura"),
                MenuQuery.providers(entries),
                "the provider tabs in namespace order (plus All, screen-side)");
        assertTrue(MenuQuery.providers(Map.of()).isEmpty(), "an empty pool has no providers");
    }

    @Test
    void subcategoriesRideTheTypePrefixConvention() {
        // hotbar#142: type: tags under one category; a pool without them
        // answers empty (the cycle then behaves exactly as before).
        Map<ResourceLocation, AbilityDefinition> entries = poolWithTypeTags();

        assertEquals(List.of("type:unique"), MenuQuery.subCategories(entries, SKILL),
                "the skill's type: tag, sorted");
        assertTrue(MenuQuery.subCategories(entries, MAGIC).isEmpty(),
                "categories without type: tags answer empty");
        assertTrue(MenuQuery.subCategories(pool(), SKILL).isEmpty(),
                "a pool without type: tags answers empty");
        assertTrue(MenuQuery.subCategories(entries, EXTRA).isEmpty(),
                "an unknown category answers empty");
    }

    @Test
    void categoryStepsWalkAnyCategorySubcategory() {
        // hotbar#142: the cycle reads any -> category -> its subs -> next
        // category; a pool without type: tags gets the plain old cycle.
        Map<ResourceLocation, AbilityDefinition> entries = poolWithTypeTags();

        List<MenuQuery.CategoryStep> steps = MenuQuery.categorySteps(entries);
        assertEquals(new MenuQuery.CategoryStep(null, null), steps.get(0), "any first");
        assertEquals(new MenuQuery.CategoryStep(WILL, null), steps.get(1));
        assertEquals(new MenuQuery.CategoryStep(MAGIC, null), steps.get(2));
        assertEquals(new MenuQuery.CategoryStep(SKILL, null), steps.get(3));
        assertEquals(new MenuQuery.CategoryStep(SKILL, "type:unique"), steps.get(4),
                "the category's subcategory follows it");
        assertEquals(new MenuQuery.CategoryStep(SPELL, null), steps.get(5), "then the next category");
        assertEquals(6, steps.size(), "exactly any + 4 categories + 1 subcategory");

        List<MenuQuery.CategoryStep> plain = MenuQuery.categorySteps(pool());
        assertEquals(MenuQuery.categories(pool()).size() + 1, plain.size(),
                "without type: tags the cycle is any + categories, as before");
        assertEquals(new MenuQuery.CategoryStep(null, null),
                MenuQuery.categorySteps(Map.of()).get(0), "an empty pool still offers any");
    }

    private static List<ResourceLocation> ids(List<MenuQuery.Entry> rows) {
        return rows.stream().map(MenuQuery.Entry::id).toList();
    }
}
