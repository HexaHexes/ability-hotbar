package org.bitwisemadness.abilityhotbar.state;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative assignment mutation (REQ-HOT-14/21).
 *
 * <p>Client requests are resolved here, never trusted directly: an
 * out-of-range slot or a malformed id is rejected fail-closed (empty result,
 * no state change). A blank id clears the slot. The raw id is stored even when
 * it resolves to no provider, so the slot renders as diagnosed inert rather
 * than being dropped (REQ-HOT-21).</p>
 */
public final class BarAssignments {

    private BarAssignments() {
    }

    /**
     * Resolves an assignment request against the current state.
     *
     * @param current       the player's current bar state
     * @param slot          the target slot index
     * @param rawAbilityId  the requested ability id, or blank/&#64;Nullable to clear
     * @return the mutated state, or empty when the request is invalid
     */
    public static Optional<BarState> assign(BarState current, int slot, @Nullable String rawAbilityId) {
        if (current == null || slot < 0 || slot >= current.assignments().length) {
            return Optional.empty();
        }
        if (rawAbilityId == null || rawAbilityId.isBlank()) {
            return Optional.of(current.withAssignment(slot, null));
        }
        try {
            ResourceLocation id = ResourceLocation.parse(rawAbilityId.trim());
            return Optional.of(current.withAssignment(slot, id));
        } catch (RuntimeException malformed) {
            // Fail-closed: a malformed id never mutates the bar.
            return Optional.empty();
        }
    }
}
