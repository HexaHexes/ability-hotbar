package org.bitwisemadness.abilityhotbar.client;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.compat.CoreActivationCompat;
import org.bitwisemadness.abilityhotbar.config.HotbarClientConfig;
import org.bitwisemadness.abilityhotbar.config.HotbarConfig;
import org.bitwisemadness.abilityhotbar.network.SelectSlotPayload;
import org.bitwisemadness.abilityhotbar.network.SwitchBarPayload;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistration;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.state.ActivationResolver;
import org.bitwisemadness.abilityhotbar.state.BarCycling;
import org.bitwisemadness.abilityhotbar.state.BarSelection;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarSwitching;

/**
 * Client input routing for the hotbar (REQ-HOT-10/11/17, REQ-HOT-22).
 *
 * <p>Ctrl+wheel moves the selected slot without touching the vanilla hotbar
 * (the scroll is consumed only while Ctrl is held, so plain wheel stays 100%
 * vanilla and there is no double-scroll); Alt+wheel cycles bars the same way,
 * with Ctrl winning when both modifiers are held. Alt+1..9/0 switches directly
 * to a bar, fail-closed past the cycle set. The activation keys resolve a
 * target slot against the synced bar state and route the intent to the server
 * through core - empty slots resolve to nothing, so no request is sent
 * (REQ-HOT-19). Activation never changes the selected slot (REQ-HOT-20), and
 * switching bars never activates anything (the switch runs through
 * {@link BarSwitching}, which never touches activation).</p>
 *
 * <p>A switch is applied optimistically on the client (so the HUD moves
 * without a round trip) and the server's echo reconciles it - see
 * {@link #switchBar}.</p>
 *
 * <p>Registered for the physical client only.</p>
 */
@EventBusSubscriber(modid = AbilityHotbar.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class HotbarInputHandler {

    private HotbarInputHandler() {
    }

    /**
     * Ctrl+wheel selects a slot, Alt+wheel cycles bars, plain wheel stays
     * vanilla. When both modifiers are held Ctrl wins - the shipped slot
     * behaviour is never displaced by the later bar control.
     */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        // REQ-HOT-18: never act over a screen, and only while a modifier this
        // handler owns is held - plain wheel must stay 100% vanilla. NeoForge
        // gates this event too, but the invariant is ours - see InputGate.
        boolean slotModifier = Screen.hasControlDown();
        boolean barModifier = Screen.hasAltDown();
        if (!InputGate.isInputCapturable(slotModifier || barModifier)) {
            return;
        }
        BarState state = localState();
        if (state == null || event.getScrollDeltaY() == 0.0) {
            return;
        }
        if (slotModifier) {
            selectSlotByScroll(state, event);
            return;
        }
        cycleBarByScroll(state, event);
    }

    /** Ctrl+wheel: move the selected slot within the range the bar draws (REQ-HOT-10). */
    private static void selectSlotByScroll(BarState state, InputEvent.MouseScrollingEvent event) {
        // The attachment may hold up to the server ceiling, but only the first
        // effectiveRenderCount() slots are drawn - scrolling must never reach an
        // undrawn slot (the renderer uses the same clamp).
        int slotCount = HotbarConfig.effectiveRenderCount(state.assignments().length);
        if (slotCount < 1) {
            return;
        }
        // Vanilla parity (tested in BarSelectionTest): scroll up -> left, down -> right.
        int direction = BarSelection.scrollDirection(event.getScrollDeltaY());
        if (direction == 0) {
            return;
        }
        int next = BarSelection.slotAfterScroll(state.selectedSlot(), direction, slotCount);
        PacketDistributor.sendToServer(new SelectSlotPayload(next));
        event.setCanceled(true);
    }

    /**
     * Alt+wheel: cycle bars prev/next - skipping empty bars when
     * {@code display.skipEmptyBars} is on (REQ-HOT-22). The scroll is claimed
     * either way: without the cancel vanilla would ALSO move its own hotbar
     * slot alongside the bar switch (or alongside our no-op).
     */
    private static void cycleBarByScroll(BarState state, InputEvent.MouseScrollingEvent event) {
        event.setCanceled(true);
        int direction = BarSelection.scrollDirection(event.getScrollDeltaY());
        int window = HotbarConfig.effectiveHotbarCount();
        List<HotbarRegistration> registrations = HotbarRegistry.all();
        Optional<ResourceLocation> target = HotbarClientConfig.SKIP_EMPTY_BARS.get()
                ? BarCycling.stepSkippingEmpty(state.activeBarId(), direction, window,
                        registrations, BarCycling.emptyBars(state))
                : BarCycling.step(state.activeBarId(), direction, window, registrations);
        target.ifPresent(id -> switchBar(state, id));
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
        // Alt+1..9/0: direct bar selection. Beyond the cycle set the request
        // resolves to nothing and is ignored fail-closed (REQ-HOT-22).
        for (int i = 0; i < HotbarKeybinds.SWITCH_BAR.length; i++) {
            while (HotbarKeybinds.SWITCH_BAR[i].consumeClick()) {
                BarCycling.direct(i, HotbarConfig.effectiveHotbarCount(), HotbarRegistry.all())
                        .ifPresent(target -> switchBar(state, target));
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

    /**
     * Applies a bar switch optimistically on the client, then sends the
     * intent. The optimistic view runs through the same {@link BarSwitching}
     * rule the server applies (registry + ceiling, reconcile + growth), so it
     * can only land where the server would accept; the server's echo -
     * persisting on accept, current state on reject - reconciles the view
     * within one round trip if it disagreed. The switch toast is recorded
     * here, at the intent, so a rejection never double-toasts on the way back.
     */
    private static void switchBar(BarState state, ResourceLocation target) {
        BarSwitching.switchTo(state, target, HotbarConfig.effectiveHotbarCount())
                .ifPresent(optimistic -> {
                    ClientBarState.set(optimistic);
                    if (HotbarClientConfig.SWITCH_TOAST.get()) {
                        SwitchToast.show(target);
                    }
                    PacketDistributor.sendToServer(new SwitchBarPayload(target));
                });
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
