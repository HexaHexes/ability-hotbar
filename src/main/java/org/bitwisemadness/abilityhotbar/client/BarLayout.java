package org.bitwisemadness.abilityhotbar.client;

import org.bitwisemadness.abilityhotbar.config.BarAnchor;

/**
 * Pure layout math for the ability bar (REQ-HOT-01/07, hotbar#86).
 *
 * <p>Given the client's display config (orientation, offsets, scale) and a
 * screen size, computes each slot's pixel rectangle. This is deliberately free
 * of any {@code GuiGraphics} / Minecraft GUI dependency so the layout - the
 * part most likely to regress across config combinations - is unit-testable in
 * isolation. The render layer only reads the resulting rectangles and blits.</p>
 *
 * <p>Sizing follows the vanilla hotbar language: a fixed slot size and gap,
 * scaled uniformly, laid out horizontally (default) or vertically. Placement
 * is driven by a {@link BarAnchor}, which resolves both axes so no anchor plus
 * orientation combination is invalid.</p>
 *
 * <p><b>Grow-from-anchor (hotbar#86):</b> cell 0 always sits nearest the
 * anchor and numbering grows outward. Left anchors grow a strip to the right
 * ({@code 0,1}); right anchors mirror it so cell 0 meets the right edge
 * ({@code 1,0} reading left to right); bottom anchors grow <em>away</em> from
 * the screen edge - horizontal bars stack their segments as rows above each
 * other ({@code 1 above 0}, an 18-slot bar becoming a 9-wide, 2-row block)
 * and vertical columns grow upward from the bottom. Side anchors with a
 * vertical orientation keep the downward column. A bar of nine or fewer slots
 * takes the same single-row/single-column path as before under every anchor.</p>
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

    /** Cells per vanilla hotbar segment - the texture's own nine cells. */
    public static final int CELLS_PER_SEGMENT = 9;

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

    /** Total extent of a run of {@code slotCount} slots along the layout axis. */
    public int extent() {
        return scaledExtent(slotCount);
    }

    /** Extent of {@code cells} slots in one run (scaled, rounded like the rest). */
    private int scaledExtent(int cells) {
        return cells * scaledSlotSize() + Math.max(0, cells - 1) * scaledGap();
    }

    /**
     * How many vanilla hotbar segments this bar needs: one for up to nine
     * slots, two beyond (the ceiling is 18). Each segment blits the complete,
     * unstretched {@code hud/hotbar} sprite, so a partial final segment shows
     * its unused cells as ordinary empty cells.
     *
     * @return the segment count (always at least 1)
     */
    public int segmentCount() {
        return Math.max(1, (slotCount + CELLS_PER_SEGMENT - 1) / CELLS_PER_SEGMENT);
    }

    /**
     * Where one segment's sprite sits relative to the bar's origin, following
     * the grow-from-anchor rule: left anchors grow the strip rightward, right
     * anchors mirror it so cell 0 meets the right edge, bottom anchors stack
     * segments upward (rows for a horizontal bar, an upward column for a
     * vertical one), and side anchors keep a vertical column growing downward.
     *
     * @param segment 0-based segment index
     * @return the {@code {dx, dy}} offset in scaled pixels; {@code {0, 0}}
     *         for the segment that starts at the origin
     * @throws IndexOutOfBoundsException when the segment does not exist
     */
    public int[] segmentOffset(int segment) {
        if (segment < 0 || segment >= segmentCount()) {
            throw new IndexOutOfBoundsException(
                    "segment " + segment + " out of range [0," + segmentCount() + ")");
        }
        if (horizontal) {
            if (anchor == BarAnchor.RIGHT_EDGE) {
                // mirrored strip: this segment's cells occupy the run measured
                // from the far (right) edge, so the last segment starts at 0
                return new int[] {
                    (slotCount - CELLS_PER_SEGMENT * (segment + 1)) * stride(), 0};
            }
            if (isBottomAnchor()) {
                // stacked rows: segment 0 is the bottom row, one stride down
                // from the block's top
                return new int[] {0, (segmentCount() - 1 - segment) * stride()};
            }
            return new int[] {segment * CELLS_PER_SEGMENT * stride(), 0};
        }
        if (isBottomAnchor()) {
            // upward column: segment 0 occupies the bottom run
            return new int[] {0, (slotCount - CELLS_PER_SEGMENT * (segment + 1)) * stride()};
        }
        return new int[] {0, segment * CELLS_PER_SEGMENT * stride()};
    }

    /**
     * The screen-space bounding box of one segment's cells: the
     * {@code {x0, y0, x1, y1}} rect a renderer scissored to it shows
     * exactly the cells that segment owns - the whole sprite for a full
     * segment, a partial final segment cut at its last cell (no ghost
     * frames past a provider's cells: hotbar#86's provider-bar rule).
     * Cells within a segment are always one contiguous run along the
     * layout axis, so the two endpoints' box is exact.
     *
     * @param segment      0-based segment index
     * @param screenWidth  current screen width (for the horizontal anchor)
     * @param screenHeight current screen height (for the vertical anchor)
     * @return the {@code {x0, y0, x1, y1}} bounds in screen pixels
     * @throws IndexOutOfBoundsException when the segment does not exist
     */
    public int[] segmentCellBounds(int segment, int screenWidth, int screenHeight) {
        if (segment < 0 || segment >= segmentCount()) {
            throw new IndexOutOfBoundsException(
                    "segment " + segment + " out of range [0," + segmentCount() + ")");
        }
        int firstIndex = segment * CELLS_PER_SEGMENT;
        int lastIndex = Math.min(slotCount - 1, firstIndex + CELLS_PER_SEGMENT - 1);
        int[] first = slotRect(firstIndex, screenWidth, screenHeight);
        int[] last = slotRect(lastIndex, screenWidth, screenHeight);
        return new int[] {
            Math.min(first[0], last[0]),
            Math.min(first[1], last[1]),
            Math.max(first[0] + first[2], last[0] + last[2]),
            Math.max(first[1] + first[3], last[1] + last[3])
        };
    }

    /**
     * The background sprite's frame width in pixels: the sprite draws at
     * {@code origin - spriteBorder()} so its own cell grid aligns with the
     * slot rects (the value the clip box must also grow by, hotbar#136).
     *
     * @return the scaled frame width, at least one pixel
     */
    public int spriteBorder() {
        return Math.max(1, (int) Math.round(scale));
    }

    /**
     * The scissor box for one segment's background sprite: the cell bounds
     * grown by {@link #spriteBorder()} on every side, so the sprite's outer
     * frame renders instead of being shaved off (hotbar#136 - the exact
     * shave the cell-only box caused). The growth is clamped to half a
     * scaled cell, so a partial segment's ghost cells - a full cell further
     * in - still clip away, and adjacent segments' boxes overlap by at most
     * twice the border (the seam where both frames draw).
     *
     * @param segment     the segment whose box to grow
     * @param screenWidth  current screen width
     * @param screenHeight current screen height
     * @return the frame-inclusive {@code (x0, y0, x1, y1)} clip box
     */
    public int[] segmentClipBounds(int segment, int screenWidth, int screenHeight) {
        int[] cells = segmentCellBounds(segment, screenWidth, screenHeight);
        int grow = Math.min(spriteBorder(), Math.max(0, scaledSlotSize() / 2));
        return new int[] {
            cells[0] - grow, cells[1] - grow, cells[2] + grow, cells[3] + grow
        };
    }

    /** Bottom-of-screen anchors are the ones a bar grows away from. */
    private boolean isBottomAnchor() {
        return anchor != BarAnchor.LEFT_EDGE && anchor != BarAnchor.RIGHT_EDGE;
    }

    /** Width of the bar's box along X. */
    private int barWidth() {
        if (horizontal) {
            // bottom anchors: the pair is a block - as wide as the widest row
            return isBottomAnchor()
                    ? scaledExtent(Math.min(slotCount, CELLS_PER_SEGMENT))
                    : extent();
        }
        return scaledSlotSize();
    }

    /** Height of the bar's box along Y. */
    private int barHeight() {
        if (horizontal) {
            if (isBottomAnchor()) {
                return scaledExtent(segmentCount());
            }
            return scaledSlotSize();
        }
        return extent();
    }

    /** The bar's origin (top-left of its box) for a given screen size and anchor. */
    public int originX(int screenWidth) {
        int base = switch (anchor) {
            case LEFT_EDGE -> EDGE_INSET;
            case RIGHT_EDGE -> screenWidth - barWidth() - EDGE_INSET;
            case ABOVE_HOTBAR, ABOVE_XP, ABOVE_STATUS -> (screenWidth - barWidth()) / 2;
        };
        return base + offsetX;
    }

    /** @return the bar's origin Y (top-left of its box) for a screen size. */
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
     * The pixel rectangle of one slot, placed by the grow-from-anchor rule:
     * cell 0 nearest the anchor, then outward - rightward for a left anchor,
     * leftward (mirrored) for a right anchor, upward from the bottom for a
     * bottom anchor's rows or column, downward for a side-anchored column.
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
        int size = scaledSlotSize();
        if (horizontal) {
            if (anchor == BarAnchor.RIGHT_EDGE) {
                x += (slotCount - 1 - index) * stride();
            } else if (isBottomAnchor()) {
                x += (index % CELLS_PER_SEGMENT) * stride();
                y += (segmentCount() - 1 - index / CELLS_PER_SEGMENT) * stride();
            } else {
                x += index * stride();
            }
        } else if (isBottomAnchor()) {
            y += (slotCount - 1 - index) * stride();
        } else {
            y += index * stride();
        }
        return new int[] {x, y, size, size};
    }
}
