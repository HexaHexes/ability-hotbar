package org.bitwisemadness.abilityhotbar.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.bitwisemadness.abilitycore.api.AvailabilityProviders;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.config.BarAnchor;
import org.bitwisemadness.abilityhotbar.config.HotbarClientConfig;
import org.bitwisemadness.abilityhotbar.config.HotbarConfig;
import org.bitwisemadness.abilityhotbar.config.StatusAnchorMode;
import org.bitwisemadness.abilityhotbar.content.ContentPlan;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProvider;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProviders;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.jetbrains.annotations.Nullable;

/**
 * The nine-slot ability bar's render layer (REQ-HOT-01/02/07, hotbar#83).
 *
 * <p>A vanilla-language {@link LayeredDraw.Layer} that reads the synced bar
 * state ({@link ClientBarState}), resolves each slot's pure {@link SlotRender}
 * decision (icons, selection, cooldown/availability overlay, inert
 * diagnostics), and blits it using {@link BarLayout}'s rectangles. The bar
 * background reuses the vanilla {@code hud/hotbar} sprite and the selection
 * reuses {@code hud/hotbar_selection}, so it reads in the vanilla texture
 * language. Every display branch lives in {@link SlotRender} (unit-tested);
 * this layer only performs the GPU draws.</p>
 *
 * <p>When the <b>active</b> bar is content-owned (non-assignable with a
 * registered {@link HotbarContentProvider}) the layer draws that provider's
 * cells instead - vanilla item rendering per cell, the provider's own empty
 * chrome, the bar's selection frame on {@code activeSlot()} - and no
 * nine-slot background behind them. A failing provider draws nothing for
 * that frame ({@link ContentPlan} logs once); both paths report the origin
 * they drew at so the switch toast follows the same fail-closed decision.</p>
 *
 * <p>Client-only, registered on the GUI-layer bus. When no snapshot has synced
 * yet it draws nothing. The actual on-screen appearance is validated by the
 * manual render checklist (client HUD cannot be proven headless - AGENTS).</p>
 */
