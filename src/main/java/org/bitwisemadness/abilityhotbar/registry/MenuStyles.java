package org.bitwisemadness.abilityhotbar.registry;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Registered menu styles (issue #129) - named skins for the ability menu,
 * the menu-side sibling of {@link HotbarStyles}.
 *
 * <p>The shipped default is pre-registered under a core-owned id (like the
 * strategy/resolver built-ins): {@code ability_hotbar:trs_saodark}, the
 * Tensura RPG System palette sync. Mods may register their own at setup;
 * the active style is the CLIENT config {@code menu.style} resolving through
 * {@link #active} - an unknown or empty value falls back to the built-in,
 * never a crash.</p>
 */
public final class MenuStyles {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The shipped style's core-owned id (the config's default answer). */
    public static final ResourceLocation BUILT_IN =
            ResourceLocation.fromNamespaceAndPath("ability_hotbar", "trs_saodark");

    private static final List<MenuStyleEntry> STYLES = new CopyOnWriteArrayList<>();

    static {
        // core owns the built-in name; registration order drives the cycler
        STYLES.add(new MenuStyleEntry(BUILT_IN,
                "ability_hotbar.menu.menustyle_trs_saodark", MenuStyle.TRS_SAODARK));
    }

    private MenuStyles() {
    }

    /**
     * Declares one menu style. Call at mod setup.
     *
     * @throws IllegalArgumentException on null ids or blank keys
     * @throws IllegalStateException    when the id is already registered (loud ownership)
     */
    public static void register(ResourceLocation id, String displayNameKey, MenuStyle style) {
        if (id == null) {
            throw new IllegalArgumentException("menu style id must not be null");
        }
        if (displayNameKey == null || displayNameKey.isBlank()) {
            throw new IllegalArgumentException("display name key must not be blank for " + id);
        }
        if (style == null) {
            throw new IllegalArgumentException("menu style must not be null for " + id);
        }
        if (get(id).isPresent()) {
            throw new IllegalStateException("menu style already registered: " + id);
        }
        STYLES.add(new MenuStyleEntry(id, displayNameKey, style));
    }

    /** @return the style entry for the id, if it exists */
    public static Optional<MenuStyleEntry> get(ResourceLocation id) {
        return STYLES.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    /** @return every registered style, in registration order (the cycler's list) */
    public static List<MenuStyleEntry> all() {
        return List.copyOf(STYLES);
    }

    /**
     * The active style for a raw config value: a known id resolves, anything
     * else falls back to the built-in (stale config entries never crash).
     */
    public static MenuStyle active(String configuredId) {
        if (configuredId != null && !configuredId.isEmpty()) {
            try {
                Optional<MenuStyleEntry> entry = get(ResourceLocation.parse(configuredId));
                if (entry.isPresent()) {
                    return entry.get().style();
                }
                LOGGER.debug("Configured menu style '{}' is unknown - using the built-in", configuredId);
            } catch (RuntimeException e) {
                LOGGER.debug("Malformed menu style id '{}' in the config", configuredId);
            }
        }
        return MenuStyle.DEFAULT;
    }

    /** Test isolation: removes a registered style (the built-in included). */
    public static boolean remove(ResourceLocation id) {
        boolean removed = STYLES.removeIf(entry -> entry.id().equals(id));
        return removed;
    }

    /**
     * One registered style: id, player-facing name key, palette.
     *
     * @param id             stable identity, namespaced
     * @param displayNameKey translation key for the style's name
     * @param style          the palette
     */
    public record MenuStyleEntry(ResourceLocation id, String displayNameKey, MenuStyle style) {
    }
}
