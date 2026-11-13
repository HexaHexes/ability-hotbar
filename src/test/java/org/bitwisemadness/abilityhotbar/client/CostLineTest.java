package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.junit.jupiter.api.Test;

/**
 * The row's cost line (issue #222, the owner's all-costs brief): every
 * declared non-zero entry in declaration order joined by ", ", a zero
 * amount declaring no cost, and an empty answer meaning no line at all.
 * Resource names pin the prettify fallback - the I18n probe is
 * client-side, as {@code MenuLabelsTest} notes.
 */
class CostLineTest {

    @Test
    void everyNonZeroCostRidesOneLineInDeclarationOrder() {
        String line = AbilityMenuScreen.costText(List.of(
                new ResourceCost("tensura:magicule", 25.0),
                new ResourceCost("tensura:aura", 5.0)));

        assertEquals("25 Magicule, 5 Aura", line,
                "both declared costs show, in declaration order");
    }

    @Test
    void zeroAmountsDeclareNoCost() {
        String line = AbilityMenuScreen.costText(List.of(
                new ResourceCost("tensura:fire", 0.0),
                new ResourceCost("tensura:water", 1.0),
                new ResourceCost("irons_spellbooks:mana", 2.0),
                new ResourceCost("tensura:light", 0.0)));

        assertEquals("1 Water, 2 Mana", line,
                "zero entries never render (Technomagy declares all six flavors)");
    }

    @Test
    void anAllZeroOrEmptyDeclarationDrawsNoCostLine() {
        assertEquals("", AbilityMenuScreen.costText(List.of()),
                "no declared cost means no line");
        assertEquals("", AbilityMenuScreen.costText(List.of(
                new ResourceCost("tensura:fire", 0.0),
                new ResourceCost("tensura:dark", 0.0))),
                "an all-zero declaration renders nothing, never '0 Fire'");
    }

    @Test
    void fractionalAmountsKeepTheirDecimal() {
        assertEquals("0.5 Aura", AbilityMenuScreen.costText(List.of(
                new ResourceCost("tensura:aura", 0.5))),
                "a fractional price stays visible");
    }
}
