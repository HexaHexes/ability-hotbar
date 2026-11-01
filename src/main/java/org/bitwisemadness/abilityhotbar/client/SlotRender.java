package org.bitwisemadness.abilityhotbar.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityAvailability;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.jetbrains.annotations.Nullable;

/**
 * The pure per-slot render decision for the bar (REQ-HOT-01/02/07).
 *
 * <p>Resolves the bar's synced state, each ability's overlay availability, and
 * its registry presence into exactly what to draw for one slot - with no GUI
 * dependency, so the display invariants are unit-testable:</p>
 *
 * <ul>
 *   <li>An <b>inert</b> slot (id present but no provider) renders as diagnosed
 *       inert - never an icon, never a crash (REQ-HOT-15/21).</li>
 *   <li>A <b>cooldown / cost / availability</b> metric that is
 *       {@code unknown} renders <em>no overlay</em> - never a wrong value
 *       (REQ-HOT-08).</li>
 *   <li>The <b>selection</b> highlight tracks the selected slot from the
 *       synced state machine (#20), independent of activation.</li>
 *   <li>An assigned slot with a resolved icon draws it; one without falls back
 *       to a placeholder rather than a blank.</li>
 * </ul>
 *
 * <p>The GUI layer only reads these records and blits; every branch above is
 * decided here and covered by tests.</p>
 *
 * @param assigned          whether the slot holds an ability id
 * @param inert             whether the id is present but unresolvable (diagnostic)
 * @param selected          whether this is the currently selected slot
 * @param iconId            resolved icon reference, or null for the placeholder
 * @param cooldownFraction  cooldown progress 0..1, or null when unknown/absent
 * @param availability      the provider's castability verdict (never null)
 * @param cost              declared cost to badge, or null when unknown/absent
 */
public record SlotRender(
        boolean assigned,
        boolean inert,
        boolean selected,
        @Nullable ResourceLocation iconId,
        @Nullable Double cooldownFraction,
        org.bitwisemadness.abilitycore.api.AvailabilityState availability,
        @Nullable ResourceCost cost) {

    /** @return true when a cooldown overlay should be drawn (known, in range). */
    public boolean hasCooldownOverlay() {
        return cooldownFraction != null;
    }

    /** @return true when a cost badge should be drawn. */
    public boolean hasCostBadge() {
        return cost != null;
    }

    /**
     * Resolves one slot's render decision from the synced bar state.
     *
     * @param assignment      the slot's assignment (null when out of range)
     * @param selected        whether this slot is the selected one
     * @param availability    overlay tuple for the slot's id, or empty when none
     * @param definition      registry definition for the slot's id, or empty when
     *                        absent (which also makes an assigned slot inert)
     * @return the decision; inert when assigned but unresolvable
     */
    public static SlotRender resolve(
            @Nullable SlotAssignment assignment,
            boolean selected,
            Optional<AbilityAvailability> availability,
            Optional<AbilityDefinition> definition) {

        if (assignment == null || !assignment.isAssigned() || assignment.abilityId() == null) {
            // Empty slot: nothing to draw, selection still applies (a cursor can
            // sit on an empty slot).
            return new SlotRender(false, false, selected, null, null,
                    org.bitwisemadness.abilitycore.api.AvailabilityState.unknown(), null);
        }

        // Assigned. No definition => the id does not resolve => diagnosed inert.
        if (definition.isEmpty()) {
            return new SlotRender(true, true, selected, null, null,
                    org.bitwisemadness.abilitycore.api.AvailabilityState.unknown(), null);
        }

        AbilityAvailability avail = availability.orElseGet(AbilityAvailability::allUnknown);
        AbilityDefinition def = definition.get();

        // Cooldown: only a known, in-range metric produces an overlay.
        Double cooldown = avail.cooldown().knownValue()
                .filter(p -> p >= 0.0 && p <= 1.0)
                .orElse(null);

        // Cost: only a known cost is badged.
        ResourceCost cost = avail.cost().knownValue().orElse(
                def.cost()); // fall back to the definition's declared cost

        return new SlotRender(true, false, selected, def.iconId(), cooldown,
                avail.availability(), cost);
    }

    /**
     * Resolves every slot of the bar at once.
     *
     * @param state         the synced bar state (null before first snapshot)
     * @param availability  lookup for a slot id's overlay tuple (may return empty)
     * @param definition    lookup for a slot id's registry definition
     * @return one decision per slot; empty list when no state has synced yet
     */
    public static List<SlotRender> resolveAll(
            @Nullable BarState state,
            Function<ResourceLocation, Optional<AbilityAvailability>> availability,
            Function<ResourceLocation, Optional<AbilityDefinition>> definition) {

        if (state == null) {
            return List.of();
        }
        SlotAssignment[] assignments = state.assignments();
        int selectedSlot = state.selectedSlot();
        List<SlotRender> out = new ArrayList<>(assignments.length);
        for (int i = 0; i < assignments.length; i++) {
            SlotAssignment assignment = assignments[i];
            boolean selected = i == selectedSlot;
            ResourceLocation id = assignment == null ? null : assignment.abilityId();
            Optional<AbilityAvailability> avail =
                    id == null ? Optional.empty() : availability.apply(id);
            Optional<AbilityDefinition> def =
                    id == null ? Optional.empty() : definition.apply(id);
            out.add(resolve(assignment, selected, avail, def));
        }
        return List.copyOf(out);
    }
}
