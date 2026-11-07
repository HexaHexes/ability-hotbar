package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityModes;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Per-slot mode resolution at activation (hotbar#29): the stored mode is
 * validated against the ability's own declared list before it ever reaches
 * the wire - a stale value falls back to the default, a modeless ability
 * gets no context, and an unmodeled pairing invents nothing.
 */
class ActivationResolverTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation HEAL =
            ResourceLocation.fromNamespaceAndPath("tensura", "heal");

    @AfterEach
    void tearDown() {
        AbilityModes.remove(FIREBALL);
        AbilityModes.remove(HEAL);
    }

    private static BarState stateWithMode(ResourceLocation id, String mode) {
        return BarState.empty(3, 1)
                .withRow(HotbarRegistry.builtinBarId(0), new SlotAssignment[]{
                        new SlotAssignment(id, mode), SlotAssignment.empty(), SlotAssignment.empty()});
    }

    @Test
    void declaredStoredModeTravelsVerbatim() {
        AbilityModes.declare(FIREBALL, List.of(
                new AbilityModes.Mode("basic", "tensura.mode.basic"),
                new AbilityModes.Mode("fire", "tensura.mode.fire")));

        var activation = ActivationResolver.resolve(
                stateWithMode(FIREBALL, "fire"), 0, ActivationSource.DIRECT_SLOT);

        assertTrue(activation.isPresent());
        assertEquals("fire", activation.get().mode(),
                "a declared stored mode travels as the mode context");
    }

    @Test
    void staleStoredModeFallsBackToTheDeclaredDefault() {
        AbilityModes.declare(FIREBALL, List.of(
                new AbilityModes.Mode("basic", "tensura.mode.basic"),
                new AbilityModes.Mode("fire", "tensura.mode.fire")));

        var activation = ActivationResolver.resolve(
                stateWithMode(FIREBALL, "removed_mode"), 0, ActivationSource.DIRECT_SLOT);

        assertTrue(activation.isPresent());
        assertEquals("basic", activation.get().mode(),
                "a stale mode falls back to the ability's first declared mode");
    }

    @Test
    void modelessAbilityGetsNoModeContext() {
        AbilityModes.declare(FIREBALL, List.of(
                new AbilityModes.Mode("basic", "tensura.mode.basic")));

        var activation = ActivationResolver.resolve(
                stateWithMode(FIREBALL, null), 0, ActivationSource.DIRECT_SLOT);

        assertTrue(activation.isPresent());
        assertEquals(null, activation.get().mode(), "a modeless pairing invents nothing");
    }

    @Test
    void undeclaredAbilityNeverSendsStoredContext() {
        // HEAL declares no modes: even a stored value must not reach the wire
        // (not cycleable - the design's own ruling).
        var activation = ActivationResolver.resolve(
                stateWithMode(HEAL, "stale"), 0, ActivationSource.DIRECT_SLOT);

        assertTrue(activation.isPresent());
        assertEquals(null, activation.get().mode());
    }
}
