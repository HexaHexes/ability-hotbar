package org.bitwisemadness.abilityhotbar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.Level;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.state.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The one-call content-bar ritual (hotbar#114): both distributions pin the
 * dist-cleaner contract (client invokes the supplier, dedicated server never
 * does), idempotent re-registration, loud misuse with the bar id, and the
 * native debug line so consumers delete theirs.
 */
class ContentBarRegistrationTest {

    private static final ResourceLocation BAR =
            ResourceLocation.fromNamespaceAndPath("otherpack", "content");
    private static final String NAME = "otherpack.bar.content";

    private final AtomicBoolean supplierInvoked = new AtomicBoolean(false);

    @BeforeEach
    @AfterEach
    void isolate() {
        HotbarRegistry.reset();
        HotbarContentProviders.reset();
        supplierInvoked.set(false);
    }

    private HotbarContentProvider stubProvider() {
        return new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 3;
            }

            @Override
            public ItemStack slotStack(int index) {
                return ItemStack.EMPTY;
            }
        };
    }

    private java.util.function.Supplier<HotbarContentProvider> countingSupplier() {
        return () -> {
            supplierInvoked.set(true);
            return stubProvider();
        };
    }

    @Test
    void clientSideRunsTheFullRitualAndLogsIt() {
        try (LogCapture logs = LogCapture.forLogger(HotbarContentProviders.class, Level.DEBUG)) {
            HotbarContentProviders.registerContentBar(BAR, NAME, countingSupplier(), true);

            assertTrue(supplierInvoked.get(), "the client constructs the provider");
            HotbarRegistration registration = HotbarRegistry.get(BAR).orElseThrow();
            assertEquals(NAME, registration.displayNameKey());
            assertFalse(registration.assignable(), "content bars are non-assignable by construction");
            assertTrue(HotbarContentProviders.get(BAR).isPresent(), "provider half registered");

            assertTrue(logs.messages().stream().anyMatch(
                    line -> line.contains(BAR.toString()) && line.contains("provider registered: true")),
                    "debug.log must show the registration without consumer code: " + logs.messages());
        }
    }

    @Test
    void dedicatedServerNeverInvokesTheSupplier() {
        try (LogCapture logs = LogCapture.forLogger(HotbarContentProviders.class, Level.DEBUG)) {
            HotbarContentProviders.registerContentBar(BAR, NAME, countingSupplier(), false);

            assertFalse(supplierInvoked.get(),
                    "constructing a client-only provider server-side trips the dist cleaner");
            assertTrue(HotbarRegistry.get(BAR).isPresent(), "the bar itself registers on both dists");
            assertTrue(HotbarContentProviders.get(BAR).isEmpty(), "no provider on the server");
            assertTrue(logs.messages().stream().anyMatch(
                    line -> line.contains(BAR.toString()) && line.contains("provider registered: false")),
                    "the flag records the skipped provider: " + logs.messages());
        }
    }

    @Test
    void identicalReRegistrationIsIdempotent() {
        HotbarContentProviders.registerContentBar(BAR, NAME, countingSupplier(), true);
        supplierInvoked.set(false);

        HotbarContentProviders.registerContentBar(BAR, NAME, countingSupplier(), true);

        assertTrue(supplierInvoked.get() || HotbarContentProviders.get(BAR).isPresent(),
                "the second call is a no-op with the provider already in place");
        HotbarRegistration registration = HotbarRegistry.get(BAR).orElseThrow();
        assertEquals(NAME, registration.displayNameKey(), "identical contract unchanged");
    }

    @Test
    void conflictingReRegistrationFailsLoudlyWithTheId() {
        HotbarContentProviders.registerContentBar(BAR, NAME, countingSupplier(), true);

        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> HotbarContentProviders.registerContentBar(BAR, "otherpack.bar.renamed",
                        countingSupplier(), true));

        assertTrue(conflict.getMessage().contains(BAR.toString()),
                "misuse messages carry the bar id: " + conflict.getMessage());
    }

    @Test
    void misuseCarriesTheBarId() {
        IllegalArgumentException blankName = assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.registerContentBar(BAR, " ", countingSupplier(), true));
        assertTrue(blankName.getMessage().contains(BAR.toString()), blankName.getMessage());

        IllegalArgumentException nullSupplier = assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.registerContentBar(BAR, NAME, null, true));
        assertTrue(nullSupplier.getMessage().contains(BAR.toString()), nullSupplier.getMessage());

        IllegalArgumentException nullAnswer = assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.registerContentBar(BAR, NAME, () -> null, true));
        assertTrue(nullAnswer.getMessage().contains(BAR.toString()),
                "a supplier returning null fails loudly with the id: " + nullAnswer.getMessage());
        assertTrue(HotbarRegistry.get(BAR).isPresent(),
                "the ritual follows the issue's order (registry first) - the bar exists");
        assertTrue(HotbarContentProviders.get(BAR).isEmpty(), "but no provider was attached");
        // ...and the same helper self-heals it: the idempotent path re-runs the
        // provider half for a live id that has none yet
        HotbarContentProviders.registerContentBar(BAR, NAME, countingSupplier(), true);
        assertTrue(HotbarContentProviders.get(BAR).isPresent(), "re-call completes the provider half");
    }
}
