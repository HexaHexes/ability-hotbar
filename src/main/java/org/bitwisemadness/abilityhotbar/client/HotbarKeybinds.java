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
 * <p>Five defaults on the mod's own category - never {@code key.irons_spellbooks.*}:
 * Ctrl+1..9 activates a slot directly, G activates the selected slot, K opens the
 * ability menu, and Right Ctrl toggles ability mode. Ctrl+wheel selection is not a
 * KeyMapping (the wheel has no Controls entry) and is routed from
 * {@link HotbarInputHandler}.</p>
 *
 * <p>The Ctrl+number binds use {@link KeyModifier#CONTROL}, so NeoForge claims the
 * number key only while Ctrl is held and leaves the vanilla hotbar's plain 1..9
 * untouched. Registered for the physical client only, so a dedicated server never
 * loads this class.</p>
 */
@EventBusSubscriber(modid = AbilityHotbar.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HotbarKeybinds {

    /** Own keybinding category - shown as its own group in the Controls screen. */
    public static final String CATEGORY = "key.categories.abilityhotbar";

    /** Ctrl+1..9: activate the corresponding slot directly (REQ-HOT-11). */
    public static final KeyMapping[] ACTIVATE_SLOT = new KeyMapping[9];

    /** G: activate the currently selected slot (REQ-HOT-11). */
    public static final KeyMapping ACTIVATE_SELECTED;

    /** K: open the ability management menu (flow owned by hotbar#27). */
    public static final KeyMapping OPEN_MENU;

    /** Right Ctrl: toggle ability mode (flow owned by hotbar#17). */
    public static final KeyMapping ABILITY_MODE;

    static {
        for (int i = 0; i < ACTIVATE_SLOT.length; i++) {
            ACTIVATE_SLOT[i] = new KeyMapping(
                    "key.abilityhotbar.activate_slot_" + (i + 1),
                    KeyConflictContext.IN_GAME,
                    KeyModifier.CONTROL,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_1 + i,
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
                GLFW.GLFW_KEY_K,
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
        event.register(ACTIVATE_SELECTED);
        event.register(OPEN_MENU);
        event.register(ABILITY_MODE);
    }
}
