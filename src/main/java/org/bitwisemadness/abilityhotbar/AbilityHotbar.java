package org.bitwisemadness.abilityhotbar;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Ability Hotbar entry point.
 */
@Mod(AbilityHotbar.MOD_ID)
public final class AbilityHotbar {

    public static final String MOD_ID = "ability_hotbar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AbilityHotbar(IEventBus modBus) {
        LOGGER.info("Ability Hotbar initialized");
    }
}
