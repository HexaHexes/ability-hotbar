package org.bitwisemadness.abilityhotbar.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Menu styles (issue #129): the TRS palette sync pinned against its recorded
 * provenance, loud registration, the config-resolution fallbacks, and the
 * grep gate that keeps palette hex literals out of the screen (they live in
 * {@link MenuStyle} only).
 */
class MenuStylesTest {

    private static final ResourceLocation CUSTOM =
            ResourceLocation.fromNamespaceAndPath("otherpack", "neon_menu");

    @AfterEach
    void isolate() {
        MenuStyles.remove(CUSTOM);
    }

    @Test
    void theShippedStyleIsPreRegisteredWithTheTrsPalette() {
        var builtIn = MenuStyles.get(MenuStyles.BUILT_IN).orElseThrow();
        assertEquals("ability_hotbar.menu.menustyle_trs_saodark", builtIn.displayNameKey());

        MenuStyle style = builtIn.style();
        // the provenance table (issue #129): Tensura RPG System's default
        // InterfaceTheme.SAO_DARK, verified against their decompiled enum
        assertEquals(0x131D2C, style.background(), "SAO_DARK background");
        assertEquals(0x22354A, style.raised(), "SAO_DARK raised");
        assertEquals(0xEAF4FF, style.text(), "SAO_DARK text");
        assertEquals(0x95ACC2, style.muted(), "SAO_DARK muted");
        assertEquals(0x67DDED, style.accent(), "SAO_DARK accent");
        assertEquals(0xFFC679, style.action(), "SAO_DARK action");
        assertEquals(15, style.dimPercent(), "their world_dimming default");
        assertEquals(78, style.opacityPercent(), "their panel_opacity default");
    }

    @Test
    void theOpacityHelperMatchesTheirFormula() {
        assertEquals(0xC6FFFFFF, MenuStyle.alpha(0xFFFFFF, 78), "78% of 255 = 198 = 0xC6");
        assertEquals(0x00123456, MenuStyle.alpha(0x123456, 0), "zero percent clears the alpha");
        assertEquals(0xFF123456, MenuStyle.alpha(0x123456, 100), "full percent");
        assertEquals(0x9967DDED, MenuStyle.alpha(0x67DDED, 60),
                "60% of 255 = 153 = 0x99 over the high byte");
        MenuStyle style = MenuStyle.TRS_SAODARK;
        assertEquals(MenuStyle.alpha(style.background(), 78), style.glass(style.background()),
                "glass is the background at the style's own opacity");
    }

    @Test
    void registrationIsLoudAndOrderDrivesTheCycler() {
        assertThrows(IllegalArgumentException.class,
                () -> MenuStyles.register(null, "k", MenuStyle.TRS_SAODARK));
        assertThrows(IllegalArgumentException.class,
                () -> MenuStyles.register(CUSTOM, "  ", MenuStyle.TRS_SAODARK));
        assertThrows(IllegalArgumentException.class,
                () -> MenuStyles.register(CUSTOM, "otherpack.style.neon", null));

        MenuStyles.register(CUSTOM, "otherpack.style.neon", MenuStyle.TRS_SAODARK);
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> MenuStyles.register(CUSTOM, "otherpack.style.other", MenuStyle.DEFAULT));
        assertTrue(duplicate.getMessage().contains(CUSTOM.toString()), duplicate.getMessage());

        assertEquals(MenuStyles.BUILT_IN, MenuStyles.all().get(0).id(),
                "the built-in sits first - the cycler starts there");
        assertEquals(CUSTOM, MenuStyles.all().get(1).id());
    }

    @Test
    void activeResolvesTheConfigAndFallsBackToTheBuiltIn() {
        assertEquals(MenuStyle.TRS_SAODARK, MenuStyles.active(""), "empty = the built-in");
        assertEquals(MenuStyle.TRS_SAODARK, MenuStyles.active(null), "unset = the built-in");
        assertEquals(MenuStyle.TRS_SAODARK, MenuStyles.active("definitely_missing"),
                "a stale config entry falls back, never a crash");
        assertEquals(MenuStyle.TRS_SAODARK, MenuStyles.active("not a resource location!"),
                "a malformed entry falls back too");

        MenuStyles.register(CUSTOM, "otherpack.style.neon", MenuStyle.DEFAULT);
        assertEquals(MenuStyle.DEFAULT, MenuStyles.active(CUSTOM.toString()),
                "a registered id resolves to its palette");
    }

    @Test
    void theScreenCarriesNoPaletteHexLiterals() throws Exception {
        // the palette lives in MenuStyle only: the screen draws through the
        // active style (the semantic inert-diagnostic red is the one exception)
        Path source = Path.of("src", "main", "java", "org", "bitwisemadness",
                "abilityhotbar", "client", "AbilityMenuScreen.java");
        assertTrue(Files.exists(source), "run from the project root (gradle does)");
        String text = Files.readString(source).replace("0xFFFF5555", "");
        assertFalse(Pattern.compile("0x[0-9A-Fa-f]{6}").matcher(text).find(),
                "one-off palette hex literals must live in MenuStyle only");
    }
}
