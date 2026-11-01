package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityAvailability;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AvailabilityState;
import org.bitwisemadness.abilitycore.api.Metric;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.junit.jupiter.api.Test;

/**
 * Per-slot render decisions (REQ-HOT-01/02/07/08): inert slots diagnose rather
 * than render an icon, unknown overlay metrics produce no overlay (never a
 * wrong value), selection tracks the synced slot, and an assigned slot falls
 * back to a placeholder when no icon resolves.
 */
class SlotRenderTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation GHOST =
            ResourceLocation.fromNamespaceAndPath("tensura", "ghost_missing");
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/icons/fireball.png");

    private static AbilityDefinition definition(ResourceLocation icon, ResourceCost cost) {
        return new AbilityDefinition("tensura.skill.x", icon,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, cost, java.util.Set.of(), java.util.Map.of());
    }

    @Test
    void emptySlotRendersNothingButKeepsSelection() {
        BarState state = BarState.empty(3).withSelectedSlot(1);
        List<SlotRender> slots = SlotRender.resolveAll(state,
                id -> Optional.empty(), id -> Optional.empty());

        assertEquals(3, slots.size());
        assertFalse(slots.get(0).assigned());
        assertTrue(slots.get(1).selected(), "cursor can sit on an empty slot");
        assertFalse(slots.get(1).assigned());
        assertNull(slots.get(1).iconId());
        assertFalse(slots.get(1).hasCooldownOverlay());
    }

    @Test
    void inertSlotDiagnosesInsteadOfRenderingAnIcon() {
        // Assigned id, but no registry definition => provider absent => inert.
        BarState state = BarState.empty(2).withAssignment(0, GHOST);
        List<SlotRender> slots = SlotRender.resolveAll(state,
                id -> Optional.empty(), id -> Optional.empty());

        SlotRender inert = slots.get(0);
        assertTrue(inert.assigned());
        assertTrue(inert.inert(), "unresolvable id renders as diagnosed inert");
        assertNull(inert.iconId(), "inert slot never draws an icon");
        assertEquals(AvailabilityState.unknown(), inert.availability());
    }

    @Test
    void assignedSlotWithDefinitionDrawsItsIcon() {
        AbilityDefinition def = definition(ICON, null);
        BarState state = BarState.empty(1).withAssignment(0, FIREBALL);

        List<SlotRender> slots = SlotRender.resolveAll(state,
                id -> Optional.empty(), id -> Optional.of(def));

        SlotRender slot = slots.get(0);
        assertTrue(slot.assigned());
        assertFalse(slot.inert());
        assertEquals(ICON, slot.iconId(), "resolved icon is drawn");
    }

    @Test
    void assignedSlotWithoutIconFallsBackToPlaceholder() {
        AbilityDefinition def = definition(null, null); // provider gave no icon
        BarState state = BarState.empty(1).withAssignment(0, FIREBALL);

        SlotRender slot = SlotRender.resolveAll(state,
                id -> Optional.empty(), id -> Optional.of(def)).get(0);

        assertFalse(slot.inert(), "a missing icon is not inert");
        assertNull(slot.iconId(), "null icon signals the placeholder, not a crash");
    }

    @Test
    void unknownCooldownProducesNoOverlay() {
        // allUnknown() => cooldown unknown => no overlay (never a wrong value).
        AbilityAvailability unknown = AbilityAvailability.allUnknown();
        AbilityDefinition def = definition(ICON, null);
        BarState state = BarState.empty(1).withAssignment(0, FIREBALL);

        SlotRender slot = SlotRender.resolveAll(state,
                id -> Optional.of(unknown), id -> Optional.of(def)).get(0);

        assertFalse(slot.hasCooldownOverlay(), "unknown cooldown draws no overlay");
        assertNull(slot.cooldownFraction());
        assertEquals(AvailabilityState.unknown(), slot.availability());
    }

    @Test
    void knownCooldownProducesAnOverlayInRange() {
        AbilityAvailability known = new AbilityAvailability(
                Metric.known(0.75), Metric.unknown(), AvailabilityState.available());
        AbilityDefinition def = definition(ICON, null);
        BarState state = BarState.empty(1).withAssignment(0, FIREBALL);

        SlotRender slot = SlotRender.resolveAll(state,
                id -> Optional.of(known), id -> Optional.of(def)).get(0);

        assertTrue(slot.hasCooldownOverlay());
        assertEquals(0.75, slot.cooldownFraction());
        assertTrue(slot.availability().isAvailable());
    }

    @Test
    void costPrefersTheAvailabilityTupleAndFallsBackToDefinition() {
        ResourceCost declared = new ResourceCost("tensura:aura", 5.0);
        ResourceCost live = new ResourceCost("tensura:aura", 20.0);
        AbilityDefinition def = definition(ICON, declared);
        BarState state = BarState.empty(1).withAssignment(0, FIREBALL);

        // Live (synced) cost wins over the static declared cost.
        AbilityAvailability withCost = new AbilityAvailability(
                Metric.unknown(), Metric.known(live), AvailabilityState.available());
        SlotRender fromTuple = SlotRender.resolveAll(state,
                id -> Optional.of(withCost), id -> Optional.of(def)).get(0);
        assertEquals(20.0, fromTuple.cost().amount());

        // No live cost -> the definition's declared cost is badged.
        SlotRender fromDef = SlotRender.resolveAll(state,
                id -> Optional.empty(), id -> Optional.of(def)).get(0);
        assertEquals(5.0, fromDef.cost().amount());
        assertTrue(fromDef.hasCostBadge());
    }

    @Test
    void selectionTracksTheSyncedSelectedSlot() {
        BarState state = BarState.empty(3)
                .withAssignment(0, FIREBALL)
                .withAssignment(2, FIREBALL)
                .withSelectedSlot(2);

        List<SlotRender> slots = SlotRender.resolveAll(state,
                id -> Optional.empty(), id -> Optional.of(definition(ICON, null)));

        assertFalse(slots.get(0).selected());
        assertFalse(slots.get(1).selected());
        assertTrue(slots.get(2).selected(), "highlight follows the state machine");
    }

    @Test
    void nullStateResolvesToNoSlots() {
        assertTrue(SlotRender.resolveAll(null, id -> Optional.empty(), id -> Optional.empty())
                .isEmpty(), "no snapshot yet => render nothing");
    }

    @Test
    void resolveAllPreservesSlotCountAndOrder() {
        AbilityDefinition def = definition(ICON, null);
        BarState state = BarState.empty(4)
                .withAssignment(1, FIREBALL)
                .withAssignment(3, FIREBALL);

        List<SlotRender> slots = SlotRender.resolveAll(state,
                id -> Optional.empty(), id -> Optional.of(def));

        assertEquals(4, slots.size());
        assertFalse(slots.get(0).assigned());
        assertTrue(slots.get(1).assigned());
        assertFalse(slots.get(2).assigned());
        assertTrue(slots.get(3).assigned());
    }

    @Test
    void lookupIsOnlyCalledForAssignedSlots() {
        // Ensure empty slots never trigger a provider/registry lookup.
        BarState state = BarState.empty(2).withAssignment(1, FIREBALL);
        AbilityDefinition def = definition(ICON, null);
        java.util.Set<ResourceLocation> askedFor = new java.util.HashSet<>();

        SlotRender.resolveAll(state,
                id -> { askedFor.add(id); return Optional.empty(); },
                id -> Optional.of(def));

        assertEquals(java.util.Set.of(FIREBALL), askedFor,
                "only assigned ids are resolved; empty slots are free");
    }

    @Test
    void availabilityLookupReceivesTheAssignmentId() {
        BarState state = BarState.empty(1).withAssignment(0, FIREBALL);
        Map<ResourceLocation, AbilityAvailability> table = Map.of(FIREBALL,
                new AbilityAvailability(Metric.known(0.5), Metric.unknown(),
                        AvailabilityState.unavailable("cooldown")));

        SlotRender slot = SlotRender.resolveAll(state,
                id -> Optional.ofNullable(table.get(id)),
                id -> Optional.of(definition(ICON, null))).get(0);

        assertFalse(slot.availability().isAvailable(), "unavailable verdict surfaces");
        assertNotNull(slot.cooldownFraction());
        assertEquals(0.5, slot.cooldownFraction());
    }
}
