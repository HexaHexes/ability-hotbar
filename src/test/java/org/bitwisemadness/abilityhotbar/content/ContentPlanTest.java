package org.bitwisemadness.abilityhotbar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bitwisemadness.abilitycore.api.AbilityAvailability;
import org.bitwisemadness.abilitycore.api.AvailabilityState;
import org.bitwisemadness.abilitycore.api.Metric;
import org.bitwisemadness.abilityhotbar.MinecraftBootstrap;
import org.bitwisemadness.abilityhotbar.state.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The guarded provider extraction (hotbar#83 §5/§6): plans carry exactly
 * what the renderer needs, a throwing provider fails closed to an empty bar
 * with one log line naming the bar (never a crash, never per-frame spam),
 * and contract violations are reported the once-per-bar way.
 */
class ContentPlanTest {

    static {
        // Real ItemStacks need the item registry, which needs a bootstrap
        // (FML's LoadingModList included) - see MinecraftBootstrap.
        MinecraftBootstrap.ensure();
    }

    private static final ResourceLocation BAR =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");
    private static final ResourceLocation OTHER =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "other");
    private static final ResourceLocation EMPTY_SPRITE =
            ResourceLocation.fromNamespaceAndPath("curios", "slot/empty_curio_slot");
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("iss", "spell/fireball");

    @BeforeEach
    @AfterEach
    void resetLatches() {
        ContentPlan.reset();
    }

    /** A well-behaved six-cell provider: cell 0 holds an item, cell 3 is active. */
    private static HotbarContentProvider sixCells() {
        return new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 6;
            }

            @Override
            public ItemStack slotStack(int index) {
                return index == 0 ? new ItemStack(Items.APPLE) : ItemStack.EMPTY;
            }

            @Override
            public int activeSlot() {
                return 3;
            }

            @Override
            public ResourceLocation emptyCellTexture() {
                return EMPTY_SPRITE;
            }
        };
    }

    private static HotbarContentProvider minimal(int count) {
        return new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return count;
            }

            @Override
            public ItemStack slotStack(int index) {
                return ItemStack.EMPTY;
            }
        };
    }

    private static long reportsFor(LogCapture logs, ResourceLocation barId) {
        return logs.messages().stream().filter(m -> m.contains(barId.toString())).count();
    }

    @Test
    void planExtractsCellsFrameAndTexture() {
        ContentPlan plan = ContentPlan.build(BAR, sixCells());

        assertFalse(plan.isEmpty());
        assertEquals(6, plan.slotCount());
        assertEquals(6, plan.stacks().size());
        assertFalse(plan.stacks().get(0).isEmpty(), "cell 0 holds the item");
        assertTrue(plan.stacks().get(5).isEmpty(), "cell 5 is empty");
        assertEquals(3, plan.activeSlot());
        assertEquals(EMPTY_SPRITE, plan.emptyTexture());
    }

    @Test
    void defaultsAreNoFrameVanillaChromeAndDerivedEmptiness() {
        HotbarContentProvider minimal = minimal(2);
        ContentPlan plan = ContentPlan.build(BAR, minimal);

        assertFalse(plan.isEmpty());
        assertEquals(-1, plan.activeSlot(), "default draws no frame");
        assertNull(plan.emptyTexture(), "default = the hotbar's own chrome");
        assertFalse(ContentPlan.hasContent(BAR, minimal), "all cells empty -> no content");

        HotbarContentProvider withContent = new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 2;
            }

            @Override
            public ItemStack slotStack(int index) {
                return index == 1 ? new ItemStack(Items.APPLE) : ItemStack.EMPTY;
            }
        };
        assertTrue(ContentPlan.hasContent(BAR, withContent),
                "the default hasContent derives from the stacks");
    }

    @Test
    void throwingProviderFailsClosedAndLogsOnceWithTheBarId() {
        HotbarContentProvider broken = new HotbarContentProvider() {
            @Override
            public int slotCount() {
                throw new IllegalStateException("boom");
            }

            @Override
            public ItemStack slotStack(int index) {
                return ItemStack.EMPTY;
            }
        };

        try (LogCapture logs = LogCapture.forLogger(ContentPlan.class)) {
            assertTrue(ContentPlan.build(BAR, broken).isEmpty(), "renders empty, never crashes");
            assertTrue(ContentPlan.build(BAR, broken).isEmpty(), "still empty next frame");
            assertFalse(ContentPlan.hasContent(BAR, broken), "a broken provider has no content");

            assertEquals(1, reportsFor(logs, BAR),
                    "logged once per bar, not per frame: " + logs.messages());
            assertTrue(logs.messages().stream().anyMatch(m -> m.contains("boom")),
                    "the failure must be named: " + logs.messages());
        }
    }

    @Test
    void cellCountOutsideTheRangeFailsClosedAndLogsOnce() {
        HotbarContentProvider zero = minimal(0);
        HotbarContentProvider absurd = minimal(ContentPlan.MAX_CELLS + 1);

        try (LogCapture logs = LogCapture.forLogger(ContentPlan.class)) {
            assertTrue(ContentPlan.build(BAR, zero).isEmpty(), "zero cells cannot be drawn");
            assertTrue(ContentPlan.build(BAR, zero).isEmpty());
            assertTrue(ContentPlan.build(OTHER, absurd).isEmpty(), "above the cap cannot be drawn");
            assertTrue(ContentPlan.build(OTHER, absurd).isEmpty());

            assertEquals(1, reportsFor(logs, BAR), "once per bar: " + logs.messages());
            assertEquals(1, reportsFor(logs, OTHER), "each bar latches separately");
        }
    }

    @Test
    void activeSlotOutsideTheCellsLosesOnlyTheFrame() {
        HotbarContentProvider badFrame = new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 6;
            }

            @Override
            public ItemStack slotStack(int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public int activeSlot() {
                return 99;
            }
        };

        try (LogCapture logs = LogCapture.forLogger(ContentPlan.class)) {
            ContentPlan plan = ContentPlan.build(BAR, badFrame);

            assertFalse(plan.isEmpty(), "the cells themselves still draw");
            assertEquals(6, plan.slotCount());
            assertEquals(-1, plan.activeSlot(), "an unframeable cell draws no frame");
            assertEquals(1, reportsFor(logs, BAR), "the violation is logged once");
        }
    }

    @Test
    void providerRejectingACellIndexFailsTheWholeFrameOnce() {
        HotbarContentProvider rejecting = new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 6;
            }

            @Override
            public ItemStack slotStack(int index) {
                if (index >= 4) {
                    throw new IndexOutOfBoundsException("cell " + index);
                }
                return ItemStack.EMPTY;
            }
        };

        try (LogCapture logs = LogCapture.forLogger(ContentPlan.class)) {
            assertTrue(ContentPlan.build(BAR, rejecting).isEmpty(),
                    "one rejected cell fails the frame closed, never a crash");
            assertTrue(ContentPlan.build(BAR, rejecting).isEmpty());
            assertEquals(1, reportsFor(logs, BAR), "logged once, not per frame");
        }
    }

    @Test
    void latchIsSessionWideAndResetAllowsAFreshReport() {
        HotbarContentProvider broken = minimal(0);

        try (LogCapture logs = LogCapture.forLogger(ContentPlan.class)) {
            ContentPlan.build(BAR, broken);
            ContentPlan.build(BAR, broken);
            assertEquals(1, reportsFor(logs, BAR));

            ContentPlan.reset();
            ContentPlan.build(BAR, broken);
            assertEquals(2, reportsFor(logs, BAR), "reset lets a fixture re-trigger");
        }
    }

    @Test
    void stateSelectionSuppliesTheDefaultFrame() {
        // hotbar#115 AC1: the state's selected slot is the default framed
        // cell when the provider does not name one; an explicit answer wins
        HotbarContentProvider noFrame = minimal(6);
        assertEquals(2, ContentPlan.build(BAR, noFrame, 2).activeSlot(),
                "the state selection becomes the frame");
        assertEquals(-1, ContentPlan.build(BAR, noFrame, -1).activeSlot(),
                "no state selection -> no frame (the old default)");
        assertEquals(5, ContentPlan.build(BAR, noFrame, 99).activeSlot(),
                "the default clamps into the cells");
        assertEquals(3, ContentPlan.build(BAR, sixCells(), 1).activeSlot(),
                "an explicit provider answer beats the default");

        HotbarContentProvider badFrame = new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 6;
            }

            @Override
            public ItemStack slotStack(int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public int activeSlot() {
                return 99;
            }
        };
        try (LogCapture logs = LogCapture.forLogger(ContentPlan.class)) {
            assertEquals(4, ContentPlan.build(BAR, badFrame, 4).activeSlot(),
                    "an invalid provider answer reports and falls back to the default");
            assertEquals(1, reportsFor(logs, BAR), "the violation is logged once");
        }
    }

    @Test
    void iconsFollowTheProviderAndDefaultToNull() {
        // hotbar#115 AC2: default = null everywhere (item-based providers
        // render their stacks exactly as before); an icon-only provider's
        // texture rides the plan and the layer draws it for empty stacks
        ContentPlan plain = ContentPlan.build(BAR, minimal(3));
        for (int i = 0; i < plain.slotCount(); i++) {
            assertNull(plain.iconAt(i), "no icon hook -> stack/chrome rendering, unchanged");
        }

        HotbarContentProvider iconOnly = new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 2;
            }

            @Override
            public ItemStack slotStack(int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public ResourceLocation iconId(int index) {
                return ICON;
            }
        };
        ContentPlan plan = ContentPlan.build(BAR, iconOnly);
        assertFalse(plan.isEmpty());
        assertTrue(plan.stacks().get(0).isEmpty() && plan.stacks().get(1).isEmpty(),
                "an icon-only provider ships no stacks");
        assertEquals(ICON, plan.iconAt(0), "cell 0 renders the texture");
        assertEquals(ICON, plan.iconAt(1), "cell 1 renders the texture");
    }

    @Test
    void namesFollowTheProviderAndDefaultToNull() {
        // the menu pill's name hook: default = null everywhere (icon-only
        // pills, byte-identical for existing providers); a named provider's
        // answer rides the plan per cell and the menu splits it beside the icon
        ContentPlan plain = ContentPlan.build(BAR, minimal(3));
        for (int i = 0; i < plain.slotCount(); i++) {
            assertNull(plain.nameAt(i), "no name hook -> the icon-only pill, unchanged");
        }

        HotbarContentProvider named = new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 2;
            }

            @Override
            public ItemStack slotStack(int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public Component slotName(int index) {
                return Component.literal(index == 0 ? "Fireball" : "Blaze");
            }
        };
        ContentPlan plan = ContentPlan.build(BAR, named);
        assertFalse(plan.isEmpty());
        assertEquals(Component.literal("Fireball"), plan.nameAt(0), "cell 0's name");
        assertEquals(Component.literal("Blaze"), plan.nameAt(1), "cell 1's name");
        assertNull(plan.nameAt(2), "outside the cells answers nothing");
    }

    @Test
    void cellAvailabilityPrefersTheProviderThenFallsBack() {
        // hotbar#115 AC3: opt-in per-cell answer wins; null = not opted in
        // (global tuple unchanged); a throw falls back + logs once
        Optional<AbilityAvailability> global =
                Optional.of(AbilityAvailability.allUnknown());

        HotbarContentProvider optIn = new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 2;
            }

            @Override
            public ItemStack slotStack(int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public AbilityAvailability availabilityFor(Player player, int index) {
                return new AbilityAvailability(Metric.known(0.5), Metric.unknown(),
                        AvailabilityState.available());
            }
        };
        AbilityAvailability chosen = ContentPlan.cellAvailability(BAR, optIn, null, 0, global)
                .orElseThrow();
        assertTrue(chosen.isAvailable(), "the provider's per-cell answer wins");
        assertEquals(Optional.of(0.5), chosen.cooldown().knownValue());

        HotbarContentProvider notOptedIn = minimal(2);
        assertEquals(global,
                ContentPlan.cellAvailability(BAR, notOptedIn, null, 0, global),
                "no opt-in -> the ability-global tuple, byte-identical");

        HotbarContentProvider boom = new HotbarContentProvider() {
            @Override
            public int slotCount() {
                return 2;
            }

            @Override
            public ItemStack slotStack(int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public AbilityAvailability availabilityFor(Player player, int index) {
                throw new IllegalStateException("overlay boom");
            }
        };
        try (LogCapture logs = LogCapture.forLogger(ContentPlan.class)) {
            assertEquals(global, ContentPlan.cellAvailability(BAR, boom, null, 0, global),
                    "a throwing hook falls back to the global tuple");
            assertEquals(global, ContentPlan.cellAvailability(BAR, boom, null, 1, global),
                    "still the global tuple next frame");
            assertEquals(1, reportsFor(logs, BAR),
                    "the failure is logged once per bar, not per frame: " + logs.messages());
        }
    }
}
