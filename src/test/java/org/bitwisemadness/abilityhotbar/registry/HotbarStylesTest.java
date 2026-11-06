package org.bitwisemadness.abilityhotbar.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Hotbar styles (issue #127): loud registration, and the one pure policy
 * function - a player's choice applies to assignable bars only, falls back
 * on stale/unknown entries, and the preference string round-trips per bar.
 */
class HotbarStylesTest {

    private static final ResourceLocation STYLE =
            ResourceLocation.fromNamespaceAndPath("otherpack", "branded");
    private static final ResourceLocation OTHER_STYLE =
            ResourceLocation.fromNamespaceAndPath("otherpack", "neon");
    private static final ResourceLocation ASSIGNABLE =
            ResourceLocation.fromNamespaceAndPath("otherpack", "player_bar");
    private static final ResourceLocation CONTENT =
            ResourceLocation.fromNamespaceAndPath("otherpack", "content_bar");
    private static final BarTextures TEXTURES = new BarTextures(
            ResourceLocation.fromNamespaceAndPath("otherpack", "gui/sprites/branded/background"),
            ResourceLocation.fromNamespaceAndPath("otherpack", "gui/sprites/branded/selection"));
    private static final BarTextures OTHER_TEXTURES = new BarTextures(
            ResourceLocation.fromNamespaceAndPath("otherpack", "gui/sprites/neon/background"),
            null);

    @AfterEach
    void isolate() {
        HotbarStyles.remove(STYLE);
        HotbarStyles.remove(OTHER_STYLE);
        HotbarRegistry.reset();
    }

    @Test
    void registrationIsLoud() {
        assertThrows(IllegalArgumentException.class, () -> HotbarStyles.register(null, "k", TEXTURES));
        assertThrows(IllegalArgumentException.class,
                () -> HotbarStyles.register(STYLE, "  ", TEXTURES));
        assertThrows(IllegalArgumentException.class,
                () -> HotbarStyles.register(STYLE, "otherpack.style.branded", null));

        HotbarStyles.register(STYLE, "otherpack.style.branded", TEXTURES);
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> HotbarStyles.register(STYLE, "otherpack.style.other", OTHER_TEXTURES));
        assertTrue(duplicate.getMessage().contains(STYLE.toString()), duplicate.getMessage());
    }

    @Test
    void stylesExposeTexturesAndIterationOrder() {
        HotbarStyles.register(STYLE, "otherpack.style.branded", TEXTURES);
        HotbarStyles.register(OTHER_STYLE, "otherpack.style.neon", OTHER_TEXTURES);

        assertEquals(TEXTURES, HotbarStyles.textures(STYLE).orElseThrow());
        assertEquals(List.of(STYLE, OTHER_STYLE),
                HotbarStyles.all().stream().map(HotbarStyle::id).toList(),
                "registration order drives the cycler");
        assertTrue(HotbarStyles.get(ResourceLocation.fromNamespaceAndPath("nobody", "missing")).isEmpty());
    }

    @Test
    void playerChoicesApplyToAssignableBarsOnly() {
        HotbarStyles.register(STYLE, "otherpack.style.branded", TEXTURES);
        HotbarRegistration assignable = new HotbarRegistration(ASSIGNABLE, "otherpack.bar.player", true);
        HotbarRegistration content = new HotbarRegistration(CONTENT, "otherpack.bar.content", false);
        String raw = HotbarStyles.withStyle("", ASSIGNABLE, STYLE.toString());

        assertEquals(TEXTURES, HotbarStyles.effective(assignable, raw, ASSIGNABLE),
                "the player's choice applies to their own bar");
        assertEquals(content.textures(), HotbarStyles.effective(content, raw, CONTENT),
                "a content bar keeps the mod's brand - the player choice is ignored");
    }

    @Test
    void staleUnknownAndMalformedChoicesFallBack() {
        HotbarStyles.register(STYLE, "otherpack.style.branded", TEXTURES);
        HotbarRegistration assignable = new HotbarRegistration(ASSIGNABLE, "otherpack.bar.player", true);

        String unknown = HotbarStyles.withStyle("", ASSIGNABLE,
                ResourceLocation.fromNamespaceAndPath("otherpack", "gone").toString());
        assertEquals(assignable.textures(), HotbarStyles.effective(assignable, unknown, ASSIGNABLE),
                "a style that was removed falls back, never a crash");

        String malformed = HotbarStyles.withStyle("", ASSIGNABLE, "not a resource location!");
        assertEquals(assignable.textures(), HotbarStyles.effective(assignable, malformed, ASSIGNABLE),
                "a malformed preference entry falls back");

        assertEquals(assignable.textures(), HotbarStyles.effective(assignable, "", ASSIGNABLE),
                "no preference = the registration's own textures");
    }

    @Test
    void thePreferenceStringRoundTripsPerBar() {
        HotbarStyles.register(STYLE, "otherpack.style.branded", TEXTURES);
        HotbarStyles.register(OTHER_STYLE, "otherpack.style.neon", OTHER_TEXTURES);
        ResourceLocation other = ResourceLocation.fromNamespaceAndPath("otherpack", "other_bar");

        String raw = HotbarStyles.withStyle("", ASSIGNABLE, STYLE.toString());
        raw = HotbarStyles.withStyle(raw, other, OTHER_STYLE.toString());
        assertEquals(STYLE.toString(), HotbarStyles.playerStyleId(raw, ASSIGNABLE));
        assertEquals(OTHER_STYLE.toString(), HotbarStyles.playerStyleId(raw, other));

        // clearing one bar never touches the other
        String cleared = HotbarStyles.withStyle(raw, ASSIGNABLE, null);
        assertEquals(null, HotbarStyles.playerStyleId(cleared, ASSIGNABLE));
        assertEquals(OTHER_STYLE.toString(), HotbarStyles.playerStyleId(cleared, other));

        assertEquals(null, HotbarStyles.playerStyleId("", ASSIGNABLE));
        assertEquals(null, HotbarStyles.playerStyleId(raw,
                ResourceLocation.fromNamespaceAndPath("otherpack", "never_set")));
    }
}
