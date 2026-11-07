package org.bitwisemadness.abilityhotbar.state;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityModes;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side activation resolution (REQ-HOT-11/19/20).
 *
 * <p>Given the client's last-known bar state, a target slot and an activation
 * source, decides whether there is anything to activate. An empty or
 * out-of-range slot resolves to nothing, so the client sends no activation
 * request at all - the first half of the REQ-HOT-19 fail-safe (an empty or
 * restricted slot never reaches the server, never consumes). The server's
 * activation pipeline re-checks cooldown, cost and eligibility anyway.</p>
 */
public final class ActivationResolver {

    private ActivationResolver() {
    }

    /**
     * @param state  the client's bar state, or null when none has synced yet
     * @param slot   the target slot index
     * @param source where the activation attempt originates
     * @return the resolved activation, or empty when the slot is empty/out of range
     */
    public static Optional<Activation> resolve(@Nullable BarState state, int slot, ActivationSource source) {
        if (state == null || source == null) {
            return Optional.empty();
        }
        SlotAssignment assignment = state.assignment(slot);
        if (assignment == null || !assignment.isAssigned() || assignment.abilityId() == null) {
            return Optional.empty();
        }
        return Optional.of(new Activation(assignment.abilityId(), source, slot,
                resolvedMode(assignment, assignment.abilityId())));
    }

    /**
     * The stored mode, validated against the ability's own declared list
     * (hotbar#29): a stored mode the ability still declares is sent as-is;
     * a stale one falls back to the first declared mode (the default); an
     * ability that declares no modes gets no mode context at all (it is not
     * cycleable) - and a modeless pairing never invents context.
     */
    @Nullable
    private static String resolvedMode(SlotAssignment assignment, ResourceLocation abilityId) {
        String stored = assignment.mode();
        if (stored == null) {
            return null;
        }
        Optional<List<AbilityModes.Mode>> declared = AbilityModes.get(abilityId);
        if (declared.isEmpty() || declared.get().isEmpty()) {
            return null;
        }
        List<AbilityModes.Mode> modes = declared.get();
        for (AbilityModes.Mode mode : modes) {
            if (mode.id().equals(stored)) {
                return stored;
            }
        }
        return modes.get(0).id();
    }

    /**
     * A single resolved activation intent: which ability, from which source,
     * which slot, under which stored mode. The client sends this to the
     * server (through core) and never acts on it locally.
     *
     * @param abilityId the ability to activate
     * @param source    the activation source (wire-mapped by core)
     * @param slot      the hotbar slot the ability was resolved from
     * @param mode      the slot's validated mode setting, or null for none
     */
    public record Activation(ResourceLocation abilityId, ActivationSource source, int slot,
            @Nullable String mode) {
    }
}
