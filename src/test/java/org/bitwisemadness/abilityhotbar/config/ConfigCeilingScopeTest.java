package org.bitwisemadness.abilityhotbar.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Documentation contract for the bar-count ceiling (hotbar#102): both
 * user-facing strings - the TOML comment and the config screen's label +
 * tooltip - must scope the ceiling to the <b>built-in</b> slice and state that
 * mod-registered bars ride along, because ADR-012 §4 never clips
 * registrations ("a curios bar cannot be shadowed out"). A wording that reads
 * as a <em>total</em> cyclable-bar cap is the defect this pins: the playtest
 * set {@code maxBars = 3}, reasonably expected three bars, and met the pack's
 * registered bars instead - the behavior was correct, the promise was not.
 *
 * <p>The comment half reads the live {@link org.neoforged.neoforge.common.ModConfigSpec.ValueSpec}
 * (the exact string the TOML writer emits), never the source text.</p>
 */
class ConfigCeilingScopeTest {

    private static final String MAX_BARS_LABEL = "ability_hotbar.configuration.maxBars";

    private static JsonObject loadLang() {
        try (InputStream in = ConfigCeilingScopeTest.class
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
    void maxBarsTomlCommentScopesItselfToBuiltInBars() {
        String comment = HotbarServerConfig.MAX_HOTBAR_COUNT.getSpec().getComment();
        assertTrue(comment.contains("built-in"),
                "the server ceiling's TOML comment must say built-in, not all bars: " + comment);
        assertTrue(comment.contains("registered"),
                "the server ceiling's TOML comment must state that registered bars ride along: " + comment);
    }

    @Test
    void maxBarsScreenStringsScopeToBuiltInBars() {
        JsonObject lang = loadLang();
        String label = lang.get(MAX_BARS_LABEL).getAsString();
        String tooltip = lang.get(MAX_BARS_LABEL + ".tooltip").getAsString();
        assertTrue(label.contains("built-in"),
                "the screen label must scope to built-in bars: " + label);
        assertTrue(tooltip.contains("built-in"),
                "the screen tooltip must scope to built-in bars: " + tooltip);
        assertTrue(tooltip.contains("registered"),
                "the screen tooltip must state that registered bars are always included: " + tooltip);
    }

    @Test
    void hotbarCountTomlCommentNamesTheRegisteredTail() {
        String comment = HotbarClientConfig.HOTBAR_COUNT.getSpec().getComment();
        assertTrue(comment.contains("Built-in"),
                "the client preference's TOML comment must scope to built-in bars: " + comment);
        assertTrue(comment.contains("registered"),
                "the client preference's TOML comment must name the registered tail: " + comment);
    }
}
