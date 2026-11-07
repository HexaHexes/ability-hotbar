package org.bitwisemadness.abilityhotbar.registry;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Registered ability-menu tabs (hotbar#154, the owner's creative-tab
 * model): providers declare tabs or declare none at all - the menu never
 * derives tabs from id namespaces.
 *
 * <p>A tab's identifier renders as <b>Icon/Item Stack + Text</b>: an
 * optional sprite or {@link ItemStack} followed by the localised display
 * name (the provider's own translation key). Membership is the provider's
 * predicate over {@code (id, definition)} - a tab filters the pool to what
 * it accepts; the menu's permanent {@code All} tab (screen-side) clears the
 * filter. A provider whose abilities are exclusive to its own hotbars
 * registers nothing and its abilities simply appear under All (plus their
 * scoped bars).</p>
 *
 * <p>One declaration per tab id, loud ownership - duplicates, blanks and
 * null memberships fail at registration (the styles/roles contract).</p>
 */
public final class MenuTabs {

    /**
     * One registered tab.
     *
     * @param id             stable namespaced tab id (the registering mod's namespace)
     * @param displayNameKey translation key for the tab's text
     * @param iconId         optional texture/sprite fallback, or null - drawn
     *                       when the stack renders nothing in a GUI context
     *                       (see {@link #resolvedIconTexture}) or when no
     *                       stack is declared
     * @param icon           optional item-stack icon, or null - the draw
     *                       preference when declared
     * @param accepts        membership: may this entry appear under the tab?
     */
    public record MenuTab(ResourceLocation id, String displayNameKey,
            @Nullable ResourceLocation iconId, @Nullable ItemStack icon,
            BiPredicate<ResourceLocation, AbilityDefinition> accepts) {

        public MenuTab {
            if (id == null) {
                throw new IllegalArgumentException("tab id must not be null");
            }
            if (displayNameKey == null || displayNameKey.isBlank()) {
                throw new IllegalArgumentException("tab display name key must not be blank for " + id);
            }
            if (accepts == null) {
                throw new IllegalArgumentException("tab membership must not be null for " + id);
            }
            if (icon != null) {
                icon = icon.copy(); // stored stacks are ours; callers may mutate theirs
            }
        }

        /** @return true when the tab carries a visual identifier at all */
        public boolean hasIcon() {
            return iconId != null || icon != null;
        }
    }

    private static final List<MenuTab> TABS = new CopyOnWriteArrayList<>();

    private MenuTabs() {
    }

    /** Declares a plain-text tab (hotbar#154). */
    public static void register(ResourceLocation id, String displayNameKey,
            BiPredicate<ResourceLocation, AbilityDefinition> accepts) {
        register(id, displayNameKey, null, null, accepts);
    }

    /** Declares a tab identified by a sprite icon plus its text. */
    public static void register(ResourceLocation id, String displayNameKey,
            ResourceLocation iconId, BiPredicate<ResourceLocation, AbilityDefinition> accepts) {
        register(id, displayNameKey, iconId, null, accepts);
    }

    /** Declares a tab identified by an item stack plus its text. */
    public static void register(ResourceLocation id, String displayNameKey, ItemStack icon,
            BiPredicate<ResourceLocation, AbilityDefinition> accepts) {
        register(id, displayNameKey, null, icon, accepts);
    }

    /**
     * Declares a tab carrying both identifier forms: the stack is the draw
     * preference and the sprite the fallback for stacks whose baked model
     * renders nothing in any vanilla GUI context (the screen's
     * resolve-or-hide rule extends to items, not just textures).
     */
    public static void register(ResourceLocation id, String displayNameKey,
            @Nullable ResourceLocation iconId, @Nullable ItemStack icon,
            BiPredicate<ResourceLocation, AbilityDefinition> accepts) {
        MenuTab tab = new MenuTab(id, displayNameKey, iconId, icon, accepts); // validates loudly
        for (MenuTab existing : TABS) {
            if (existing.id().equals(id)) {
                throw new IllegalStateException("Menu tab already registered: " + id);
            }
        }
        TABS.add(tab);
    }

    /** @return every registered tab, in registration order (the strip's order) */
    public static List<MenuTab> all() {
        return List.copyOf(TABS);
    }

    /** @return the tab for the id, if a provider declared it */
    public static Optional<MenuTab> get(ResourceLocation id) {
        return TABS.stream().filter(tab -> tab.id().equals(id)).findFirst();
    }

    /** Test isolation / future reload paths. */
    public static boolean remove(ResourceLocation id) {
        return TABS.removeIf(tab -> tab.id().equals(id));
    }

    /**
     * How a resolved icon draws - the render side's "do not display what
     * cannot be resolved" rule (owner's brief): a <b>texture</b> form (a
     * path, with or without {@code .png}; drawn like the bar's own icons)
     * or a <b>sprite</b> form (a bare GUI-sprite name), or empty when the
     * underlying resource does not exist - callers then render the tab
     * text-only, never a missing-texture box.
     *
     * @param iconId         the registered icon location (nullable)
     * @param resourceExists whether the candidate resource file is present
     * @return the draw form, or empty when nothing resolves
     */
    public static Optional<IconTexture> resolvedIconTexture(@Nullable ResourceLocation iconId,
            java.util.function.Predicate<ResourceLocation> resourceExists) {
        if (iconId == null) {
            return Optional.empty();
        }
        String path = iconId.getPath();
        if (path.contains("/") || path.endsWith(".png")) {
            ResourceLocation file = path.endsWith(".png")
                    ? iconId
                    : ResourceLocation.fromNamespaceAndPath(iconId.getNamespace(), path + ".png");
            return resourceExists.test(file) ? Optional.of(new IconTexture(file, false))
                    : Optional.empty();
        }
        ResourceLocation spriteFile = ResourceLocation.fromNamespaceAndPath(
                iconId.getNamespace(), "textures/gui/sprites/" + path + ".png");
        return resourceExists.test(spriteFile) ? Optional.of(new IconTexture(iconId, true))
                : Optional.empty();
    }

    /** A resolved icon: the value to draw and which draw call takes it. */
    public record IconTexture(ResourceLocation drawWith, boolean sprite) {
    }
}
