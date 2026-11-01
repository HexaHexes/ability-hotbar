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
import org.bitwisemadness.abilityhotbar.state.BarState;

/**
 * The nine-slot ability bar's render layer (REQ-HOT-01/02/07).
 *
 * <p>A vanilla-language {@link LayeredDraw.Layer} that reads the synced bar
 * state ({@link ClientBarState}), resolves each slot's pure {@link SlotRender}
 * decision (icons, selection, cooldown/availability overlay, inert
 * diagnostics), and blits it using {@link BarLayout}'s rectangles. Every display
 * branch lives in {@link SlotRender} (unit-tested); this layer only performs
 * the GPU draws.</p>
 *
 * <p>Client-only, registered on the GUI-layer bus. When no snapshot has synced
 * yet it draws nothing. The actual on-screen appearance is validated by the
 * manual render checklist (client HUD cannot be proven headless - AGENTS).</p>
 */
@EventBusSubscriber(modid = AbilityHotbar.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AbilityBarLayer implements LayeredDraw.Layer {

    /** Placeholder marker: a resolved-but-null icon draws this neutral slot. */
    private static final int EMPTY_COLOR = 0x88000000;
    private static final int SLOT_BORDER = 0xFF3C3C3C;
    private static final int SELECTED_BORDER = 0xFFFFD700;
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

        BarLayout layout = new BarLayout(
                state.assignments().length,
                20,
                2,
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
        for (int i = 0; i < slots.size(); i++) {
            SlotRender slot = slots.get(i);
            int[] rect = layout.slotRect(i, screenW, screenH);
            drawSlot(gui, slot, rect);
        }
    }

    /** Draws one slot: background, icon/placeholder, overlays, selection. */
    private static void drawSlot(GuiGraphics gui, SlotRender slot, int[] rect) {
        int x = rect[0];
        int y = rect[1];
        int w = rect[2];
        int h = rect[3];

        // Background: empty/placeholder neutral, inert tinted red.
        int bg = slot.inert() ? INERT_COLOR : EMPTY_COLOR;
        gui.fill(x, y, x + w, y + h, bg);

        // Icon (only when assigned, not inert, and one resolves), else placeholder.
        if (slot.assigned() && !slot.inert() && HotbarClientConfig.SHOW_ICONS.get()) {
            drawIcon(gui, slot.iconId(), x, y, w, h);
        }

        drawOverlays(gui, slot, x, y, w, h);

        // Border: gold for the selected slot, neutral otherwise.
        int border = slot.selected() ? SELECTED_BORDER : SLOT_BORDER;
        drawBorder(gui, x, y, w, h, border);
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

    private static void drawBorder(GuiGraphics gui, int x, int y, int w, int h, int color) {
        gui.fill(x, y, x + w, y + 1, color);
        gui.fill(x, y + h - 1, x + w, y + h, color);
        gui.fill(x, y, x + 1, y + h, color);
        gui.fill(x + w - 1, y, x + w, y + h, color);
    }
}
