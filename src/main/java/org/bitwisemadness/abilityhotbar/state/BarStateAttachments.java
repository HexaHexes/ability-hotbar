package org.bitwisemadness.abilityhotbar.state;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;
import org.bitwisemadness.abilityhotbar.config.HotbarServerConfig;

import java.util.function.Supplier;

/**
 * Registers the bar state attachment type on the NeoForge attachment registry
 * (REQ-HOT-14, ADR-004).
 */
public final class BarStateAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, AbilityHotbar.MOD_ID);

    /**
     * The bar state attachment: per-player, persisted, copied on death.
     * Serialized via the versioned {@link BarStateSerializer}.
     *
     * <p>The state is sized to the <b>server</b> ceiling, never the client
     * preference: the attachment is authoritative server-side data and the
     * CLIENT config is not loaded on a dedicated server. The client renders
     * the first {@code effectiveSlotsPerBar()} entries.</p>
     */
    public static final Supplier<AttachmentType<BarState>> BAR_STATE =
            ATTACHMENT_TYPES.register("bar_state", () -> AttachmentType.builder(
                            () -> BarState.empty(HotbarServerConfig.MAX_HOTBAR_SLOTS.get()))
                    .serialize(new BarStateSerializer())
                    .copyOnDeath()
                    .build());

    /** Call from the mod constructor to register on the mod bus. */
    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
