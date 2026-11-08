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
 * Text identifier rules (both forms may ride together - the stack is the
 * draw preference, the sprite the fallback for stacks that render nothing
 * in a GUI; stored stacks are copies), registration order as strip order,
 * and the membership contract the screen filters with.
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
    void anIconAndAnItemStackMayRideTogether() {
        // both identifier kinds register fine on their own ...
        MenuTabs.register(TAB, "tensura.tab.skills",
                ResourceLocation.fromNamespaceAndPath("tensura", "gui/tab"), ALL);
        MenuTabs.register(OTHER_TAB, "tensura.tab.magic", new ItemStack(Items.BOOK), ALL);
        assertTrue(MenuTabs.get(TAB).orElseThrow().hasIcon());
        assertTrue(MenuTabs.get(OTHER_TAB).orElseThrow().hasIcon());
        // ... and so does one tab declaring both: the stack is the draw
        // preference, the sprite the fallback for stacks whose model
        // renders nothing in any vanilla GUI context (skill books' book).
        MenuTabs.remove(TAB);
        ResourceLocation sprite = ResourceLocation.fromNamespaceAndPath("tensura", "gui/tab");
        MenuTabs.register(TAB, "tensura.tab.skills", sprite, new ItemStack(Items.BOOK), ALL);
        MenuTabs.MenuTab both = MenuTabs.get(TAB).orElseThrow();
        assertEquals(sprite, both.iconId());
        assertEquals(Items.BOOK, both.icon().getItem(), "the stack keeps its draw preference");
        assertTrue(both.hasIcon());
    }

    @Test
    void subCategoryOrderIsDeclaredPerTabAndDiesWithIt() {
        MenuTabs.register(TAB, "tensura.tab.skills", ALL);
        MenuTabs.subCategoryOrder(TAB, List.of("type:resistance", "type:ultimate"));
        assertEquals(List.of("type:resistance", "type:ultimate"),
                MenuTabs.subCategoryOrder(TAB), "declared order wins");
        assertEquals(List.of(), MenuTabs.subCategoryOrder(OTHER_TAB),
                "an undeclared tab keeps no order (alphabetical)");
        assertEquals(List.of(), MenuTabs.subCategoryOrder(null), "null tab asks for nothing");
        assertThrows(IllegalArgumentException.class,
                () -> MenuTabs.subCategoryOrder(null, List.of("type:x")), "null id is loud");
        assertThrows(IllegalArgumentException.class,
                () -> MenuTabs.subCategoryOrder(TAB, null), "null list is loud");
        assertTrue(MenuTabs.remove(TAB), "tab removal succeeds");
        assertEquals(List.of(), MenuTabs.subCategoryOrder(TAB),
                "the declaration dies with its tab");
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

    @Test
    void resolvedIconTexturePrefersTextureFormAndNeverGuesses() {
        // Path without .png resolves to the suffixed file (the bar's own
        // texture convention) and draws with blit - like hotbar icons.
        var texture = MenuTabs.resolvedIconTexture(
                ResourceLocation.fromNamespaceAndPath("tensura",
                        "textures/gui/skill/skill_tab_icon"),
                rl -> rl.getPath().endsWith(".png"));
        assertTrue(texture.isPresent(), "an existing texture file resolves");
        assertEquals("textures/gui/skill/skill_tab_icon.png",
                texture.get().drawWith().getPath());
        assertFalse(texture.get().sprite(), "path form draws with blit");

        // an explicit .png passes through unchanged
        assertEquals("textures/x.png", MenuTabs.resolvedIconTexture(
                        ResourceLocation.fromNamespaceAndPath("tensura", "textures/x.png"),
                        rl -> true).orElseThrow().drawWith().getPath(),
                "an explicit suffix is kept");

        // a bare name resolves against the sprite folder, draws as sprite
        var sprite = MenuTabs.resolvedIconTexture(
                ResourceLocation.fromNamespaceAndPath("hotbar", "spark"),
                rl -> rl.getPath().equals("textures/gui/sprites/spark.png"));
        assertTrue(sprite.isPresent(), "an existing sprite resolves");
        assertTrue(sprite.get().sprite(), "bare name form draws with blitSprite");
        assertEquals("spark", sprite.get().drawWith().getPath());
    }

    @Test
    void unresolvableIconsResolveToNothing() {
        assertFalse(MenuTabs.resolvedIconTexture(
                        ResourceLocation.fromNamespaceAndPath("tensura", "textures/missing.png"),
                        rl -> false).isPresent(),
                "a missing texture resolves to nothing (text-only tab)");
        assertFalse(MenuTabs.resolvedIconTexture(
                        ResourceLocation.fromNamespaceAndPath("hotbar", "absent_sprite"),
                        rl -> false).isPresent(),
                "a missing sprite resolves to nothing (text-only tab)");
        assertFalse(MenuTabs.resolvedIconTexture(null, rl -> true).isPresent(),
                "a null icon resolves to nothing");
    }
}
