package org.bitwisemadness.abilityhotbar.client;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    private static final int ROW_HEIGHT = 18;
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
    private static final int SLOT_HEIGHT = 16;

    private ResourceLocation selectedBar;
    private int selectedSlot;
    @Nullable
    private ResourceLocation categoryFilter;
    @Nullable
    private String tagFilter;
    @Nullable
    private String subCategoryFilter;
    @Nullable
    private String providerFilter;
    private int scrollOffset;
    private EditBox searchBox;

    public AbilityMenuScreen() {
        super(Component.translatable("ability_hotbar.menu.title"));
        // Loaded once per open (a resize re-runs init, not this constructor),
        // so mid-session filters are not clobbered by the last saved pair.
        categoryFilter = parseFilter(HotbarClientConfig.MENU_CATEGORY.get());
        tagFilter = emptyToNull(HotbarClientConfig.MENU_TAG.get());
        subCategoryFilter = emptyToNull(HotbarClientConfig.MENU_SUBCATEGORY.get());
        providerFilter = emptyToNull(HotbarClientConfig.MENU_PROVIDER.get());
        // A corrupt pref must never blank the menu (the parseFilter posture):
        // filters that name nothing in the pool drop to "no filter".
        var pool = AbilityDiscovery.entries();
        if (providerFilter != null && !MenuQuery.providers(pool).contains(providerFilter)) {
            providerFilter = null;
        }
        if (subCategoryFilter != null && (categoryFilter == null
                || !MenuQuery.subCategories(pool, categoryFilter).contains(subCategoryFilter))) {
            subCategoryFilter = null;
        }
        BarState state = ClientBarState.get();
        List<ResourceLocation> bars = manageableBars();
        ResourceLocation active = state == null ? null : state.activeBarId();
        selectedBar = active != null && bars.contains(active) ? active
                : bars.isEmpty() ? null : bars.get(0);
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
        drawFilterChips(gui, mouseX, mouseY);
        drawAbilityList(gui, mouseX, mouseY, state);
        drawSlotCells(gui, mouseX, mouseY, target);
        drawSlotDetail(gui, mouseX, mouseY, target);
        drawHintBar(gui);
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

    /** The collapsed filter strip: category and tag cycle chips (#27). */
    private void drawFilterChips(GuiGraphics gui, int mouseX, int mouseY) {
        MenuStyle style = style();
        int[] category = categoryChipRect();
        boolean categoryHover = inside(mouseX, mouseY,
                category[0], FILTER_Y, category[1], CHIP_HEIGHT);
        MenuChrome.glass(gui, category[0], FILTER_Y, category[1], CHIP_HEIGHT,
                style, categoryHover);
        gui.drawString(this.font, categoryChipLabel(), category[0] + 6, FILTER_Y + 4,
                categoryHover ? style.text() : style.action());
        int[] tag = tagChipRect();
        boolean tagHover = inside(mouseX, mouseY, tag[0], FILTER_Y, tag[1], CHIP_HEIGHT);
        MenuChrome.glass(gui, tag[0], FILTER_Y, tag[1], CHIP_HEIGHT, style, tagHover);
        gui.drawString(this.font, tagChipLabel(), tag[0] + 6, FILTER_Y + 4,
                tagHover ? style.text() : style.action());
    }

    /** The category chip's rect for the current label (shared with hits). */
    private int[] categoryChipRect() {
        int width = Math.min(this.font.width(categoryChipLabel()) + 12,
                Math.max(20, this.width - 8 - 156));
        return new int[] {156, width};
    }

    /** The tag chip's rect, right of the category chip (shared with hits). */
    private int[] tagChipRect() {
        int x = categoryChipRect()[0] + categoryChipRect()[1] + 6;
        int width = Math.min(this.font.width(tagChipLabel()) + 12,
                Math.max(20, this.width - 8 - x));
        return new int[] {x, width};
    }

    private Component categoryChipLabel() {
        if (categoryFilter == null) {
            return withChevron(Component.translatable("ability_hotbar.menu.category_any"));
        }
        String name = MenuLabels.category(categoryFilter).getString();
        if (subCategoryFilter != null) {
            // the hierarchical cycle's composite label: "Skill / Unique" (#142)
            name = name + " / " + MenuLabels.subcategory(subCategoryFilter).getString();
        }
        return withChevron(Component.translatable("ability_hotbar.menu.category_fmt",
                Component.literal(name)));
    }

    private Component tagChipLabel() {
        Component base = tagFilter == null
                ? Component.translatable("ability_hotbar.menu.tag_any")
                : Component.translatable("ability_hotbar.menu.tag_fmt",
                        MenuLabels.tag(tagFilter));
        return withChevron(base);
    }

    /** The cycle affordance on every filter chip (the chip owns a click). */
    private static Component withChevron(Component base) {
        return Component.literal(base.getString() + " >");
    }

    /**
     * The header's provider tab strip (hotbar#142): {@code All} plus one tab
     * per provider namespace, on the style pill's row - the menu splits its
     * pool per provider without a fourth filter chip. Tabs shrink to fit
     * beside the pill; the active tab is the current provider filter.
     */
    private void drawProviderTabs(GuiGraphics gui, int mouseX, int mouseY) {
        List<String> providers = MenuQuery.providers(AbilityDiscovery.entries());
        if (providers.isEmpty()) {
            return;
        }
        MenuStyle style = style();
        int tabWidth = providerTabWidth(providers);
        int x = LIST_LEFT;
        for (int i = -1; i < providers.size(); i++) {
            boolean all = i < 0;
            String namespace = all ? null : providers.get(i);
            boolean active = all ? providerFilter == null : namespace.equals(providerFilter);
            boolean hovered = inside(mouseX, mouseY, x, STYLE_PILL_Y, tabWidth - 2, 14);
            if (active) {
                gui.fill(x, STYLE_PILL_Y, x + tabWidth - 2, STYLE_PILL_Y + 14,
                        MenuStyle.alpha(style.accent(), 30));
            } else if (hovered) {
                gui.fill(x, STYLE_PILL_Y, x + tabWidth - 2, STYLE_PILL_Y + 14,
                        MenuStyle.alpha(style.raised(), 55));
            }
            Component label = all
                    ? Component.translatable("ability_hotbar.menu.provider_all")
                    : MenuLabels.provider(namespace);
            String text = this.font.plainSubstrByWidth(label.getString(), tabWidth - 6);
            gui.drawString(this.font, text, x + 3, STYLE_PILL_Y + 3,
                    active ? style.text() : hovered ? style.text() : style.muted());
            x += tabWidth;
        }
    }

    /** One provider tab's width: capped, floored, shrunk to fit beside the style pill. */
    private int providerTabWidth(List<String> providers) {
        int available = (this.width - 16)
                - (styleCyclerVisible() ? STYLE_PILL_WIDTH + 4 : 0);
        return Math.min(72, Math.max(24, available / (providers.size() + 1)));
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
            gui.drawString(this.font, slotLabel(i + 1), x + 3, y + 4,
                    selected ? style.text() : style.muted());
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
     * The width of one slot cell: capped at 40px, floored at 1px.
     * Package-private for the geometry fixture (draw and hit-test share it).
     */
    static int slotWidth(int slotCount, int screenWidth) {
        if (slotCount <= 0) {
            return 0;
        }
        return Math.min(40, Math.max(1, (screenWidth - 16) / slotCount));
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
                gui.blit(icon, LIST_LEFT + 2, y, 0, 0, 16, 16, 16, 16);
            }
            Component displayName = Component.translatable(row.definition().displayNameKey());
            String name = displayName.getString();
            gui.drawString(this.font, name, LIST_LEFT + 22, y + 5, style.text());
            String cost = costText(row.definition());
            if (!cost.isEmpty()) {
                gui.drawString(this.font, cost,
                        LIST_LEFT + listWidth - this.font.width(cost), y + 5,
                        style.action());
            }
            drawValueStrip(gui, row, name, cost, y, listWidth, style);
            // the cyan row underline (their list chrome)
            gui.fill(LIST_LEFT, y + ROW_HEIGHT - 2, LIST_LEFT + listWidth, y + ROW_HEIGHT - 1,
                    MenuStyle.alpha(style.accent(), 45));
        }
        if (hoveredIndex >= 0) {
            // Drain the rows' queued batches before the overlay: fills, icon
            // blits and text flush per render type, so row content still in
            // flight would paint over the panel's background (hotbar#140).
            gui.flush();
            drawRowMeta(gui, rows.get(hoveredIndex), hoveredY, listWidth, listBottom);
        }
    }

    /**
     * The row's at-a-glance live value (issue #148): the first PROGRESS
     * field's value text + a mini meter, drawn between the name and the
     * cost — and only when it fits, so long names never collide (the full
     * presentation always shows in the tooltip).
     */
    private void drawValueStrip(GuiGraphics gui, MenuQuery.Entry row, String name, String cost,
            int y, int listWidth, MenuStyle style) {
        PresentationField progress = presentationOf(row.id())
                .flatMap(present -> present.fields().stream()
                        .filter(field -> field.kind() == PresentationField.Kind.PROGRESS)
                        .findFirst())
                .orElse(null);
        if (progress == null) {
            return;
        }
        int costLeft = cost.isEmpty() ? LIST_LEFT + listWidth
                : LIST_LEFT + listWidth - this.font.width(cost) - 6;
        int meterWidth = 24;
        int meterX = costLeft - meterWidth;
        int valueWidth = progress.value().isEmpty() ? 0 : this.font.width(progress.value()) + 4;
        int startX = LIST_LEFT + 22 + this.font.width(name) + 6;
        if (meterX - valueWidth < startX) {
            return; // no room: the tooltip carries the full presentation
        }
        if (!progress.value().isEmpty()) {
            gui.drawString(this.font, progress.value(), meterX - valueWidth, y + 5,
                    style.muted());
        }
        MenuChrome.meter(gui, meterX, y + 7, meterWidth, 5, progress.progress(), style);
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
        // provider tabs (header line 2, hotbar#142): All + one per provider
        List<String> providers = MenuQuery.providers(AbilityDiscovery.entries());
        if (!providers.isEmpty()) {
            int tabWidth = providerTabWidth(providers);
            int tabX = LIST_LEFT;
            for (int i = -1; i < providers.size(); i++) {
                if (inside(mouseX, mouseY, tabX, STYLE_PILL_Y, tabWidth - 2, 14)) {
                    providerFilter = i < 0 ? null : providers.get(i);
                    scrollOffset = 0;
                    return true;
                }
                tabX += tabWidth;
            }
        }
        // category / tag chips cycle the filters
        int[] category = categoryChipRect();
        if (inside(mouseX, mouseY, category[0], FILTER_Y, category[1], CHIP_HEIGHT)) {
            cycleCategory();
            return true;
        }
        int[] tag = tagChipRect();
        if (inside(mouseX, mouseY, tag[0], FILTER_Y, tag[1], CHIP_HEIGHT)) {
            cycleTag();
            return true;
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
                    ActivationRequest.NO_SLOT, null);
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
        HotbarClientConfig.MENU_CATEGORY.set(categoryFilter == null ? "" : categoryFilter.toString());
        HotbarClientConfig.MENU_TAG.set(tagFilter == null ? "" : tagFilter);
        HotbarClientConfig.MENU_SUBCATEGORY.set(subCategoryFilter == null ? "" : subCategoryFilter);
        HotbarClientConfig.MENU_PROVIDER.set(providerFilter == null ? "" : providerFilter);
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

    /** Cycles the header's target-bar chip through every manageable bar. */
    private void cycleBar() {
        List<ResourceLocation> bars = manageableBars();
        if (bars.isEmpty()) {
            return;
        }
        int index = selectedBar == null ? -1 : bars.indexOf(selectedBar);
        selectedBar = bars.get((index + 1) % bars.size());
        selectedSlot = 0;
        scrollOffset = 0;
    }

    private void cycleCategory() {
        // the hierarchical cycle (#142): any -> category -> its subcategories
        List<MenuQuery.CategoryStep> steps = MenuQuery.categorySteps(AbilityDiscovery.entries());
        if (steps.isEmpty()) {
            return;
        }
        int index = -1; // a state no step matches (corrupt pref) resets to "any"
        for (int i = 0; i < steps.size(); i++) {
            MenuQuery.CategoryStep step = steps.get(i);
            if (java.util.Objects.equals(step.category(), categoryFilter)
                    && java.util.Objects.equals(step.subTag(), subCategoryFilter)) {
                index = i;
                break;
            }
        }
        MenuQuery.CategoryStep next = steps.get((index + 1) % steps.size());
        categoryFilter = next.category();
        subCategoryFilter = next.subTag();
        scrollOffset = 0;
    }

    private void cycleTag() {
        List<String> tags = MenuQuery.tags(AbilityDiscovery.entries());
        if (tags.isEmpty()) {
            tagFilter = null;
            return;
        }
        int index = tagFilter == null ? -1 : tags.indexOf(tagFilter);
        tagFilter = index + 1 >= tags.size() ? null : tags.get(index + 1);
        scrollOffset = 0;
    }

    private List<ResourceLocation> manageableBars() {
        return MenuQuery.manageableBars(HotbarConfig.effectiveHotbarCount(), HotbarRegistry.all());
    }

    private List<MenuQuery.Entry> visibleRows() {
        // hotbar#135's owner call: only unlocked abilities are visible -
        // proven-unlearned ids filter out, unknown always shows (UnlockRule).
        return MenuQuery.visibleEntries(AbilityDiscovery.entries(), selectedBar,
                searchBox == null ? "" : searchBox.getValue(), categoryFilter, subCategoryFilter,
                tagFilter, providerFilter,
                id -> !UnlockRule.provenUnlearned(
                        AbilityUnlocks.isLearned(this.minecraft.player, id)),
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
        return this.height - 32;
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
