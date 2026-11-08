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
                    .defineInRange("display.slotsPerBar", 9, 1, 18);

    /**
     * How many <b>built-in</b> hotbars the client's cycle set includes. The
     * effective value is {@code min(this, HotbarServerConfig.maxBars)};
     * registered bars are appended after this window and are never clipped
     * by it (ADR-012).
     */
    public static final ModConfigSpec.IntValue HOTBAR_COUNT =
            BUILDER.comment("Built-in hotbars in the cycle set (clamped to the server's maxBars; "
                            + "mod-registered bars are always included)")
                    .defineInRange("display.hotbarCount", 3, 1, 10);

    /**
     * Whether wheel cycling skips bars where every slot is unassigned
     * (default on). Direct selection (Alt+digit) never skips - it always
     * lands where pressed.
     */
    public static final ModConfigSpec.BooleanValue SKIP_EMPTY_BARS =
            BUILDER.comment("Mouse-wheel bar cycling skips bars with no assigned abilities "
                            + "(direct selection always lands where pressed)")
                    .define("display.skipEmptyBars", true);

    /**
     * Whether switching bars shows the newly active bar's name briefly above
     * the bar (REQ-HOT-22's CLIENT-preference notification).
     */
    public static final ModConfigSpec.BooleanValue SWITCH_TOAST =
            BUILDER.comment("Show the bar's name briefly after switching hotbars")
                    .define("display.switchToast", true);

    /**
     * The ability menu's last registered tab; a persisted CLIENT
     * preference - search/filter state only, bar contents stay server
     * data.
     */
    public static final ModConfigSpec.ConfigValue<String> MENU_TAB =
            BUILDER.comment("Last menu tab chosen in the ability menu (empty = the All tab)")
                    .define("menu.lastTab", "");

    /**
     * The last subcategory tab; same rules as {@link #MENU_TAB} - saved as
     * the pair {@code <tab>;<filter>} since the filter became tab-scoped.
     */
    public static final ModConfigSpec.ConfigValue<String> MENU_SUBCATEGORY =
            BUILDER.comment("Last subcategory filter chosen in the ability menu (empty = no filter)")
                    .define("menu.lastSubCategory", "");

    /** Description pane wheel speed, lines per notch (owner: configurable). */
    public static final ModConfigSpec.IntValue DESCRIPTION_SCROLL_LINES =
            BUILDER.comment("Description pane lines scrolled per wheel notch")
                    .defineInRange("menu.descriptionScrollLines", 16, 1, 64);

    /** Drag-to-assign: a row drag onto a slot cell assigns there. */
    public static final ModConfigSpec.BooleanValue DRAG_TO_ASSIGN =
            BUILDER.comment("Allow dragging an ability row onto a slot cell to assign it")
                    .define("menu.dragToAssign", true);

    /**
     * The active menu style (issue #129): a registered style id, empty =
     * the shipped built-in (the Tensura RPG System palette sync) - a CLIENT
     * visual preference like the rest of the menu settings.
     */
    public static final ModConfigSpec.ConfigValue<String> MENU_STYLE =
            BUILDER.comment("The ability menu's style id (empty = the built-in style)")
                    .define("menu.style", "");

    /**
     * The player's per-bar hotbar styles (issue #127): an encoded
     * {@code barId=styleId;...} string - a CLIENT visual preference, same
     * class as the filter prefs; bar contents stay server data. Parsing and
     * rewriting live pure in {@code HotbarStyles} (resource-location paths
     * never contain the separators).
     */
    public static final ModConfigSpec.ConfigValue<String> BAR_STYLES =
            BUILDER.comment("Per-bar hotbar styles as barId=styleId pairs separated by ';' "
                    + "(empty = vanilla default)")
            .define("menu.barStyles", "");

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

    /** Horizontal pixel offset from the anchor point. */
    public static final ModConfigSpec.IntValue OFFSET_X =
            BUILDER.comment("Horizontal offset (pixels) from the bar's anchor")
                    .defineInRange("display.offsetX", 0, -500, 500);

    /** Vertical pixel offset from the anchor point. */
    public static final ModConfigSpec.IntValue OFFSET_Y =
            BUILDER.comment("Vertical offset (pixels) from the bar's anchor")
                    .defineInRange("display.offsetY", 0, -500, 500);

    /** Render scale applied to the whole bar (1.0 = native size). */
    public static final ModConfigSpec.DoubleValue SCALE =
            BUILDER.comment("Bar render scale (0.5 to 2.0)")
                    .defineInRange("display.scale", 1.0, 0.5, 2.0);

    /** Draw ability icons in assigned slots (falls back to a placeholder when absent). */
    public static final ModConfigSpec.BooleanValue SHOW_ICONS =
            BUILDER.comment("Draw ability icons in assigned slots")
                    .define("display.showIcons", true);

    /**
     * Where the bar is placed on screen. Every anchor is a complete 2D rule, so
     * it is valid with either {@link #HORIZONTAL} value.
     */
    public static final ModConfigSpec.EnumValue<BarAnchor> ANCHOR =
            BUILDER.comment("Bar anchor: aboveHotbar, aboveXp, aboveStatus, leftEdge or rightEdge "
                            + "(valid with either orientation)")
                    .defineEnum("display.anchor", BarAnchor.ABOVE_HOTBAR);

    /**
     * How {@link BarAnchor#ABOVE_STATUS} resolves the status-cluster height.
     * Only read when {@link #ANCHOR} is {@code aboveStatus}.
     */
    public static final ModConfigSpec.EnumValue<StatusAnchorMode> STATUS_ANCHOR_MODE =
            BUILDER.comment("aboveStatus height: DYNAMIC tracks the player's hearts, armor and "
                            + "oxygen; FIXED uses the vanilla baseline (only read when "
                            + "display.anchor = aboveStatus)")
                    .defineEnum("display.statusAnchorMode", StatusAnchorMode.DYNAMIC);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private HotbarClientConfig() {
    }
}
