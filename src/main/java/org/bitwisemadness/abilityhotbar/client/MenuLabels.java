package org.bitwisemadness.abilityhotbar.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Player-facing labels for the menu's open vocabularies (hotbar#139): the
 * menu never renders a raw category id, tag or provider namespace. Each
 * value first checks a convention translation key - providers ship the
 * entry under their own lang file:
 *
 * <ul>
 *   <li>category {@code ns:path} -&gt; {@code ability_hotbar.category.ns.path}
 *       (path separators become dots)</li>
 *   <li>tag {@code some:tag} -&gt; {@code ability_hotbar.tag.some.tag}
 *       (separators become dots, underscores kept)</li>
 *   <li>provider namespace -&gt; {@code ability_hotbar.provider.ns}</li>
 * </ul>
 *
 * <p>When no translation exists the prettified raw value shows instead
 * (separators to spaces, first letter capitalised): {@code tensura:magic}
 * reads as "Magic", {@code battlewill_style} as "Battlewill style" - never
 * a raw id and never a missing key on screen. The key builders and the
 * prettifier are pure (fixtures); the {@link I18n} probe is client-side.</p>
 */
final class MenuLabels {

    private MenuLabels() {
    }

    /** The convention translation key for a category id. */
    static String categoryKey(ResourceLocation category) {
        return "ability_hotbar.category." + category.getNamespace() + "."
                + separatorsToDots(category.getPath());
    }

    /** The convention translation key for a tag string. */
    static String tagKey(String tag) {
        return "ability_hotbar.tag." + separatorsToDots(tag);
    }

    /** The convention translation key for a provider namespace. */
    static String providerKey(String namespace) {
        return "ability_hotbar.provider." + namespace;
    }

    /** Player-facing fallback: separators to spaces, first letter capitalised. */
    static String prettify(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String words = raw.replace('/', ' ').replace(':', ' ').replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** A localised category name: the convention key when translated, else the prettified path. */
    static Component category(ResourceLocation category) {
        String key = categoryKey(category);
        return I18n.exists(key)
                ? Component.translatable(key)
                : Component.literal(prettify(category.getPath()));
    }

    /** A localised tag name: the convention key when translated, else the prettified string. */
    static Component tag(String tag) {
        String key = tagKey(tag);
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(prettify(tag));
    }

    /** A localised provider (mod) name: the convention key when translated, else the prettified namespace. */
    static Component provider(@Nullable String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return Component.literal("");
        }
        String key = providerKey(namespace);
        return I18n.exists(key)
                ? Component.translatable(key)
                : Component.literal(prettify(namespace));
    }

    /** {@code a/b:c} -&gt; {@code a.b.c} so the key stays a valid translation id. */
    private static String separatorsToDots(String raw) {
        return raw.replace('/', '.').replace(':', '.');
    }
}
