package org.bitwisemadness.abilityhotbar.client;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.AbilityPresentation;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.PresentationField;
import org.bitwisemadness.abilitycore.api.PresentationProviders;
import org.bitwisemadness.abilitycore.api.Toggle;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilityhotbar.UnlockRule;
import org.bitwisemadness.abilityhotbar.compat.CoreActivationCompat;
import org.bitwisemadness.abilityhotbar.config.HotbarClientConfig;
import org.bitwisemadness.abilityhotbar.config.HotbarConfig;
import org.bitwisemadness.abilityhotbar.network.AssignAbilityPayload;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.registry.HotbarStyle;
import org.bitwisemadness.abilityhotbar.registry.HotbarStyles;
import org.bitwisemadness.abilityhotbar.registry.MenuStyle;
import org.bitwisemadness.abilityhotbar.registry.MenuStyles;
import org.bitwisemadness.abilityhotbar.registry.MenuTabs;
import org.bitwisemadness.abilityhotbar.state.Bar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.jetbrains.annotations.Nullable;

/**
 * The hotbar ability setup screen - an actual gameplay menu, not config UI
 * (REQ-HOT-12, hotbar#27): the player picks which ability sits in each slot
 * of each <b>assignable</b> hotbar.
 *
 * <p>Layout follows the owner's TRS skill-menu design (design comment on
 * hotbar#27): a header band with the title top-left and the target-bar
 * selector (chip + slot meter) top-right, one collapsed filter strip (search
 * pill + category/tag chips), the full-width ability list with inline
 * hover metadata, and a bottom stack of chamfered slot cells over the hint
 * bar. A filtered row click assigns to the selected slot; the slot cells
 * select the target slot, the clear pill empties it - all the same
 * server-validated {@link AssignAbilityPayload} naming the target bar
 * (ADR-012 id targeting), so the screen is never authoritative: the
 * snapshot echo repaints it with server truth.</p>
 *
 * <p>Search, category and tag filters come from {@link MenuQuery}; the
 * chosen filters persist as CLIENT config ({@code menu.lastCategory}/{@code
 * menu.lastTag}) while bar contents stay server data. Registered
 * non-assignable bars are never listed and the skip-empty cycling option
 * does not apply here (ADR-012). Opens from the menu-key seam, closes with
 * the same key (when the search box is not focused) or Esc.</p>
 *
 * <p>Every fill and text color resolves through the active
 * {@link MenuStyle} (issue #129) via the {@link MenuChrome} primitives: the
 * backdrop is the style's blur + world dim + cyan hairlines, the panel
 * chrome is chamfered glass, and no one-off palette hex literal lives here
 * (the inert-diagnostic red is the only exception). The menu-style cycler
 * pill (hint-bar right end, visible once more than one style is
 * registered) writes the CLIENT {@code menu.style} preference.</p>
 */
public final class AbilityMenuScreen extends Screen {

    private static final int ROW_HEIGHT = 36;
    private static final int LIST_LEFT = 8;
    /** The list starts 6px under the collapsed filter strip (#141). */
    private static final int LIST_TOP = 64;
    /** Below the list: slot detail, slot cells and the hint bar (#27). */
    private static final int LIST_BOTTOM_PAD = 54;
    private static final int INERT_COLOR = 0xFFFF5555;
    /** The filter strip row (search pill + category/tag chips). */
    private static final int FILTER_Y = 40;
    private static final int CHIP_HEIGHT = 18;
    private static final int SEARCH_X = 8;
    private static final int SEARCH_Y = 44;
    private static final int SEARCH_WIDTH = 140;
    /** The header's target-bar chip (cycles the bar on click). */
    private static final int BAR_CHIP_Y = 4;
    private static final int BAR_CHIP_HEIGHT = 14;
    private static final int METER_WIDTH = 44;
    /** The bar texture-style cycler pill (issue #127), header line 2 right. */
    private static final int STYLE_PILL_WIDTH = 110;
    private static final int STYLE_PILL_Y = 21;
    /** The menu-style cycler pill (issue #129), hint-bar right end. */
    private static final int MENU_STYLE_PILL_WIDTH = 120;
    private static final int SLOT_HEIGHT = 32;
    /** The hint-bar prev/next bar buttons (issue #157): 14px cells, 2px gap. */
    private static final int BAR_NAV_SIZE = 14;
    private static final int BAR_NAV_GAP = 2;

    private ResourceLocation selectedBar;
    private int selectedSlot;
    @Nullable
    private String subCategoryFilter;
    @Nullable
    private ResourceLocation selectedTab;
    private int scrollOffset;
    private EditBox searchBox;
    // The hovered row's meta panel, stashed by the list pass and drawn
    // LAST in render() so list chrome can never paint over it (brief).
    @Nullable
    private MenuQuery.Entry metaRow;
    private int metaRowY;
    private int metaListWidth;
    private int metaListBottom;

    public AbilityMenuScreen() {
        super(Component.translatable("ability_hotbar.menu.title"));
        // Loaded once per open (a resize re-runs init, not this constructor),
        // so mid-session filters are not clobbered by the last saved pair.
        subCategoryFilter = emptyToNull(HotbarClientConfig.MENU_SUBCATEGORY.get());
        selectedTab = parseFilter(HotbarClientConfig.MENU_TAB.get());
        BarState state = ClientBarState.get();
        List<ResourceLocation> bars = manageableBars();
        ResourceLocation active = state == null ? null : state.activeBarId();
        selectedBar = active != null && bars.contains(active) ? active
                : bars.isEmpty() ? null : bars.get(0);
        if (selectedTab != null && MenuTabs.get(selectedTab).isEmpty()) {
            selectedTab = null; // a removed or unknown tab id falls back to All
        }
        // A corrupt pref must never blank the menu (the parseFilter posture):
        // a subcategory naming nothing under the selected tab drops to All.
        if (subCategoryFilter != null && !subTypeTags().contains(subCategoryFilter)) {
            subCategoryFilter = null;
        }
    }

