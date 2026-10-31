package org.bitwisemadness.abilityhotbar.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;

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
}
