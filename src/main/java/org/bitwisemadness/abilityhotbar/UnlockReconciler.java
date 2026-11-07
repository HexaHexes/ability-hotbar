package org.bitwisemadness.abilityhotbar;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilityhotbar.state.Bar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The unlock reconciliation sweep (hotbar#147): losing an unlock removes the
 * ability from any hotbar slot. Server-side at {@link
 * org.bitwisemadness.abilityhotbar.network.BarStateSync}'s choke points -
 * every snapshot and every accepted apply re-sweeps, so mid-session
 * revocation lands on the next echo without a tick loop, and no client ever
 * writes this.
 *
 * <p>Only a <b>proven-unlearned</b> answer clears (the {@link UnlockRule}
 * posture): unknown, undeclared and provider failures never touch a slot,
 * so a pool without unlock declarations round-trips untouched. One log line
 * per distinct id actually cleared; a cleared slot holds nothing on the
 * next sweep, so re-reconciling never re-logs.</p>
 */
public final class UnlockReconciler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private UnlockReconciler() {
    }

    /**
     * @param player  the owning player (nullable only in fixtures - core's
     *                unlock read accepts it)
     * @param current the state to sweep
     * @return the state with provably-unlearned slots cleared, or empty when
     *         nothing changed (callers skip the write entirely)
     */
    public static Optional<BarState> reconcile(@Nullable ServerPlayer player, BarState current) {
        if (current == null) {
            return Optional.empty();
        }
        Bar[] original = current.bars();
        Bar[] rebuilt = null;
        Set<String> clearedIds = new LinkedHashSet<>();
        for (int b = 0; b < original.length; b++) {
            Bar bar = original[b];
            for (int slot = 0; slot < bar.slotCount(); slot++) {
                SlotAssignment assignment = bar.assignment(slot);
                ResourceLocation abilityId = assignment == null ? null : assignment.abilityId();
                if (abilityId == null
                        || !UnlockRule.provenUnlearned(AbilityUnlocks.isLearned(player, abilityId))) {
                    continue;
                }
                if (rebuilt == null) {
                    rebuilt = original.clone();
                }
                rebuilt[b] = rebuilt[b].withAssignment(slot, null); // null clears the slot
                clearedIds.add(abilityId.toString());
            }
        }
        if (rebuilt == null) {
            return Optional.empty();
        }
        for (String abilityId : clearedIds) {
            LOGGER.info("Removed unlearned ability {} from the player's hotbar (hotbar#147)",
                    abilityId);
        }
        return Optional.of(new BarState(rebuilt, current.activeBarId(), current.schemaVersion()));
    }
}
