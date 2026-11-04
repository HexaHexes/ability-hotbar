package org.bitwisemadness.abilityhotbar.content;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The safe, per-frame extraction of a provider's content (hotbar#83 §5).
 *
 * <p>Every provider call is guarded: a provider that throws produces an
 * <b>empty</b> plan - the bar renders nothing for that frame, never a crash -
 * and the failure is logged once per bar id with the bar named, so a broken
 * provider cannot spam the log at frame rate. Contract violations - a cell
 * count that cannot be drawn, a framed cell outside the plan, an index the
 * provider rejects - are reported the same once-per-bar way. Providers are
 * code, so a reported failure persists until restart: the latch is
 * session-lifetime and nothing needs to clear it.</p>
 *
 * <p>Common classes only: this is the headless half of the provider path.
 * The GUI layer consumes {@link #build} results; the skip-empty predicate
 * only asks {@link #hasContent}. Neither runs on a dedicated server.</p>
 */
public final class ContentPlan {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The most cells a plan will ever hold. A provider owns its count, but
     * an absurd one would blow up the layout: outside {@code [1, MAX_CELLS]}
     * the plan fails closed (empty, logged once) instead of allocating.
     */
    public static final int MAX_CELLS = 18;

    /** Bars whose failure has already been reported this session. */
    private static final Set<ResourceLocation> LOGGED = ConcurrentHashMap.newKeySet();

    private final int slotCount;
    private final List<ItemStack> stacks;
    private final int activeSlot;
    @Nullable
    private final ResourceLocation emptyTexture;

    private ContentPlan(int slotCount, List<ItemStack> stacks, int activeSlot,
            @Nullable ResourceLocation emptyTexture) {
        this.slotCount = slotCount;
        this.stacks = stacks;
        this.activeSlot = activeSlot;
        this.emptyTexture = emptyTexture;
    }

    /**
     * Extracts everything the renderer needs for one frame, guarding every
     * provider call. Any failure yields {@link #isEmpty()} - the caller draws
     * nothing - with one log line naming the bar.
     *
     * @param barId    the bar the provider belongs to (named in diagnostics)
     * @param provider the bar's content provider
     * @return the frame's plan; empty when the provider cannot be trusted
     */
    public static ContentPlan build(ResourceLocation barId, HotbarContentProvider provider) {
        if (barId == null || provider == null) {
            return empty();
        }
        try {
            int count = provider.slotCount();
            if (count < 1 || count > MAX_CELLS) {
                report(barId, new IllegalArgumentException(
                        "slotCount " + count + " outside [1," + MAX_CELLS + "]"));
                return empty();
            }
            List<ItemStack> cells = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ItemStack stack = provider.slotStack(i);
                cells.add(stack == null ? ItemStack.EMPTY : stack);
            }
            int active = provider.activeSlot();
            if (active < -1 || active >= count) {
                report(barId, new IllegalArgumentException(
                        "activeSlot " + active + " outside [-1," + count + ")"));
                active = -1;
            }
            ResourceLocation texture = provider.emptyCellTexture();
            return new ContentPlan(count, List.copyOf(cells), active, texture);
        } catch (Exception e) {
            report(barId, e);
            return empty();
        }
    }

    /**
     * The skip-empty answer with the same guarded, once-per-bar diagnostics.
     * A provider that throws counts as <b>empty</b> - consistent with the
     * render path, where it draws nothing.
     *
     * @param barId    the bar the provider belongs to (named in diagnostics)
     * @param provider the bar's content provider
     * @return whether the bar currently holds content
     */
    public static boolean hasContent(ResourceLocation barId, HotbarContentProvider provider) {
        if (barId == null || provider == null) {
            return false;
        }
        try {
            return provider.hasContent();
        } catch (Exception e) {
            report(barId, e);
            return false;
        }
    }

    /**
     * Reports a provider failure once per bar for the session. Providers are
     * code - a failure persists until restart - so the latch can never hide
     * a recurrence, and frame-rate callers cannot flood the log.
     */
    private static void report(ResourceLocation barId, Exception failure) {
        if (LOGGED.add(barId)) {
            LOGGER.error("Content provider for bar {} failed ({}): the bar renders empty; logged once",
                    barId, failure.toString(), failure);
        }
    }

    /** Forgets reported failures - test-only, so fixtures can re-trigger. */
    public static void reset() {
        LOGGED.clear();
    }

    private static ContentPlan empty() {
        return new ContentPlan(0, List.of(), -1, null);
    }

    /** @return true when nothing may be drawn this frame */
    public boolean isEmpty() {
        return slotCount == 0;
    }

    /** @return the cell count of a non-empty plan */
    public int slotCount() {
        return slotCount;
    }

    /** @return one stack per cell; empty for an empty plan */
    public List<ItemStack> stacks() {
        return stacks;
    }

    /** @return the cell to frame, or {@code -1} for none */
    public int activeSlot() {
        return activeSlot;
    }

    /** @return the empty-cell sprite, or null for the hotbar's own chrome */
    @Nullable
    public ResourceLocation emptyTexture() {
        return emptyTexture;
    }
}
