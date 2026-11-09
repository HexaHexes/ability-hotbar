package org.bitwisemadness.abilityhotbar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Selection-sprite drawing shared by the menu's slot pills and the bar's
 * frames: the sprite with its <b>window subtracted</b>, so only the frame
 * ever lands. Whatever pack overrides {@code hud/hotbar_selection} -
 * Excalibur's window is a white wash, vanilla's is transparent - the
 * background shows through the middle and the item or icon under the
 * frame stays crisp (the owner's reports: the white square buried icons,
 * and a one-pixel remnant survived the first cut on the left, right and
 * bottom).
 *
 * <p>The window is the sprite's own 16x16 at (4,4) of 24x23, solved for
 * the blit's size and grown one pixel on those three sides (the top edge
 * is exact) - the growth is the observed rasterization, so the cut
 * reaches the window as the GPU draws it. A turned frame (vertical bars)
 * maps the same window through the quarter-turn: {@link #turned}'s
 * footprint swaps the axes, and the scissor is screen-space, so the bands
 * stay axis-aligned.</p>
 */
final class SpriteDraw {

    private SpriteDraw() {
    }

    /**
     * The selection sprite's window in draw space: {@code {x, y, w, h}}
     * for a {@code width x height} blit - the rasterized window, grown
     * one pixel left/right/bottom (the owner's white-remnant report).
     * Package-private so the geometry fixtures can pin it directly.
     */
    static int[] window(int width, int height) {
        int x0 = (int) Math.ceil(4 * width / 24.0 - 0.5) - 1;
        int x1 = (int) Math.ceil(20 * width / 24.0 - 0.5) + 1;
        int y0 = (int) Math.ceil(4 * height / 23.0 - 0.5);
        int y1 = (int) Math.ceil(20 * height / 23.0 - 0.5) + 1;
        int left = Math.max(0, x0);
        int top = Math.max(0, y0);
        return new int[] {
                left,
                top,
                Math.max(0, Math.min(width, x1) - left),
                Math.max(0, Math.min(height, y1) - top)};
    }

    /**
     * Draws the sprite with its window cut out: four scissored passes,
     * one band each around the window, so the middle never paints.
     * {@code rotation} is the sprite's own turn - 0, 90 or 270, the
     * turns {@link #turned} anchors correctly (the menu and horizontal
     * bars pass 0, vertical bars 270).
     */
    static void ring(GuiGraphics gui, ResourceLocation sprite, int x, int y,
            int width, int height, float rotation) {
        int[] win = window(width, height);
        int wx = win[0];
        int wy = win[1];
        int ww = win[2];
        int wh = win[3];
        int turn = ((int) rotation % 360 + 360) % 360;
        int left;
        int top;
        int winW;
        int winH;
        int footW;
        int footH;
        if (turn == 90) {
            // turned()'s 90-degree mapping: screenX = x + height - sy,
            // screenY = y + sx - the window's axes swap with the sprite
            left = x + height - (wy + wh);
            top = y + wx;
            winW = wh;
            winH = ww;
            footW = height;
            footH = width;
        } else if (turn == 270) {
            // 270: screenX = x + sy, screenY = y + width - sx
            left = x + wy;
            top = y + width - (wx + ww);
            winW = wh;
            winH = ww;
            footW = height;
            footH = width;
        } else {
            left = x + wx;
            top = y + wy;
            winW = ww;
            winH = wh;
            footW = width;
            footH = height;
        }
        int right = left + winW;
        int bottom = top + winH;
        int[][] bands = {
                {x, y, footW, top - y},
                {x, bottom, footW, y + footH - bottom},
                {x, top, left - x, winH},
                {right, top, x + footW - right, winH}
        };
        for (int[] band : bands) {
            if (band[2] <= 0 || band[3] <= 0) {
                continue;
            }
            gui.enableScissor(band[0], band[1], band[0] + band[2], band[1] + band[3]);
            try {
                if (turn == 0) {
                    gui.blitSprite(sprite, x, y, width, height);
                } else {
                    turned(gui, sprite, x, y, width, height, rotation);
                }
            } finally {
                gui.disableScissor();
            }
        }
    }

    /**
     * Blits a landscape sprite turned a quarter turn (or three), so it
     * keeps its native proportions instead of being squashed into a
     * portrait box: drawn at {@code (nativeW, nativeH)} and turned about
     * its centre, it occupies {@code (nativeH, nativeW)} starting at
     * {@code (dx, dy)} - any quarter turn swaps width and height the same
     * way, so the footprint is identical for 90 and 270 degrees; only the
     * content's orientation differs.
     *
     * <p>90 degrees (the bar background) maps +X to +Y, and GUI +Y points
     * down, so the sprite's left cap lands at the top: cell 1 under slot
     * 0, at the top of a vertical bar just as at the left of a horizontal
     * one. 270 degrees (the selection frame - 90 plus hotbar#109's
     * 180-degree flip) presents the sprite the other way round.</p>
     */
    static void turned(GuiGraphics gui, ResourceLocation sprite, int dx, int dy,
            int nativeW, int nativeH, float degrees) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        try {
            // Centre of the box the sprite fills once turned: a quarter turn
            // swaps its width and height, so anchor on the swapped half-extents.
            pose.translate(dx + nativeH / 2.0, dy + nativeW / 2.0, 0.0);
            pose.mulPose(Axis.ZP.rotationDegrees(degrees));
            pose.translate(-nativeW / 2.0, -nativeH / 2.0, 0.0);
            gui.blitSprite(sprite, 0, 0, nativeW, nativeH);
        } finally {
            // Never leak the transform into the next sprite's draw.
            pose.popPose();
        }
    }
}
