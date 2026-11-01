package org.bitwisemadness.abilityhotbar.client;

import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.bitwisemadness.abilitycore.api.AvailabilityProviders;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.config.HotbarClientConfig;
import org.bitwisemadness.abilityhotbar.config.HotbarConfig;
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
        // the first effectiveSlotsPerBar() entries (server maxSlots clamps the client
        // preference, REQ-CFG-02). Saved state may exceed the ceiling, so clamp here.
        int renderCount = Math.min(HotbarConfig.effectiveSlotsPerBar(), state.assignments().length);
        if (renderCount < 1) {
            return;
        }

        BarLayout layout = new BarLayout(
                renderCount,
                20,
                0,
                HotbarClientConfig.HORIZONTAL.get(),
                HotbarClientConfig.OFFSET_X.get(),
                HotbarClientConfig.OFFSET_Y.get(),
                HotbarClientConfig.SCALE.get());

        List<SlotRender> slots = SlotRender.resolveAll(
                state,
                id -> AvailabilityProviders.availabilityFor(mc.player, id),
                AbilityDiscovery::get);

        int screenW = gui.guiWidth();
        int screenH = gui.guiHeight();
        drawBarBackground(gui, layout, screenW, screenH);
        for (int i = 0; i < renderCount && i < slots.size(); i++) {
            SlotRender slot = slots.get(i);
            int[] rect = layout.slotRect(i, screenW, screenH);
            drawSlot(gui, slot, rect, layout.scale());
        }
    }

    /**
     * Blits the vanilla hotbar sprite as the bar's background so the bar reads in
     * the vanilla texture language (REQ-HOT-01). The sprite is drawn at its native
     * 182x22 (x scale) proportions - never stretched to the layout's slot box -
     * because vanilla hardcodes that size and resource packs design for it;
     * stretching distorted the art and shifted our slots off the texture's frames.
     */
    private static void drawBarBackground(GuiGraphics gui, BarLayout layout, int screenW, int screenH) {
        boolean horizontal = layout.horizontal();
        int x = layout.originX(screenW);
        int y = layout.originY(screenH);
        if (horizontal) {
            // Offset by the sprite's 1px frame so its slot cells align with our grid.
            int border = Math.max(1, (int) Math.round(layout.scale()));
            int w = (int) Math.round(VANILLA_HOTBAR_WIDTH * layout.scale());
            int h = (int) Math.round(VANILLA_HOTBAR_HEIGHT * layout.scale());
            gui.blitSprite(VANILLA_HOTBAR, x - border, y - border, w, h);
        } else {
            // The strip is a fixed horizontal 9-slot asset; it cannot represent a
            // vertical bar without distortion (known REQ-HOT-01 limitation).
            gui.blitSprite(VANILLA_HOTBAR, x, y, layout.scaledSlotSize(), layout.extent());
        }
    }

    /** Draws one slot: icon/placeholder, overlays, and the vanilla selection frame. */
    private static void drawSlot(GuiGraphics gui, SlotRender slot, int[] rect, double scale) {
        int x = rect[0];
        int y = rect[1];
        int w = rect[2];
        int h = rect[3];

        // Inert slots keep a diagnostic red tint over the vanilla frame.
        if (slot.inert()) {
            gui.fill(x, y, x + w, y + h, INERT_COLOR);
        }

        // Icon (only when assigned, not inert, and one resolves), else placeholder.
        if (slot.assigned() && !slot.inert() && HotbarClientConfig.SHOW_ICONS.get()) {
            drawIcon(gui, slot.iconId(), x, y, w, h);
        }

        drawOverlays(gui, slot, x, y, w, h);

        // Vanilla selection frame at its native 24x23 (x scale), offset to frame the
        // cell exactly as vanilla does (2px up-left) - not stretched to cell + 2.
        if (slot.selected()) {
            int offset = (int) Math.round(2 * scale);
            int selW = (int) Math.round(VANILLA_SELECTION_WIDTH * scale);
            int selH = (int) Math.round(VANILLA_SELECTION_HEIGHT * scale);
            gui.blitSprite(VANILLA_SELECTION, x - offset, y - offset, selW, selH);
        }
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
