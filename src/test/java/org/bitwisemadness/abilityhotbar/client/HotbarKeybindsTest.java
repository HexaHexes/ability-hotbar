package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * The direct-activation key family (REQ-HOT-11, hotbar#86 requirement 6):
 * eighteen bindings - slots 1..9 default to Ctrl+1..9, slots 10..18 ship
 * with no default value (rebindable, claiming no key) so raising the slot
 * ceiling never invents a chord or shadows a vanilla key.
 *
 * <p>Constructing the mappings is exactly what the Controls screen does, so
 * this fixture also proves the family loads headless.</p>
 */
class HotbarKeybindsTest {

    private static final int SLOTS = 18;

    @Test
    void familyCoversAllEighteenSlots() {
        assertEquals(SLOTS, HotbarKeybinds.ACTIVATE_SLOT.length,
                "one binding per slot up to the new ceiling");
        for (int i = 0; i < SLOTS; i++) {
            assertNotNull(HotbarKeybinds.ACTIVATE_SLOT[i], "slot " + (i + 1) + " must have a binding");
            assertEquals("key.abilityhotbar.activate_slot_" + (i + 1),
                    HotbarKeybinds.ACTIVATE_SLOT[i].getName(),
                    "slot " + (i + 1) + " must carry its own translation key");
        }
    }

    @Test
    void slotsOneToNineKeepTheCtrlDigitDefaults() {
        for (int i = 0; i < 9; i++) {
            var mapping = HotbarKeybinds.ACTIVATE_SLOT[i];
            assertEquals(org.lwjgl.glfw.GLFW.GLFW_KEY_1 + i, mapping.getKey().getValue(),
                    "slot " + (i + 1) + " must default to digit " + (i + 1));
            assertEquals(net.neoforged.neoforge.client.settings.KeyModifier.CONTROL,
                    mapping.getKeyModifier(),
                    "slot " + (i + 1) + " must stay in the Ctrl family");
        }
    }

    @Test
    void slotsTenToEighteenShipUnbound() {
        for (int i = 9; i < SLOTS; i++) {
            var mapping = HotbarKeybinds.ACTIVATE_SLOT[i];
            assertEquals(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN, mapping.getKey().getValue(),
                    "slot " + (i + 1) + " must claim no default key (#86)");
        }
    }
}
