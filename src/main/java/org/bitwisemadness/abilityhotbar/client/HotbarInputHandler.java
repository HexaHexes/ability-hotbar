package org.bitwisemadness.abilityhotbar.client;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
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
 * <p><b>Press and release travel together</b> (core#159): every press
 * records the hold its key carries, and the falling edge of
 * {@code KeyMapping.isDown()} sends the matching release through core -
 * the key-up scan runs <em>before and regardless of</em> {@link InputGate},
 * because an up-edge arriving while a screen opens must still end the hold
 * (a missed key-up would strand it server-side until ARR's max-held-time).
 * Native ARR keys release through the same isDown edge, so the pair timing
 * is native parity; stateless presses (empty slots, no core) record nothing
 * and release nothing.</p>
 *
 * <p>Registered for the physical client only.</p>
 */
@EventBusSubscriber(modid = AbilityHotbar.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class HotbarInputHandler {

    private HotbarInputHandler() {
    }

    /**
     * One in-flight hold: exactly what a key's press sent, so its key-up
     * can mirror the same intent back (core#159). The record equals its
     * own fields, so fixtures can pin the flush semantics directly.
     */
    record HeldActivation(ResourceLocation abilityId, ActivationSource source, int slot,
            String mode) {
    }

    /** Pseudo-key index for {@link HotbarKeybinds#ACTIVATE_SELECTED}. */
    static final int SELECTED_KEY = HotbarKeybinds.ACTIVATE_SLOT.length;

    /** One hold record per activation key; null = nothing in flight. */
    private static final HeldActivation[] HOLDS =
            new HeldActivation[SELECTED_KEY + 1];

    /**
     * Records the hold behind a press and returns any hold the same key
     * still carried. A second press drained in the same tick (a multi-tap
     * that queued two clicks) therefore flushes the displaced one as a
     * release first, so press/release pairs stay 1:1 on the wire exactly
     * as native ARR's key edges produce them.
     *
     * @param key        the activation key index ({@link #SELECTED_KEY} for G)
     * @param activation what this press sent
     * @return the displaced prior hold to release first, or null
     */
    static HeldActivation recordHold(int key, HeldActivation activation) {
        HeldActivation displaced = HOLDS[key];
        HOLDS[key] = activation;
        return displaced;
    }

    /**
     * Claims a key's hold for release: exactly-once (the second read finds
     * none), so a duplicated key-up never sends a second release.
     */
    static HeldActivation claimHold(int key) {
        HeldActivation held = HOLDS[key];
        HOLDS[key] = null;
        return held;
    }

    /** Drops every hold without sending (no connection to notify). */
    static void clearHolds() {
        java.util.Arrays.fill(HOLDS, null);
    }

    /**
     * Falling-edge release: for every recorded hold whose key is no longer
     * down, send the mirror-image release through core. Runs first and
     * ungated in {@link #onClientTick} - the up-edge is valid even with a
     * screen open (chat mid-hold), and a lost key-up is a stranded hold.
     * With no player there is nobody to notify (the server clears its own
     * holds on quit), so the records simply drop.
     */
    private static void releaseHeldKeys() {
        if (Minecraft.getInstance().player == null) {
            clearHolds();
            return;
        }
        for (int key = 0; key < HOLDS.length; key++) {
            HeldActivation held = HOLDS[key];
            if (held == null || keyStillDown(key)) {
                continue;
            }
            sendRelease(claimHold(key));
        }
    }

    /** The key currently down for a hold index (the G key is its own index). */
    private static boolean keyStillDown(int key) {
        return key == SELECTED_KEY
                ? HotbarKeybinds.ACTIVATE_SELECTED.isDown()
                : HotbarKeybinds.ACTIVATE_SLOT[key].isDown();
    }

    /** One release through core, gated exactly like its press. */
    private static void sendRelease(HeldActivation held) {
        if (held != null && CoreActivationCompat.isPresent()) {
            CoreActivationCompat.release(held.abilityId(), held.source(), held.slot(), held.mode());
        }
    }

    /**
     * Ctrl+wheel selects a slot, Alt+wheel cycles bars, plain wheel stays
     * vanilla. When both modifiers are held Ctrl wins - the shipped slot
     * behaviour is never displaced by the later bar control.
     *
     * <p><b>{@code HIGHEST} is load-bearing (hotbar#106):</b> architectury's
     * NeoForge bridge ({@code EventHandlerImplClient.eventInputEvent},
     * priority {@code HIGH}, {@code receiveCanceled} default false) forwards
     * this event to {@code ClientRawInputEvent.MOUSE_SCROLLED}, where Tensura:
     * Reincarnated's ability-preset scrolling listens
     * ({@code PlayerInputHandler.init}). Cancelling here at {@code HIGHEST}
     * claims the modifier chords before that bridge runs - at the old
     * {@code NORMAL} the bridge fired first and one notch drove both features
     * (the "preset 1..9" notifications). Plain wheel never cancels, so
     * non-overlapping chords elsewhere still work. Fixture-pinned by
     * {@code ScrollOwnershipTest}.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
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

    /** Rising-edge dispatch, falling-edge release (one tap = press + one key-up). */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // The key-up half first, ungated: an up-edge with a screen open
        // (chat opened mid-hold) must still end the hold - see
        // releaseHeldKeys. REQ-HOT-18's gate applies to presses only.
        releaseHeldKeys();
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
        int drawn = HotbarConfig.effectiveRenderCount(state.assignments().length);
        for (int i = 0; i < HotbarKeybinds.ACTIVATE_SLOT.length; i++) {
            while (HotbarKeybinds.ACTIVATE_SLOT[i].consumeClick()) {
                // vanilla parity (hotbar#108): the digit parks the cursor on
                // its slot too - two independent intents over one key press
                digitSelection(i, state.selectedSlot(), drawn)
                        .ifPresent(payload -> PacketDistributor.sendToServer(payload));
                activate(state, i, ActivationSource.DIRECT_SLOT, i);
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
            activate(state, state.selectedSlot(), ActivationSource.SELECTED_SLOT, SELECTED_KEY);
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
     * Ctrl+digit's selection half (hotbar#108): the request that parks the
     * cursor on the digit's slot, through the same server-authoritative path
     * Ctrl+wheel uses ({@code SelectSlotPayload} - the server fail-closes an
     * out-of-range index exactly as before). Empty when the slot is not
     * drawn this frame (vanilla parity: digits past the bar are inert) or
     * already selected (no redundant packet). Config-free - the caller
     * passes the drawn count, mirroring the scroll path's clamp.
     *
     * @param index        the digit's zero-based slot index
     * @param selectedSlot the cursor's current slot
     * @param renderCount  how many slots the bar draws this frame
     * @return the selection request, or empty for a no-op press
     */
    static Optional<SelectSlotPayload> digitSelection(
            int index, int selectedSlot, int renderCount) {
        if (index < 0 || index >= renderCount || index == selectedSlot) {
            return Optional.empty();
        }
        return Optional.of(new SelectSlotPayload(index));
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

    /**
     * Resolves the slot and sends the intent through core; empty slots send
     * nothing and record no hold (stateless presses release nothing). The
     * crosshair hint rides along only for {@code targeted} abilities
     * (hotbar#94) - {@link TargetHints} reads the client's crosshair and
     * fails closed to no-hint for anything else. A resolved press records
     * its hold under {@code holdKey} so the key-up can mirror it back
     * (core#159); a displaced record (multi-tap drained in one tick) is
     * released first to keep the wire 1:1.
     */
    private static void activate(BarState state, int slot, ActivationSource source, int holdKey) {
        if (!CoreActivationCompat.isPresent()) {
            return;
        }
        ActivationResolver.resolve(state, slot, source).ifPresent(a -> {
            CoreActivationCompat.activate(
                    a.abilityId(), a.source(), a.slot(),
                    TargetHints.forActivation(a.abilityId()), a.mode());
            HeldActivation displaced = recordHold(holdKey,
                    new HeldActivation(a.abilityId(), a.source(), a.slot(), a.mode()));
            sendRelease(displaced);
        });
    }
}