    @Override
    protected void init() {
        // Borderless: MenuChrome draws the search pill behind the text, so
        // the widget contributes only its text, cursor and hint.
        searchBox = new EditBox(this.font, SEARCH_X, SEARCH_Y, SEARCH_WIDTH, 16,
                Component.translatable("ability_hotbar.menu.search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("ability_hotbar.menu.search"));
        searchBox.setResponder(value -> this.scrollOffset = 0);
        searchBox.setTextColor(style().text());
        searchBox.setTextColorUneditable(style().muted());
        addRenderableWidget(searchBox);
    }

    /** The active menu style: the config id resolved through the registry. */
    private MenuStyle style() {
        return MenuStyles.active(HotbarClientConfig.MENU_STYLE.get());
    }

    /**
     * The style's backdrop (issue #129/#27): vanilla blur first (their
     * blur-on default), then {@link MenuChrome#backdrop} - world dim,
     * hairline frame, header band and ticks - and the search pill that sits
     * under the borderless search widget.
     */
    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gui, mouseX, mouseY, partialTick);
        MenuStyle style = style();
        MenuChrome.backdrop(gui, this.width, this.height, style);
        MenuChrome.glass(gui, SEARCH_X - 2, FILTER_Y, SEARCH_WIDTH + 4, CHIP_HEIGHT,
                style, false);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick); // backdrop + the search box
        MenuStyle style = style();
        // title, top-left inside the frame, with the accent underline
        Component title = Component.translatable("ability_hotbar.menu.title");
        gui.drawString(this.font, title, LIST_LEFT, 6, style.text());
        gui.fill(LIST_LEFT, 16, LIST_LEFT + this.font.width(title), 17, style.accent());
        if (menuStylePillVisible()) {
            drawMenuStylePill(gui, mouseX, mouseY);
        }

        BarState state = ClientBarState.get();
        if (state == null) {
            gui.drawString(this.font, Component.translatable("ability_hotbar.menu.waiting"),
                    LIST_LEFT, LIST_TOP, style.muted());
            return;
        }

