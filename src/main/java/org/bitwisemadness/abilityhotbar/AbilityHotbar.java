package org.bitwisemadness.abilityhotbar;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.bitwisemadness.abilityhotbar.client.ClientConfigScreen;
import org.bitwisemadness.abilityhotbar.compat.CoreMasteryCompat;
import org.bitwisemadness.abilityhotbar.compat.CoreScopeCompat;
import org.bitwisemadness.abilityhotbar.compat.CoreSyncCompat;
import org.bitwisemadness.abilityhotbar.config.HotbarClientConfig;
import org.bitwisemadness.abilityhotbar.config.HotbarServerConfig;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
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
        // Claim NeoForge's config screen before Configured can, so its editor (which
        // reverts unedited values on save) never handles our file. See ClientConfigScreen.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientConfigScreen.register(modContainer);
        }
        // Per-player bar state attachment (REQ-HOT-14, ADR-004)
        BarStateAttachments.register(modBus);
        // CoreSyncCompat, the #90 scope guard and the pane's mastery display
        // (CoreMasteryCompat, REQ-CORE-12) register only when core is present:
        // their core references must never resolve on a core-less runtime.
        if (ModList.get().isLoaded("ability_core")) {
            CoreSyncCompat.register();
            CoreScopeCompat.register();
            CoreMasteryCompat.register();
        }
        LOGGER.info("Ability Hotbar initialized");
    }
}
