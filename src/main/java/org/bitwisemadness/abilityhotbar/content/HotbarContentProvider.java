package org.bitwisemadness.abilityhotbar.content;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.abilitycore.api.AbilityAvailability;
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

    /**
     * The texture for one cell when it renders <b>no stack</b> (hotbar#115):
     * ISS-style spell cells are textures, not items. {@code null} (the
     * default) means "render the stack" - item-based providers are unchanged
     * - and the layer consults this only when the cell's stack is empty, so
     * a stack always wins over an icon for the same cell.
     *
     * @param index in {@code [0, slotCount())} by contract
     * @return the cell's texture, or null to render the stack / empty chrome
     */
    @Nullable
    default ResourceLocation iconId(int index) {
        return null;
    }

    /**
     * The cell's player-facing name for the menu's slot pill: the menu
     * draws it beside the icon when present (the ISS spellbook strip
     * shows each spell's name). {@code null} (the default) = no name -
     * the pill stays icon-only, byte-identical for every existing
     * provider. Read client-side like every other hook, guarded by
     * {@code ContentPlan} (a throw fails the frame closed, logged once);
     * the HUD ignores this hook - its cells have no name line.
     *
     * @param index in {@code [0, slotCount())} by contract
     * @return the cell's name, or null for none
     */
    @Nullable
    default Component slotName(int index) {
        return null;
    }

    /**
     * The menu pill's hover tooltip (the owner's provider toolbar brief):
     * the lines drawn when the cursor rests on the pill's ability icon in
     * the menu's slot strip. {@code null} (the default) = no tooltip - the
     * pill reads exactly as before, byte-identical for every existing
     * provider. Read client-side like every other hook and extracted
     * lazily for the one hovered cell by {@link ContentPlan#cellTooltip}:
     * a throw produces no tooltip, logged once with the bar id. The HUD
     * never calls this - it has no cursor.
     *
     * @param player the player being rendered (may be null only in tests)
     * @param index  the hovered cell, in {@code [0, slotCount())} by contract
     * @return the tooltip lines, or null for none
     */
    @Nullable
    default List<Component> slotTooltip(@Nullable Player player, int index) {
        return null;
    }

    /**
     * Per-cell availability (hotbar#115): when a provider opts in, its
     * answer replaces the ability-global overlay tuple for that cell's
     * cooldown/cost overlays (per-spell mana and cooldown). {@code null}
     * (the default) = not opted in - the existing
     * {@code AvailabilityProviders} per-ability tuple remains the answer, so
     * providers that do not opt in are byte-identical to today. Guarded by
     * {@code ContentPlan} like every other provider call: a throw falls back
     * to the global tuple, logged once with the bar id.
     *
     * @param player the player being rendered (may be null only in tests)
     * @param index  the cell whose overlays are drawn
     * @return this cell's availability, or null to use the global tuple
     */
    @Nullable
    default AbilityAvailability availabilityFor(@Nullable Player player, int index) {
        return null;
    }
}
