package org.bitwisemadness.abilityhotbar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig.Entry;
import com.electronwill.nightconfig.toml.TomlFormat;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Drives the client spec through the load / correct / set / save / reload cycle
 * that NeoForge's configuration screen and its {@code ConfigWatcher} use, without
 * a running client.
 *
 * <p>Regression guard for the report that editing one {@code display.*} key reset
 * an unrelated one: {@code acceptConfig()} only skips its {@code correct()} +
 * {@code save()} pass when {@code isCorrect()} holds, and {@code ConfigWatcher}
 * reloads the whole file on any disk change - so a well-formed file must be
 * correct, correct nothing, and survive a write/read round trip intact.</p>
 */
class ClientConfigRoundTripTest {

    /**
     * The live file, byte for byte (comments included - {@code isCorrect()} is
     * sensitive to them, and a shortened comment makes an otherwise valid file
     * "incorrect" on every load).
     */
    private static final String REAL_FILE = """
            [display]
            #Bar anchor: aboveHotbar, aboveXp, aboveStatus, leftEdge or rightEdge (valid with either orientation)
            #Allowed Values: ABOVE_HOTBAR, ABOVE_XP, ABOVE_STATUS, LEFT_EDGE, RIGHT_EDGE
            anchor = "RIGHT_EDGE"
            #Bar orientation: true = horizontal, false = vertical
            horizontal = false
            #Preferred slots per hotbar bar (clamped to the server's maxSlots)
            # Default: 9
            # Range: 1 ~ 18
            slotsPerBar = 9
            #Built-in hotbars in the cycle set (clamped to the server's maxBars; mod-registered bars are always included)
            # Default: 3
            # Range: 1 ~ 10
            hotbarCount = 3
            #Mouse-wheel bar cycling skips bars with no assigned abilities (direct selection always lands where pressed)
            skipEmptyBars = true
            #Show the bar's name briefly after switching hotbars
            switchToast = true
            #Bar opacity (0.0 to 1.0)
            # Default: 1.0
            # Range: 0.0 ~ 1.0
            opacity = 1.0
            #Show cooldown progress on slots
            showCooldowns = true
            #Show resource cost indicators on slots
            showCosts = true
            #Horizontal offset (pixels) from the bar's anchor
            # Default: 0
            # Range: -500 ~ 500
            offsetX = 0
            #Vertical offset (pixels) from the bar's anchor
            # Default: 0
            # Range: -500 ~ 500
            offsetY = 0
            #Bar render scale (0.5 to 2.0)
            # Default: 1.0
            # Range: 0.5 ~ 2.0
            scale = 1.0
            #Draw ability icons in assigned slots
            showIcons = true
            #aboveStatus height: DYNAMIC tracks the player's hearts, armor and oxygen; FIXED uses the vanilla baseline (only read when display.anchor = aboveStatus)
            #Allowed Values: FIXED, DYNAMIC
            statusAnchorMode = "DYNAMIC"
            [menu]
            #Last category filter chosen in the ability menu (empty = no filter)
            lastCategory = ""
            #Last tag filter chosen in the ability menu (empty = no filter)
            lastTag = ""
            """;

    private static CommentedConfig parse(String toml) {
        return TomlFormat.instance().createParser().parse(toml);
    }

    private static String write(CommentedConfig config) {
        StringWriter out = new StringWriter();
        TomlFormat.instance().createWriter().write(config, out);
        return out.toString();
    }

    /** Every key under [display], normalised to a String so types cannot hide a change. */
    private static Map<String, Object> snapshot(CommentedConfig config) {
        Map<String, Object> out = new TreeMap<>();
        Object display = config.get("display");
        if (display instanceof UnmodifiableConfig section) {
            for (Entry entry : section.entrySet()) {
                // getRawValue() is generic, so bind to Object before String.valueOf
                Object raw = entry.getRawValue();
                out.put("display." + entry.getKey(), String.valueOf(raw));
            }
        } else {
            out.put("display", String.valueOf(display));
        }
        return out;
    }

    @Test
    void aWellFormedFileIsConsideredCorrect() {
        // acceptConfig() skips correct()+save() only when this holds; false here
        // means NeoForge rewrites our file on every load and every watcher reload.
        assertTrue(HotbarClientConfig.SPEC.isCorrect(parse(REAL_FILE)),
                "the live client file must be considered correct");
    }

    @Test
    void correctRewritesNothing() {
        CommentedConfig config = parse(REAL_FILE);
        Map<String, Object> before = snapshot(config);
        HotbarClientConfig.SPEC.correct(config);

        assertEquals(before, snapshot(config),
                "correct() rewrote these keys, so every load/reload reverts them to defaults");
    }

    @Test
    void everySetValueSurvivesWriteAndReload() {
        CommentedConfig config = parse(REAL_FILE);

        // What ConfigValue.set() does: writes just that one path.
        config.set(List.of("display", "scale"), 1.5d);
        config.set(List.of("display", "horizontal"), Boolean.TRUE);
        config.set(List.of("display", "anchor"), BarAnchor.ABOVE_XP);

        // What save() + a watcher reload does: write the file, read it back,
        // then correct it against the spec.
        CommentedConfig reloaded = parse(write(config));
        HotbarClientConfig.SPEC.correct(reloaded);

        assertEquals(Boolean.TRUE, reloaded.get("display.horizontal"), "horizontal reset by reload");
        assertEquals(1.5d, ((Number) reloaded.get("display.scale")).doubleValue(),
                "scale reset by reload");
        assertEquals("ABOVE_XP", reloaded.get("display.anchor"), "anchor reset by reload");
        assertEquals(Integer.valueOf(9), reloaded.get("display.slotsPerBar"),
                "an untouched key was reset by reload");
    }
}
