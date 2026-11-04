package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The two-digit slot-number convention (maintainer ruling): every rendered
 * slot number is zero-padded to 01..18 - the menu's slot row and hovered
 * detail (via {@link AbilityMenuScreen#slotLabel}) and the keybind names the
 * Controls screen shows. Physical key references (Ctrl + 1..9) and bar
 * numbers are not slot numbers and stay unpadded.
 */
class SlotNumberFormatTest {

    @Test
    void menuSlotLabelsAreZeroPadded() {
        assertEquals("01", AbilityMenuScreen.slotLabel(1));
        assertEquals("09", AbilityMenuScreen.slotLabel(9));
        assertEquals("10", AbilityMenuScreen.slotLabel(10));
        assertEquals("18", AbilityMenuScreen.slotLabel(18));
    }

    @Test
    void keybindNamesEndInTheTwoDigitSlotNumber() {
        JsonObject lang = loadLang();
        for (int slot = 1; slot <= 18; slot++) {
            String key = "key.abilityhotbar.activate_slot_" + slot;
            String name = lang.get(key).getAsString();
            assertTrue(name.endsWith(String.format(Locale.ROOT, "%02d", slot)),
                    key + " must end in the two-digit slot number, got: " + name);
        }
    }

    private static JsonObject loadLang() {
        try (InputStream in = SlotNumberFormatTest.class
                .getResourceAsStream("/assets/ability_hotbar/lang/en_us.json")) {
            assertTrue(in != null, "en_us.json must be on the test classpath");
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
