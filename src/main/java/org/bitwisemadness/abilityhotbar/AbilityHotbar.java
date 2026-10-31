package org.bitwisemadness.abilityhotbar;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.bitwisemadness.abilityhotbar.config.HotbarClientConfig;
import org.bitwisemadness.abilityhotbar.config.HotbarServerConfig;
import org.slf4j.Logger;

/**
 * Ability Hotbar entry point.
 */
@Mod(AbilityHotbar.MOD_ID)
public final class AbilityHotbar {

    public static final String MOD_ID = "ability_hotbar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AbilityHotbar(IEventBus modBus, ModContainer modContainer) {
        // SERVER config: authoritative, synced to clients, per-world overridable
        modContainer.registerConfig(ModConfig.Type.SERVER, HotbarServerConfig.SPEC, "ability_hotbar-server.toml");
        // CLIENT config: display preferences only, never synced, never policy
        modContainer.registerConfig(ModConfig.Type.CLIENT, HotbarClientConfig.SPEC, "ability_hotbar-client.toml");
        LOGGER.info("Ability Hotbar initialized");
    }
}
