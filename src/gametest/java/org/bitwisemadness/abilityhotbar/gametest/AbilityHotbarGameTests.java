package org.bitwisemadness.abilityhotbar.gametest;

import java.util.Arrays;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.config.HotbarServerConfig;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.state.Bar;
import org.bitwisemadness.abilityhotbar.state.BarAssignments;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilityhotbar.state.BarSwitching;

/**
 * Runtime tests for the hotbar mod. Keep the {@code expectedTests} count in
 * {@code build.gradle} in sync with the number of {@code @GameTest} methods
 * here, or the harness fails the run.
 */
@GameTestHolder(AbilityHotbar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AbilityHotbarGameTests {

    private AbilityHotbarGameTests() {
    }

    /** The mod is loaded and the empty structure template resolves. */
    @GameTest(template = "empty")
    public static void mod_loads(GameTestHelper helper) {
        helper.succeed();
    }

    /**
     * The bar state attachment is registered and attach/get works on a player
     * (REQ-HOT-14). Proves the attachment type, its serializer and the
     * {@code copyOnDeath} wiring all load without error.
     */
    @GameTest(template = "empty")
    public static void bar_state_attachment_roundtrips(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);

        BarState initial = player.getData(BarStateAttachments.BAR_STATE);
        helper.assertTrue(initial.assignments().length >= 1,
                "default bar state should expose at least one slot");
        helper.assertTrue(initial.assignment(0).isEmpty(),
                "default slot 0 should be empty");

        ResourceLocation abilityId =
                ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "test_ability");
        player.setData(BarStateAttachments.BAR_STATE, initial.withAssignment(0, abilityId));

        BarState updated = player.getData(BarStateAttachments.BAR_STATE);
        helper.assertTrue(updated.assignment(0).isAssigned(),
                "slot 0 should be assigned after setData");
        helper.assertTrue(abilityId.equals(updated.assignment(0).abilityId()),
                "slot 0 should hold the assigned raw id");
        helper.succeed();
    }

    /**
     * The server-side assignment resolution path mutates the attachment for a
     * valid request and leaves it untouched for an invalid one (REQ-HOT-14/21).
     */
    @GameTest(template = "empty")
    public static void assignment_request_resolves_server_side(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        BarState initial = player.getData(BarStateAttachments.BAR_STATE);

        BarState next = BarAssignments.assign(initial, initial.activeBarId(), 0, "tensura:fireball")
                .orElse(null);
        helper.assertTrue(next != null, "a valid assignment must resolve");
        player.setData(BarStateAttachments.BAR_STATE, next);
        helper.assertTrue(player.getData(BarStateAttachments.BAR_STATE).assignment(0).isAssigned(),
                "slot 0 must be assigned after a valid request");
        helper.assertTrue(BarAssignments.assign(initial, initial.activeBarId(),
                        initial.assignments().length, "tensura:fireball").isEmpty(),
                "an out-of-range slot must be rejected");
        helper.assertTrue(BarAssignments.assign(initial, initial.activeBarId(), 0, "not a valid id").isEmpty(),
                "a malformed id must be rejected");
        helper.succeed();
    }

    /**
     * A bar switch moves the active bar and changes <b>nothing else</b> - the
     * server-side half of #28's no-cast AC (no cast, no mode reset, no
     * restriction bypass): the transition is a pure state change proven
     * byte-identical apart from the active bar id, and a request beyond the
     * server's ceiling or naming an unknown id is rejected without touching
     * state at all. The client-input half is the {@code input-gating-matrix}
     * plus the manual checklists.
     */
    @GameTest(template = "empty")
    public static void switch_changes_only_the_active_bar(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ResourceLocation abilityId =
                ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "test_ability");
        BarState seeded = player.getData(BarStateAttachments.BAR_STATE)
                .withAssignment(0, abilityId)
                .withSelectedSlot(2);
        player.setData(BarStateAttachments.BAR_STATE, seeded);

        int ceiling = HotbarServerConfig.MAX_HOTBAR_COUNT.get();
        BarState switched = BarSwitching.switchTo(seeded, HotbarRegistry.builtinBarId(1), ceiling)
                .orElse(null);
        helper.assertTrue(switched != null, "a built-in switch within the ceiling must be accepted");
        helper.assertTrue(HotbarRegistry.builtinBarId(1).equals(switched.activeBarId()),
                "the active bar must be bar 1");

        // No cast, no other mutation: every stored bar keeps its id, slots and
        // selection exactly as they were.
        Bar[] before = seeded.bars();
        Bar[] after = switched.bars();
        helper.assertTrue(before.length == after.length, "a switch must not add or drop bars here");
        for (int i = 0; i < before.length; i++) {
            helper.assertTrue(before[i].id().equals(after[i].id()), "bar identities must not move");
            helper.assertTrue(Arrays.equals(before[i].assignments(), after[i].assignments()),
                    "a switch must not change any assignment");
            helper.assertTrue(before[i].selectedSlot() == after[i].selectedSlot(),
                    "a switch must not move any selection");
        }
        helper.assertTrue(seeded.schemaVersion() == switched.schemaVersion(),
                "a switch must not change the schema version");
        // The view now targets bar 1: bar 0's assignment and selection are gone
        // from the active view, replaced by bar 1's own (empty, unselected).
        helper.assertTrue(switched.assignment(0).isEmpty(),
                "the active view must target bar 1, not bar 0's data");
        helper.assertTrue(switched.selectedSlot() == 0, "bar 1's own selection applies");

        // Rejected requests change nothing: beyond the ceiling, and unknown ids.
        helper.assertTrue(BarSwitching.switchTo(seeded, HotbarRegistry.builtinBarId(ceiling), ceiling).isEmpty(),
                "a built-in at the ceiling must be rejected fail-closed");
        helper.assertTrue(BarSwitching.switchTo(seeded,
                        ResourceLocation.fromNamespaceAndPath("nobody", "nowhere"), ceiling).isEmpty(),
                "an unregistered id must be rejected fail-closed");

        // The payload handler's persist step keeps the switched state on the
        // player, so the next snapshot shows the new active bar.
        player.setData(BarStateAttachments.BAR_STATE, switched);
        helper.assertTrue(HotbarRegistry.builtinBarId(1)
                        .equals(player.getData(BarStateAttachments.BAR_STATE).activeBarId()),
                "the switched state must persist to the attachment");
        helper.succeed();
    }
}
