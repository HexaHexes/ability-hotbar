package org.bitwisemadness.tensuraabilityhotbar;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Tensura Ability Hotbar entry point.
 */
@Mod(TensuraAbilityHotbar.MOD_ID)
public final class TensuraAbilityHotbar {

    public static final String MOD_ID = "tensura_ability_hotbar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TensuraAbilityHotbar(IEventBus modBus) {
        LOGGER.info("Tensura Ability Hotbar initialized");
    }
}
