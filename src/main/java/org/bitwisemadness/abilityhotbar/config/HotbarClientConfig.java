package org.bitwisemadness.abilityhotbar.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side display preferences (REQ-CFG-01/02).
 *
 * <p>CLIENT configs are loaded only on the physical client, are never synced
 * to the server, and can never widen server policy. These are pure display
 * and input preferences.</p>
 */
public final class HotbarClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * The player's preferred number of slots per hotbar bar.
     * The effective value is {@code min(this, HotbarServerConfig.maxHotbarSlots)}.
     */
    public static final ModConfigSpec.IntValue SLOTS_PER_BAR =
            BUILDER.comment("Preferred slots per hotbar bar (clamped to the server's maxSlots)")
                    .defineInRange("display.slotsPerBar", 9, 1, 10);

    /** Show the bar horizontally (true) or vertically (false). */
    public static final ModConfigSpec.BooleanValue HORIZONTAL =
            BUILDER.comment("Bar orientation: true = horizontal, false = vertical")
                    .define("display.horizontal", true);

    /** Bar opacity (0.0 = invisible, 1.0 = fully opaque). */
    public static final ModConfigSpec.DoubleValue OPACITY =
            BUILDER.comment("Bar opacity (0.0 to 1.0)")
                    .defineInRange("display.opacity", 1.0, 0.0, 1.0);

    /** Show cooldown overlays on slots. */
    public static final ModConfigSpec.BooleanValue SHOW_COOLDOWNS =
            BUILDER.comment("Show cooldown progress on slots")
                    .define("display.showCooldowns", true);

    /** Show resource-cost indicators on slots. */
    public static final ModConfigSpec.BooleanValue SHOW_COSTS =
            BUILDER.comment("Show resource cost indicators on slots")
                    .define("display.showCosts", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private HotbarClientConfig() {
    }
}
