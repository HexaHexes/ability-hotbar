package org.bitwisemadness.abilityhotbar.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a content-owned (non-assignable) hotbar shows, provided by the mod
 * that owns that bar's content (REQ-HOT-22, hotbar#83, ADR-012 §3).
 *
 * <p>Registered per bar at mod setup through
 * {@link HotbarContentProviders#register}; only bars that are <b>not</b>
 * assignable may carry a provider - assignable bars keep attachment-owned
 * state. Implementations are read <b>client-side, every frame</b> from state
 * the registering mod already syncs to the owning client: no new sync, no
 * server round trip, and a dedicated server must never invoke one. The
 * interface itself uses common types ({@link ItemStack}) so registration is
 * callable on both distributions.</p>
 *
 * <p>A minimal provider implements {@link #slotCount()} and
 * {@link #slotStack(int)}; everything else defaults to "derived emptiness,
 * no framed cell, the hotbar's own empty-cell chrome". Every call is
 * guarded by {@link ContentPlan}: a provider that throws makes the bar
 * render empty for that frame (logged once with the bar id), never a crash.</p>
 */
public interface HotbarContentProvider {

    /**
     * Cells to draw this frame. The renderer draws exactly this many - one
     * row at the built-in cell size - so the count may be re-read every
     * frame. Must be within {@code [1, ContentPlan.MAX_CELLS]}.
     *
     * @return the cell count
     */
    int slotCount();

    /**
     * The display stack for one cell; rendered through vanilla item
     * rendering, so tint, foil and other mods' item-render passes fire.
     *
     * @param index in {@code [0, slotCount())} by contract
     * @return the cell's stack; {@link ItemStack#EMPTY} means an empty cell
     */
    ItemStack slotStack(int index);

    /**
     * The skip-empty answer (ADR-012 §5): does this bar currently hold any
     * content? The wheel asks this before landing on the bar. Default
     * derives it from the stacks - any non-empty cell means content.
     *
     * @return true when at least one cell holds something
     */
    default boolean hasContent() {
        int count = Math.max(0, slotCount());
        for (int i = 0; i < count; i++) {
            ItemStack stack = slotStack(i);
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cell to frame with the bar's selection frame - the cell the bar's
     * own verb acts on (pseudocore: the active core, whose index decides
     * what a press applies to).
     *
     * @return the cell index, or {@code -1} for none (the default)
     */
    default int activeSlot() {
        return -1;
    }

    /**
     * Background for empty cells. {@code null} (the default) means the
     * hotbar's own empty-cell chrome, clipped to this bar's cells so no
     * ghost frames from the nine-slot texture ever show.
     *
     * @return the sprite id, or null for the hotbar's own chrome
     */
    @Nullable
    default ResourceLocation emptyCellTexture() {
        return null;
    }
}
