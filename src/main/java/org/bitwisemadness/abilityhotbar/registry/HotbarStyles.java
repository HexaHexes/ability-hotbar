package org.bitwisemadness.abilityhotbar.registry;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Registered hotbar styles - named texture sets (issue #127).
 *
 * <p>Mods register styles at setup ({@link #register}) and point their
 * <b>content bars</b> at one by id ({@code registerContentBar(barId, key,
 * styleId)}); <b>assignable</b> bars are player-owned, so the player applies
 * a style through the menu - the choice rides a CLIENT preference string
 * ({@code menu.barStyles}, {@code barId=styleId;...} - resource-location
 * paths never contain {@code ';' } or {@code '='}, so the encoding is
 * unambiguous) and the layer resolves it through {@link #effective}, which
 * holds the whole policy: a player choice applies to <b>assignable bars
 * only</b> (a content bar keeps its mod's brand), falls back to the
 * registration's textures on a stale or unparseable entry, and never throws.</p>
 *
 * <p>Styles resolve to their {@link BarTextures} at content-bar registration
 * (unknown style -&gt; loud {@link IllegalArgumentException}: register styles
 * before bars, the same order-safety story as providers), so the render path
 * reads resolved textures only.</p>
 */
public final class HotbarStyles {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<HotbarStyle> STYLES = new CopyOnWriteArrayList<>();

    private HotbarStyles() {
    }

    /**
     * Declares one named style. Call at mod setup, before the bars that use it.
     *
     * @throws IllegalArgumentException on null ids, blank keys or null textures
     * @throws IllegalStateException    when the id is already registered (loud ownership)
     */
    public static void register(ResourceLocation id, String displayNameKey, BarTextures textures) {
        if (id == null) {
            throw new IllegalArgumentException("style id must not be null");
        }
        if (displayNameKey == null || displayNameKey.isBlank()) {
            throw new IllegalArgumentException("display name key must not be blank for " + id);
        }
        if (textures == null) {
            throw new IllegalArgumentException("style textures must not be null for " + id);
        }
        for (HotbarStyle style : STYLES) {
            if (style.id().equals(id)) {
                throw new IllegalStateException("hotbar style already registered: " + id);
            }
        }
        STYLES.add(new HotbarStyle(id, displayNameKey, textures));
    }

    /** @return the style for the id, if a mod declared it */
    public static Optional<HotbarStyle> get(ResourceLocation id) {
        return STYLES.stream().filter(style -> style.id().equals(id)).findFirst();
    }

    /** @return the style's textures, if the style exists */
    public static Optional<BarTextures> textures(ResourceLocation id) {
        return get(id).map(HotbarStyle::textures);
    }

    /** @return every registered style, in registration order (the cycler's list) */
    public static List<HotbarStyle> all() {
        return List.copyOf(STYLES);

    }

    /** Test isolation: removes a declared style. */
    public static boolean remove(ResourceLocation id) {
        boolean removed = STYLES.removeIf(style -> style.id().equals(id));
        return removed;
    }

    /**
     * The whole resolution policy (issue #127), pure: a player's choice
     * applies to <b>assignable</b> bars only, and only when the style still
     * exists; anything else falls back to the registration's textures (which
     * default to vanilla). A stale or unparseable preference entry logs a
     * debug line and falls back - never a crash.
     */
    public static BarTextures effective(HotbarRegistration registration, String rawStyles,
            ResourceLocation barId) {
        if (registration.assignable()) {
            String styleId = playerStyleId(rawStyles, barId);
            if (styleId != null) {
                Optional<BarTextures> style = parse(styleId).flatMap(HotbarStyles::textures);
                if (style.isPresent()) {
                    return style.get();
                }
                LOGGER.debug("Bar {} preference names unknown style '{}' - falling back", barId, styleId);
            }
        }
        return registration.textures();
    }

    /** The bar's preferred style id from the raw preference string, or null. */
    @Nullable
    public static String playerStyleId(String rawStyles, ResourceLocation barId) {
        if (rawStyles == null || rawStyles.isEmpty()) {
            return null;
        }
        String prefix = barId + "=";
        for (String entry : rawStyles.split(";")) {
            if (entry.startsWith(prefix)) {
                String value = entry.substring(prefix.length());
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /**
     * The raw preference string with this bar's entry set (or dropped when
     * {@code styleId} is null - the vanilla default), other bars untouched.
     * Pure: resource-location paths never contain {@code ';' } or {@code '='}.
     */
    public static String withStyle(String rawStyles, ResourceLocation barId, @Nullable String styleId) {
        StringBuilder rebuilt = new StringBuilder();
        String prefix = barId + "=";
        if (rawStyles != null && !rawStyles.isEmpty()) {
            for (String entry : rawStyles.split(";")) {
                if (entry.startsWith(prefix) || entry.isEmpty()) {
                    continue;
                }
                if (rebuilt.length() > 0) {
                    rebuilt.append(';');
                }
                rebuilt.append(entry);
            }
        }
        if (styleId != null) {
            if (rebuilt.length() > 0) {
                rebuilt.append(';');
            }
            rebuilt.append(prefix).append(styleId);
        }
        return rebuilt.toString();
    }

    private static Optional<ResourceLocation> parse(String styleId) {
        try {
            return Optional.of(ResourceLocation.parse(styleId));
        } catch (RuntimeException e) {
            LOGGER.debug("Malformed style id '{}' in the bar preference", styleId);
            return Optional.empty();
        }
    }
}