@EventBusSubscriber(modid = AbilityHotbar.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AbilityBarLayer implements LayeredDraw.Layer {

    /** Vanilla 9-slot hotbar background sprite, native 182x22 (Gui.renderItemHotbar hardcodes these). */
    private static final ResourceLocation VANILLA_HOTBAR =
            ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final int VANILLA_HOTBAR_WIDTH = 182;
    private static final int VANILLA_HOTBAR_HEIGHT = 22;
    /** Vanilla per-slot selection frame sprite, native 24x23. */
    private static final ResourceLocation VANILLA_SELECTION =
            ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    private static final int VANILLA_SELECTION_WIDTH = 24;
    private static final int VANILLA_SELECTION_HEIGHT = 23;
    /**
     * The selection sprite's top ring is 4 native px (left/right 4, bottom 3:
     * the transparent window sits at 4..19 in both axes of the 24x23 texture -
     * measured from the vanilla asset). The selector renders at 270 degrees
     * (hotbar#109: the shipped 90-degree turn plus the requested 180-degree
     * flip), which maps this TOP ring onto the frame's LEFT - so the
     * window-solve inset pairs with it (a stale bottom-ring inset parks the
     * window one pixel right of the slot).
     */
    private static final int SELECTION_TOP_RING = 4;

    private static final int INERT_COLOR = 0x66FF2222;
    private static final int UNAVAILABLE_TINT = 0x66FF0000;
    private static final int COOLDOWN_COLOR = 0xAA000000;
    private static final int COST_BG = 0xCC000000;

    private AbilityBarLayer() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR,
                ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "ability_bar"),
                new AbilityBarLayer());
    }

    @Override
    public void render(GuiGraphics gui, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }
        BarState state = ClientBarState.get();
        if (state == null) {
            return; // no snapshot yet: render nothing, never a guess
        }
        int screenW = gui.guiWidth();
        int screenH = gui.guiHeight();

        // A content-owned bar (provider-backed) draws its own cells; anything
        // else draws the stored bar. Both paths return the origin they drew
        // at, or null when they drew nothing, so the toast follows that.
        ResourceLocation activeId = state.activeBarId();
        HotbarContentProvider provider = HotbarContentProviders.get(activeId).orElse(null);
        int[] origin = provider != null
                ? renderProviderBar(gui, mc, activeId, provider, state, screenW, screenH)
                : renderStoredBar(gui, mc, state, screenW, screenH);
        if (origin == null) {
            return;
        }

        // The switch toast is text, not a sprite: it rides after the bar's
        // blend-managed draw, one line above the origin, and only while its
        // self-expiring window is open (display.switchToast gates recording).
        if (SwitchToast.isVisible(System.currentTimeMillis())) {
            gui.drawString(mc.font, SwitchToast.message(), origin[0], origin[1] - 12, 0xFFFFFF);
        }
    }

    /**
     * Draws the stored bar's slots from the synced state.
     *
     * @return the origin the bar was drawn at, or null when nothing drew
     */
    @Nullable
    private static int[] renderStoredBar(GuiGraphics gui, Minecraft mc, BarState state,
            int screenW, int screenH) {
        // The attachment is sized to the server ceiling, but the client renders only
        // the first effectiveRenderCount() entries (server maxSlots clamps the client
        // preference, REQ-CFG-02). Saved state may exceed the ceiling, so clamp here.
        int renderCount = HotbarConfig.effectiveRenderCount(state.assignments().length);
        if (renderCount < 1) {
            return null;
        }

        BarAnchor anchor = HotbarClientConfig.ANCHOR.get();
        BarLayout layout = new BarLayout(
                renderCount,
                20,
                0,
                HotbarClientConfig.HORIZONTAL.get(),
                anchor,
                statusDepth(anchor, mc.player),
                HotbarClientConfig.OFFSET_X.get(),
                HotbarClientConfig.OFFSET_Y.get(),
                HotbarClientConfig.SCALE.get());

        List<SlotRender> slots = SlotRender.resolveAll(
                state,
                id -> AvailabilityProviders.availabilityFor(mc.player, id),
                AbilityDiscovery::get);

        // Vanilla wraps its hotbar draw in enableBlend/disableBlend. Blend matters:
        // a pack-replaced hud/hotbar_selection centre is semi-transparent white and
        // renders opaque without it; never leak blend into the next frame's draw.
        RenderSystem.enableBlend();
        try {
            drawBarBackground(gui, layout, screenW, screenH);
            for (int i = 0; i < renderCount && i < slots.size(); i++) {
                SlotRender slot = slots.get(i);
                int[] rect = layout.slotRect(i, screenW, screenH);
                drawSlot(gui, slot, rect, layout);
            }
        } finally {
            // Never leak blend state into the next frame's vanilla hotbar draw.
            RenderSystem.disableBlend();
        }
        return new int[] {layout.originX(screenW), layout.originY(screenH)};
    }

    /**
     * Draws a content-provider bar: one row of the provider's cells at the
     * built-in cell size on the shared anchor, items through vanilla item
     * rendering, the provider's empty chrome (or the hotbar's own, clipped
     * to these cells so no ghost frames show), and the selection frame on
     * {@code activeSlot()}. No nine-slot background sits behind it.
     *
     * @return the origin the bar was drawn at, or null when the plan failed
     */
    @Nullable
    private static int[] renderProviderBar(GuiGraphics gui, Minecraft mc, ResourceLocation barId,
            HotbarContentProvider provider, BarState state, int screenW, int screenH) {
        ContentPlan plan = ContentPlan.build(barId, provider);
        if (plan.isEmpty()) {
            return null; // broken provider: nothing this frame (already logged once)
        }
        BarAnchor anchor = HotbarClientConfig.ANCHOR.get();
        BarLayout layout = new BarLayout(
                plan.slotCount(),
                20,
                0,
                HotbarClientConfig.HORIZONTAL.get(),
                anchor,
                statusDepth(anchor, mc.player),
                HotbarClientConfig.OFFSET_X.get(),
                HotbarClientConfig.OFFSET_Y.get(),
                HotbarClientConfig.SCALE.get());

        RenderSystem.enableBlend();
        try {
            if (plan.emptyTexture() == null) {
                drawCellChromeClipped(gui, layout, screenW, screenH);
            }
            List<ItemStack> cells = plan.stacks();
            for (int i = 0; i < plan.slotCount(); i++) {
                int[] rect = layout.slotRect(i, screenW, screenH);
                ItemStack stack = cells.get(i);
                if (stack.isEmpty()) {
                    ResourceLocation texture = plan.emptyTexture();
                    if (texture != null) {
                        gui.blitSprite(texture, rect[0], rect[1], rect[2], rect[3]);
                    }
                } else {
                    drawCellItem(gui, mc, stack, rect, layout.scale());
                }
            }
            int active = plan.activeSlot();
            if (active >= 0) {
                drawSelectionFrame(gui, layout, layout.slotRect(active, screenW, screenH));
            }
            drawSeededOverlays(gui, mc, state, layout, screenW, screenH);
        } finally {
            RenderSystem.disableBlend();
        }
        return new int[] {layout.originX(screenW), layout.originY(screenH)};
    }

    /**
     * Overlays for a provider bar's own assignments (#89): a mod-seeded verb
     * contributes only its availability/cooldown/cost overlays over the
     * provider's cell - the content stays the provider's ItemStack, never a
     * second icon (ADR-012 §3 amendment). Cells without an assignment, or an
     * active bar this state does not store, contribute nothing (the
     * fail-closed view yields the empty sentinel).
     */
    private static void drawSeededOverlays(GuiGraphics gui, Minecraft mc, BarState state,
            BarLayout layout, int screenW, int screenH) {
        for (int i = 0; i < layout.slotCount(); i++) {
            SlotAssignment assignment = state.assignment(i);
            if (assignment == null || !assignment.isAssigned() || assignment.abilityId() == null) {
                continue;
            }
            ResourceLocation id = assignment.abilityId();
            SlotRender decision = SlotRender.resolve(assignment, false,
                    AvailabilityProviders.availabilityFor(mc.player, id), AbilityDiscovery.get(id));
            int[] rect = layout.slotRect(i, screenW, screenH);
            drawOverlays(gui, decision, rect[0], rect[1], rect[2], rect[3]);
        }
    }

    /**
     * The hotbar's own empty-cell chrome for a provider that supplies no
     * texture: the background sprite drawn exactly as the stored bar would,
     * but scissored to this bar's cells - never the nine ghost frames.
     */
    private static void drawCellChromeClipped(GuiGraphics gui, BarLayout layout,
            int screenW, int screenH) {
        int[] first = layout.slotRect(0, screenW, screenH);
        int[] last = layout.slotRect(layout.slotCount() - 1, screenW, screenH);
        int x0 = Math.min(first[0], last[0]);
        int y0 = Math.min(first[1], last[1]);
        int x1 = Math.max(first[0] + first[2], last[0] + last[2]);
        int y1 = Math.max(first[1] + first[3], last[1] + last[3]);
        gui.enableScissor(x0, y0, x1, y1);
        try {
            // One sprite only: provider bars keep single-segment chrome per
            // #86's open-question default (their cells are provider-owned).
            drawBackgroundSegment(gui, layout, screenW, screenH, 0);
        } finally {
            gui.disableScissor();
        }
    }

    /**
     * One content cell: the stack through vanilla item rendering (tint, foil
     * and other mods' item-render passes fire) plus its count/durability
     * decorations, 16px centred in the 20px cell and scaled with the bar.
     */
    private static void drawCellItem(GuiGraphics gui, Minecraft mc, ItemStack stack,
            int[] rect, double scale) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        try {
            pose.translate(rect[0], rect[1], 0.0);
            pose.scale((float) scale, (float) scale, 1.0f);
            gui.renderItem(stack, 2, 2);
            gui.renderItemDecorations(mc.font, stack, 2, 2);
        } finally {
            pose.popPose();
        }
    }

    /** The vanilla selection frame around one cell, honouring orientation. */
    private static void drawSelectionFrame(GuiGraphics gui, BarLayout layout, int[] rect) {
        int[] frame = selectionFrameRect(rect, layout.horizontal(), layout.scale());
        if (frame[4] == 0) {
            gui.blitSprite(VANILLA_SELECTION, frame[0], frame[1], frame[2], frame[3]);
        } else {
            blitTurned(gui, VANILLA_SELECTION, frame[0], frame[1], frame[2], frame[3], frame[4]);
        }
    }

    /**
     * The frame's blit args {@code (x, y, nativeW, nativeH, rotationDegrees)}
     * for one slot - package-private so the window-vs-slot alignment
     * (hotbar#103) and the vertical orientation (hotbar#109) are pinned
     * without a renderer. Horizontal: rotation 0, the symmetric {@code offset}
     * centers the sprite's 4/4 side rings on the slot. Vertical: rotation
     * <b>270</b> (the shipped 90-degree turn plus the requested 180-degree
     * flip) - the flip maps the sprite's 4px TOP ring onto the frame's LEFT,
     * so the window-solve inset is that ring: solving x for the
     * <b>window</b> (never the box) keeps the 16px window where the icon
     * draws, slot + 2 scaled px, exactly like the horizontal result.
     *
     * @param rect       the slot's {@code (x, y, w, h)}
     * @param horizontal the layout orientation
     * @param scale      the layout's uniform scale
     * @return the frame's {@code (x, y, w, h, rotation)} blit args
     */
    static int[] selectionFrameRect(int[] rect, boolean horizontal, double scale) {
        int x = rect[0];
        int y = rect[1];
        int offset = (int) Math.round(2 * scale);
        int selW = (int) Math.round(VANILLA_SELECTION_WIDTH * scale);
        int selH = (int) Math.round(VANILLA_SELECTION_HEIGHT * scale);
        if (horizontal) {
            return new int[] {x - offset, y - offset, selW, selH, 0};
        }
        int leftInset = (int) Math.round(SELECTION_TOP_RING * selH / (float) VANILLA_SELECTION_HEIGHT);
        return new int[] {x + offset - leftInset, y - offset, selW, selH, 270};
    }

    /**
     * Resolves the status-cluster depth the {@code aboveStatus} anchor rests on.
     *
     * <p>Anchors other than {@code aboveStatus} never read it, and {@code FIXED}
     * never moves, so neither touches the player. {@code DYNAMIC} mirrors
     * vanilla's row bookkeeping in {@link StatusCluster} so the bar clears the
     * armor, health, hunger and oxygen rows for any heart count.</p>
     */
    private static int statusDepth(BarAnchor anchor, Player player) {
        if (anchor != BarAnchor.ABOVE_STATUS) {
            return StatusCluster.BASELINE_DEPTH;
        }
        if (HotbarClientConfig.STATUS_ANCHOR_MODE.get() == StatusAnchorMode.FIXED) {
            return StatusCluster.BASELINE_DEPTH;
        }
        int rows = StatusCluster.heartRows(
                player.getMaxHealth(), player.getHealth(), player.getAbsorptionAmount());
        boolean armor = player.getArmorValue() > 0;
        boolean oxygen = player.getAirSupply() < player.getMaxAirSupply()
                || player.isEyeInFluid(FluidTags.WATER);
        return StatusCluster.depth(rows, armor, oxygen);
    }

    /**
     * Blits the bar's background so it reads in the vanilla texture language
     * (REQ-HOT-01, hotbar#86): one complete {@code hud/hotbar} sprite per
     * {@link BarLayout#segmentCount() segment}, each at native 182x22
     * proportions - never squashed, stretched or cropped, because vanilla
     * hardcodes that size and resource packs design for it. Segments are
     * placed by the layout's grow-from-anchor rule (strip to the right of a
     * left anchor, mirrored at a right anchor, stacked as rows above a
     * bottom anchor), so a bar of 10..18 slots reads as two adjacent
     * hotbars - a partial segment's unused cells render empty - and the
     * anchor, offsets, scale and orientation move them as one unit.
     *
     * <p>A horizontal strip cannot fill a vertical box at all, so the
     * vertical path turns each sprite a quarter turn instead of scaling it
     * into the box.</p>
     */
    private static void drawBarBackground(GuiGraphics gui, BarLayout layout, int screenW, int screenH) {
        for (int segment = 0; segment < layout.segmentCount(); segment++) {
            drawBackgroundSegment(gui, layout, screenW, screenH, segment);
        }
    }

    /** One segment's sprite: native size, framed so its cells align with the grid. */
    private static void drawBackgroundSegment(GuiGraphics gui, BarLayout layout,
            int screenW, int screenH, int segment) {
        int x = layout.originX(screenW);
        int y = layout.originY(screenH);
        int[] offset = layout.segmentOffset(segment);
        // Offset by the sprite's 1px frame so its slot cells align with our grid.
        int border = Math.max(1, (int) Math.round(layout.scale()));
        int w = (int) Math.round(VANILLA_HOTBAR_WIDTH * layout.scale());
        int h = (int) Math.round(VANILLA_HOTBAR_HEIGHT * layout.scale());
        int dx = x - border + offset[0];
        int dy = y - border + offset[1];
        if (layout.horizontal()) {
            gui.blitSprite(VANILLA_HOTBAR, dx, dy, w, h);
        } else {
            blitTurned(gui, VANILLA_HOTBAR, dx, dy, w, h, 90f);
        }
    }

    /**
     * Blits a landscape sprite turned a quarter turn (or three), so it keeps
     * its native proportions instead of being squashed into a portrait box.
     *
     * <p>The sprite is drawn at {@code (nativeW, nativeH)} and turned about
     * its centre, so it occupies {@code (nativeH, nativeW)} starting at
     * {@code (dx, dy)} - any quarter turn swaps width and height the same
     * way, so the footprint is identical for 90 and 270 degrees; only the
     * content's orientation differs.</p>
     *
     * <p>90 degrees (the bar background) maps +X to +Y, and GUI +Y points
     * down, so the sprite's left cap lands at the top: cell 1 under slot 0,
     * at the top of a vertical bar just as at the left of a horizontal one.
     * 270 degrees (the selection frame - 90 plus hotbar#109's 180-degree
     * flip) presents the sprite the other way round.</p>
     */
    private static void blitTurned(
            GuiGraphics gui, ResourceLocation sprite, int dx, int dy, int nativeW, int nativeH,
            float degrees) {
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

    /** Draws one slot: icon/placeholder, overlays, and the vanilla selection frame. */
    private static void drawSlot(GuiGraphics gui, SlotRender slot, int[] rect, BarLayout layout) {
        int x = rect[0];
        int y = rect[1];
        int w = rect[2];
        int h = rect[3];

        // Inert slots keep a diagnostic red tint over the vanilla frame.
        if (slot.inert()) {
            gui.fill(x, y, x + w, y + h, INERT_COLOR);
        }

        // Vanilla draws the selection frame BEFORE the item (Gui.renderItemHotbar
        // blits HOTBAR_SELECTION, then the item loop), so a pack-replaced frame
        // cannot cover the slot content. Match that order here.
        if (slot.selected()) {
            drawSelectionFrame(gui, layout, rect);
        }

        // Icon on top of the selection frame (as vanilla layers items over it).
        if (slot.assigned() && !slot.inert() && HotbarClientConfig.SHOW_ICONS.get()) {
            drawIcon(gui, slot.iconId(), x, y, w, h);
        }

        drawOverlays(gui, slot, x, y, w, h);
    }

    /** Draws the state overlays: cooldown, unavailable tint, cost badge. */
    private static void drawOverlays(GuiGraphics gui, SlotRender slot, int x, int y, int w, int h) {
        // Cooldown overlay (known progress only; unknown draws nothing).
        if (slot.hasCooldownOverlay() && HotbarClientConfig.SHOW_COOLDOWNS.get()) {
            Double cooldown = slot.cooldownFraction();
            if (cooldown != null) {
                drawCooldown(gui, cooldown, x, y, w, h);
            }
        }

        // Unavailable verdict tint (never applied to unknown - no wrong value).
        if (!slot.availability().isAvailable()
                && slot.availability() instanceof org.bitwisemadness.abilitycore.api.AvailabilityState.Unavailable) {
            gui.fill(x, y, x + w, y + h, UNAVAILABLE_TINT);
        }

        // Cost badge (known cost only).
        if (slot.hasCostBadge() && HotbarClientConfig.SHOW_COSTS.get()) {
            gui.fill(x, y + h - 4, x + w, y + h, COST_BG);
        }
    }

    private static void drawIcon(GuiGraphics gui, ResourceLocation icon, int x, int y, int w, int h) {
        if (icon == null) {
            // No icon resolves: keep the neutral background (placeholder), no crash.
            return;
        }
        int pad = 2;
        gui.blit(icon, x + pad, y + pad, 0, 0, w - pad * 2, h - pad * 2, w - pad * 2, h - pad * 2);
    }

    private static void drawCooldown(GuiGraphics gui, double progress, int x, int y, int w, int h) {
        // Darken from the top by (1 - progress): 0 progress = fully covered, 1 = clear.
        int covered = (int) Math.round((1.0 - progress) * h);
        if (covered > 0) {
            gui.fill(x, y, x + w, y + covered, COOLDOWN_COLOR);
        }
    }
}
