package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Status-cluster depth (REQ-HOT-07): the anchor must clear the armor, health,
 * hunger and oxygen rows for any player state, and must never sit below the
 * cluster vanilla itself would draw.
 */
class StatusClusterTest {

    @Test
    void heartRowsFollowVanillaFormula() {
        assertEquals(1, StatusCluster.heartRows(20, 20, 0), "baseline player");
        assertEquals(1, StatusCluster.heartRows(10, 10, 0), "below baseline");
        assertEquals(2, StatusCluster.heartRows(40, 40, 0), "one extra row");
        assertEquals(2, StatusCluster.heartRows(30, 30, 0), "partially filled second row");
        assertEquals(2, StatusCluster.heartRows(20, 20, 20), "absorption adds a row");
        assertEquals(1, StatusCluster.heartRows(0, 0, 0), "never below one row");
    }

    @Test
    void baselineIsTheFullVanillaCluster() {
        assertEquals(49, StatusCluster.BASELINE_DEPTH);
        assertEquals(StatusCluster.BASELINE_DEPTH, StatusCluster.depth(1, true, true),
                "one heart row plus the armor and oxygen rows");
    }

    @Test
    void depthClearsEveryComponentOfTheCluster() {
        assertEquals(39, StatusCluster.depth(1, false, false), "hearts and food only");
        assertEquals(49, StatusCluster.depth(1, true, false), "armor row above the hearts");
        assertEquals(49, StatusCluster.depth(1, false, true), "oxygen row above the hunger");
        assertEquals(49, StatusCluster.depth(1, true, true), "armor and oxygen together");
    }

    @Test
    void depthGrowsWithHeartRows() {
        assertEquals(49, StatusCluster.depth(2, false, false), "second heart row");
        assertEquals(59, StatusCluster.depth(2, true, false), "second row plus armor");
        assertEquals(69, StatusCluster.depth(3, true, false), "third row plus armor");
        assertTrue(StatusCluster.depth(4, true, true) > StatusCluster.depth(3, true, true),
                "monotonically increasing in heart rows");
    }

    @Test
    void oxygenOnlyRaisesTheRightColumn() {
        // The left column already dominates here, so oxygen must not double-count.
        assertEquals(StatusCluster.depth(3, true, false), StatusCluster.depth(3, true, true));
        // But with no armor it is the deciding column.
        assertTrue(StatusCluster.depth(1, false, true) > StatusCluster.depth(1, false, false));
    }

    @Test
    void neverSitsBelowTheClusterVanillaDraws() {
        for (int rows = 1; rows <= 8; rows++) {
            for (boolean armor : new boolean[] {true, false}) {
                for (boolean oxygen : new boolean[] {true, false}) {
                    int ours = StatusCluster.depth(rows, armor, oxygen);
                    int vanilla = vanillaDepth(rows, armor, oxygen);
                    assertTrue(ours >= vanilla,
                            "rows=" + rows + " armor=" + armor + " oxygen=" + oxygen
                                    + " ours=" + ours + " vanilla=" + vanilla);
                }
            }
        }
    }

    @Test
    void matchesVanillaExactlyForOneOrTwoHeartRows() {
        for (boolean armor : new boolean[] {true, false}) {
            for (boolean oxygen : new boolean[] {true, false}) {
                for (int rows = 1; rows <= 2; rows++) {
                    assertEquals(vanillaDepth(rows, armor, oxygen),
                            StatusCluster.depth(rows, armor, oxygen),
                            "rows=" + rows + " armor=" + armor + " oxygen=" + oxygen);
                }
            }
        }
    }

    @Test
    void heartRowsBelowOneAreClamped() {
        assertEquals(StatusCluster.depth(1, true, false), StatusCluster.depth(0, true, false));
        assertEquals(StatusCluster.depth(1, true, false), StatusCluster.depth(-5, true, false));
    }

    /**
     * Vanilla's own bookkeeping, read from {@code Gui} bytecode: the row step is
     * compressed to {@code max(12 - rows, 3)} from three heart rows upward, so it
     * can be smaller than the nominal 10 px our model uses.
     */
    private static int vanillaDepth(int rows, boolean armor, boolean oxygen) {
        int step = Math.max(12 - rows, 3);
        int left = 39 + (rows - 1) * step + (armor ? 10 : 0);
        int right = 39 + (oxygen ? 10 : 0);
        return Math.max(left, right);
    }
}
