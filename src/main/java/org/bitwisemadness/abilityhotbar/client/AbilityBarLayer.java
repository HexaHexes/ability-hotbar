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
import org.bitwisemadness.abilityhotbar.state.BarState;

/**
 * The nine-slot ability bar's render layer (REQ-HOT-01/02/07).
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

        // The attachment is sized to the server ceiling, but the client renders only
        // the first effectiveRenderCount() entries (server maxSlots clamps the client
        // preference, REQ-CFG-02). Saved state may exceed the ceiling, so clamp here.
        int renderCount = HotbarConfig.effectiveRenderCount(state.assignments().length);
        if (renderCount < 1) {
            return;
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

        int screenW = gui.guiWidth();
        int screenH = gui.guiHeight();
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
     * Blits the vanilla hotbar sprite as the bar's background so the bar reads in
     * the vanilla texture language (REQ-HOT-01). The sprite is drawn at its native
     * 182x22 (x scale) proportions - never squashed to the layout's slot box -
     * because vanilla hardcodes that size and resource packs design for it;
     * stretching distorted the art and shifted our slots off the texture's frames.
     *
     * <p>A horizontal strip cannot fill a vertical box at all, so the vertical
     * path turns it a quarter turn instead of scaling it into the box.</p>
     */
    private static void drawBarBackground(GuiGraphics gui, BarLayout layout, int screenW, int screenH) {
        int x = layout.originX(screenW);
        int y = layout.originY(screenH);
        // Offset by the sprite's 1px frame so its slot cells align with our grid.
        int border = Math.max(1, (int) Math.round(layout.scale()));
        int w = (int) Math.round(VANILLA_HOTBAR_WIDTH * layout.scale());
        int h = (int) Math.round(VANILLA_HOTBAR_HEIGHT * layout.scale());
        if (layout.horizontal()) {
            gui.blitSprite(VANILLA_HOTBAR, x - border, y - border, w, h);
        } else {
            blitTurned90(gui, VANILLA_HOTBAR, x - border, y - border, w, h);
        }
    }

    /**
     * Blits a landscape sprite turned a quarter turn clockwise, so it keeps its
     * native proportions instead of being squashed into a portrait box.
     *
     * <p>The sprite is drawn at {@code (nativeW, nativeH)} and turned about its
     * centre, so it occupies {@code (nativeH, nativeW)} starting at
     * {@code (dx, dy)} - the target's width comes from the sprite's height and
     * its height from the sprite's width.</p>
     *
     * <p>A quarter turn maps +X to +Y, and GUI +Y points down, so the sprite's
     * left cap lands at the top. That puts cell 1 under slot 0, which sits at the
     * top of a vertical bar just as it sits at the left of a horizontal one.</p>
     */
    private static void blitTurned90(
            GuiGraphics gui, ResourceLocation sprite, int dx, int dy, int nativeW, int nativeH) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        try {
            // Centre of the box the sprite fills once turned: a quarter turn
            // swaps its width and height, so anchor on the swapped half-extents.
            pose.translate(dx + nativeH / 2.0, dy + nativeW / 2.0, 0.0);
            pose.mulPose(Axis.ZP.rotationDegrees(90));
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
        double scale = layout.scale();

        // Inert slots keep a diagnostic red tint over the vanilla frame.
        if (slot.inert()) {
            gui.fill(x, y, x + w, y + h, INERT_COLOR);
        }

        // Vanilla draws the selection frame BEFORE the item (Gui.renderItemHotbar
        // blits HOTBAR_SELECTION, then the item loop), so a pack-replaced frame
        // cannot cover the slot content. Match that order here.
        if (slot.selected()) {
            int offset = (int) Math.round(2 * scale);
            int selW = (int) Math.round(VANILLA_SELECTION_WIDTH * scale);
            int selH = (int) Math.round(VANILLA_SELECTION_HEIGHT * scale);
            if (layout.horizontal()) {
                gui.blitSprite(VANILLA_SELECTION, x - offset, y - offset, selW, selH);
            } else {
                blitTurned90(gui, VANILLA_SELECTION, x - offset, y - offset, selW, selH);
            }
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
            drawCooldown(gui, slot.cooldownFraction(), x, y, w, h);
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
