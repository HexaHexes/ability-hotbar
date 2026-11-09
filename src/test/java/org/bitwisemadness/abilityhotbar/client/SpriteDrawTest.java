package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The shared window solve ({@link SpriteDraw}) at the bar's draw sizes:
 * the same sprite, the same pack, so the growth the owner observed on the
 * menu's pills applies to the HUD frames too (their report: pseudocore
 * items washed by the selector). A broken solve would either scissor the
 * whole frame away or leak the white square back over the icon.
 */
class SpriteDrawTest {

    @Test
    void nativeSizedDrawGrowsTheThreeSides() {
        // 24x23 at scale 1: the pack's window reaches one pixel beyond
        // the nominal 16x16 on left/right/bottom, top exact (owner)
        int[] window = SpriteDraw.window(24, 23);
        assertEquals(3, window[0], "window x (left grown)");
        assertEquals(4, window[1], "window y (top exact)");
        assertEquals(18, window[2], "window width (right grown)");
        assertEquals(17, window[3], "window height (bottom grown)");
    }

    @Test
    void everyDrawSizeKeepsTheWindowInsideAndNonEmpty() {
        for (int width : new int[] {8, 16, 24, 28, 36, 48, 72}) {
            for (int height : new int[] {7, 16, 23, 27, 46, 69, 88}) {
                int[] rect = SpriteDraw.window(width, height);
                assertTrue(rect[0] >= 0 && rect[1] >= 0
                                && rect[0] + rect[2] <= width
                                && rect[1] + rect[3] <= height,
                        "the window stays inside the box at " + width + "x" + height);
                assertTrue(rect[2] > 0 && rect[3] > 0,
                        "the window is never empty at " + width + "x" + height);
            }
        }
    }

    @Test
    void theMenuSolveIsTheSharedOne() {
        // the menu's pills and the bar's frames must cut the same rect -
        // one solve, so the two can never drift apart again
        for (int side : new int[] {8, 16, 24, 26, 27, 28}) {
            int[] shared = SpriteDraw.window(side, side);
            int[] menu = AbilityMenuScreen.selectorWindow(side);
            assertEquals(shared[0], menu[0], "menu x at side " + side);
            assertEquals(shared[1], menu[1], "menu y at side " + side);
            assertEquals(shared[2], menu[2], "menu w at side " + side);
            assertEquals(shared[3], menu[3], "menu h at side " + side);
        }
    }
}
