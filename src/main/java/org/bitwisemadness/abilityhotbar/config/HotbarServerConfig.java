package org.bitwisemadness.abilityhotbar.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-authoritative configuration (REQ-CFG-01/02).
 *
 * <p>SERVER configs are loaded on both sides, synced to clients automatically,
 * and can be overridden per-world via {@code <world>/serverconfig/}. Clients
 * can never write these values - they are the hard ceiling.</p>
 */
public final class HotbarServerConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * The maximum number of slots any hotbar may have. The client's
     * {@code slotsPerBar} preference is clamped to this value. This is the
     * non-bypassable safety constraint (layer 1 in the precedence model).
     */
    public static final ModConfigSpec.IntValue MAX_HOTBAR_SLOTS =
            BUILDER.comment("Maximum slots per hotbar bar (server ceiling - clients cannot exceed this)")
                    .defineInRange("hotbar.maxSlots", 10, 1, 10);

    /**
     * The maximum number of cyclable hotbars. The brief's stretch goal
     * (REQ-HOT-22) defaults to 3 with a configurable max of 10.
     */
    public static final ModConfigSpec.IntValue MAX_HOTBAR_COUNT =
            BUILDER.comment("Maximum number of cyclable hotbars (server ceiling)")
                    .defineInRange("hotbar.maxBars", 10, 1, 10);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private HotbarServerConfig() {
    }
}
