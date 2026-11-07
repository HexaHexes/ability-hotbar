package org.bitwisemadness.abilityhotbar.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilityhotbar.MinecraftBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Registered menu tabs (hotbar#154): loud ownership, the Icon/Item Stack +
 * Text identifier rules (never both, stored stacks are copies), registration
 * order as strip order, and the membership contract the screen filters with.
 */
class MenuTabsTest {

    static {
        // Item-stack icons need the item registry, which needs a bootstrap
        // (FML's LoadingModList included) - see MinecraftBootstrap.
        MinecraftBootstrap.ensure();
    }

    private static final ResourceLocation TAB =
            ResourceLocation.fromNamespaceAndPath("tensura", "skills");
    private static final ResourceLocation OTHER_TAB =
            ResourceLocation.fromNamespaceAndPath("tensura", "magic");
    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation OTHER_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "heal");

    private static final BiPredicate<ResourceLocation, AbilityDefinition> ALL =
            (id, definition) -> true;
    private static final BiPredicate<ResourceLocation, AbilityDefinition> ONLY_FIRE =
            (id, definition) -> id.equals(ABILITY);

    @AfterEach
    void isolate() {
        MenuTabs.remove(TAB);
        MenuTabs.remove(OTHER_TAB);
    }

    private static AbilityDefinition def() {
        return new AbilityDefinition("tensura.skill.x", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, java.util.List.of(), ConsumptionStrategies.ALL,
                AmountResolvers.STATIC, java.util.Set.of(), java.util.Map.of());
    }

    @Test
    void registrationIsLoud() {
        assertThrows(IllegalArgumentException.class, () -> MenuTabs.register(null, "k", ALL));
        assertThrows(IllegalArgumentException.class, () -> MenuTabs.register(TAB, " ", ALL));
        assertThrows(IllegalArgumentException.class, () -> MenuTabs.register(TAB, "k", (BiPredicate<ResourceLocation, AbilityDefinition>) null));

        MenuTabs.register(TAB, "tensura.tab.skills", ALL);
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> MenuTabs.register(TAB, "tensura.tab.other", ALL));
        assertTrue(duplicate.getMessage().contains(TAB.toString()), duplicate.getMessage());
        assertTrue(MenuTabs.remove(TAB));
        assertFalse(MenuTabs.remove(TAB), "second removal is a no-op");
    }

    @Test
    void anIconAndAnItemStackNeverBoth() {
        // both identifier kinds register fine on their own ...
        MenuTabs.register(TAB, "tensura.tab.skills",
                ResourceLocation.fromNamespaceAndPath("tensura", "gui/tab"), ALL);
        MenuTabs.register(OTHER_TAB, "tensura.tab.magic", new ItemStack(Items.BOOK), ALL);
        assertTrue(MenuTabs.get(TAB).orElseThrow().hasIcon());
        assertTrue(MenuTabs.get(OTHER_TAB).orElseThrow().hasIcon());
        // ... but one tab declaring both is unconstructible (the record guards it)
        assertThrows(IllegalArgumentException.class, () -> new MenuTabs.MenuTab(TAB, "k",
                ResourceLocation.fromNamespaceAndPath("tensura", "gui/tab"),
                new ItemStack(Items.BOOK), ALL));
    }

    @Test
    void identifiersRoundTripAndStacksAreCopies() {
        ResourceLocation sprite = ResourceLocation.fromNamespaceAndPath("tensura", "gui/tab");
        MenuTabs.register(TAB, "tensura.tab.skills", sprite, ALL);
        MenuTabs.MenuTab spriteTab = MenuTabs.get(TAB).orElseThrow();
        assertEquals(sprite, spriteTab.iconId());
        assertNull(spriteTab.icon());
        assertTrue(spriteTab.hasIcon());

        ItemStack stack = new ItemStack(Items.BOOK);
        MenuTabs.register(OTHER_TAB, "tensura.tab.magic", stack, ALL);
        MenuTabs.MenuTab stackTab = MenuTabs.get(OTHER_TAB).orElseThrow();
        assertNull(stackTab.iconId());
        assertTrue(stackTab.hasIcon());
        assertNotSame(stack, stackTab.icon(), "stored stacks are defensive copies");
        assertEquals(Items.BOOK, stackTab.icon().getItem());

        // a plain registration carries no visual identifier at all
        MenuTabs.remove(TAB);
        MenuTabs.register(TAB, "tensura.tab.skills", ALL);
        assertFalse(MenuTabs.get(TAB).orElseThrow().hasIcon());
    }

    @Test
    void registrationOrderIsStripOrder() {
        MenuTabs.register(TAB, "tensura.tab.skills", ALL);
        MenuTabs.register(OTHER_TAB, "tensura.tab.magic", ALL);
        List<MenuTabs.MenuTab> all = MenuTabs.all();
        assertEquals(2, all.size());
        assertEquals(TAB, all.get(0).id(), "first registration draws first");
        assertEquals(OTHER_TAB, all.get(1).id());
        assertEquals(List.of(TAB, OTHER_TAB),
                all.stream().map(MenuTabs.MenuTab::id).toList());
    }

    @Test
    void membershipFiltersThePool() {
        MenuTabs.register(TAB, "tensura.tab.skills", ONLY_FIRE);
        MenuTabs.MenuTab tab = MenuTabs.get(TAB).orElseThrow();
        AbilityDefinition definition = def();
        assertTrue(tab.accepts().test(ABILITY, definition), "its member passes");
        assertFalse(tab.accepts().test(OTHER_ABILITY, definition), "others are out");
    }
}
