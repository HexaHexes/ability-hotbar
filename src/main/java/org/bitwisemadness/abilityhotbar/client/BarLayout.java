package org.bitwisemadness.abilityhotbar.client;

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
 * scaled uniformly, laid out horizontally (default) or vertically.</p>
 *
 * @param slotCount number of slots (1..10, already clamped by config)
 * @param slotSize  unscaled slot edge length in pixels
 * @param gap       unscaled gap between slots in pixels
 * @param horizontal true for a left-to-right bar, false for top-to-bottom
 * @param offsetX   horizontal pixel offset from the anchor
 * @param offsetY   vertical pixel offset from the anchor
 * @param scale     uniform render scale (0.5..2.0)
 */
public record BarLayout(
        int slotCount,
        int slotSize,
        int gap,
        boolean horizontal,
        int offsetX,
        int offsetY,
        double scale) {

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

    /** The bar's origin (top-left of slot 0) for a given screen size and anchor. */
    public int originX(int screenWidth) {
        // Center the bar horizontally, then apply the configured offset.
        return (screenWidth - (horizontal ? extent() : scaledSlotSize())) / 2 + offsetX;
    }

    /** @return the bar's origin Y (top-left of slot 0) for a screen size. */
    public int originY(int screenHeight) {
        // Sit just above the vanilla hotbar region by default; offset refines it.
        return screenHeight - (horizontal ? scaledSlotSize() : extent()) - 22 + offsetY;
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
