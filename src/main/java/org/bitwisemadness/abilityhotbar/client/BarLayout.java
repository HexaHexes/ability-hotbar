package org.bitwisemadness.abilityhotbar.client;

import org.bitwisemadness.abilityhotbar.config.BarAnchor;

/**
 * Pure layout math for the nine-slot bar (REQ-HOT-01/07).
 *
 * <p>Given the client's display config (orientation, offsets, scale) and a
 * screen size, computes each slot's pixel rectangle. This is deliberately free
 * of any {@code GuiGraphics} / Minecraft GUI dependency so the layout - the
 * part most likely to regress across config combinations - is unit-testable in
 * isolation. The render layer only reads the resulting rectangles and blits.</p>
 *
 * <p>Sizing follows the vanilla hotbar language: a fixed slot size and gap,
 * scaled uniformly, laid out horizontally (default) or vertically. Placement is
 * driven by a {@link BarAnchor}, which resolves both axes so no anchor plus
 * orientation combination is invalid.</p>
 *
 * @param slotCount number of slots (1..18, already clamped by config)
 * @param slotSize  unscaled slot edge length in pixels
 * @param gap       unscaled gap between slots in pixels
 * @param horizontal true for a left-to-right bar, false for top-to-bottom
 * @param anchor    where the bar is placed on screen
 * @param statusDepth pixels from the bottom of the screen to the top of the
 *                    status cluster; only read for {@link BarAnchor#ABOVE_STATUS}
 * @param offsetX   horizontal pixel offset from the anchor
 * @param offsetY   vertical pixel offset from the anchor
 * @param scale     uniform render scale (0.5..2.0)
 */
public record BarLayout(
        int slotCount,
        int slotSize,
        int gap,
        boolean horizontal,
        BarAnchor anchor,
        int statusDepth,
        int offsetX,
        int offsetY,
        double scale) {

    /**
     * Inset from a screen edge, in pixels. Covers the vanilla selection frame's
     * 2 px-per-side overhang and the background sprite's border at every
     * supported scale (0.5..2.0), so neither is clipped.
     */
    private static final int EDGE_INSET = 4;

    /** Vanilla hotbar height: its top edge sits this far above the screen bottom. */
    private static final int HOTBAR_DEPTH = 22;

    /** Distance from the screen bottom to the vanilla experience bar's top edge. */
    private static final int XP_DEPTH = 29;

    public BarLayout {
        if (slotCount < 1) {
            throw new IllegalArgumentException("slotCount must be >= 1, got " + slotCount);
        }
        if (slotSize < 1) {
            throw new IllegalArgumentException("slotSize must be >= 1, got " + slotSize);
        }
        if (gap < 0) {
            throw new IllegalArgumentException("gap must be >= 0, got " + gap);
        }
        if (anchor == null) {
            throw new IllegalArgumentException("anchor must not be null");
        }
        if (anchor == BarAnchor.ABOVE_STATUS && statusDepth < 1) {
            throw new IllegalArgumentException(
                    "statusDepth must be >= 1 for ABOVE_STATUS, got " + statusDepth);
        }
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale must be finite and > 0, got " + scale);
        }
    }

    /** Scaled slot edge length (rounded). */
    public int scaledSlotSize() {
        return Math.max(1, (int) Math.round(slotSize * scale));
    }

    /** Scaled gap between slots (rounded). */
    public int scaledGap() {
        return Math.max(0, (int) Math.round(gap * scale));
    }

    /** Stride from one slot's origin to the next, along the layout axis. */
    public int stride() {
        return scaledSlotSize() + scaledGap();
    }

    /** Total extent of the bar along its layout axis (scaled). */
    public int extent() {
        return slotCount * scaledSlotSize() + (slotCount - 1) * scaledGap();
    }

    /** Width of the slot grid along X. */
    private int barWidth() {
        return horizontal ? extent() : scaledSlotSize();
    }

    /** Height of the slot grid along Y. */
    private int barHeight() {
        return horizontal ? scaledSlotSize() : extent();
    }

    /** The bar's origin (top-left of slot 0) for a given screen size and anchor. */
    public int originX(int screenWidth) {
        int base = switch (anchor) {
            case LEFT_EDGE -> EDGE_INSET;
            case RIGHT_EDGE -> screenWidth - barWidth() - EDGE_INSET;
            case ABOVE_HOTBAR, ABOVE_XP, ABOVE_STATUS -> (screenWidth - barWidth()) / 2;
        };
        return base + offsetX;
    }

    /** @return the bar's origin Y (top-left of slot 0) for a screen size. */
    public int originY(int screenHeight) {
        int base = switch (anchor) {
            case ABOVE_HOTBAR -> screenHeight - barHeight() - HOTBAR_DEPTH;
            case ABOVE_XP -> screenHeight - barHeight() - XP_DEPTH;
            case ABOVE_STATUS -> screenHeight - barHeight() - statusDepth;
            case LEFT_EDGE, RIGHT_EDGE -> (screenHeight - barHeight()) / 2;
        };
        return base + offsetY;
    }

    /**
     * The pixel rectangle of one slot.
     *
     * @param index 0-based slot index
     * @param screenWidth  current screen width (for the horizontal anchor)
     * @param screenHeight current screen height (for the vertical anchor)
     * @return the slot's {@code (x, y, width, height)} in screen pixels
     */
    public int[] slotRect(int index, int screenWidth, int screenHeight) {
        if (index < 0 || index >= slotCount) {
            throw new IndexOutOfBoundsException(
                    "slot index " + index + " out of range [0," + slotCount + ")");
        }
        int x = originX(screenWidth);
        int y = originY(screenHeight);
        int step = index * stride();
        if (horizontal) {
            x += step;
        } else {
            y += step;
        }
        int size = scaledSlotSize();
        return new int[] {x, y, size, size};
    }
}
