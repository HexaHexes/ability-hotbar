package org.bitwisemadness.abilityhotbar.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.lwjgl.glfw.GLFW;

/**
 * The hotbar's rebindable keybindings (REQ-HOT-10/11/17, SPIKE-hot-3).
 *
 * <p>Twenty-two bound defaults on the mod's own category - never
 * {@code key.irons_spellbooks.*}: Ctrl+1..9 activates a slot directly,
 * Alt+1..9 and Alt+0 switch bars directly, G activates the selected slot,
 * the grave accent (left of 1) opens the ability menu, and Right Ctrl
 * toggles ability mode - plus nine deliberately <b>unbound</b>
 * slot-activation bindings for slots 10..18 (hotbar#86: no default value,
 * rebindable, claiming no key). Ctrl+wheel (slot) and Alt+wheel (bar)
 * selection are not KeyMappings (the wheel has no Controls entry) and are
 * routed from {@link HotbarInputHandler}.</p>
 *
 * <p>The Ctrl+number and Alt+number binds use {@link KeyModifier#CONTROL} /
 * {@link KeyModifier#ALT}, so NeoForge claims the digit only while that
 * modifier is held and leaves the vanilla hotbar's plain 1..9/0 untouched -
 * the vanilla-digit suppression the controls spec requires. Like the Ctrl
 * binds, either side of the modifier activates a bind (NeoForge's modifier
 * matching is side-agnostic; Left Alt/Left Ctrl are the primary keys).
 * Registered for the physical client only, so a dedicated server never loads
 * this class.</p>
 */
@EventBusSubscriber(modid = AbilityHotbar.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HotbarKeybinds {

    /** Own keybinding category - shown as its own group in the Controls screen. */
    public static final String CATEGORY = "key.categories.abilityhotbar";

    /**
     * Ctrl+1..9: activate the corresponding slot directly (REQ-HOT-11).
     * Package-private: a public mutable array would let any class rewrite it.
     *
     * <p>Slots 10..18 (reachable once {@code hotbar.maxSlots} allows more
     * than nine, hotbar#86) use the same bindings with <b>no default value</b>
     * - {@code GLFW_KEY_UNKNOWN} claims no key until the player binds one in
     * the Controls screen; the Ctrl modifier stays as the family default so a
     * user-assigned key behaves like the rest.</p>
     */
    static final KeyMapping[] ACTIVATE_SLOT = new KeyMapping[18];

    /**
     * Alt+1..9, Alt+0: switch to the corresponding bar directly - index 0..8
     * for digits 1..9, index 9 for the 0 key (bar 10) (REQ-HOT-22).
     * Package-private for the same reason as {@link #ACTIVATE_SLOT}.
     */
    static final KeyMapping[] SWITCH_BAR = new KeyMapping[10];

    /** G: activate the currently selected slot (REQ-HOT-11). */
    public static final KeyMapping ACTIVATE_SELECTED;

    /** Grave accent (left of 1): open the ability management menu (hotbar#27). */
    public static final KeyMapping OPEN_MENU;

    /** Right Ctrl: toggle ability mode (flow owned by hotbar#17). */
    public static final KeyMapping ABILITY_MODE;

    static {
        for (int i = 0; i < ACTIVATE_SLOT.length; i++) {
            // Slots 1..9 default to Ctrl+1..9; slots 10..18 ship unbound
            // (GLFW_KEY_UNKNOWN) - rebindable, claiming no default key (#86).
            int key = i < 9 ? GLFW.GLFW_KEY_1 + i : GLFW.GLFW_KEY_UNKNOWN;
            ACTIVATE_SLOT[i] = new KeyMapping(
                    "key.abilityhotbar.activate_slot_" + (i + 1),
                    KeyConflictContext.IN_GAME,
                    KeyModifier.CONTROL,
                    InputConstants.Type.KEYSYM,
                    key,
                    CATEGORY);
        }
        for (int i = 0; i < SWITCH_BAR.length; i++) {
            // digits 1..9 then 0: GLFW_KEY_1..9 are consecutive, GLFW_KEY_0 is not
            int key = i < 9 ? GLFW.GLFW_KEY_1 + i : GLFW.GLFW_KEY_0;
            SWITCH_BAR[i] = new KeyMapping(
                    "key.abilityhotbar.switch_bar_" + (i + 1),
                    KeyConflictContext.IN_GAME,
                    KeyModifier.ALT,
                    InputConstants.Type.KEYSYM,
                    key,
                    CATEGORY);
        }
        ACTIVATE_SELECTED = new KeyMapping(
                "key.abilityhotbar.activate_selected",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                CATEGORY);
        OPEN_MENU = new KeyMapping(
                "key.abilityhotbar.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                CATEGORY);
        ABILITY_MODE = new KeyMapping(
                "key.abilityhotbar.ability_mode",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_CONTROL,
                CATEGORY);
    }

    private HotbarKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : ACTIVATE_SLOT) {
            event.register(key);
        }
        for (KeyMapping key : SWITCH_BAR) {
            event.register(key);
        }
        event.register(ACTIVATE_SELECTED);
        event.register(OPEN_MENU);
        event.register(ABILITY_MODE);
    }
}
