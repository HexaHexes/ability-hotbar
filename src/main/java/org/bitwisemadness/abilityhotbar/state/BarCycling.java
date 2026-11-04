package org.bitwisemadness.abilityhotbar.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.content.ContentPlan;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProviders;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;

/**
 * Pure hotbar cycling math (REQ-HOT-22, ADR-012).
 *
 * <p>The cycle set is ordered: the built-in window
 * ({@code display.hotbarCount} clamped by {@code hotbar.maxBars}), then every
 * registered bar in registration order - registered bars are never clipped by
 * the window. All targeting is by id, mirroring the switch payload: an index
 * beyond the set fails closed, so a desynced window can never select a bar the
 * player did not ask for.</p>
 *
 * <p>Three entry points:</p>
 * <ul>
 *   <li>{@link #step} - Alt+wheel prev/next with wrap;</li>
 *   <li>{@link #stepSkippingEmpty} - the same, skipping bars whose slots are
 *       all unassigned when {@code display.skipEmptyBars} is on (inert ids
 *       count as assigned; a content-provider bar answers through
 *       {@code hasContent()}, a provider-less one fails closed to empty);</li>
 *   <li>{@link #direct} - Alt+digit, which always lands where pressed.</li>
 * </ul>
 *
 * <p>Config-free - the caller passes the window - so every rule is
 * unit-testable without a loaded config (the input handler reads
 * {@code HotbarConfig.effectiveHotbarCount()}).</p>
 */
public final class BarCycling {

    private BarCycling() {
    }

    /**
     * The ordered cycle set: built-in bars {@code 0..window-1}, then every
     * registered bar in registration order (ADR-012 - the window never clips
     * registrations).
     *
     * @param window        the built-in window size ({@code display.hotbarCount} clamped by {@code hotbar.maxBars})
     * @param registrations the live registrations, in registration order
     * @return the immutable cycle set; empty only when the window is 0 and nothing is registered
     */
    public static List<ResourceLocation> cycleSet(int window, List<HotbarRegistration> registrations) {
        List<ResourceLocation> set = new ArrayList<>(Math.max(window, 0) + registrations.size());
        for (int i = 0; i < window; i++) {
            set.add(HotbarRegistry.builtinBarId(i));
        }
        for (HotbarRegistration registration : registrations) {
            set.add(registration.id());
        }
        return List.copyOf(set);
    }

    /**
     * Alt+wheel: the bar one step away, wrapping at the ends.
     *
     * @param current       the active bar's id; when it lies outside the set (the
     *                      window was lowered while it was active) the step enters
     *                      the set at the near end instead of teleporting across it
     * @param direction     -1 for previous, +1 for next, 0 for no movement
     * @return the neighbouring bar, or empty when the direction is 0 or the set is empty
     */
    public static Optional<ResourceLocation> step(ResourceLocation current, int direction,
            int window, List<HotbarRegistration> registrations) {
        List<ResourceLocation> set = cycleSet(window, registrations);
        if (set.isEmpty() || direction == 0) {
            return Optional.empty();
        }
        int index = set.indexOf(current);
        if (index < 0) {
            return Optional.of(direction > 0 ? set.get(0) : set.get(set.size() - 1));
        }
        int sign = direction > 0 ? 1 : -1;
        return Optional.of(set.get(Math.floorMod(index + sign, set.size())));
    }

    /**
     * Alt+digit: the bar at {@code index} in the cycle set (Alt+1 is index 0,
     * Alt+0 is index 9). Beyond the set - or a negative index - the request
     * fails closed (empty), so a digit the window does not cover selects
     * nothing at all.
     *
     * @param index the zero-based digit target
     * @return the targeted bar, or empty when the index is outside the set
     */
    public static Optional<ResourceLocation> direct(int index, int window,
            List<HotbarRegistration> registrations) {
        if (index < 0) {
            return Optional.empty();
        }
        List<ResourceLocation> set = cycleSet(window, registrations);
        return index < set.size() ? Optional.of(set.get(index)) : Optional.empty();
    }

    /**
     * Alt+wheel with {@code display.skipEmptyBars}: the next bar in
     * {@code direction} that has content, wrapping past empty ones. When
     * <b>every</b> bar in the set is empty, the request falls back to one
     * normal step so a fresh player can still wheel onto a bar to fill it.
     *
     * <p>The walk meets the current bar again as its last candidate: when it
     * is the only bar with content there is nowhere better to land, so the
     * result is the current bar (no movement) rather than a step onto an
     * empty one. Alt+digit never uses this path - direct selection always
     * lands where pressed.</p>
     *
     * @param current the active bar's id
     * @param direction -1 for previous, +1 for next, 0 for no movement
     * @param isEmpty reports whether a bar has no assigned abilities - build it with {@link #emptyBars}
     * @return the landing bar, or empty when the direction is 0 or the set is empty
     */
    public static Optional<ResourceLocation> stepSkippingEmpty(ResourceLocation current, int direction,
            int window, List<HotbarRegistration> registrations, Predicate<ResourceLocation> isEmpty) {
        List<ResourceLocation> set = cycleSet(window, registrations);
        if (set.isEmpty() || direction == 0) {
            return Optional.empty();
        }
        int start = set.indexOf(current);
        if (start < 0) {
            // Outside the set: no anchor to walk from - normal stepping.
            return step(current, direction, window, registrations);
        }
        int sign = direction > 0 ? 1 : -1;
        for (int i = 1; i <= set.size(); i++) {
            ResourceLocation candidate = set.get(Math.floorMod(start + sign * i, set.size()));
            if (!isEmpty.test(candidate)) {
                return Optional.of(candidate);
            }
        }
        // Every bar in the set is empty (the current one included): normal step.
        return Optional.of(set.get(Math.floorMod(start + sign, set.size())));
    }

    /**
     * The emptiness predicate wheel skipping reads from a synced state:
     * a stored bar is empty when every one of its slots is unassigned. An
     * <b>inert</b> id (present but unresolvable) counts as assigned, so a
     * diagnosed slot is content, not emptiness.
     *
     * <p>A bar the state does not store - a non-assignable bar, whose content
     * lives with the registering mod - asks its <b>content provider</b>
     * (ADR-012 §5, hotbar#83): {@code hasContent()} decides, a throwing
     * provider counts as empty, and a registered bar with <b>no</b> provider
     * fails closed to empty. The predicate therefore only ever runs where a
     * provider may be invoked - the client's input handler - never on a
     * dedicated server.</p>
     *
     * @param state the synced bar state; a null state skips the stored lookup
     * @return the predicate for {@link #stepSkippingEmpty}
     */
    public static Predicate<ResourceLocation> emptyBars(BarState state) {
        return id -> {
            if (id == null) {
                return false;
            }
            if (state != null) {
                for (Bar bar : state.bars()) {
                    if (bar.id().equals(id)) {
                        return allSlotsUnassigned(bar);
                    }
                }
            }
            // Not stored: the provider decides; no provider fails closed to
            // empty (hotbar#83 §4) - the wheel never lands on unknown content.
            return HotbarContentProviders.get(id)
                    .map(provider -> !ContentPlan.hasContent(id, provider))
                    .orElse(true);
        };
    }

    /** True when a stored bar holds no assigned ability in any slot. */
    private static boolean allSlotsUnassigned(Bar bar) {
        for (SlotAssignment assignment : bar.assignments()) {
            if (assignment != null && assignment.isAssigned()) {
                return false;
            }
        }
        return true;
    }
}
