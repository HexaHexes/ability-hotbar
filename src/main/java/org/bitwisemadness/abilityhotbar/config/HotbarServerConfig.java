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
                    .defineInRange("hotbar.maxSlots", 10, 1, 18);

    /**
     * The server ceiling for <b>built-in</b> cyclable bars (REQ-HOT-22):
     * how many built-in bars a state may allocate and a client's
     * {@code display.hotbarCount} preference may never exceed. Registered
     * bars are not built-ins and never count against it - the ceiling gates
     * the window, never the cycle set's registered tail (ADR-012 §4,
     * hotbar#102). The client preference defaults lower (3); this ceiling
     * defaults to the configured maximum of 10 (ADR-011).
     */
    public static final ModConfigSpec.IntValue MAX_HOTBAR_COUNT =
            BUILDER.comment("Maximum built-in hotbars in the cycle set (server ceiling; "
                            + "mod-registered bars always ride along and are never clipped)")
                    .defineInRange("hotbar.maxBars", 10, 1, 10);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private HotbarServerConfig() {
    }
}
