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
import org.bitwisemadness.abilityhotbar.registry.AbilityBarScopes;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The menu's query layer (hotbar#27 AC1/AC7/AC8): which bars the screen
 * offers, and how one bar's pool narrows by scope, search, category and tag
 * across the open provider vocabulary.
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

        assertEquals(List.of(FIREBALL, FIREBOLT),
                ids(MenuQuery.visibleEntries(entries, bar, "fire", null, null, NAMES)),
                "search matches the resolved name case-insensitively");
        assertEquals(List.of(FIREBALL, HEAL),
                ids(MenuQuery.visibleEntries(entries, bar, null, SKILL, null, NAMES)),
                "category narrows to one kind");
        assertEquals(List.of(HEAL),
                ids(MenuQuery.visibleEntries(entries, bar, null, SKILL, "passive", NAMES)),
                "tag narrows inside the category");
        assertEquals(5, MenuQuery.visibleEntries(entries, bar, "", null, null, NAMES).size(),
                "blank text and null filters pass everything");
        assertTrue(MenuQuery.visibleEntries(entries, bar, "zzz-nothing", null, null, NAMES).isEmpty(),
                "a search with no match empties the list");
        assertTrue(MenuQuery.visibleEntries(Map.of(), bar, null, null, null, NAMES).isEmpty(),
                "an empty pool stays empty");
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

        assertFalse(ids(MenuQuery.visibleEntries(entries, builtIn, null, null, null, NAMES))
                .contains(PRESS), "press is out of scope on a built-in bar");
        assertTrue(ids(MenuQuery.visibleEntries(entries, CURIOS, null, null, null, NAMES))
                .contains(PRESS), "its own bar keeps it");
        assertEquals(4, MenuQuery.visibleEntries(entries, builtIn, null, null, null, NAMES).size(),
                "every unscoped ability is still listed");
    }

    @Test
    void visibleEntriesPreservesInputOrder() {
        Map<ResourceLocation, AbilityDefinition> entries = pool();
        ResourceLocation bar = HotbarRegistry.builtinBarId(0);

        assertEquals(List.of(FIREBALL, HEAL, FLASH, FIREBOLT, PRESS),
                ids(MenuQuery.visibleEntries(entries, bar, null, null, null, NAMES)),
                "the screen sorts only if it wants to - the query keeps discovery order");
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
    }

    private static List<ResourceLocation> ids(List<MenuQuery.Entry> rows) {
        return rows.stream().map(MenuQuery.Entry::id).toList();
    }
}
