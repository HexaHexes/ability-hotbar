package org.bitwisemadness.abilityhotbar.network;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.abilitycore.api.AbilityModes;
import org.bitwisemadness.abilitycore.api.ModeContract;
import org.bitwisemadness.abilitycore.config.ConfigDiagnostics;
import org.bitwisemadness.abilityhotbar.UnlockReconciler;
import org.bitwisemadness.abilityhotbar.config.HotbarServerConfig;
import org.bitwisemadness.abilityhotbar.state.Bar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side sync actions for the bar state (REQ-HOT-14, ADR-003).
 *
 * <p>Centralises the two send points: a lifecycle snapshot on the boundaries
 * core signals, and the authoritative echo after an accepted assignment.
 * Every path sweeps through {@link UnlockReconciler} first (hotbar#147), so
 * a slot whose ability is provably unlearned is cleared - saved and echoed -
 * on the next snapshot or apply without a tick loop. Every path also sends
 * the <b>hydrated</b> display copy (play-test round): a cycleable slot's
 * mode is filled from the provider's live projection so the menu's slots
 * and row pill show the ability's current mode even before anything cycles
 * it - a slot that already carries a declared setting keeps that setting
 * (per-slot modes: the projection is the ability's one live value and
 * never overwrites what the player set on a slot) - hydration never
 * persists, the stored state stays exactly what was accepted.</p>
 */
public final class BarStateSync {

    private BarStateSync() {
    }

    /**
     * The state with every bar's row grown to the server's slot ceiling:
     * a save written under an older, smaller ceiling keeps its shorter
     * rows forever, so the bar drew nine slots whatever both configs said
     * (owner's report). Growing grants the empty slots the current
     * ceiling allows - the render still clamps to the client preference,
     * so this never shows more than the player asked for. Idempotent: a
     * row at or beyond the ceiling passes through untouched.
     * Package-private for the fixture (the caller is server-side).
     */
    static BarState paddedToCeiling(BarState state) {
        int slots = ConfigDiagnostics.readOrDefault("hotbar.maxSlots",
                () -> HotbarServerConfig.MAX_HOTBAR_SLOTS.get(), 18);
        BarState out = state;
        for (Bar bar : state.bars()) {
            SlotAssignment[] row = bar.assignments();
            if (row.length >= slots) {
                continue;
            }
            SlotAssignment[] grown = new SlotAssignment[slots];
            System.arraycopy(row, 0, grown, 0, row.length);
            for (int slot = row.length; slot < slots; slot++) {
                grown[slot] = SlotAssignment.empty();
            }
            out = out.withRow(bar.id(), grown);
        }
        return out;
    }

    /** Sends the player's current bar state to their client (unlock-reconciled first). */
    public static void sendSnapshot(ServerPlayer player) {
        BarState current = player.getData(BarStateAttachments.BAR_STATE);
        BarState effective = paddedToCeiling(UnlockReconciler.reconcile(player, current)
                .orElse(current));
        if (effective != current) {
            player.setData(BarStateAttachments.BAR_STATE, effective);
        }
        send(player, effective);
    }

    /** Persists an accepted assignment and echoes the authoritative state back. */
    public static void acceptAssignment(ServerPlayer player, BarState next) {
        persistAndEcho(player, reconciled(player, next));
    }

    /** Persists an accepted selection and echoes the authoritative state back. */
    public static void acceptSelection(ServerPlayer player, BarState next) {
        persistAndEcho(player, reconciled(player, next));
    }

    /** Persists an accepted bar switch and echoes the authoritative state back. */
    public static void acceptSwitch(ServerPlayer player, BarState next) {
        persistAndEcho(player, reconciled(player, next));
    }

    private static void persistAndEcho(ServerPlayer player, BarState effective) {
        player.setData(BarStateAttachments.BAR_STATE, effective);
        send(player, effective);
    }

    /** The wire copy: the persisted state with every fillable mode projected. */
    private static void send(ServerPlayer player, BarState effective) {
        PacketDistributor.sendToPlayer(player, new BarStateSnapshotPayload(
                hydrated(effective, id -> ModeContract.currentMode(player, id))));
    }

    /**
     * The display copy of a state with each cycleable slot's mode filled
     * from the provider's live projection (the owner's play-test round:
     * slots show the ability's mode from the get-go). Pure - the caller
     * supplies the projection read. Only a projected mode the ability
     * <b>actually declares</b> is written (an undeclared id never reaches
     * the wire), a slot whose stored setting is still declared keeps
     * <b>its own</b> value (per-slot modes - the projection is the
     * ability's one live answer and never overwrites a setting), an
     * absent projection keeps the stored value or nothing - unknown fills
     * nothing, never a guess.
     */
    static BarState hydrated(BarState state,
            Function<ResourceLocation, Optional<String>> projected) {
        BarState out = state;
        for (Bar bar : state.bars()) {
            SlotAssignment[] row = bar.assignments();
            SlotAssignment[] next = null;
            for (int slot = 0; slot < row.length; slot++) {
                String mode = fillableMode(row[slot], projected);
                if (mode != null && !mode.equals(row[slot].mode())) {
                    if (next == null) {
                        next = row.clone();
                    }
                    next[slot] = row[slot].withMode(mode);
                }
            }
            if (next != null) {
                out = out.withRow(bar.id(), next);
            }
        }
        return out;
    }

    /**
     * The mode hydration may write for one slot, or null: only a
     * projected mode the ability <b>actually declares</b> qualifies (an
     * undeclared id never reaches the wire), a modeless ability and an
     * empty slot never gain a mode, and an absent projection fills
     * nothing - unknown stays unknown, never a guess.
     *
     * <p><b>The slot's own setting wins when it is still declared</b>
     * (owner: each slot sets its mode). The projection is the ability's
     * ONE live value; writing it over a stored mode collapses every slot
     * holding that ability - on any bar - onto one mode, which is
     * precisely the per-slot setting hotbar#29 exists to keep. The
     * projection therefore fills only what the slot does not already
     * say: a never-set slot (the play-test "from the get-go" fill) and a
     * stale one the ability no longer declares.</p>
     */
    @Nullable
    private static String fillableMode(SlotAssignment assignment,
            Function<ResourceLocation, Optional<String>> projected) {
        if (assignment == null || !assignment.isAssigned()
                || assignment.abilityId() == null) {
            return null;
        }
        Optional<List<AbilityModes.Mode>> declared =
                AbilityModes.get(assignment.abilityId());
        if (declared.isEmpty() || declared.get().isEmpty()) {
            return null;
        }
        List<AbilityModes.Mode> modes = declared.get();
        String stored = assignment.mode();
        if (stored != null && modes.stream()
                .anyMatch(candidate -> candidate.id().equals(stored))) {
            return null; // the setting is live: keep it, never re-project
        }
        String mode = projected.apply(assignment.abilityId()).orElse(null);
        if (mode == null || modes.stream()
                .noneMatch(candidate -> candidate.id().equals(mode))) {
            return null;
        }
        return mode;
    }

    /** The sweep applied to every accepted apply (hotbar#147) - unchanged states pass through. */
    private static BarState reconciled(ServerPlayer player, BarState next) {
        return UnlockReconciler.reconcile(player, next).orElse(next);
    }
}
