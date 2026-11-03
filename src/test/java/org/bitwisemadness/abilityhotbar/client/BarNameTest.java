package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Bar display-name resolution for the switch notification (REQ-HOT-22, #28
 * AC 4): registered bars speak through their registration, built-ins through
 * their own lang entry, and identities the registry does not know show the
 * raw id - diagnosed, never an invented name.
 */
class BarNameTest {

    private static final ResourceLocation EXTRA =
            ResourceLocation.fromNamespaceAndPath("otherpack", "extra");

    @BeforeEach
    @AfterEach
    void isolateRegistry() {
        HotbarRegistry.reset();
    }

    private static JsonObject loadLang() {
        try (InputStream in = BarNameTest.class
                .getResourceAsStream("/assets/ability_hotbar/lang/en_us.json")) {
            assertTrue(in != null, "en_us.json must be on the test classpath");
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void registeredBarsSpeakThroughTheirDisplayNameKey() {
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);

        Component name = BarName.of(EXTRA);

        assertTrue(name.getContents() instanceof TranslatableContents,
                "registered bars must translate through their key");
        assertEquals("otherpack.bar.extra",
                ((TranslatableContents) name.getContents()).getKey());
    }

    @Test
    void builtInBarsUseTheirOwnLangEntry() {
        Component name = BarName.of(HotbarRegistry.builtinBarId(0));

        assertTrue(name.getContents() instanceof TranslatableContents);
        assertEquals(AbilityHotbar.MOD_ID + ".bar_0",
                ((TranslatableContents) name.getContents()).getKey());
    }

    @Test
    void unknownAndUnidentifiedIdsShowTheRawIdNeverAnInventedName() {
        Component unidentified = BarName.of(HotbarRegistry.unidentifiedBarId(2));
        assertTrue(unidentified.getContents() instanceof PlainTextContents,
                "an unproven identity shows as a literal, not a translation");
        assertEquals("ability_hotbar:unidentified_2", unidentified.getString());

        Component stranger = BarName.of(ResourceLocation.fromNamespaceAndPath("nobody", "nowhere"));
        assertEquals("nobody:nowhere", stranger.getString());

        assertEquals("", BarName.of(null).getString(), "null id names nothing");
    }

    @Test
    void everyBuiltInBarNameHasANonEmptyLangEntry() {
        // Without these the toast would render the raw key in-game - the gap
        // the config-localisation guard covers for config labels, here for
        // the ten built-in bar names.
        JsonObject lang = loadLang();
        for (int i = 0; i < 10; i++) {
            String key = AbilityHotbar.MOD_ID + ".bar_" + i;
            assertTrue(lang.has(key), "missing from en_us.json: " + key);
            assertTrue(lang.get(key).isJsonPrimitive() && !lang.get(key).getAsString().isBlank(),
                    "blank translation for: " + key);
        }
        assertFalse(lang.get(AbilityHotbar.MOD_ID + ".bar_0").getAsString().isBlank());
    }
}
