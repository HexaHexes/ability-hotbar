package org.bitwisemadness.abilityhotbar.client;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.abilitycore.api.ModeChangeHandlers;
import org.bitwisemadness.abilityhotbar.network.CycleModePayload;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.jetbrains.annotations.Nullable;

/**
 * Seam for toggling ability mode from the Right Ctrl keybind (REQ-HOT-16),
 * implemented by tensura-iss-integration#51: one press sends ONE cycle
 * request for the selected slot - the server resolves the ability from its
 * own state, routes through core's mode-change contract (tensura's handler
 * runs ARR's native body), and echoes the resulting stored mode back. The
 * client never computes or authoritative-stores a mode value.
 *
 * <p>The key's default is deliberately <b>unbound</b>: Tensura's native
 * next-mode key IS Right Ctrl (bytecode-pinned: `NEXT_ABILITY_MODE` =
 * GLFW 342), so binding ours to the same key would advance twice per
 * press (both KeyMappings consume one click). Native keeps Right Ctrl;
 * this keybind is for players who rebind it to their own key.</p>
 */
public final class AbilityMode {

    private AbilityMode() {
    }

    /**
     * The cycle request for the current state - the testable half (the
     * {@code digitSelection} shape): empty when there is no state, no
     * assignment in the selected slot, or no registered mode-change
     * adapter for that ability (iss/curios ids are not cycleable - the
     * client's same-construction knowledge avoids a pointless round trip).
     *
     * @param state the client's synced bar state (nullable)
     * @return the slot-only request, or empty for a no-op press
     */
    static Optional<CycleModePayload> requestFor(@Nullable BarState state) {
        if (state == null) {
            return Optional.empty();
        }
        int slot = state.selectedSlot();
        SlotAssignment assignment = state.assignment(slot);
        if (assignment == null || assignment.abilityId() == null) {
            return Optional.empty();
        }
        if (!ModeChangeHandlers.has(assignment.abilityId())) {
            return Optional.empty();
        }
        return Optional.of(new CycleModePayload(slot));
    }

    /** Toggles ability mode: one press, one server-validated cycle (REQ-HOT-16). */
    public static void toggle() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        requestFor(ClientBarState.get()).ifPresent(PacketDistributor::sendToServer);
    }
}
