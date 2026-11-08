package org.bitwisemadness.abilityhotbar.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
     * A tab's live content (owner's directive): the mod that registered
     * the tab supplies WHICH abilities it lists, per player - the menu
     * resolves the ids against core's definitions and still applies
     * visibility and the tab's own {@code accepts} on top. Null on a tab
     * means the classic global-pool membership (accepts only).
     */
    @FunctionalInterface
    public interface TabContent {

        /**
         * @param player the asking player (client menu reads; null = an
         *               unknown context - return only what is known
         *               unconditionally, never guess)
         * @return the ids this tab currently lists (empty = nothing;
         *         unknown ids are dropped, never invented)
         */
        java.util.Collection<ResourceLocation> entries(@Nullable net.minecraft.world.entity.player.Player player);
    }

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
     * @param content        the mod's live content handler, or null for the
     *                       global pool (owner's handler directive)
     * @param informational  when true the tab never slots: rows are inert
     *                       to press, drag and click (info only)
     */
    public record MenuTab(ResourceLocation id, String displayNameKey,
            @Nullable ResourceLocation iconId, @Nullable ItemStack icon,
            BiPredicate<ResourceLocation, AbilityDefinition> accepts,
            @Nullable TabContent content, boolean informational) {

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

    /**
     * One declarable provider category tab (owner's generalisation brief:
     * tensura's and iss' tab shapes abstracted so any mod can register
     * and expand the family): membership is the definition's
     * {@code category}, the strip order is optional, and the content
     * handler (nullable) makes the list dynamic per player.
     *
     * @param id                tab id (the provider's namespace)
     * @param displayNameKey    provider-owned translation key
     * @param iconId            sprite fallback, or null
     * @param icon              preferred stack, or null
     * @param category          the definition category this tab lists
     * @param subCategoryOrder  the {@code type:} strip order, or null (none)
     * @param content           live content handler, or null (global pool)
     */
    public record CategoryTab(ResourceLocation id, String displayNameKey,
            @Nullable ResourceLocation iconId, @Nullable ItemStack icon,
            ResourceLocation category, @Nullable List<String> subCategoryOrder,
            @Nullable TabContent content) {

        public CategoryTab {
            if (id == null || displayNameKey == null || displayNameKey.isBlank()) {
                throw new IllegalArgumentException("category tab needs an id and a name key");
            }
            if (category == null) {
                throw new IllegalArgumentException("category tab needs a category for " + id);
            }
            if (icon != null) {
                icon = icon.copy();
            }
        }
    }

    private static final List<MenuTab> TABS = new CopyOnWriteArrayList<>();
    private static final Map<ResourceLocation, List<String>> SUBCATEGORY_ORDERS =
            new ConcurrentHashMap<>();

    private MenuTabs() {
    }

    /** Declares a plain-text tab (hotbar#154). */
    public static void register(ResourceLocation id, String displayNameKey,
            BiPredicate<ResourceLocation, AbilityDefinition> accepts) {
        register(id, displayNameKey, null, null, accepts, null, false);
    }

    /** Declares a plain tab with the mod's own content handler. */
    public static void register(ResourceLocation id, String displayNameKey,
            BiPredicate<ResourceLocation, AbilityDefinition> accepts,
            TabContent content) {
        register(id, displayNameKey, null, null, accepts, content, false);
    }

    /** Declares a tab identified by a sprite icon plus its text. */
    public static void register(ResourceLocation id, String displayNameKey,
            ResourceLocation iconId, BiPredicate<ResourceLocation, AbilityDefinition> accepts) {
        register(id, displayNameKey, iconId, null, accepts, null, false);
    }

    /** Declares a tab identified by an item stack plus its text. */
    public static void register(ResourceLocation id, String displayNameKey, ItemStack icon,
            BiPredicate<ResourceLocation, AbilityDefinition> accepts) {
        register(id, displayNameKey, null, icon, accepts, null, false);
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
        register(id, displayNameKey, iconId, icon, accepts, null, false);
    }

    /**
     * Declares a tab with the mod's own content handler (owner's handler
     * directive): the handler names the entries, accepts and visibility
     * still narrow them.
     */
    public static void register(ResourceLocation id, String displayNameKey,
            @Nullable ResourceLocation iconId, @Nullable ItemStack icon,
            BiPredicate<ResourceLocation, AbilityDefinition> accepts,
            @Nullable TabContent content) {
        register(id, displayNameKey, iconId, icon, accepts, content, false);
    }

    /**
     * The full declaration: both icon forms, membership, live content and
     * the informational flag (rows never slot - info only).
     */
    public static void register(ResourceLocation id, String displayNameKey,
            @Nullable ResourceLocation iconId, @Nullable ItemStack icon,
            BiPredicate<ResourceLocation, AbilityDefinition> accepts,
            @Nullable TabContent content, boolean informational) {
        MenuTab tab = new MenuTab(id, displayNameKey, iconId, icon, accepts, // validates loudly
                content, informational);
        for (MenuTab existing : TABS) {
            if (existing.id().equals(id)) {
                throw new IllegalStateException("Menu tab already registered: " + id);
            }
        }
        TABS.add(tab);
    }

    /**
     * Registers a provider's whole category-tab family from one
     * declaration (owner's generalisation brief): membership is the
     * declared {@code category} equality, and a declared strip order is
     * recorded with the tab - the tensura and iss shapes, reusable.
     */
    public static void registerCategoryTabs(List<CategoryTab> tabs) {
        if (tabs == null) {
            throw new IllegalArgumentException("category tabs must not be null");
        }
        for (CategoryTab spec : tabs) {
            ResourceLocation category = spec.category();
            register(spec.id(), spec.displayNameKey(), spec.iconId(), spec.icon(),
                    (id, definition) -> definition.category().equals(category),
                    spec.content(), false);
            if (spec.subCategoryOrder() != null) {
                subCategoryOrder(spec.id(), spec.subCategoryOrder());
            }
        }
    }

    /** @return every registered tab, in registration order (the strip's order) */
    public static List<MenuTab> all() {
        return List.copyOf(TABS);
    }

    /** @return the tab for the id, if a provider declared it */
    public static Optional<MenuTab> get(ResourceLocation id) {
        return TABS.stream().filter(tab -> tab.id().equals(id)).findFirst();
    }

    /**
     * Declares the display order of a tab's {@code type:} subcategory
     * strip (the owner's reorder brief): listed tags first, in order;
     * then any present-but-unlisted tags alphabetically. A listed tag the
     * pool never has simply never draws, so a provider may reserve order
     * slots for categories it expects later. Loud on nulls; one order per
     * tab - the last declaration wins.
     *
     * @param tabId        the registered tab the order belongs to
     * @param orderedTags  the raw {@code type:} tags, in display order
     */
    public static void subCategoryOrder(ResourceLocation tabId, List<String> orderedTags) {
        if (tabId == null) {
            throw new IllegalArgumentException("subcategory order tab id must not be null");
        }
        if (orderedTags == null) {
            throw new IllegalArgumentException(
                    "subcategory order must not be null for " + tabId);
        }
        SUBCATEGORY_ORDERS.put(tabId, List.copyOf(orderedTags));
    }

    /**
     * @param tabId the registered tab
     * @return the declared strip order for the tab, empty when it declared
     *         none (the caller then keeps plain alphabetical order)
     */
    public static List<String> subCategoryOrder(ResourceLocation tabId) {
        if (tabId == null) {
            return List.of();
        }
        return SUBCATEGORY_ORDERS.getOrDefault(tabId, List.of());
    }

    /** Test isolation / future reload paths: the tab's order dies with it. */
    public static boolean remove(ResourceLocation id) {
        SUBCATEGORY_ORDERS.remove(id);
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
