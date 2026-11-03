package org.bitwisemadness.abilityhotbar.state;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.slf4j.Logger;

/**
 * Server-side hotbar switching (REQ-HOT-22, ADR-012).
 *
 * <p>The switch payload names the target <b>by id</b> and this class is the
 * server's validation boundary: a target is accepted when it is either a
 * registered bar (assignable or not - non-assignable bars are switchable,
 * ADR-012) or a built-in id below the server's bar ceiling. Anything else -
 * an unknown id, an unregistered id in the reserved namespace, a built-in at
 * or beyond the ceiling - is rejected fail-closed with a log line and no
 * state change.</p>
 *
 * <p>An accepted switch moves the active bar id and nothing else: no
 * assignment, no selection, no activation (the no-cast invariant of #28 - the
 * GameTest asserts the state is otherwise byte-identical). Two
 * reconciliations make the landing state always renderable:</p>
 *
 * <ul>
 *   <li>The state is reconciled with the registry first, so a registered
 *       assignable bar always exists to switch to (the same rule load-time
 *       reconciliation applies).</li>
 *   <li>A built-in target is grown into a short state: a save migrated from
 *       the single-bar format stores only bar 0, and switching to bar 2
 *       without allocating bars 1-2 would land on an unstored id and blank
 *       the HUD (fail-closed views). Growth appends empty built-ins sized
 *       like the state's own bars and never touches stored data.</li>
 * </ul>
 *
 * <p>Pure and config-free - the caller passes the server's ceiling, so the
 * rule is unit-testable without a loaded config (the payload handler reads
 * {@code hotbar.maxBars}).</p>
 */
public final class BarSwitching {

    private static final Logger LOGGER = LogUtils.getLogger();

    private BarSwitching() {
    }

    /**
     * Resolves a switch request against the server's own registry and ceiling.
     *
     * @param current        the player's current bar state
     * @param target         the requested bar's id
     * @param builtinCeiling exclusive upper bound for built-in indices (server {@code hotbar.maxBars})
     * @return the state with the new active bar, or empty when the request is rejected
     */
    public static Optional<BarState> switchTo(BarState current, ResourceLocation target, int builtinCeiling) {
        if (current == null || target == null) {
            return Optional.empty();
        }
        int index = HotbarRegistry.builtinIndex(target);
        boolean registered = HotbarRegistry.get(target).isPresent();
        if (index < 0 && !registered) {
            LOGGER.warn("Bar switch rejected: {} is neither registered nor a built-in bar", target);
            return Optional.empty();
        }
        if (index >= 0 && index >= builtinCeiling) {
            LOGGER.warn("Bar switch rejected: built-in bar {} is at or beyond the ceiling {}",
                    target, builtinCeiling);
            return Optional.empty();
        }
        BarState landing = grownTo(current.reconciledWith(HotbarRegistry.all()), index);
        return Optional.of(landing.withActiveBar(target));
    }

    /**
     * Appends every built-in up to {@code builtinIndex} that the state does not
     * store yet, sized like the state's own bars. A negative index (a
     * registered target) needs no built-ins - reconciliation already handled
     * that case. Bar order is irrelevant: every consumer looks bars up by id.
     *
     * @param state         the reconciled state
     * @param builtinIndex  the built-in index being switched to, or -1 for a registered target
     * @return the state, grown when a built-in below the target was missing
     */
    private static BarState grownTo(BarState state, int builtinIndex) {
        if (builtinIndex < 0) {
            return state;
        }
        List<Bar> bars = new ArrayList<>(Arrays.asList(state.bars()));
        boolean grew = false;
        int slotCount = bars.get(0).slotCount();
        for (int i = 0; i <= builtinIndex; i++) {
            ResourceLocation id = HotbarRegistry.builtinBarId(i);
            if (bars.stream().noneMatch(bar -> bar.id().equals(id))) {
                bars.add(Bar.empty(id, slotCount));
                grew = true;
            }
        }
        return grew
                ? new BarState(bars.toArray(new Bar[0]), state.activeBarId(), state.schemaVersion())
                : state;
    }
}
