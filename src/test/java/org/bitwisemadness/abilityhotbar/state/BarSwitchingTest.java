package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Server-side switch validation (REQ-HOT-22, ADR-012): the payload names the
 * target by id, the server accepts registered bars (any assignability) and
 * built-ins below its ceiling, rejects everything else fail-closed, and an
 * accepted switch moves the active bar and nothing else (the no-cast
 * invariant of hotbar#28).
 *
 * <p>The ceiling is passed in rather than read from config, so these fixtures
 * run without a loaded {@code hotbar.maxBars} - the payload handler supplies
 * the real value.</p>
 */
class BarSwitchingTest {

    private static final int CEILING = 3;
    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation EXTRA =
            ResourceLocation.fromNamespaceAndPath("otherpack", "extra");
    private static final ResourceLocation ALERTS =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");

    @BeforeEach
    @AfterEach
    void isolateRegistry() {
        HotbarRegistry.reset();
    }

    @Test
    void switchesToABuiltInBarWithinTheCeiling() {
        BarState state = BarState.empty(3, CEILING);

        BarState switched = BarSwitching.switchTo(state, HotbarRegistry.builtinBarId(2), CEILING)
                .orElseThrow();

        assertEquals(HotbarRegistry.builtinBarId(2), switched.activeBarId());
        assertEquals(CEILING, switched.bars().length, "a full state needs no growth");
    }

    @Test
    void switchesToRegisteredBarsWhetherAssignableOrNot() {
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);
        HotbarRegistry.register(ALERTS, "pseudocore.bar.curios", false);
        BarState state = BarState.empty(3, CEILING);

        BarState toAssignable = BarSwitching.switchTo(state, EXTRA, CEILING).orElseThrow();
        assertEquals(EXTRA, toAssignable.activeBarId());
        // the assignable registration was reconciled into the state first, so
        // the landing bar exists with its own slots
        assertEquals(CEILING + 1, toAssignable.bars().length);
        assertEquals(3, toAssignable.bars()[CEILING].assignments().length);

        BarState toNonAssignable = BarSwitching.switchTo(state, ALERTS, CEILING).orElseThrow();
        assertEquals(ALERTS, toNonAssignable.activeBarId());
        // non-assignable bars carry no attachment state: the view fails closed
        assertEquals(CEILING + 1, toNonAssignable.bars().length, "only the assignable bar was appended");
        assertEquals(0, toNonAssignable.assignments().length);
    }

    @Test
    void rejectsUnknownReservedAndNullTargetsFailClosed() {
        BarState state = BarState.empty(3, CEILING).withAssignment(0, FIREBALL);

        assertTrue(BarSwitching.switchTo(state, null, CEILING).isEmpty());
        assertTrue(BarSwitching.switchTo(null, HotbarRegistry.builtinBarId(0), CEILING).isEmpty());
        assertTrue(BarSwitching.switchTo(state,
                ResourceLocation.fromNamespaceAndPath("nobody", "nowhere"), CEILING).isEmpty());
        assertTrue(BarSwitching.switchTo(state, HotbarRegistry.unidentifiedBarId(0), CEILING).isEmpty());
        assertTrue(BarSwitching.switchTo(state,
                ResourceLocation.fromNamespaceAndPath("ability_hotbar", "not_a_bar"), CEILING).isEmpty());

        // no rejection changed anything
        assertEquals(HotbarRegistry.builtinBarId(0), state.activeBarId());
        assertEquals(FIREBALL, state.assignment(0).abilityId());
    }

    @Test
    void rejectsBuiltInsAtOrBeyondTheCeiling() {
        BarState state = BarState.empty(3, CEILING);

        assertTrue(BarSwitching.switchTo(state, HotbarRegistry.builtinBarId(CEILING), CEILING).isEmpty(),
                "the ceiling is exclusive");
        assertTrue(BarSwitching.switchTo(state, HotbarRegistry.builtinBarId(CEILING + 5), CEILING).isEmpty());
        assertTrue(BarSwitching.switchTo(state,
                HotbarRegistry.builtinBarId(Integer.MAX_VALUE), CEILING).isEmpty());
        assertEquals(HotbarRegistry.builtinBarId(0), state.activeBarId());
    }

    @Test
    void growsAShortStateSoTheTargetBuiltInExists() {
        // A save migrated from the single-bar format stores only bar 0;
        // switching to bar 2 must allocate bars 1-2 rather than land on an
        // unstored id and blank the HUD (fail-closed views).
        BarState shortState = BarState.empty(3, 1).withAssignment(0, FIREBALL);

        BarState switched = BarSwitching.switchTo(shortState, HotbarRegistry.builtinBarId(2), CEILING)
                .orElseThrow();

        assertEquals(3, switched.bars().length, "bars 1 and 2 allocated for the target");
        assertEquals(HotbarRegistry.builtinBarId(0), switched.bars()[0].id());
        assertEquals(HotbarRegistry.builtinBarId(1), switched.bars()[1].id());
        assertEquals(HotbarRegistry.builtinBarId(2), switched.bars()[2].id());
        // stored data intact; new bars empty and sized like the state's own bars
        assertEquals(FIREBALL, switched.bars()[0].assignment(0).abilityId());
        assertTrue(switched.bars()[1].assignment(0).isEmpty());
        assertEquals(3, switched.bars()[1].assignments().length);
        assertEquals(HotbarRegistry.builtinBarId(2), switched.activeBarId());
    }

    @Test
    void anAcceptedSwitchChangesOnlyTheActiveBar() {
        BarState state = BarState.empty(3, CEILING)
                .withAssignment(0, FIREBALL)
                .withSelectedSlot(2);

        BarState switched = BarSwitching.switchTo(state, HotbarRegistry.builtinBarId(1), CEILING)
                .orElseThrow();

        assertEquals(HotbarRegistry.builtinBarId(1), switched.activeBarId());
        for (int i = 0; i < state.bars().length; i++) {
            assertArrayEquals(state.bars()[i].assignments(), switched.bars()[i].assignments(),
                    "a switch must not change any assignment");
            assertEquals(state.bars()[i].selectedSlot(), switched.bars()[i].selectedSlot(),
                    "a switch must not move any selection");
            assertEquals(state.bars()[i].id(), switched.bars()[i].id());
        }
        assertEquals(state.schemaVersion(), switched.schemaVersion());
        // the active view flips to bar 1's own (empty) content; the original
        // state object is untouched (immutability)
        assertEquals(FIREBALL, state.assignment(0).abilityId());
        assertTrue(switched.assignment(0).isEmpty());
    }

    @Test
    void reconcilesAMissingRegisteredBarBeforeSwitchingToIt() {
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);
        // a state that predates the registration (no reconcile has run on it)
        BarState state = BarState.empty(3, CEILING);

        BarState switched = BarSwitching.switchTo(state, EXTRA, CEILING).orElseThrow();

        assertEquals(EXTRA, switched.activeBarId());
        assertTrue(switched.bars()[CEILING].id().equals(EXTRA));
        assertTrue(switched.bars()[CEILING].assignment(0).isEmpty(),
                "the landing bar exists and starts empty");
    }
}
