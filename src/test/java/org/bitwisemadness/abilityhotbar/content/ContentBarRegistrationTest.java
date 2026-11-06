package org.bitwisemadness.abilityhotbar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Level;
import org.bitwisemadness.abilityhotbar.registry.BarTextures;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.registry.HotbarStyles;
import org.bitwisemadness.abilityhotbar.state.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The one-call content-bar ritual (hotbar#114, reshaped by hotbar#120): the
 * helper registers the <b>bar only</b> - it never takes or touches a provider
 * supplier, which is the linkage trap (a supplier argument links its target
 * class on both distributions). The provider half lives in the consumer's
 * dist-gated {@code register} call and is pinned by the gametest on a real
 * dedicated server.
 */
class ContentBarRegistrationTest {

    private static final ResourceLocation BAR =
            ResourceLocation.fromNamespaceAndPath("otherpack", "content");
    private static final String NAME = "otherpack.bar.content";
    private static final ResourceLocation STYLE =
            ResourceLocation.fromNamespaceAndPath("otherpack", "branded");
    private static final BarTextures STYLE_TEXTURES = new BarTextures(
            ResourceLocation.fromNamespaceAndPath("otherpack", "gui/sprites/branded/background"),
            null);

    @BeforeEach
    @AfterEach
    void isolate() {
        HotbarRegistry.reset();
        HotbarContentProviders.reset();
        HotbarStyles.remove(STYLE);
    }

    @Test
    void freshRegistrationRegistersTheBarAndAttachesNoProvider() {
        try (LogCapture logs = LogCapture.forLogger(HotbarContentProviders.class, Level.DEBUG)) {
            HotbarContentProviders.registerContentBar(BAR, NAME);

            HotbarRegistration registration = HotbarRegistry.get(BAR).orElseThrow();
            assertEquals(NAME, registration.displayNameKey());
            assertFalse(registration.assignable(), "content bars are non-assignable by construction");
            assertEquals(BarTextures.DEFAULT, registration.textures(),
                    "the two-arg form means no sprite overrides (hotbar#125)");
            assertTrue(HotbarContentProviders.get(BAR).isEmpty(),
                    "the helper never attaches a provider (hotbar#120: no supplier exists to link)");
            assertTrue(logs.messages().stream().anyMatch(
                    line -> line.contains(BAR.toString()) && line.contains("registered via one-call helper")),
                    "debug.log shows the registration without consumer code: " + logs.messages());
        }
    }

    @Test
    void stylesRideTheContentBarRegistration() {
        HotbarStyles.register(STYLE, "otherpack.style.branded", STYLE_TEXTURES);

        HotbarContentProviders.registerContentBar(BAR, NAME, STYLE);
        assertEquals(STYLE_TEXTURES, HotbarRegistry.get(BAR).orElseThrow().textures(),
                "the style resolves to its textures at registration (render path unchanged)");

        // an unknown style fails loudly with both ids - register styles first
        HotbarRegistry.reset();
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.registerContentBar(BAR, NAME,
                        ResourceLocation.fromNamespaceAndPath("otherpack", "nope")));
        assertTrue(unknown.getMessage().contains("nope"), unknown.getMessage());
        assertTrue(unknown.getMessage().contains(BAR.toString()), unknown.getMessage());
        assertTrue(HotbarRegistry.get(BAR).isEmpty(), "nothing registers on a failed lookup");
    }

    @Test
    void texturesRideTheContract() {
        var custom = new BarTextures(
                ResourceLocation.fromNamespaceAndPath("otherpack", "gui/sprites/content/background"),
                null);
        HotbarContentProviders.registerContentBar(BAR, NAME, custom);
        assertEquals(custom, HotbarRegistry.get(BAR).orElseThrow().textures());

        // identical re-call stays a no-op
        HotbarContentProviders.registerContentBar(BAR, NAME, custom);

        // same id, same name, different textures = a different contract
        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> HotbarContentProviders.registerContentBar(BAR, NAME,
                        new BarTextures(null,
                                ResourceLocation.fromNamespaceAndPath("otherpack",
                                        "gui/sprites/content/selection"))));
        assertTrue(conflict.getMessage().contains(BAR.toString()),
                "contract conflicts carry the bar id: " + conflict.getMessage());
    }

    @Test
    void identicalReRegistrationIsIdempotent() {
        HotbarContentProviders.registerContentBar(BAR, NAME);
        HotbarContentProviders.registerContentBar(BAR, NAME);

        HotbarRegistration registration = HotbarRegistry.get(BAR).orElseThrow();
        assertEquals(NAME, registration.displayNameKey(), "identical contract unchanged");
        assertTrue(HotbarContentProviders.get(BAR).isEmpty(), "still no provider half");
    }

    @Test
    void conflictingReRegistrationFailsLoudlyWithTheId() {
        HotbarContentProviders.registerContentBar(BAR, NAME);

        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> HotbarContentProviders.registerContentBar(BAR, "otherpack.bar.renamed"));

        assertTrue(conflict.getMessage().contains(BAR.toString()),
                "misuse messages carry the bar id: " + conflict.getMessage());
    }

    @Test
    void assignableBarsAreRejectedLoudlyWithTheId() {
        HotbarRegistry.register(BAR, NAME, true);

        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> HotbarContentProviders.registerContentBar(BAR, NAME));

        assertTrue(conflict.getMessage().contains(BAR.toString()),
                "assignable bars keep attachment-owned state: " + conflict.getMessage());
    }

    @Test
    void misuseCarriesTheBarId() {
        IllegalArgumentException blankName = assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.registerContentBar(BAR, " "));
        assertTrue(blankName.getMessage().contains(BAR.toString()), blankName.getMessage());

        IllegalArgumentException nullName = assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.registerContentBar(BAR, null));
        assertTrue(nullName.getMessage().contains(BAR.toString()), nullName.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.registerContentBar(null, NAME));
    }
}
