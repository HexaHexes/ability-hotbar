package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.junit.jupiter.api.Test;

/**
 * Event-order ownership for the modifier scroll chords (hotbar#106): the
 * handler must cancel at {@code HIGHEST} so the cancel lands before
 * architectury's NeoForge bridge ({@code EventHandlerImplClient
 * .eventInputEvent}, priority {@code HIGH}, receiveCanceled default false)
 * forwards the notch to {@code ClientRawInputEvent.MOUSE_SCROLLED} - where
 * Tensura: Reincarnated's ability-preset scrolling listens. At {@code NORMAL}
 * the bridge ran first and one Alt+wheel notch drove both features ("preset
 * 1..9" notifications alongside bar cycling).
 *
 * <p>This pins the ordering contract that cannot be asserted from a unit
 * test any other way: the annotation IS the mechanism.</p>
 */
class ScrollOwnershipTest {

    @Test
    void modifierScrollHandlerCancelsBeforeArchitecturysBridge() throws Exception {
        Method handler = HotbarInputHandler.class.getDeclaredMethod(
                "onMouseScroll", InputEvent.MouseScrollingEvent.class);
        SubscribeEvent subscription = handler.getAnnotation(SubscribeEvent.class);

        assertNotNull(subscription, "onMouseScroll must stay an event subscriber");
        assertEquals(EventPriority.HIGHEST, subscription.priority(),
                "HIGHEST is load-bearing: cancelling after architectury's HIGH bridge "
                        + "lets Tensura's preset scrolling fire on the same notch (hotbar#106)");
    }
}
