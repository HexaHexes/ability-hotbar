package org.bitwisemadness.abilityhotbar.state;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.AbilityBarScopes;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative assignment mutation (REQ-HOT-14/21, hotbar#27/#90).
 *
 * <p>Client requests are resolved here, never trusted directly: the target
 * bar must be <b>stored and assignable</b> (a nonexistent or non-assignable
 * bar is rejected fail-closed - ADR-012, hotbar#27 AC9), the slot must fit
 * that bar, a <b>scoped</b> ability (#90) must be going to its scope bar, and
 * a malformed id never mutates anything. A blank id clears the slot. The raw
 * id is stored even when it resolves to no provider, so the slot renders as
 * diagnosed inert rather than being dropped (REQ-HOT-21).</p>
 *
 * <p>Two entry points: the bar-id form is what the menu's payload carries
 * (it may target any assignable bar, not only the active one); the three-arg
 * form is the convenience for "the active bar", which the older call sites
 * and tests use.</p>
 */
public final class BarAssignments {

    private BarAssignments() {
    }

    /**
     * Resolves an assignment request against the <b>active</b> bar.
     *
     * @param current       the player's current bar state
     * @param slot          the target slot index
     * @param rawAbilityId  the requested ability id, or blank/&#64;Nullable to clear
     * @return the mutated state, or empty when the request is invalid
     */
    public static Optional<BarState> assign(BarState current, int slot, @Nullable String rawAbilityId) {
        return assign(current, current == null ? null : current.activeBarId(), slot, rawAbilityId);
    }

    /**
     * Resolves an assignment request against a <b>named</b> bar (the menu's
     * payload, hotbar#27 AC9).
     *
     * @param current       the player's current bar state
     * @param barId         the requested target bar
     * @param slot          the target slot index
     * @param rawAbilityId  the requested ability id, or blank/&#64;Nullable to clear
     * @return the mutated state, or empty when the request is invalid
     */
    public static Optional<BarState> assign(BarState current, ResourceLocation barId, int slot,
            @Nullable String rawAbilityId) {
        if (current == null || barId == null) {
            return Optional.empty();
        }
        // Nonexistent or non-assignable target: rejected fail-closed (AC9).
        // isAssignable answers false for unknown ids and for registered
        // content-owned bars alike; built-ins answer true by pattern.
        if (!HotbarRegistry.isAssignable(barId)) {
            return Optional.empty();
        }
        Bar target = null;
        for (Bar bar : current.bars()) {
            if (bar.id().equals(barId)) {
                target = bar;
                break;
            }
        }
        if (target == null) {
            // Defensive: reconciliation keeps every assignable bar stored, so
            // an unstored assignable id is a state this write cannot trust.
            return Optional.empty();
        }
        if (slot < 0 || slot >= target.slotCount()) {
            return Optional.empty();
        }
        if (rawAbilityId == null || rawAbilityId.isBlank()) {
            return Optional.of(current.withAssignment(barId, slot, null));
        }
        try {
            ResourceLocation id = ResourceLocation.parse(rawAbilityId.trim());
            if (!AbilityBarScopes.isInScope(id, barId)) {
                // Scoped ability, wrong bar: rejected fail-closed (#90).
                return Optional.empty();
            }
            return Optional.of(current.withAssignment(barId, slot, id));
        } catch (RuntimeException malformed) {
            // Fail-closed: a malformed id never mutates the bar.
            return Optional.empty();
        }
    }
}
