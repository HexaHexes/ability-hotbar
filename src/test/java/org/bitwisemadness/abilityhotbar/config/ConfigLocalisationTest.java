package org.bitwisemadness.abilityhotbar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig.Entry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.junit.jupiter.api.Test;

/**
 * Guards the config screen's localisation (issue #69).
 *
 * <p>NeoForge's {@code ConfigurationScreen} derives each label as
 * {@code modId + ".configuration." + entryKey} and renders the raw key when
 * that entry is missing from {@code en_us.json}; enum buttons render the raw
 * constant unless the enum implements {@code TranslatableEnum}. This test
 * walks both specs exactly like the screen does (section entries and leaf
 * entries, keyed by entry name only) and fails when a label or tooltip has no
 * lang entry, when two entries collide on one key, or when an enum stops
 * wiring its translated name.</p>
 */
class ConfigLocalisationTest {

    private static final String CONFIG_PREFIX = AbilityHotbar.MOD_ID + ".configuration.";

    private static JsonObject loadLang() {
        try (InputStream in = ConfigLocalisationTest.class
                .getResourceAsStream("/assets/ability_hotbar/lang/en_us.json")) {
            assertNotNull(in, "en_us.json must be on the test classpath");
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Walks one config file's values the way ConfigurationScreen does: top
     * entries are sections (label + tooltip, then recurse), section entries
     * are values (label + tooltip). Both are keyed by entry name alone.
     *
     * @return every translation key the screen will look up for this file
     */
    private static List<String> screenKeys(ModConfigSpec spec) {
        List<String> keys = new ArrayList<>();
        collectSection(spec.getValues(), keys);
        return keys;
    }

    private static void collectSection(UnmodifiableConfig section, List<String> keys) {
        for (Entry entry : section.entrySet()) {
            // getRawValue() is generic: bind to Object before instanceof (char[] trap)
            Object raw = entry.getRawValue();
            if (raw instanceof UnmodifiableConfig subsection) {
                keys.add(CONFIG_PREFIX + entry.getKey());
                keys.add(CONFIG_PREFIX + entry.getKey() + ".tooltip");
                collectSection(subsection, keys);
            } else {
                keys.add(CONFIG_PREFIX + entry.getKey());
                keys.add(CONFIG_PREFIX + entry.getKey() + ".tooltip");
            }
        }
    }

    @Test
    void everyScreenKeyHasANonEmptyLangEntry() {
        JsonObject lang = loadLang();
        List<String> keys = new ArrayList<>();
        keys.addAll(screenKeys(HotbarClientConfig.SPEC));
        keys.addAll(screenKeys(HotbarServerConfig.SPEC));

        for (String key : keys) {
            assertTrue(lang.has(key), "missing from en_us.json: " + key);
            assertTrue(lang.get(key).isJsonPrimitive() && !lang.get(key).getAsString().isBlank(),
                    "blank translation for: " + key);
        }
    }

    @Test
    void noTwoScreenEntriesShareOneTranslationKey() {
        // The screen keys labels by entry name only, so two sections with the
        // same leaf name (or a section and a value sharing a name) would show
        // one wrong label for both. Duplicates mean the scheme has broken.
        List<String> keys = new ArrayList<>();
        keys.addAll(screenKeys(HotbarClientConfig.SPEC));
        keys.addAll(screenKeys(HotbarServerConfig.SPEC));
        keys.removeIf(k -> k.endsWith(".tooltip"));

        Set<String> unique = new HashSet<>(keys);
        assertEquals(keys.size(), unique.size(),
                "duplicate label keys derived from the specs: " + keys);
    }

    @Test
    void enumButtonsShowTranslatedNamesNotRawConstants() {
        JsonObject lang = loadLang();

        for (BarAnchor anchor : BarAnchor.values()) {
            assertTrue(lang.has(anchor.translationKey()),
                    "missing from en_us.json: " + anchor.translationKey());
            Component name = anchor.getTranslatedName();
            assertTrue(name.getContents() instanceof TranslatableContents,
                    anchor + " must render as a translatable component");
            TranslatableContents contents = (TranslatableContents) name.getContents();
            assertEquals(anchor.translationKey(), contents.getKey(),
                    anchor + " must translate through its own key");
        }

        for (StatusAnchorMode mode : StatusAnchorMode.values()) {
            assertTrue(lang.has(mode.translationKey()),
                    "missing from en_us.json: " + mode.translationKey());
            Component name = mode.getTranslatedName();
            assertTrue(name.getContents() instanceof TranslatableContents,
                    mode + " must render as a translatable component");
            TranslatableContents contents = (TranslatableContents) name.getContents();
            assertEquals(mode.translationKey(), contents.getKey(),
                    mode + " must translate through its own key");
        }
    }
}
