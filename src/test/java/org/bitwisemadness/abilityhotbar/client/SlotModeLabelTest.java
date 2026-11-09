package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityModes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The slot pill's third line (owner's brief): a cell shows the mode only
 * when the slotted ability has <b>multiple</b> modes to choose between -
 * a single-mode ability keeps its two text lines, because its one mode is
 * the only shape it has, not a setting. The same rule gates the row's
 * cycle pill, which would otherwise offer a cycle that can only refuse.
 */
class SlotModeLabelTest {

    private static final ResourceLocation MULTI =
            ResourceLocation.fromNamespaceAndPath("label_test", "multi");
    private static final ResourceLocation SINGLE =
            ResourceLocation.fromNamespaceAndPath("label_test", "single");
    private static final ResourceLocation UNDECLARED =
            ResourceLocation.fromNamespaceAndPath("label_test", "undeclared");

    @BeforeAll
    static void declareModes() {
        AbilityModes.declare(MULTI, List.of(
                new AbilityModes.Mode("a", "label_test.multi.a"),
                new AbilityModes.Mode("b", "label_test.multi.b")));
        AbilityModes.declare(SINGLE, List.of(
                new AbilityModes.Mode("only", "label_test.single.only")));
    }

    @Test
    void aMultiModeAbilityEarnsItsThirdLine() {
        assertNotNull(AbilityMenuScreen.modeLabelFor(MULTI, "a"),
                "two declared modes: the pill may take a third line");
        assertNotNull(AbilityMenuScreen.modeLabelFor(MULTI, "b"),
                "either declared mode labels the cell");
    }

    @Test
    void aSingleModeAbilityStaysTwoLines() {
        assertNull(AbilityMenuScreen.modeLabelFor(SINGLE, "only"),
                "one declared mode is not a setting - no third line");
    }

    @Test
    void noContextOrNoDeclarationStaysTwoLines() {
        assertNull(AbilityMenuScreen.modeLabelFor(MULTI, null),
                "no stored mode context: nothing to show");
        assertNull(AbilityMenuScreen.modeLabelFor(UNDECLARED, "anything"),
                "an undeclared ability never gains a mode line");
        assertNull(AbilityMenuScreen.modeLabelFor(null, "anything"),
                "a null id is unknown, never a guessed label");
    }
}
