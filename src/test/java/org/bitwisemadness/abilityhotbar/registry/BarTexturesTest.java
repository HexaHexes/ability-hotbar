package org.bitwisemadness.abilityhotbar.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Per-bar sprite overrides (hotbar#125): the optional textures ride the
 * registration through both entry points, absent overrides resolve to the
 * vanilla pair, and the resolution helpers are the layer's whole decision.
 */
class BarTexturesTest {

    private static final ResourceLocation BAR =
            ResourceLocation.fromNamespaceAndPath("otherpack", "branded");
    private static final ResourceLocation VANILLA_BACKGROUND =
            ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation VANILLA_SELECTION =
            ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    private static final ResourceLocation CUSTOM_BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("otherpack", "gui/sprites/branded/background");
    private static final ResourceLocation CUSTOM_SELECTION =
            ResourceLocation.fromNamespaceAndPath("otherpack", "gui/sprites/branded/selection");

    @AfterEach
    void isolate() {
        HotbarRegistry.reset();
    }

    @Test
    void registrationsWithoutOverridesResolveToTheVanillaPair() {
        HotbarRegistration legacyShape = new HotbarRegistration(BAR, "otherpack.bar.branded", true);
        assertEquals(BarTextures.DEFAULT, legacyShape.textures(),
                "the pre-texture constructor shape means no overrides");

        HotbarRegistration registered = HotbarRegistry.register(BAR, "otherpack.bar.branded", true);
        assertEquals(BarTextures.DEFAULT, registered.textures());
        assertEquals(VANILLA_BACKGROUND, registered.textures().backgroundOr(VANILLA_BACKGROUND),
                "the layer's fallback path stays byte-identical for built-ins");
        assertEquals(VANILLA_SELECTION, registered.textures().selectionOr(VANILLA_SELECTION));
    }

    @Test
    void overridesCarryThroughRegistrationAndResolve() {
        var custom = new BarTextures(CUSTOM_BACKGROUND, CUSTOM_SELECTION);
        HotbarRegistration registered =
                HotbarRegistry.register(BAR, "otherpack.bar.branded", true, custom);

        assertEquals(custom, registered.textures());
        assertEquals(CUSTOM_BACKGROUND, registered.textures().backgroundOr(VANILLA_BACKGROUND));
        assertEquals(CUSTOM_SELECTION, registered.textures().selectionOr(VANILLA_SELECTION));
    }

    @Test
    void partialOverridesKeepTheMissingHalfVanilla() {
        var backgroundOnly = new BarTextures(CUSTOM_BACKGROUND, null);
        assertEquals(CUSTOM_BACKGROUND, backgroundOnly.backgroundOr(VANILLA_BACKGROUND));
        assertEquals(VANILLA_SELECTION, backgroundOnly.selectionOr(VANILLA_SELECTION),
                "an unset half falls back to vanilla");
    }

    @Test
    void nullTexturesNormalizeToDefault() {
        HotbarRegistration registered =
                HotbarRegistry.register(BAR, "otherpack.bar.branded", true, null);
        assertEquals(BarTextures.DEFAULT, registered.textures(),
                "an absent texture argument is the optional case, not a crash");
        assertEquals(BarTextures.DEFAULT,
                new HotbarRegistration(BAR, "otherpack.bar.branded", true, null).textures());
    }

    @Test
    void duplicatesStillFailLoudlyWithTheId() {
        HotbarRegistry.register(BAR, "otherpack.bar.branded", true,
                new BarTextures(CUSTOM_BACKGROUND, null));

        IllegalStateException conflict = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> HotbarRegistry.register(BAR, "otherpack.bar.branded", true,
                        new BarTextures(null, CUSTOM_SELECTION)));

        assertTrue(conflict.getMessage().contains(BAR.toString()),
                "the duplicate check is still by id: " + conflict.getMessage());
    }
}
