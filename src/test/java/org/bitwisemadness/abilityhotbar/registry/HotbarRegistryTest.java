package org.bitwisemadness.abilityhotbar.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Registration API contract (hotbar#77, ADR-012): what a mod declares at
 * setup, the loud failures for bad declarations, and the assignability query
 * the assignment screen and server-side validation consume.
 */
class HotbarRegistryTest {

    private static final ResourceLocation EXTRA =
            ResourceLocation.fromNamespaceAndPath("otherpack", "extra");
    private static final ResourceLocation READONLY =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");

    @AfterEach
    void reset() {
        HotbarRegistry.reset();
    }

    @Test
    void registerStoresIdNameKeyAndFlag() {
        HotbarRegistration registration = HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);

        assertEquals(EXTRA, registration.id());
        assertEquals("otherpack.bar.extra", registration.displayNameKey());
        assertTrue(registration.assignable());
        assertEquals(registration, HotbarRegistry.get(EXTRA).orElseThrow());
        assertEquals(registration, HotbarRegistry.all().get(0));
    }

    @Test
    void duplicateIdFailsLoudlyEvenWithDifferentMetadata() {
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> HotbarRegistry.register(EXTRA, "otherpack.bar.other_name", false));
        assertTrue(e.getMessage().contains(EXTRA.toString()));
        // the first registration survives untouched
        assertTrue(HotbarRegistry.get(EXTRA).orElseThrow().assignable());
        assertEquals(1, HotbarRegistry.all().size());
    }

    @Test
    void reservedBuiltinNamespaceFailsLoudly() {
        ResourceLocation reserved = HotbarRegistry.builtinBarId(7);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> HotbarRegistry.register(reserved, "impersonation", true));
        assertTrue(e.getMessage().contains("reserved"));
        // the built-in itself is untouched and still implicitly assignable
        assertFalse(HotbarRegistry.get(reserved).isPresent());
        assertTrue(HotbarRegistry.isAssignable(reserved));
    }

    @Test
    void blankOrNullNameKeyFailsLoudly() {
        assertThrows(IllegalArgumentException.class,
                () -> HotbarRegistry.register(EXTRA, "  ", true));
        assertThrows(IllegalArgumentException.class,
                () -> HotbarRegistry.register(EXTRA, null, true));
        assertThrows(IllegalArgumentException.class,
                () -> HotbarRegistry.register(null, "key", true));
        assertTrue(HotbarRegistry.all().isEmpty());
    }

    @Test
    void isAssignableAnswersBuiltinsRegisteredAndUnknown() {
        // built-ins: implicitly assignable, never registered
        assertTrue(HotbarRegistry.isAssignable(HotbarRegistry.builtinBarId(0)));
        assertTrue(HotbarRegistry.isAssignable(HotbarRegistry.builtinBarId(9)));
        assertTrue(HotbarRegistry.get(HotbarRegistry.builtinBarId(0)).isEmpty());

        // registered bars answer with their flag
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);
        HotbarRegistry.register(READONLY, "pseudocore.bar.curios", false);
        assertTrue(HotbarRegistry.isAssignable(EXTRA));
        assertFalse(HotbarRegistry.isAssignable(READONLY));

        // unknown ids are not assignable (the server re-validates with this)
        assertFalse(HotbarRegistry.isAssignable(
                ResourceLocation.fromNamespaceAndPath("nobody", "nowhere")));
        assertFalse(HotbarRegistry.isAssignable(null));
    }

    @Test
    void isBuiltinMatchesPositionalIdsOnly() {
        assertTrue(HotbarRegistry.isBuiltin(HotbarRegistry.builtinBarId(0)));
        assertTrue(HotbarRegistry.isBuiltin(HotbarRegistry.builtinBarId(42)));
        assertFalse(HotbarRegistry.isBuiltin(
                ResourceLocation.fromNamespaceAndPath("ability_hotbar", "anything_else")));
        assertFalse(HotbarRegistry.isBuiltin(
                ResourceLocation.fromNamespaceAndPath("ability_hotbar", "bar_x")));
        assertFalse(HotbarRegistry.isBuiltin(
                ResourceLocation.fromNamespaceAndPath("ability_hotbar", "bar_")));
        assertFalse(HotbarRegistry.isBuiltin(
                ResourceLocation.fromNamespaceAndPath("otherpack", "bar_0")));
        assertFalse(HotbarRegistry.isBuiltin(HotbarRegistry.unidentifiedBarId(0)));
        assertFalse(HotbarRegistry.isBuiltin(null));
    }

    @Test
    void builtinIndexInvertsBuiltinBarIdAndFailsClosed() {
        assertEquals(0, HotbarRegistry.builtinIndex(HotbarRegistry.builtinBarId(0)));
        assertEquals(42, HotbarRegistry.builtinIndex(HotbarRegistry.builtinBarId(42)));
        // fail-closed for anything that is not a built-in id
        assertEquals(-1, HotbarRegistry.builtinIndex(HotbarRegistry.unidentifiedBarId(0)));
        assertEquals(-1, HotbarRegistry.builtinIndex(EXTRA));
        assertEquals(-1, HotbarRegistry.builtinIndex(
                ResourceLocation.fromNamespaceAndPath("ability_hotbar", "bar_x")));
        assertEquals(-1, HotbarRegistry.builtinIndex(
                ResourceLocation.fromNamespaceAndPath("ability_hotbar", "bar_")));
        assertEquals(-1, HotbarRegistry.builtinIndex(
                ResourceLocation.fromNamespaceAndPath("otherpack", "bar_1")));
        assertEquals(-1, HotbarRegistry.builtinIndex(null));
        // all digits, but out of int range: not a usable index either
        assertEquals(-1, HotbarRegistry.builtinIndex(
                ResourceLocation.fromNamespaceAndPath("ability_hotbar", "bar_99999999999999999999")));
    }

    @Test
    void builtinAndUnidentifiedIdsAreFormattedAndBoundsChecked() {
        assertEquals("ability_hotbar:bar_3", HotbarRegistry.builtinBarId(3).toString());
        assertEquals("ability_hotbar:unidentified_3",
                HotbarRegistry.unidentifiedBarId(3).toString());
        assertThrows(IllegalArgumentException.class, () -> HotbarRegistry.builtinBarId(-1));
        assertThrows(IllegalArgumentException.class, () -> HotbarRegistry.unidentifiedBarId(-1));
    }

    @Test
    void assignableBarsFiltersAndPreservesRegistrationOrder() {
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);
        HotbarRegistry.register(READONLY, "pseudocore.bar.curios", false);
        HotbarRegistry.register(
                ResourceLocation.fromNamespaceAndPath("third", "bar"), "third.bar", true);

        List<HotbarRegistration> assignable = HotbarRegistry.assignableBars();
        assertEquals(2, assignable.size());
        assertEquals(EXTRA, assignable.get(0).id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("third", "bar"),
                assignable.get(1).id());
        // all() keeps the full order, non-assignable included
        assertEquals(3, HotbarRegistry.all().size());
        assertEquals(READONLY, HotbarRegistry.all().get(1).id());
    }

    @Test
    void resetClearsEveryRegistration() {
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);
        assertFalse(HotbarRegistry.all().isEmpty());

        HotbarRegistry.reset();

        assertTrue(HotbarRegistry.all().isEmpty());
        assertTrue(HotbarRegistry.assignableBars().isEmpty());
        assertTrue(HotbarRegistry.get(EXTRA).isEmpty());
    }
}