        Bar target = targetBar(state);
        drawHeader(gui, mouseX, mouseY, target);
        drawProviderTabs(gui, mouseX, mouseY);
        if (styleCyclerVisible()) {
            drawStylePill(gui, mouseX, mouseY);
        }
        drawSubcategoryTabs(gui, mouseX, mouseY);
        drawAbilityList(gui, mouseX, mouseY, state);
        drawSlotCells(gui, mouseX, mouseY, target);
        drawSlotDetail(gui, mouseX, mouseY, target);
        drawHintBar(gui);
        drawBarNav(gui, mouseX, mouseY);
        if (metaRow != null) {
            // The meta panel draws last: list chrome (and everything else)
            // can never paint over the tooltip (the owner's layering brief).
            gui.flush();
            drawRowMeta(gui, metaRow, metaRowY, metaListWidth, metaListBottom);
        }
    }

    /** The header's right cluster: target-bar chip, used/total, slot meter. */
    private void drawHeader(GuiGraphics gui, int mouseX, int mouseY, @Nullable Bar target) {
        List<ResourceLocation> bars = manageableBars();
        if (bars.isEmpty()) {
            return;
        }
        MenuStyle style = style();
        int[] chip = barChipRect(target);
        boolean hovered = inside(mouseX, mouseY, chip[0], BAR_CHIP_Y, chip[1], BAR_CHIP_HEIGHT);
        MenuChrome.glass(gui, chip[0], BAR_CHIP_Y, chip[1], BAR_CHIP_HEIGHT, style, hovered);
        gui.drawString(this.font, barChipLabel(), chip[0] + 6, BAR_CHIP_Y + 3,
                hovered ? style.text() : style.muted());
        if (target == null) {
            return;
        }
        String count = countText(target);
        gui.drawString(this.font, count,
                this.width - 8 - METER_WIDTH - 4 - this.font.width(count), 6,
                style.muted());
        float fraction = usedSlots(target) / (float) Math.max(1, target.slotCount());
        MenuChrome.meter(gui, this.width - 8 - METER_WIDTH, 8, METER_WIDTH, 7,
                fraction, style);
    }

    /** The bar chip's rect for the current header (shared with hit-testing). */
    private int[] barChipRect(@Nullable Bar target) {
        if (manageableBars().isEmpty()) {
            return new int[] {LIST_LEFT, 0};
        }
        int chipWidth = this.font.width(barChipLabel()) + 12;
        int reserved = target == null ? 0
                : METER_WIDTH + 4 + this.font.width(countText(target)) + 4;
        return new int[] {this.width - 8 - reserved - chipWidth, chipWidth};
    }

    private Component barChipLabel() {
        List<ResourceLocation> bars = manageableBars();
        if (bars.isEmpty()) {
            return Component.empty();
        }
        return BarName.of(selectedBar == null ? bars.get(0) : selectedBar);
    }

    /** The header's "used/total" readout (empty while the bar is unknown). */
    private static String countText(@Nullable Bar target) {
        return target == null ? "" : usedSlots(target) + "/" + target.slotCount();
    }

    private static int usedSlots(Bar bar) {
        int used = 0;
        for (SlotAssignment assignment : bar.assignments()) {
            if (assignment.isAssigned()) {
                used++;
            }
        }
        return used;
    }

    /**
     * The subcategory tab strip beside the search bar (the owner's brief):
     * "All" plus only the subcategories with entries under the CURRENT tab -
     * clicking one sets the subcategory filter directly (the category and
     * tag cycle chips are gone). No subtypes, no strip.
     */
    private void drawSubcategoryTabs(GuiGraphics gui, int mouseX, int mouseY) {
        List<String> types = subTypeTags();
        if (types.isEmpty()) {
            return;
        }
        MenuStyle style = style();
        int count = types.size() + 1;
        for (int i = 0; i < count; i++) {
            boolean all = i == 0;
            String typeTag = all ? null : types.get(i - 1);
            int[] rect = subTypeRect(i, count);
            boolean active = all ? subCategoryFilter == null : typeTag.equals(subCategoryFilter);
            boolean hovered = inside(mouseX, mouseY, rect[0], FILTER_Y, rect[1], CHIP_HEIGHT);
            MenuChrome.glass(gui, rect[0], FILTER_Y, rect[1], CHIP_HEIGHT, style, active || hovered);
            Component label = all
                    ? Component.translatable("ability_hotbar.menu.subcategory_any")
                    : MenuLabels.subcategory(typeTag);
            gui.drawString(this.font,
                    this.font.plainSubstrByWidth(label.getString(), Math.max(4, rect[1] - 6)),
                    rect[0] + 4, FILTER_Y + 4,
                    active ? style.action() : hovered ? style.text() : style.muted());
        }
    }

    /** One subcategory tab's rect (draw and hit share it): sized to fit the row. */
    private int[] subTypeRect(int index, int count) {
        int x = SEARCH_X + SEARCH_WIDTH + 6;
        int available = this.width - 8 - x;
        int width = Math.max(16, Math.min(64, available / count));
        return new int[] {x + index * width, width - 2};
    }

    /**
     * The tab-filtered pool (what the subcategory strip keys on): the
     * visibility pool narrowed by the selected provider tab's membership,
     * or the whole pool under the permanent All tab.
     */
    private Map<ResourceLocation, AbilityDefinition> tabPool() {
        MenuTabs.MenuTab selected = selectedTab == null ? null
                : MenuTabs.get(selectedTab).orElse(null);
        Map<ResourceLocation, AbilityDefinition> pool = menuPool();
        if (selected == null) {
            return pool;
        }
        Map<ResourceLocation, AbilityDefinition> out = new java.util.HashMap<>(pool.size());
        pool.forEach((id, definition) -> {
            if (selected.accepts().test(id, definition)) {
                out.put(id, definition);
            }
        });
        return out;
    }

    /** The subcategory tags with entries under the current tab (the strip's tabs). */
    private List<String> subTypeTags() {
        return MenuQuery.subTypeTags(tabPool());
    }

    /**
     * The header's registered-tab strip (hotbar#154): the permanent All tab
     * plus every tab a provider registered - drawn as **Icon/Item Stack +
     * Text** (the owner's identifier format), each shrinking to fit beside
     * the style pill. A registry with no tabs draws nothing and the pool
     * stays unfiltered.
     */
    private void drawProviderTabs(GuiGraphics gui, int mouseX, int mouseY) {
        List<MenuTabs.MenuTab> tabs = MenuTabs.all();
        if (tabs.isEmpty()) {
            return;
        }
        MenuStyle style = style();
        int tabWidth = providerTabWidth(tabs);
        int x = LIST_LEFT;
        for (int i = -1; i < tabs.size(); i++) {
            boolean all = i < 0;
            MenuTabs.MenuTab tab = all ? null : tabs.get(i);
            boolean active = all ? selectedTab == null : tab.id().equals(selectedTab);
            boolean hovered = inside(mouseX, mouseY, x, STYLE_PILL_Y, tabWidth - 2, 14);
            if (active) {
                gui.fill(x, STYLE_PILL_Y, x + tabWidth - 2, STYLE_PILL_Y + 14,
                        MenuStyle.alpha(style.accent(), 30));
            } else if (hovered) {
                gui.fill(x, STYLE_PILL_Y, x + tabWidth - 2, STYLE_PILL_Y + 14,
                        MenuStyle.alpha(style.raised(), 55));
            }
            int textX = x + 3;
            if (!all && tab.hasIcon()) {
                textX = drawTabIcon(gui, tab, x + 3, STYLE_PILL_Y + 1);
            }
            Component label = all
                    ? Component.translatable("ability_hotbar.menu.tab_all")
                    : MenuLabels.named(tab.displayNameKey());
            String text = this.font.plainSubstrByWidth(label.getString(),
                    x + tabWidth - 4 - textX);
            gui.drawString(this.font, text, textX, STYLE_PILL_Y + 3,
                    active ? style.text() : hovered ? style.text() : style.muted());
            x += tabWidth;
        }
    }

    /** Uniform tab icon box - every tab icon draws into this one size. */
    private static final int TAB_ICON = 12;

    /**
     * The Icon/Item Stack half of a tab's identifier; returns the text's x.
     * Both forms draw through the SAME scaled 16px box (the owner's brief:
     * one uniform size, like the bar's own icons), and an icon whose
     * resource cannot be resolved - missing texture file or empty stack -
     * draws nothing: the tab stays text-only, never a broken box.
     */
    private int drawTabIcon(GuiGraphics gui, MenuTabs.MenuTab tab, int x, int y) {
        ResourceLocation iconId = tab.iconId();
        ItemStack stack = tab.icon();
        MenuTabs.IconTexture texture = iconId == null ? null
                : MenuTabs.resolvedIconTexture(iconId, rl -> Minecraft.getInstance()
                        .getResourceManager().getResource(rl).isPresent()).orElse(null);
        if (texture == null && (stack == null || stack.isEmpty())) {
            return x; // nothing resolves: text-only, same padding as no icon
        }
        var pose = gui.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0.0);
            float scale = TAB_ICON / 16.0f;
            pose.scale(scale, scale, 1.0f);
            if (texture != null) {
                if (texture.sprite()) {
                    gui.blitSprite(texture.drawWith(), 0, 0, 16, 16);
                } else {
                    gui.blit(texture.drawWith(), 0, 0, 0, 0, 16, 16, 16, 16);
                }
            } else {
                gui.renderItem(stack, 0, 0);
            }
        } finally {
            pose.popPose();
        }
        return x + TAB_ICON + 3;
    }

    /** One registered tab's width: capped, floored, shrunk to fit beside the style pill. */
    private int providerTabWidth(List<MenuTabs.MenuTab> tabs) {
        int available = (this.width - 16)
                - (styleCyclerVisible() ? STYLE_PILL_WIDTH + 4 : 0);
        return Math.min(72, Math.max(24, available / (tabs.size() + 1)));
    }

    /** The style cycler shows only for an assignable bar with registered styles. */
    private boolean styleCyclerVisible() {
        if (selectedBar == null || HotbarStyles.all().isEmpty()) {
            return false;
        }
        return HotbarRegistry.get(selectedBar)
                .map(registration -> registration.assignable()).orElse(false);
    }

    private void drawStylePill(GuiGraphics gui, int mouseX, int mouseY) {
        MenuStyle style = style();
        int x = this.width - 8 - STYLE_PILL_WIDTH;
        boolean hovered = inside(mouseX, mouseY, x, STYLE_PILL_Y, STYLE_PILL_WIDTH, 14);
        MenuChrome.glass(gui, x, STYLE_PILL_Y, STYLE_PILL_WIDTH, 14, style, hovered);
        Component label = styleLabel();
        String text = this.font.plainSubstrByWidth(label.getString(), STYLE_PILL_WIDTH - 8);
        gui.drawString(this.font, text, x + 4, STYLE_PILL_Y + 3,
                hovered ? style.text() : style.muted());
    }

    /** The selected bar's current style name (default = the vanilla pair). */
    private Component styleLabel() {
        String styleId = selectedBar == null ? null
                : HotbarStyles.playerStyleId(HotbarClientConfig.BAR_STYLES.get(), selectedBar);
        if (styleId == null) {
            return Component.translatable("ability_hotbar.menu.style_default");
        }
        return HotbarStyles.get(ResourceLocation.parse(styleId))
                .map(style -> Component.translatable(style.displayNameKey()))
                .orElseGet(() -> Component.translatable("ability_hotbar.menu.style_default"));
    }

    /** Cycles the selected assignable bar: default -> styles -> default. */
    private void cycleStyle() {
        List<HotbarStyle> styles = HotbarStyles.all();
        if (selectedBar == null || styles.isEmpty()) {
            return;
        }
        String raw = HotbarClientConfig.BAR_STYLES.get();
        String current = HotbarStyles.playerStyleId(raw, selectedBar);
        int index = -1; // the vanilla default sits before every style
        for (int i = 0; i < styles.size(); i++) {
            if (styles.get(i).id().toString().equals(current)) {
                index = i;
                break;
            }
        }
        index = (index + 1) % (styles.size() + 1);
        String next = index < 0 ? null : styles.get(index).id().toString();
        HotbarClientConfig.BAR_STYLES.set(HotbarStyles.withStyle(raw, selectedBar, next));
        HotbarClientConfig.SPEC.save();
    }

    /** The menu-style pill shows only when a style beyond the built-in exists. */
    private boolean menuStylePillVisible() {
        return MenuStyles.all().size() > 1;
    }

    private int[] menuStyleRect() {
        return new int[] {this.width - 8 - MENU_STYLE_PILL_WIDTH, this.height - 15,
                MENU_STYLE_PILL_WIDTH, 14};
    }

    private void drawMenuStylePill(GuiGraphics gui, int mouseX, int mouseY) {
        MenuStyle style = style();
        int[] rect = menuStyleRect();
        boolean hovered = inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]);
        MenuChrome.glass(gui, rect[0], rect[1], rect[2], rect[3], style, hovered);
        String text = this.font.plainSubstrByWidth(menuStyleLabel().getString(), rect[2] - 8);
        gui.drawString(this.font, text, rect[0] + 4, rect[1] + 2,
                hovered ? style.text() : style.muted());
    }

    /** The active menu style's display name (the built-in when unset/stale). */
    private Component menuStyleLabel() {
        String configured = HotbarClientConfig.MENU_STYLE.get();
        if (configured != null && !configured.isEmpty()) {
            try {
                var entry = MenuStyles.get(ResourceLocation.parse(configured));
                if (entry.isPresent()) {
                    return Component.translatable(entry.get().displayNameKey());
                }
            } catch (RuntimeException malformed) {
                // a corrupt pref must not stop the menu - fall through
            }
        }
        return MenuStyles.get(MenuStyles.BUILT_IN)
                .map(entry -> Component.translatable(entry.displayNameKey()))
                .orElseGet(() -> Component.translatable("ability_hotbar.menu.menustyle_trs_saodark"));
    }

    /** Cycles the menu style: built-in -> registered styles -> built-in. */
    private void cycleMenuStyle() {
        List<MenuStyles.MenuStyleEntry> styles = MenuStyles.all();
        if (styles.isEmpty()) {
            return;
        }
        String configured = HotbarClientConfig.MENU_STYLE.get();
        int index = -1; // an unset or stale value starts from the front
        for (int i = 0; i < styles.size(); i++) {
            ResourceLocation id = styles.get(i).id();
            if (configured == null || configured.isEmpty()
                    ? id.equals(MenuStyles.BUILT_IN)
                    : id.toString().equals(configured)) {
                index = i;
                break;
            }
        }
        int next = (index + 1) % styles.size();
        ResourceLocation nextId = styles.get(next).id();
        // the built-in stays the config's default answer (empty value)
        HotbarClientConfig.MENU_STYLE.set(
                nextId.equals(MenuStyles.BUILT_IN) ? "" : nextId.toString());
        HotbarClientConfig.SPEC.save();
    }

    /** The bottom stack's chamfered slot cells: click selects the target slot. */
    private void drawSlotCells(GuiGraphics gui, int mouseX, int mouseY, @Nullable Bar target) {
        int slotCount = target == null ? 0 : target.slotCount();
        if (slotCount == 0) {
            return;
        }
        MenuStyle style = style();
        int cellWidth = slotWidth(slotCount, this.width);
        int y = slotY();
        for (int i = 0; i < slotCount; i++) {
            int x = LIST_LEFT + i * cellWidth;
            boolean selected = i == selectedSlot;
            boolean hovered = inside(mouseX, mouseY, x, y, cellWidth - 1, SLOT_HEIGHT);
            MenuChrome.glass(gui, x, y, cellWidth - 1, SLOT_HEIGHT, style, selected);
            if (!selected && hovered) {
                MenuChrome.chamferFill(gui, x + 1, y + 1, cellWidth - 3, SLOT_HEIGHT - 2,
                        Math.max(0, MenuChrome.CHAMFER - 1),
                        MenuStyle.alpha(style.raised(), 55));
            }
            // Assigned-ability icon (#156): the rows' resolution with the
            // item cells' 2px padding; empty/unresolvable/icon-less cells
            // and showIcons = false keep the plain number.
            ResourceLocation icon = slotIcon(target, i);
            boolean iconDrawn = icon != null && HotbarClientConfig.SHOW_ICONS.get()
                    && slotIconFits(cellWidth);
            if (iconDrawn) {
                // The brief's doubled cell icon: whole texture in the
                // doubled box (the rows' technique, 24 of 28+ cells).
                gui.blit(icon, x + 2, y + 2, 0, 0, 24, 24, 24, 24);
            }
            int labelX = slotLabelX(cellWidth, iconDrawn);
            if (labelX >= 0) {
                gui.drawString(this.font, slotLabel(i + 1), x + labelX, y + 11,
                        selected ? style.text() : style.muted());
            }
        }
    }

    /**
     * Slot numbers always render zero-padded (01..18), here and in the
     * keybind names. Package-private for the format fixture.
     */
    static String slotLabel(int slot1Based) {
        return String.format(Locale.ROOT, "%02d", slot1Based);
    }

    /**
     * The width of one slot cell: capped at 80px (the doubled size),
     * floored at 1px. Package-private for the geometry fixture (draw and
     * hit-test share it).
     */
    static int slotWidth(int slotCount, int screenWidth) {
        if (slotCount <= 0) {
            return 0;
        }
        return Math.min(80, Math.max(1, (screenWidth - 16) / slotCount));
    }

    /**
     * Whether an assigned-ability icon fits a footer cell (hotbar#156):
     * the 12px icon at the item cells' 2px padding needs 16px of width.
     * Package-private for the geometry fixture.
     */
    static boolean slotIconFits(int cellWidth) {
        return cellWidth >= 28;
    }

    /**
     * The footer cell's slot-number x offset (hotbar#156): 28 when the icon
     * draws (doubled, at 2..26) and the number still fits beside it, -1 for
     * an icon-only cell (under 36 wide), 3 for the plain number cell.
     * Package-private for the geometry fixture (draw shares it).
     */
    static int slotLabelX(int cellWidth, boolean iconDrawn) {
        if (iconDrawn) {
            return cellWidth >= 36 ? 28 : -1;
        }
        return 3;
    }

    /**
     * A footer cell's assigned-ability icon (hotbar#156): the rows'
     * resolution - the definition's own {@code iconId} - or null for an
     * empty slot, an id that no longer resolves, or an icon-less
     * definition (never a guessed glyph).
     */
    @Nullable
    private static ResourceLocation slotIcon(Bar target, int slot) {
        SlotAssignment assignment = target.assignment(slot);
        if (assignment == null || !assignment.isAssigned() || assignment.abilityId() == null) {
            return null;
        }
        return AbilityDiscovery.get(assignment.abilityId())
                .map(AbilityDefinition::iconId)
                .orElse(null);
    }

    /**
     * The list-relative row index under a screen Y coordinate, or -1 outside
     * the list or past its last fully drawable row. Package-private for the
     * geometry fixture - the click and hover paths share it.
     */
    static int rowIndexAt(int mouseY, int listTop, int listBottom, int rowHeight) {
        if (rowHeight <= 0 || mouseY < listTop || mouseY >= listBottom) {
            return -1;
        }
        int relative = (mouseY - listTop) / rowHeight;
        return relative < (listBottom - listTop) / rowHeight ? relative : -1;
    }

    /** What the selected slot currently holds: name, empty, or the raw inert id. */
    private void drawSlotDetail(GuiGraphics gui, int mouseX, int mouseY,
            @Nullable Bar target) {
        if (target == null) {
            return;
        }
        MenuStyle style = style();
        StringBuilder label = new StringBuilder();
        label.append("Slot ").append(slotLabel(selectedSlot + 1)).append(": ");
        var assignment = target.assignment(selectedSlot);
        ResourceLocation abilityId = assignment == null || !assignment.isAssigned()
                ? null : assignment.abilityId();
        Component name;
        boolean inert = false;
        if (abilityId == null) {
            name = Component.translatable("ability_hotbar.menu.empty_slot");
        } else {
            var definition = AbilityDiscovery.get(abilityId);
            inert = definition.isEmpty();
            name = definition
                    .<Component>map(d -> Component.translatable(d.displayNameKey()))
                    .orElseGet(() -> Component.literal(abilityId.toString()));
        }
        int y = this.height - 47;
        gui.drawString(this.font, label.toString(), LIST_LEFT, y, style.muted());
        int nameX = LIST_LEFT + this.font.width(label.toString());
        gui.drawString(this.font, name.getString(), nameX, y,
                inert ? INERT_COLOR : style.text());
        // the clear pill, right end of the slot-detail line
        int[] clear = clearRect();
        boolean hovered = inside(mouseX, mouseY, clear[0], clear[1], clear[2], clear[3]);
        MenuChrome.glass(gui, clear[0], clear[1], clear[2], clear[3], style, hovered);
        Component clearLabel = Component.translatable("ability_hotbar.menu.clear");
        int textX = clear[0] + (clear[2] - this.font.width(clearLabel)) / 2;
        gui.drawString(this.font, clearLabel, textX, y,
                hovered ? style.text() : style.action());
    }

    /** The clear button's rect (shared with hit-testing). */
    private int[] clearRect() {
        return new int[] {this.width - 64, this.height - 49, 56, 14};
    }

    private void drawAbilityList(GuiGraphics gui, int mouseX, int mouseY, BarState state) {
        List<MenuQuery.Entry> rows = visibleRows();
        metaRow = null; // reset per frame; drawn last from render()
        MenuStyle style = style();
        int listWidth = this.width - 16;
        int listBottom = this.height - LIST_BOTTOM_PAD;
        int visible = Math.max(0, (listBottom - LIST_TOP) / ROW_HEIGHT);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() - visible)));
        if (rows.isEmpty()) {
            gui.drawString(this.font, Component.translatable("ability_hotbar.menu.no_results"),
                    LIST_LEFT, LIST_TOP, style.muted());
            return;
        }
        int y = LIST_TOP;
        int hoveredIndex = -1;
        int hoveredY = -1;
        for (int i = scrollOffset;
                i < rows.size() && y + ROW_HEIGHT <= listBottom;
                i++, y += ROW_HEIGHT) {
            MenuQuery.Entry row = rows.get(i);
            boolean hovered = inside(mouseX, mouseY, LIST_LEFT, y, listWidth, ROW_HEIGHT - 2);
            if (hovered) {
                hoveredIndex = i;
                hoveredY = y;
                gui.fill(LIST_LEFT, y, LIST_LEFT + listWidth, y + ROW_HEIGHT - 2,
                        MenuStyle.alpha(style.raised(), 55));
                // the active row's left-bar marker (their list selection)
                gui.fill(LIST_LEFT, y, LIST_LEFT + 2, y + ROW_HEIGHT - 2, style.accent());
            }
            ResourceLocation icon = row.definition().iconId();
            if (icon != null && HotbarClientConfig.SHOW_ICONS.get()) {
                // The brief's doubled size: the whole texture squeezed into
                // the one doubled box, exactly the bar-icons technique.
                gui.blit(icon, LIST_LEFT + 2, y, 0, 0, 32, 32, 32, 32);
            }
            Component displayName = Component.translatable(row.definition().displayNameKey());
            String name = displayName.getString();
            int textY = y + (ROW_HEIGHT - 9) / 2;
            gui.drawString(this.font, name, LIST_LEFT + 38, textY, style.text());
            String cost = costText(row.definition());
            if (!cost.isEmpty()) {
                gui.drawString(this.font, cost,
                        LIST_LEFT + listWidth - this.font.width(cost), textY,
                        style.action());
            }
            // The unlock grey (#135's rule, the second state): a listed
            // ability the player is provably unlearned for wears the
            // unavailable tint; unknown stays undimmed (never-guess).
            if (UnlockRule.provenUnlearned(
                    AbilityUnlocks.isLearned(this.minecraft.player, row.id()))) {
                gui.fill(LIST_LEFT, y, LIST_LEFT + listWidth, y + ROW_HEIGHT - 2,
                        AbilityBarLayer.UNAVAILABLE_TINT);
            }
            // the cyan row underline (their list chrome)
            gui.fill(LIST_LEFT, y + ROW_HEIGHT - 2, LIST_LEFT + listWidth, y + ROW_HEIGHT - 1,
                    MenuStyle.alpha(style.accent(), 45));
        }
        if (hoveredIndex >= 0) {
            // Stash the hovered row's meta; render() draws the panel LAST
            // so the list chrome can never paint over the tooltip (brief).
            metaRow = rows.get(hoveredIndex);
            metaRowY = hoveredY;
            metaListWidth = listWidth;
            metaListBottom = listBottom;
        }
    }

    /**
     * The hovered row's metadata, inline directly under (or above) the row -
     * no detail pane, no sidebar (the owner's exclusion on #27). Lines run
     * meta -> stats -> live presentation fields -> toggle -> description
     * (issues #119/#148), 12px apart; the height and toggle-line helpers
     * are shared with the button's hit test, so draw and click can never
     * disagree. A description alone renders byte-identically to the
     * pre-#119 panel.
     */
    private void drawRowMeta(GuiGraphics gui, MenuQuery.Entry entry, int rowY,
            int listWidth, int listBottom) {
        MenuStyle style = style();
        AbilityDefinition definition = entry.definition();
        String description = definition.description().orElse(null);
        boolean hasStats = !definition.stats().isEmpty();
        boolean hasDescription = description != null;
        Optional<AbilityPresentation> presentation = presentationOf(entry.id());
        int fieldLines = presentation.map(present -> present.fields().size()).orElse(0);
        Toggle toggle = presentation.map(AbilityPresentation::toggle).orElse(null);
        int panelH = panelHeight(hasStats, fieldLines, toggle != null, hasDescription);
        int panelY = panelYFor(rowY, panelH, listBottom);
        // hotbar#155: the opaque panel fill keeps covered rows from
        // bleeding through and interleaving with the panel's own lines
        MenuChrome.tooltip(gui, LIST_LEFT, panelY, listWidth, panelH, style);
        gui.drawString(this.font, metaText(definition), LIST_LEFT + 5, panelY + 3,
                style.muted());
        int lineY = panelY + 13;
        if (hasStats) {
            // issue #119: declared stats in declaration order, in the
            // value-bearing action colour (the cost badge's language);
            // labels are provider keys resolved through MenuLabels
            String stats = definition.stats().stream()
                    .map(stat -> MenuLabels.stat(stat.labelKey()).getString() + ": " + stat.value())
                    .collect(Collectors.joining("   "));
            gui.drawString(this.font, this.font.plainSubstrByWidth(stats, listWidth - 10),
                    LIST_LEFT + 5, lineY, style.action());
            lineY += 12;
        }
        if (presentation.isPresent()) {
            for (PresentationField field : presentation.get().fields()) {
                drawField(gui, field, lineY, listWidth, style);
                lineY += 12;
            }
        }
        if (toggle != null) {
            drawToggleRow(gui, toggle, panelY, listWidth, hasStats, fieldLines, style);
            lineY += 12;
        }
        if (hasDescription) {
            var parts = this.font.split(Component.literal(description), listWidth - 10);
            if (!parts.isEmpty()) {
                gui.drawString(this.font, parts.get(0), LIST_LEFT + 5, lineY,
                        MenuStyle.alpha(style.muted(), 70));
            }
        }
    }

    /** One live presentation field in the tooltip panel (issue #148). */
    private void drawField(GuiGraphics gui, PresentationField field, int y, int listWidth,
            MenuStyle style) {
        String label = MenuLabels.stat(field.labelKey()).getString() + ": ";
        int x = LIST_LEFT + 5;
        int labelWidth = Math.min(this.font.width(label), listWidth / 2);
        gui.drawString(this.font, this.font.plainSubstrByWidth(label, labelWidth), x, y,
                style.muted());
        int valueX = x + labelWidth + 2;
        if (field.kind() == PresentationField.Kind.PROGRESS) {
            MenuChrome.meter(gui, valueX, y + 1, 40, 6, field.progress(), style);
            valueX += 44;
        }
        if (!field.value().isEmpty()) {
            gui.drawString(this.font,
                    this.font.plainSubstrByWidth(field.value(),
                            LIST_LEFT + listWidth - valueX - 4),
                    valueX, y, style.text());
        }
    }

    /** The toggle state line + Enable/Disable button inside the panel (issue #148). */
    private void drawToggleRow(GuiGraphics gui, Toggle toggle,
            int panelY, int listWidth, boolean stats, int fieldLines, MenuStyle style) {
        int y = panelY + toggleLineOffset(stats, fieldLines);
        gui.drawString(this.font,
                Component.translatable(toggle.enabled()
                        ? "ability_hotbar.menu.toggle_on" : "ability_hotbar.menu.toggle_off"),
                LIST_LEFT + 5, y, toggle.enabled() ? style.text() : style.muted());
        int[] button = toggleButtonRect(panelY, listWidth, stats, fieldLines);
        MenuChrome.glass(gui, button[0], button[1], button[2], button[3], style, true);
        Component label = Component.translatable(toggle.enabled()
                ? "ability_hotbar.menu.toggle_disable" : "ability_hotbar.menu.toggle_enable");
        gui.drawString(this.font, label,
                button[0] + (button[2] - this.font.width(label)) / 2, button[1] + 2,
                style.action());
    }

    /** The tooltip panel's height: 14px base + 12 per extra line (issues #119/#148). */
    static int panelHeight(boolean stats, int fieldLines, boolean toggle, boolean description) {
        return 14 + (stats ? 12 : 0) + fieldLines * 12 + (toggle ? 12 : 0)
                + (description ? 12 : 0);
    }

    /** The toggle line's offset from the panel top (draw and hit share it). */
    static int toggleLineOffset(boolean stats, int fieldLines) {
        return 13 + (stats ? 12 : 0) + fieldLines * 12;
    }

    /** The panel's top for a hovered row: under it, flipped above for the last (#27). */
    private static int panelYFor(int rowY, int panelH, int listBottom) {
        int panelY = rowY + ROW_HEIGHT;
        return panelY + panelH > listBottom ? rowY - panelH : panelY;
    }

    /** The live presentation for one ability (core's contract, issue #148). */
    private Optional<AbilityPresentation> presentationOf(ResourceLocation id) {
        return PresentationProviders.present(this.minecraft.player, id);
    }

    /** The category/tags/cost line shared by the inline meta panel. */
    private static String metaText(AbilityDefinition definition) {
        StringBuilder meta = new StringBuilder();
        meta.append(MenuLabels.category(definition.category()).getString());
        if (!definition.tags().isEmpty()) {
            meta.append("  ").append(definition.tags().stream()
                    .map(tag -> MenuLabels.tag(tag).getString())
                    .collect(Collectors.joining(", ")));
        }
        String cost = costText(definition);
        if (!cost.isEmpty()) {
            meta.append("  ").append(cost);
        }
        return meta.toString();
    }

    /** The hint line, bottom-left (their hint-line pattern). */
    private void drawHintBar(GuiGraphics gui) {
        gui.drawString(this.font, Component.translatable("ability_hotbar.menu.hint"),
                LIST_LEFT, this.height - 13, style().muted());
    }

    /**
     * The hint-bar line's prev/next bar buttons (issue #157): style-coloured
     * triangle glyphs (the vanilla font ships no arrow characters - verified
     * against font/default.json), glass chrome left of the menu-style pill.
     * A single-bar list draws them dimmed and inert; no bar at all draws
     * nothing (the header chip's own rule).
     */
    private void drawBarNav(GuiGraphics gui, int mouseX, int mouseY) {
        List<ResourceLocation> bars = manageableBars();
        if (bars.isEmpty()) {
            return;
        }
        boolean enabled = bars.size() >= 2;
        MenuStyle style = style();
        int[][] navs = barNavRects(this.width, this.height, menuStylePillVisible());
        for (int i = 0; i < navs.length; i++) {
            int[] rect = navs[i];
            boolean hovered = enabled
                    && inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]);
            MenuChrome.glass(gui, rect[0], rect[1], rect[2], rect[3], style, hovered);
            if (!enabled) {
                gui.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3],
                        MenuStyle.alpha(style.raised(), 60));
            }
            int glyph = !enabled ? MenuStyle.alpha(style.muted(), 140)
                    : hovered ? MenuStyle.alpha(style.text(), 255)
                    : MenuStyle.alpha(style.muted(), 255);
            drawBarNavGlyph(gui, rect[0], rect[1], i == 0, glyph);
        }
    }

    /** Nine style-coloured rows: flat edge outward, apex inward (◀ / ▶). */
    private static void drawBarNavGlyph(GuiGraphics gui, int x, int y, boolean left, int color) {
        for (int row = 0; row < 9; row++) {
            int inset = Math.abs(row - 4);
            int x0 = left ? x + 3 + inset : x + 3;
            int x1 = left ? x + 11 : x + 11 - inset;
            gui.fill(x0, y + 2 + row, x1, y + 3 + row, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true; // the search box took it
        }
        searchBox.setFocused(false);
        if (button != 0) {
            return false;
        }
        // menu style cycler (issue #129): the pill owns its rect bottom-right
        if (menuStylePillVisible()) {
            int[] rect = menuStyleRect();
            if (inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3])) {
                cycleMenuStyle();
                return true;
            }
        }
        if (ClientBarState.get() == null) {
            return false;
        }
        BarState state = ClientBarState.get();
        Bar target = targetBar(state);
        // bar chip (header): cycles the target bar
        if (!manageableBars().isEmpty()) {
            int[] chip = barChipRect(target);
            if (inside(mouseX, mouseY, chip[0], BAR_CHIP_Y, chip[1], BAR_CHIP_HEIGHT)) {
                cycleBar();
                return true;
            }
        }
        // registered tabs (header line 2, hotbar#154): All + one per registration
        List<MenuTabs.MenuTab> tabs = MenuTabs.all();
        if (!tabs.isEmpty()) {
            int tabWidth = providerTabWidth(tabs);
            int tabX = LIST_LEFT;
            for (int i = -1; i < tabs.size(); i++) {
                if (inside(mouseX, mouseY, tabX, STYLE_PILL_Y, tabWidth - 2, 14)) {
                    selectedTab = i < 0 ? null : tabs.get(i).id();
                    scrollOffset = 0;
                    return true;
                }
                tabX += tabWidth;
            }
        }
        // subcategory tabs (beside the search bar) select the subfilter
        List<String> subTypes = subTypeTags();
        if (!subTypes.isEmpty()) {
            int count = subTypes.size() + 1;
            for (int i = 0; i < count; i++) {
                int[] rect = subTypeRect(i, count);
                if (inside(mouseX, mouseY, rect[0], FILTER_Y, rect[1], CHIP_HEIGHT)) {
                    subCategoryFilter = i == 0 ? null : subTypes.get(i - 1);
                    scrollOffset = 0;
                    return true;
                }
            }
        }
        // bar texture-style cycler (issue #127): header line 2 right
        if (styleCyclerVisible()) {
            int x = this.width - 8 - STYLE_PILL_WIDTH;
            if (inside(mouseX, mouseY, x, STYLE_PILL_Y, STYLE_PILL_WIDTH, 14)) {
                cycleStyle();
                return true;
            }
        }
        // slot cells (bottom stack)
        int slotCount = target == null ? 0 : target.slotCount();
        int cellWidth = slotWidth(slotCount, this.width);
        int y = slotY();
        for (int i = 0; i < slotCount; i++) {
            if (inside(mouseX, mouseY, LIST_LEFT + i * cellWidth, y,
                    cellWidth - 1, SLOT_HEIGHT)) {
                selectedSlot = i;
                return true;
            }
        }
        // clear button (slot-detail line, right end)
        if (target != null) {
            int[] clear = clearRect();
            if (inside(mouseX, mouseY, clear[0], clear[1], clear[2], clear[3])) {
                sendAssignment("");
                return true;
            }
        }
        // prev/next bar buttons (issue #157): the hint-bar line, same list
        // as the header chip; a single-bar pair stays inert but owns its rects
        List<ResourceLocation> navBars = manageableBars();
        if (!navBars.isEmpty()) {
            int[][] navs = barNavRects(this.width, this.height, menuStylePillVisible());
            for (int i = 0; i < navs.length; i++) {
                int[] rect = navs[i];
                if (inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3])) {
                    if (navBars.size() >= 2) {
                        if (i == 0) {
                            previousBar();
                        } else {
                            cycleBar();
                        }
                    }
                    return true;
                }
            }
        }
        // ability rows
        List<MenuQuery.Entry> rows = visibleRows();
        int listBottom = this.height - LIST_BOTTOM_PAD;
        int relative = rowIndexAt((int) mouseY, LIST_TOP, listBottom, ROW_HEIGHT);
        if (relative >= 0) {
            int offset = relative + scrollOffset;
            if (offset < rows.size()) {
                MenuQuery.Entry row = rows.get(offset);
                // the tooltip's Enable/Disable button owns its rect first
                // (issue #148): pressing it must never also assign the row
                int rowY = LIST_TOP + relative * ROW_HEIGHT;
                int[] toggleButton = toggleButtonHit(row, rowY, listBottom);
                if (toggleButton != null && inside(mouseX, mouseY, toggleButton[0],
                        toggleButton[1], toggleButton[2], toggleButton[3])) {
                    sendToggle(row.id());
                    return true;
                }
                sendAssignment(row.id().toString());
                return true;
            }
        }
        return false;
    }

    /**
     * The Enable/Disable button's screen rect for a hovered row, or null
     * when the row presents no toggle - the shared geometry (panel height,
     * flip and toggle line) guarantees the hit matches what was drawn.
     */
    @Nullable
    private int[] toggleButtonHit(MenuQuery.Entry row, int rowY, int listBottom) {
        Optional<AbilityPresentation> presentation = presentationOf(row.id());
        if (presentation.isEmpty() || presentation.get().toggle() == null) {
            return null;
        }
        boolean stats = !row.definition().stats().isEmpty();
        int fieldLines = presentation.get().fields().size();
        boolean description = row.definition().description().isPresent();
        int panelY = panelYFor(rowY,
                panelHeight(stats, fieldLines, true, description), listBottom);
        return toggleButtonRect(panelY, this.width - 16, stats, fieldLines);
    }

    /** The Enable/Disable button's rect inside the panel (draw and hit share it). */
    private int[] toggleButtonRect(int panelY, int listWidth, boolean stats, int fieldLines) {
        return new int[] {LIST_LEFT + listWidth - 60,
                panelY + toggleLineOffset(stats, fieldLines), 56, 12};
    }

    /** The panel's Enable/Disable button: a MENU-source activation, no slot (#148). */
    private void sendToggle(ResourceLocation abilityId) {
        if (CoreActivationCompat.isPresent()) {
            CoreActivationCompat.activate(abilityId, ActivationSource.MENU,
                    ActivationRequest.NO_SLOT, null, null);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (scrollY > 0) {
            scrollOffset--;
        } else if (scrollY < 0) {
            scrollOffset++;
        }
        return true; // the wheel belongs to the list while the menu is open
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The menu key closes - unless the search box is focused and the
        // player is typing that character into their search.
        if (keyCode == HotbarKeybinds.OPEN_MENU.getKey().getValue()
                && (searchBox == null || !searchBox.isFocused())) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // The remembered filters are CLIENT config; bar contents never are.
        HotbarClientConfig.MENU_SUBCATEGORY.set(subCategoryFilter == null ? "" : subCategoryFilter);
        HotbarClientConfig.MENU_TAB.set(selectedTab == null ? "" : selectedTab.toString());
        HotbarClientConfig.SPEC.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // managing the bar must not freeze the world
    }

    /** Assign or clear the selected slot of the named bar (server-validated). */
    private void sendAssignment(String abilityId) {
        if (selectedBar == null) {
            return;
        }
        PacketDistributor.sendToServer(
                new AssignAbilityPayload(selectedSlot, abilityId, selectedBar));
    }

    /**
     * One step through the manageable list (issue #157): a missing or
     * stale selection enters forward at the first bar, backward at the
     * last; otherwise floor-mod wrap. -1 for an empty list. Package-
     * private for the fixture.
     */
    static int cycleIndex(int current, int size, int delta) {
        if (size <= 0) {
            return -1;
        }
        if (current < 0 || current >= size) {
            return delta >= 0 ? 0 : size - 1;
        }
        return Math.floorMod(current + delta, size);
    }

    /** Cycles the target bar forward (the header chip and the ▶ button). */
    private void cycleBar() {
        cycleBarBy(1);
    }

    /** Cycles the target bar backward with wrap-around (the ◀ button). */
    private void previousBar() {
        cycleBarBy(-1);
    }

    /** The shared cycle step: chip and buttons reset slot/scroll identically. */
    private void cycleBarBy(int delta) {
        List<ResourceLocation> bars = manageableBars();
        if (bars.isEmpty()) {
            return;
        }
        int current = selectedBar == null ? -1 : bars.indexOf(selectedBar);
        int index = cycleIndex(current, bars.size(), delta);
        if (index >= 0) {
            selectBar(bars.get(index));
        }
    }

    /** Selects a target bar exactly as the chip always has: reset slot+scroll. */
    private void selectBar(ResourceLocation bar) {
        selectedBar = bar;
        selectedSlot = 0;
        scrollOffset = 0;
    }

    private List<ResourceLocation> manageableBars() {
        return MenuQuery.manageableBars(HotbarConfig.effectiveHotbarCount(), HotbarRegistry.all());
    }

    /**
     * The pool after core's visibility contract (core#124, hotbar#153).
     * Reads Minecraft.getInstance() because the constructor calls this
     * before Screen.minecraft exists (alpha.72's crash: the saved-filter
     * validation ran against a null field the moment the menu opened).
     */
    private Map<ResourceLocation, AbilityDefinition> menuPool() {
        return MenuQuery.visiblePool(AbilityDiscovery.entries(),
                Minecraft.getInstance().player);
    }

    private List<MenuQuery.Entry> visibleRows() {
        // Visibility comes from core's contract via menuPool; unlock greys at
        // draw (#135); the selected tab narrows here (#154) - namespace
        // filtering moved into registered tabs, so that parameter is null.
        MenuTabs.MenuTab tab = selectedTab == null ? null
                : MenuTabs.get(selectedTab).orElse(null);
        return MenuQuery.visibleEntries(menuPool(), selectedBar,
                searchBox == null ? "" : searchBox.getValue(), null, subCategoryFilter,
                null, null,
                id -> true,
                tab == null ? null : tab.accepts(),
                definition -> Component.translatable(definition.displayNameKey()));
    }

    @Nullable
    private Bar targetBar(BarState state) {
        if (selectedBar == null) {
            return null;
        }
        for (Bar bar : state.bars()) {
            if (bar.id().equals(selectedBar)) {
                return bar;
            }
        }
        return null;
    }

    /** The bottom stack's slot-cell row top (shared with hit-testing). */
    private int slotY() {
        return this.height - 48;
    }

    /**
     * The hint-bar line's prev/next bar-button rects (issue #157): right
     * side, immediately left of the menu-style pill when it shows. Draw and
     * hit-test share it so they cannot drift. Package-private for the
     * geometry fixture.
     */
    static int[][] barNavRects(int screenWidth, int screenHeight, boolean stylePillVisible) {
        int rightEdge = stylePillVisible
                ? screenWidth - 8 - MENU_STYLE_PILL_WIDTH - 4
                : screenWidth - 8;
        int prevX = rightEdge - (BAR_NAV_SIZE * 2 + BAR_NAV_GAP);
        int y = screenHeight - 15;
        return new int[][] {
                {prevX, y, BAR_NAV_SIZE, BAR_NAV_SIZE},
                {prevX + BAR_NAV_SIZE + BAR_NAV_GAP, y, BAR_NAV_SIZE, BAR_NAV_SIZE}};
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static String costText(AbilityDefinition definition) {
        var cost = RepresentativeCost.of(definition);
        if (cost == null) {
            return "";
        }
        double amount = cost.amount();
        String formatted = amount == Math.floor(amount) ? String.valueOf((long) amount)
                : String.valueOf(amount);
        return formatted + " " + cost.resourceId();
    }

    @Nullable
    private static ResourceLocation parseFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ResourceLocation.parse(raw.trim());
        } catch (RuntimeException malformed) {
            return null; // a corrupt pref must not stop the menu from opening
        }
    }

    @Nullable
    private static String emptyToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }
}
