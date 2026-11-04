package org.bitwisemadness.abilityhotbar.state;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilityhotbar.content.ContentPlan;
import org.bitwisemadness.abilityhotbar.network.BarStateSync;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * <p>The owner write API for content-owned bars (hotbar#89, ADR-012 §3
 * amendment): the mod that owns a non-assignable bar writes the verb into
 * its own cells, server-side, at any time - on join and whenever its content
 * changes. Pseudocore's curios bar is the first consumer: each filled Curios
 * slot gets {@code press} seeded at the matching cell, so activating that
 * cell releases <em>that cell's</em> core.</p>
 *
 * <p>Validation is loud and fail-closed: an unregistered bar, an
 * <b>assignable</b> bar (player/menu-owned - this API never touches those),
 * an out-of-range slot or a malformed id all throw before anything is
 * written, so a rejected call mutates nothing. A null id <b>clears</b> the
 * cell - the owner's re-seed is a plain overwrite, which makes it
 * idempotent and authoritative over any prior state (AC: seed - change -
 * re-seed shows the latest).</p>
 *
 * <p>Seeded cells ride the existing machinery: they live in the same
 * attachment as everything else, so every snapshot carries them and
 * activation resolves them through the normal path with
 * {@code slotContext} = the cell index. Common code only (distribution
 * fixture pins it); the {@link ServerPlayer} overload persists and echoes a
 * snapshot so the client sees the seed immediately.</p>
 */
public final class SeededAssignments {

    private SeededAssignments() {
    }

    /**
     * Writes (or clears) one cell of a registered, non-assignable bar.
     * Pure: the caller persists the result.
     *
     * @param current       the player's current bar state
     * @param barId         the content-owned bar to write into
     * @param slot          the cell index, {@code [0, ContentPlan.MAX_CELLS)}
     * @param rawAbilityId  the ability to seed, or null to clear the cell
     * @return the state with that cell written; the bar entry is created and
     *         grown on demand so a first seed never depends on prior writes
     * @throws IllegalArgumentException on a null state/bar, an unregistered bar,
     *                                  an assignable bar, an out-of-range slot
     *                                  or a malformed id - nothing is written
     */
    public static BarState seed(BarState current, ResourceLocation barId, int slot,
            @Nullable String rawAbilityId) {
        if (current == null) {
            throw new IllegalArgumentException("bar state must not be null");
        }
        if (barId == null) {
            throw new IllegalArgumentException("bar id must not be null");
        }
        HotbarRegistration registration = HotbarRegistry.get(barId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "bar id is not a registered hotbar: " + barId));
        if (registration.assignable()) {
            throw new IllegalArgumentException(
                    "assignable bars are player/menu-owned; seeding is for content-owned bars: "
                            + barId);
        }
        if (slot < 0 || slot >= ContentPlan.MAX_CELLS) {
            throw new IllegalArgumentException(
                    "slot " + slot + " outside [0," + ContentPlan.MAX_CELLS + ") for " + barId);
        }
        ResourceLocation ability = null;
        if (rawAbilityId != null) {
            try {
                ability = ResourceLocation.parse(rawAbilityId.trim());
            } catch (RuntimeException malformed) {
                // Loud and fail-closed: the owner's literal is wrong, fix it
                // at the call site - nothing is written.
                throw new IllegalArgumentException(
                        "malformed ability id '" + rawAbilityId + "' for " + barId, malformed);
            }
        }

        Bar[] bars = current.bars();
        int index = -1;
        for (int i = 0; i < bars.length; i++) {
            if (bars[i].id().equals(barId)) {
                index = i;
                break;
            }
        }
        Bar target = index >= 0 ? bars[index] : Bar.empty(barId, slot + 1);
        if (target.slotCount() <= slot) {
            target = grownTo(target, slot + 1);
        }
        target = target.withAssignment(slot, ability);
        if (index >= 0) {
            bars[index] = target;
        } else {
            Bar[] grown = new Bar[bars.length + 1];
            System.arraycopy(bars, 0, grown, 0, bars.length);
            grown[bars.length] = target;
            bars = grown;
        }
        return new BarState(bars, current.activeBarId(), current.schemaVersion());
    }

    /**
     * Owner write for a live player: resolves against their attachment,
     * persists, and echoes a snapshot so the client's state sees the seed
     * immediately (the seeding loop on join may call this per cell; callers
     * wanting one echo can drive {@link #seed} themselves and send once).
     *
     * @param player        the owning player
     * @param barId         the content-owned bar to write into
     * @param slot          the cell index
     * @param rawAbilityId  the ability to seed, or null to clear
     * @throws IllegalArgumentException see {@link #seed(BarState, ResourceLocation, int, String)}
     */
    public static void seed(ServerPlayer player, ResourceLocation barId, int slot,
            @Nullable String rawAbilityId) {
        BarState next = seed(player.getData(BarStateAttachments.BAR_STATE), barId, slot, rawAbilityId);
        player.setData(BarStateAttachments.BAR_STATE, next);
        BarStateSync.sendSnapshot(player);
    }

    /** Grows a bar's slot array, preserving its slots, selection and id. */
    private static Bar grownTo(Bar bar, int slotCount) {
        SlotAssignment[] existing = bar.assignments();
        SlotAssignment[] grown = new SlotAssignment[slotCount];
        System.arraycopy(existing, 0, grown, 0, existing.length);
        for (int i = existing.length; i < slotCount; i++) {
            grown[i] = SlotAssignment.empty();
        }
        return new Bar(bar.id(), grown, bar.selectedSlot());
    }

    /**
     * The stored cells of a content-owned bar, or empty when it has never
     * been seeded - the shape a consumer reads back after re-seeding.
     */
    public static Optional<Bar> storedBar(BarState state, ResourceLocation barId) {
        if (state == null || barId == null) {
            return Optional.empty();
        }
        for (Bar bar : state.bars()) {
            if (bar.id().equals(barId)) {
                return Optional.of(bar);
            }
        }
        return Optional.empty();
    }
}
