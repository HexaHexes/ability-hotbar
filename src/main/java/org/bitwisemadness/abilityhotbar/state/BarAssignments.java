package org.bitwisemadness.abilityhotbar.state;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.AbilityBarScopes;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative assignment mutation (REQ-HOT-14/21, hotbar#90).
 *
 * <p>Client requests are resolved here, never trusted directly: an
 * out-of-range slot or a malformed id is rejected fail-closed (empty result,
 * no state change), and a <b>scoped</b> ability (#90) is rejected on any bar
 * other than its scope bar. A blank id clears the slot. The raw id is stored
 * even when it resolves to no provider, so the slot renders as diagnosed
 * inert rather than being dropped (REQ-HOT-21).</p>
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
            if (!AbilityBarScopes.isInScope(id, current.activeBarId())) {
                // Scoped ability, wrong bar: rejected fail-closed (#90 AC3).
                return Optional.empty();
            }
            return Optional.of(current.withAssignment(slot, id));
        } catch (RuntimeException malformed) {
            // Fail-closed: a malformed id never mutates the bar.
            return Optional.empty();
        }
    }

    /**
     * Whether {@link #assign} would refuse purely for <b>scope</b> reasons:
     * the id parses and is scoped to a bar other than the active one. The
     * payload layer uses this to answer a scope rejection with a snapshot
     * resync, so a client that showed the assignment optimistically converges
     * on the server's refusal (#90 AC3). Malformed, blank and unscoped ids
     * keep their existing silent-drop path.
     *
     * @param current      the player's current bar state
     * @param rawAbilityId the requested ability id
     * @return true when the only reason for refusal is the scope
     */
    public static boolean outOfScope(BarState current, @Nullable String rawAbilityId) {
        if (current == null || rawAbilityId == null || rawAbilityId.isBlank()) {
            return false;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(rawAbilityId.trim());
            return !AbilityBarScopes.isInScope(id, current.activeBarId());
        } catch (RuntimeException malformed) {
            return false;
        }
    }
}
