package org.bitwisemadness.abilityhotbar.content;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Content-provider registration contract (hotbar#83 §1): a code API at mod
 * setup that fails loudly - unknown bar id, assignable bar, duplicate - in
 * the same exception vocabulary as {@link HotbarRegistry#register}, and is
 * callable without any client class on the classpath (the distribution
 * fixture pins that half).
 */
class HotbarContentProvidersTest {

    private static final ResourceLocation CURIOS =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");
    private static final ResourceLocation EXTRA =
            ResourceLocation.fromNamespaceAndPath("otherpack", "extra");
    private static final ResourceLocation BUILTIN = HotbarRegistry.builtinBarId(0);

    @BeforeEach
    @AfterEach
    void isolate() {
        HotbarRegistry.reset();
        HotbarContentProviders.reset();
    }

    private static HotbarContentProvider minimal() {
        return new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 2;
            }

            @Override
            public ItemStack slotStack(int index) {
                return ItemStack.EMPTY;
            }
        };
    }

    @Test
    void registerStoresTheProviderForANonAssignableBar() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        HotbarContentProvider provider = minimal();

        HotbarContentProviders.register(CURIOS, provider);

        assertTrue(HotbarContentProviders.get(CURIOS).isPresent());
        assertSame(provider, HotbarContentProviders.get(CURIOS).orElseThrow());
    }

    @Test
    void unknownBarIdFailsLoudly() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.register(CURIOS, minimal()));

        assertTrue(e.getMessage().contains(CURIOS.toString()), "the id must be named: " + e.getMessage());
        assertTrue(HotbarContentProviders.get(CURIOS).isEmpty());
    }

    @Test
    void assignableBarFailsLoudly() {
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.register(EXTRA, minimal()));

        assertTrue(e.getMessage().contains("assignable"), "the reason must be named: " + e.getMessage());
        assertTrue(HotbarContentProviders.get(EXTRA).isEmpty(),
                "assignable bars keep attachment-owned state");
    }

    @Test
    void duplicateProviderFailsLoudlyAndKeepsTheFirst() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        HotbarContentProvider first = minimal();
        HotbarContentProviders.register(CURIOS, first);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> HotbarContentProviders.register(CURIOS, minimal()));

        assertTrue(e.getMessage().contains(CURIOS.toString()));
        assertSame(first, HotbarContentProviders.get(CURIOS).orElseThrow(),
                "a duplicate must never shadow the first registration");
    }

    @Test
    void nullAndBuiltinIdsFailLoudly() {
        assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.register(null, minimal()));
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.register(CURIOS, null));
        // built-ins carry no registration: providers hang off registrations only
        assertThrows(IllegalArgumentException.class,
                () -> HotbarContentProviders.register(BUILTIN, minimal()));
        assertTrue(HotbarContentProviders.get(BUILTIN).isEmpty());
    }

    @Test
    void resetClearsEveryProvider() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        HotbarContentProviders.register(CURIOS, minimal());

        HotbarContentProviders.reset();

        assertTrue(HotbarContentProviders.get(CURIOS).isEmpty());
    }

    @Test
    void getAnswersEmptyForNullAndUnknownIds() {
        assertTrue(HotbarContentProviders.get(null).isEmpty());
        assertTrue(HotbarContentProviders.get(EXTRA).isEmpty());
    }
}
