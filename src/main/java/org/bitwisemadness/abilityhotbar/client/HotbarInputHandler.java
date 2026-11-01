package org.bitwisemadness.abilityhotbar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.compat.CoreActivationCompat;
import org.bitwisemadness.abilityhotbar.network.SelectSlotPayload;
import org.bitwisemadness.abilityhotbar.state.ActivationResolver;
import org.bitwisemadness.abilityhotbar.state.BarSelection;
import org.bitwisemadness.abilityhotbar.state.BarState;

/**
 * Client input routing for the hotbar (REQ-HOT-10/11/17).
 *
 * <p>Ctrl+wheel moves the selected slot without touching the vanilla hotbar
 * (the scroll is consumed only while Ctrl is held, so plain wheel stays 100%
 * vanilla and there is no double-scroll). The activation keys resolve a target
 * slot against the synced bar state and route the intent to the server through
 * core - empty slots resolve to nothing, so no request is sent (REQ-HOT-19).
 * Activation never changes the selected slot (REQ-HOT-20).</p>
 *
 * <p>Registered for the physical client only.</p>
 */
@EventBusSubscriber(modid = AbilityHotbar.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class HotbarInputHandler {

    private HotbarInputHandler() {
    }

    /** Ctrl+wheel: claim the scroll for slot selection; leave plain wheel to vanilla. */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        // REQ-HOT-18: never act over a screen, and only while Ctrl is held. NeoForge
        // already gates this event behind "no screen + player", but the invariant is
        // ours to state explicitly rather than lean on - see InputGate.
        if (!InputGate.isInputCapturable(Screen.hasControlDown())) {
            return;
        }
        BarState state = localState();
        if (state == null || event.getScrollDeltaY() == 0.0) {
            return;
        }
        int direction = event.getScrollDeltaY() > 0 ? 1 : -1;
        int next = BarSelection.slotAfterScroll(state.selectedSlot(), direction, state.assignments().length);
        PacketDistributor.sendToServer(new SelectSlotPayload(next));
        event.setCanceled(true);
    }

    /** Rising-edge key dispatch: one consumeClick() per tap. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // REQ-HOT-18: keys only act with no screen open (chat/inventory/GUI). Vanilla
        // normally suppresses in-game keybinds while a screen is up, but the invariant
        // is ours to state explicitly - a screen must never see an intercepted key.
        if (!InputGate.isInputCapturable()) {
            return;
        }
        BarState state = localState();
        if (state == null) {
            return;
        }
        for (int i = 0; i < HotbarKeybinds.ACTIVATE_SLOT.length; i++) {
            while (HotbarKeybinds.ACTIVATE_SLOT[i].consumeClick()) {
                activate(state, i, ActivationSource.DIRECT_SLOT);
            }
        }
        while (HotbarKeybinds.ACTIVATE_SELECTED.consumeClick()) {
            activate(state, state.selectedSlot(), ActivationSource.SELECTED_SLOT);
        }
        while (HotbarKeybinds.OPEN_MENU.consumeClick()) {
            AbilityMenu.open();
        }
        while (HotbarKeybinds.ABILITY_MODE.consumeClick()) {
            AbilityMode.toggle();
        }
    }

    /** The client's synced bar state, or null when no player/snapshot is present. */
    private static BarState localState() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : ClientBarState.get();
    }

    /** Resolves the slot and sends the intent through core; empty slots send nothing. */
    private static void activate(BarState state, int slot, ActivationSource source) {
        if (!CoreActivationCompat.isPresent()) {
            return;
        }
        ActivationResolver.resolve(state, slot, source)
                .ifPresent(a -> CoreActivationCompat.activate(a.abilityId(), a.source(), a.slot()));
    }
}
