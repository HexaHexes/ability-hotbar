package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProvider;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProviders;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cycle-set and skip-empty targeting (REQ-HOT-22, ADR-012, hotbar#83): the
 * window then registrations in order (never clipped), prev/next with wrap,
 * direct digits fail-closed past the set, and wheel skipping honours
 * emptiness - stored bars by their slots, provider bars by {@code hasContent()},
 * provider-less ones fail-closed to empty - while Alt+digit never skips.
 *
 * <p>The cycle math takes the window and registrations as parameters, so it
 * touches no config; the provider fixtures register against the static
 * registries and reset them around every test.</p>
 */
class BarCyclingTest {

    private static final ResourceLocation EXTRA =
            ResourceLocation.fromNamespaceAndPath("otherpack", "extra");
    private static final ResourceLocation CURIOS =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");
    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation GHOST =
            ResourceLocation.fromNamespaceAndPath("tensura", "ghost_missing");

    /**
     * The provider fixtures register against the static registries (a
     * provider's bar must exist in {@link HotbarRegistry} first), so every
     * test starts from - and leaves behind - both empty.
     */
    @BeforeEach
    @AfterEach
    void isolateRegistries() {
        HotbarRegistry.reset();
        HotbarContentProviders.reset();
    }

    private static HotbarRegistration registration(ResourceLocation id, boolean assignable) {
        return new HotbarRegistration(id, id.toString(), assignable);
    }

    private static HotbarContentProvider providerWithContent(boolean content) {
        return new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 2;
            }

            @Override
            public ItemStack slotStack(int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean hasContent() {
                return content;
            }
        };
    }

    @Test
    void cycleSetIsBuiltInWindowThenRegistrationsInOrder() {
        List<HotbarRegistration> registrations =
                List.of(registration(EXTRA, true), registration(CURIOS, false));

        assertEquals(List.of(HotbarRegistry.builtinBarId(0), HotbarRegistry.builtinBarId(1),
                        HotbarRegistry.builtinBarId(2), EXTRA, CURIOS),
                BarCycling.cycleSet(3, registrations));
        // the window never clips registrations (ADR-012)
        assertEquals(List.of(HotbarRegistry.builtinBarId(0), EXTRA, CURIOS),
                BarCycling.cycleSet(1, registrations));
        // and a zero window still leaves the registered tail reachable
        assertEquals(List.of(EXTRA, CURIOS), BarCycling.cycleSet(0, registrations));
    }

    @Test
    void stepWrapsPrevAndNextAroundTheSet() {
        List<HotbarRegistration> registrations = List.of(registration(EXTRA, true));
        // set: [bar_0, bar_1, bar_2, extra]

        assertEquals(HotbarRegistry.builtinBarId(1),
                BarCycling.step(HotbarRegistry.builtinBarId(0), 1, 3, registrations).orElseThrow());
        assertEquals(EXTRA,
                BarCycling.step(HotbarRegistry.builtinBarId(2), 1, 3, registrations).orElseThrow());
        assertEquals(HotbarRegistry.builtinBarId(0),
                BarCycling.step(EXTRA, 1, 3, registrations).orElseThrow(), "next wraps to the front");
        assertEquals(HotbarRegistry.builtinBarId(2),
                BarCycling.step(EXTRA, -1, 3, registrations).orElseThrow());
        assertEquals(EXTRA,
                BarCycling.step(HotbarRegistry.builtinBarId(0), -1, 3, registrations).orElseThrow(),
                "prev wraps to the back");
        assertTrue(BarCycling.step(HotbarRegistry.builtinBarId(0), 0, 3, registrations).isEmpty());
        assertTrue(BarCycling.step(HotbarRegistry.builtinBarId(0), 1, 0, List.of()).isEmpty(),
                "an empty set has nowhere to step");
    }

    @Test
    void stepFromOutsideTheSetEntersAtTheNearEnd() {
        // The window was lowered while bar_7 was active: the next step enters
        // the set at the near end instead of teleporting across it.
        ResourceLocation outside = HotbarRegistry.builtinBarId(7);
        assertEquals(HotbarRegistry.builtinBarId(0),
                BarCycling.step(outside, 1, 3, List.of()).orElseThrow());
        assertEquals(HotbarRegistry.builtinBarId(2),
                BarCycling.step(outside, -1, 3, List.of()).orElseThrow());
    }

    @Test
    void directMapsDigitsAndFailsClosedBeyondTheSet() {
        List<HotbarRegistration> registrations = List.of(registration(EXTRA, true));
        // set: [bar_0, bar_1, bar_2, extra]

        assertEquals(HotbarRegistry.builtinBarId(0),
                BarCycling.direct(0, 3, registrations).orElseThrow(), "Alt+1");
        assertEquals(HotbarRegistry.builtinBarId(2),
                BarCycling.direct(2, 3, registrations).orElseThrow(), "Alt+3");
        assertEquals(EXTRA,
                BarCycling.direct(3, 3, registrations).orElseThrow(), "Alt+4 reaches the tail");
        assertTrue(BarCycling.direct(4, 3, registrations).isEmpty(),
                "Alt+5 beyond the set is ignored fail-closed");
        assertTrue(BarCycling.direct(9, 3, registrations).isEmpty(),
                "Alt+0 beyond the set is ignored fail-closed");
        assertTrue(BarCycling.direct(-1, 3, registrations).isEmpty());
    }

    @Test
    void skipWalkSkipsEmptyBarsAndCountsInertIdsAsContent() {
        // content: bar_0 slot 0, and bar_2 slot 1 holding an inert id - an
        // id with no provider still counts as assigned, so bar_2 is not empty.
        BarState state = BarState.empty(2, 3)
                .withAssignment(0, FIREBALL)
                .withActiveBar(HotbarRegistry.builtinBarId(2))
                .withAssignment(1, GHOST);
        Predicate<ResourceLocation> isEmpty = BarCycling.emptyBars(state);

        assertEquals(HotbarRegistry.builtinBarId(2),
                BarCycling.stepSkippingEmpty(HotbarRegistry.builtinBarId(0), 1, 3, List.of(), isEmpty)
                        .orElseThrow(), "bar_1 is empty, bar_2 has content");
        assertEquals(HotbarRegistry.builtinBarId(2),
                BarCycling.stepSkippingEmpty(HotbarRegistry.builtinBarId(0), -1, 3, List.of(), isEmpty)
                        .orElseThrow(), "prev wraps straight to the content");
        assertEquals(HotbarRegistry.builtinBarId(0),
                BarCycling.stepSkippingEmpty(HotbarRegistry.builtinBarId(2), 1, 3, List.of(), isEmpty)
                        .orElseThrow(), "next from bar_2 wraps to bar_0, which has content");
    }

    @Test
    void skipWalkFallsBackToNormalSteppingWhenEveryBarIsEmpty() {
        BarState empty = BarState.empty(2, 3);
        Predicate<ResourceLocation> isEmpty = BarCycling.emptyBars(empty);

        // A fresh player must still be able to wheel onto a bar to fill it.
        assertEquals(HotbarRegistry.builtinBarId(1),
                BarCycling.stepSkippingEmpty(HotbarRegistry.builtinBarId(0), 1, 3, List.of(), isEmpty)
                        .orElseThrow());
        assertEquals(HotbarRegistry.builtinBarId(2),
                BarCycling.stepSkippingEmpty(HotbarRegistry.builtinBarId(1), 1, 3, List.of(), isEmpty)
                        .orElseThrow());
    }

    @Test
    void skipWalkStaysWhenTheCurrentBarIsTheOnlyContent() {
        BarState state = BarState.empty(2, 3).withAssignment(0, FIREBALL);
        Predicate<ResourceLocation> isEmpty = BarCycling.emptyBars(state);

        // Every other bar is empty: there is nowhere better to land, so the
        // request resolves to the current bar rather than stepping onto an
        // empty one.
        assertEquals(HotbarRegistry.builtinBarId(0),
                BarCycling.stepSkippingEmpty(HotbarRegistry.builtinBarId(0), 1, 3, List.of(), isEmpty)
                        .orElseThrow());
    }

    @Test
    void unstoredBarsWithoutAProviderCountAsEmpty() {
        // hotbar#83 §4: a registered non-assignable bar with no provider fails
        // closed to empty - the wheel skips it rather than landing on content
        // nothing here can vouch for.
        BarState state = BarState.empty(2, 3).withAssignment(0, FIREBALL);
        List<HotbarRegistration> registrations = List.of(registration(CURIOS, false));
        Predicate<ResourceLocation> isEmpty = BarCycling.emptyBars(state);

        // from bar_0: bar_1 and bar_2 are stored-empty, curios has no provider
        // (empty) - the only content is bar_0 itself, so the walk stays put.
        assertEquals(HotbarRegistry.builtinBarId(0),
                BarCycling.stepSkippingEmpty(HotbarRegistry.builtinBarId(0), 1, 3, registrations,
                        isEmpty).orElseThrow());
    }

    @Test
    void providerBarWithContentIsNotSkipped() {
        // ADR-012 §5: a provider bar's emptiness is its hasContent() answer.
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        HotbarContentProviders.register(CURIOS, providerWithContent(true));
        BarState state = BarState.empty(2, 3).withAssignment(0, FIREBALL);
        List<HotbarRegistration> registrations = List.of(registration(CURIOS, false));
        Predicate<ResourceLocation> isEmpty = BarCycling.emptyBars(state);

        assertEquals(CURIOS,
                BarCycling.stepSkippingEmpty(HotbarRegistry.builtinBarId(0), 1, 3, registrations,
                        isEmpty).orElseThrow(), "content on curios makes it a landing");
    }

    @Test
    void providerBarWithoutContentIsSkipped() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        HotbarContentProviders.register(CURIOS, providerWithContent(false));
        BarState state = BarState.empty(2, 3).withAssignment(0, FIREBALL);
        List<HotbarRegistration> registrations = List.of(registration(CURIOS, false));
        Predicate<ResourceLocation> isEmpty = BarCycling.emptyBars(state);

        // every candidate is empty (curios reports no content either), so the
        // walk wraps back to the only content - bar_0 - and stays there.
        assertEquals(HotbarRegistry.builtinBarId(0),
                BarCycling.stepSkippingEmpty(HotbarRegistry.builtinBarId(0), 1, 3, registrations,
                        isEmpty).orElseThrow());
    }

    @Test
    void skipWalkOutsideTheSetFallsBackToNormalStepping() {
        BarState empty = BarState.empty(2, 3);
        Predicate<ResourceLocation> isEmpty = BarCycling.emptyBars(empty);

        // bar_7 is outside the window: no anchor to walk from.
        assertEquals(HotbarRegistry.builtinBarId(0),
                BarCycling.stepSkippingEmpty(HotbarRegistry.builtinBarId(7), 1, 3, List.of(), isEmpty)
                        .orElseThrow());
    }

    @Test
    void emptyBarsPredicateReadsStoredContentAndProviders() {
        BarState state = BarState.empty(2, 3).withAssignment(0, FIREBALL);
        Predicate<ResourceLocation> isEmpty = BarCycling.emptyBars(state);

        assertTrue(isEmpty.test(HotbarRegistry.builtinBarId(1)), "stored and untouched is empty");
        assertFalse(isEmpty.test(HotbarRegistry.builtinBarId(0)), "an assignment is content");
        // nothing stored and no provider vouches for it: fail-closed to empty
        assertTrue(isEmpty.test(CURIOS), "an unproven bar counts as empty (hotbar#83 §4)");
        // null state: only the stored lookup is skipped, the provider rule stands
        assertTrue(BarCycling.emptyBars(null).test(HotbarRegistry.builtinBarId(0)),
                "no state and no provider proves nothing -> empty");
        assertFalse(BarCycling.emptyBars(state).test(null), "a null id is never a bar");
    }
}
