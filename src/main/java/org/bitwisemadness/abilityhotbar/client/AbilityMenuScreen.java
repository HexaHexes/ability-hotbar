package org.bitwisemadness.abilityhotbar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.AbilityModes;
import org.bitwisemadness.abilitycore.api.AbilityPresentation;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.IconProviders;
import org.bitwisemadness.abilitycore.api.PresentationField;
import org.bitwisemadness.abilitycore.api.PresentationProviders;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilitycore.api.Toggle;
import org.bitwisemadness.abilitycore.config.ConfigDiagnostics;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilityhotbar.UnlockRule;
import org.bitwisemadness.abilityhotbar.compat.CoreActivationCompat;
import org.bitwisemadness.abilityhotbar.compat.CoreMasteryCompat;
import org.bitwisemadness.abilityhotbar.config.HotbarClientConfig;
import org.bitwisemadness.abilityhotbar.config.HotbarConfig;
import org.bitwisemadness.abilityhotbar.content.ContentPlan;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProvider;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProviders;
import org.bitwisemadness.abilityhotbar.network.AssignAbilityPayload;
import org.bitwisemadness.abilityhotbar.network.ReorderSlotsPayload;
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
 * hotbar#27, reworked by the owner's nine-point brief): a header band with
 * the title top-left and the slot meter top-right, one collapsed filter
 * strip (search pill + subcategory tabs), the ability list narrowed to the
 * left two thirds with the <b>rightmost third reserved as the detail pane</b>
 * (name, description, status or mastery bonuses, activation - drawn
 * last, so list chrome can never paint over it), and a bottom stack of
 * chamfered slot cells that stretches across the width - up to nine per
 * row (18 slots split into two aligned rows), filled cells showing the
 * ability's icon plus its truncated name, numbers only on empty cells,
 * the selected cell reading darker than the hover lift - beside the bar
 * chip column (clear pill below it, the prev/next pair vertical) over the
 * hint bar. Toggleable rows carry their state under the name with an
 * Enable/Disable button at the row's right end. A filtered row click
 * assigns to the selected slot; the clear pill empties it - all the same
 * server-validated {@link AssignAbilityPayload} naming the target bar
 * (ADR-012 id targeting), so the screen is never authoritative: the
 * snapshot echo repaints it with server truth.</p>
 *
 * <p>Search, the selected subcategory tab and the selected tab come from
 * {@link MenuQuery}; the chosen filters persist as CLIENT config ({@code
 * menu.lastSubCategory}/{@code menu.lastTab}) while bar contents stay server
 * data. Registered non-assignable bars are never listed and the skip-empty
 * cycling option does not apply here (ADR-012). Opens from the menu-key
 * seam, closes with the same key (when the search box is not focused) or
 * Esc.</p>
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

    /** The shipped list-row height - the live value is menu.rowHeight. */
    private static final int DEFAULT_ROW_HEIGHT = 36;

    /**
     * The live list-row height ({@code menu.rowHeight}), read through
     * core's diagnostics helper so a headless fixture (spec unloaded)
     * runs on the shipped default instead of throwing.
     */
    private static int menuRowHeight() {
        return ConfigDiagnostics.readOrDefault("menu.rowHeight",
                () -> HotbarClientConfig.ROW_HEIGHT.get(), DEFAULT_ROW_HEIGHT);
    }
    private static final int LIST_LEFT = 8;
    private static final int INERT_COLOR = 0xFFFF5555;
    /** The filter strip row (search pill + subcategory tabs). */
    private static final int FILTER_Y = 40;
    private static final int SEARCH_X = 8;
    private static final int SEARCH_WIDTH = 140;
    /** The bottom bar chip beside the slot strip (cycles the bar on click). */
    private static final int BAR_CHIP_HEIGHT = 14;
    /** The clear pill's width in the chip column below the indicator. */
    private static final int CLEAR_WIDTH = 56;
    private static final int METER_WIDTH = 44;
    /** The bar texture-style cycler pill (issue #127), header line 2 right. */
    private static final int STYLE_PILL_WIDTH = 110;
    private static final int STYLE_PILL_Y = 21;
    /** The menu-style cycler pill (issue #129), hint-bar right end. */
    private static final int MENU_STYLE_PILL_WIDTH = 120;
    /**
     * The slot strip: double-height cells, at most {@link #MAX_ROW_SLOTS}
     * per row (owner's brief: 18 slots split into two rows of nine).
     */
    private static final int MAX_ROW_SLOTS = 9;
    /** The shipped slot-cell height - the live value is menu.slotCellHeight. */
    private static final int DEFAULT_CELL_HEIGHT = 32;

    /**
     * The live slot-cell height ({@code menu.slotCellHeight}, spec floor
     * 30 - the three-line cell needs it), read through core's
     * diagnostics helper so a headless fixture (spec unloaded) runs on
     * the shipped default instead of throwing.
     */
    private static int menuCellHeight() {
        return ConfigDiagnostics.readOrDefault("menu.slotCellHeight",
                () -> HotbarClientConfig.SLOT_CELL_HEIGHT.get(), DEFAULT_CELL_HEIGHT);
    }
    /** The gap between the two slot rows. */
    private static final int SLOT_GAP = 2;
    /** The vanilla hotbar selection sprite - empty-slot/drop indicator. */
    private static final ResourceLocation VANILLA_SELECTION =
            ResourceLocation.fromNamespaceAndPath("minecraft", "hud/hotbar_selection");
    /** The prev/next bar buttons beside the strip (issue #157): 14px cells. */
    private static final int BAR_NAV_SIZE = 14;
    private static final int BAR_NAV_GAP = 2;

    /**
     * The filter row's pill height: the hotbar slot pills' own height
     * (owner's brief - the search bar and subcategory pills match them).
     */
    private static int filterPillHeight() {
        return menuCellHeight();
    }

    /** The list starts 6px under the filter strip (#141) - grown with its pills. */
    static int listTop() {
        return FILTER_Y + filterPillHeight() + 6;
    }

    /** The subcategory strip's scroll position, clamped to its overflow. */
    static int clampSubScroll(int scroll, int count, int viewportWidth, int pillWidth) {
        int max = Math.max(0, count * pillWidth - viewportWidth);
        return Math.max(0, Math.min(max, scroll));
    }

    private ResourceLocation selectedBar;
    private int selectedSlot;
    @Nullable
    private String subCategoryFilter;
    // The subcategory filter belongs to the tab it was picked on (owner's
    // brief): each tab remembers its own choice for this session, and a
    // tab switch swaps to that tab's filter instead of leaking one.
    private final Map<ResourceLocation, String> subFilterMemory = new HashMap<>();
    @Nullable
    private ResourceLocation selectedTab;
    private int scrollOffset;
    private EditBox searchBox;
    // The detail pane's sticky subject: the last hovered ability (null
    // until the first hover - the pane then shows the selected slot's
    // ability), set by the list pass and drawn LAST in render().
    @Nullable
    private ResourceLocation detailId;
    private int detailScroll;
    private int detailScrollMax;
    // Press-drag scrolling inside the pane (owner's brief): a press over
    // the pane starts a drag; dragY accumulates into whole 9px lines.
    private boolean detailDragging;
    private double detailDragRemainder;
    // The last clicked ability: its row keeps the hover treatment after
    // the pointer leaves (owner's brief - visual assignment feedback).
    @Nullable
    private ResourceLocation selectedRowId;
    // Drag-to-assign (owner's brief): a press on a row arms it, movement
    // past the threshold promotes it to a drag, and the release drops it
    // on a slot cell - in place it stays the old click-assign.
    @Nullable
    private ResourceLocation pressedRowId;
    @Nullable
    private ResourceLocation dragAbilityId;
    private double pressStartX;
    private double pressStartY;
    // The subcategory strip's scroll (owner: arrows + drag-scroll): a
    // press in the viewport arms it, movement past the threshold turns
    // it into a horizontal scroll, release activates an un-dragged pill
    private int subStripScroll;
    private boolean subStripArmed;
    private boolean subStripDragging;
    private double subStripPressX;
    private int subStripScrollStart;
    private int pressedSubPill = -1;
    // The row's mode preview (owner: the row's mode button never writes
    // a hotbar slot) - a menu-local cycle, scoped to this screen's life
    private final Map<ResourceLocation, String> rowModePreview = new HashMap<>();
    // Slot drag-reorder (owner: drag one slot onto another): a press on a
    // filled slot arms it, the threshold promotes it to a drag whose ghost
    // rides the cursor, and the release asks the server to swap the cells
    private int pressedSlot = -1;
    private int dragSlot = -1;

    public AbilityMenuScreen() {
        super(Component.translatable("ability_hotbar.menu.title"));
        // Loaded once per open (a resize re-runs init, not this constructor),
        // so mid-session filters are not clobbered by the last saved pair.
        selectedTab = parseFilter(HotbarClientConfig.MENU_TAB.get());
        BarState state = ClientBarState.get();
        List<ResourceLocation> bars = manageableBars();
        ResourceLocation active = state == null ? null : state.activeBarId();
        selectedBar = active != null && bars.contains(active) ? active
                : bars.isEmpty() ? null : bars.get(0);
        // a removed, unknown or now-empty tab falls back to All (owner:
        // empty tabs are not displayed - and never stay selected); the
        // dead tab's subcategory choice is kept for its return
        revalidateSelectedTab();
        // The pair parse runs after the tab fallback so a dead tab id can
        // never leave its filter behind on All (parseFilter posture).
        subCategoryFilter = parseSubCategory(
                HotbarClientConfig.MENU_SUBCATEGORY.get(), selectedTab);
        // A corrupt pref must never blank the menu (the parseFilter posture):
        // a subcategory naming nothing under the selected tab drops to All.
        if (subCategoryFilter != null && !subTypeTags().contains(subCategoryFilter)) {
            subCategoryFilter = null;
        }
    }

    /**
     * Parses the saved subcategory pref (owner's brief: the filter belongs
     * to one tab): the pair {@code <tab>;<filter>} applies only when its
     * owner is the restored tab - All is the empty owner - and a bare
     * legacy value keeps its old meaning (validated by the caller).
     */
    @Nullable
    private static String parseSubCategory(String raw, @Nullable ResourceLocation tab) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int cut = raw.indexOf(';');
        if (cut < 0) {
            return emptyToNull(raw);
        }
        String owner = raw.substring(0, cut);
        boolean ownerAll = owner.isEmpty();
        if (ownerAll != (tab == null) || !ownerAll && !owner.equals(tab.toString())) {
            return null;
        }
        return emptyToNull(raw.substring(cut + 1));
    }

    @Override
    protected void init() {
        // Borderless: vanilla draws at the RAW widget x/y (no centering
        // or inset - EditBox.renderWidget), so the widget itself sits
        // where the text should: centred in the pill, four pixels in
        int textY = FILTER_Y + (filterPillHeight() - 9) / 2;
        searchBox = new EditBox(this.font, SEARCH_X + 4, textY, SEARCH_WIDTH - 4,
                FILTER_Y + filterPillHeight() - textY,
                Component.translatable("ability_hotbar.menu.search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("ability_hotbar.menu.search"));
        searchBox.setResponder(value -> this.scrollOffset = 0);
        searchBox.setTextColor(style().text());
        searchBox.setTextColorUneditable(style().muted());
        addRenderableWidget(searchBox);
        if (this.minecraft != null && this.minecraft.player != null) {
            // The pane's mastery section (REQ-CORE-12): solicited on open,
            // throttled against resize re-inits - core never pushes, so the
            // section appears with the answered snapshot and never guesses.
            CoreMasteryCompat.request(this.minecraft.player.getUUID());
        }
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
        MenuChrome.glass(gui, SEARCH_X - 2, FILTER_Y, SEARCH_WIDTH + 4, filterPillHeight(),
                style, false);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick); // backdrop + the search box
        // owner: empty tabs and empty subcategories are not displayed -
        // both revalidated before anything reads them, so a vanished tab
        // falls to All and an emptied subcategory pick clears with it
        revalidateSelectedTab();
        if (subCategoryFilter != null && !subTypeTags().contains(subCategoryFilter)) {
            subCategoryFilter = null;
        }
        MenuStyle style = style();
        // title, top-left inside the frame, with the accent underline
        Component title = Component.translatable("ability_hotbar.menu.title");
        gui.drawString(this.font, title, LIST_LEFT, 6, style.text());
        gui.fill(LIST_LEFT, 16, LIST_LEFT + this.font.width(title), 17, style.accent());
        if (menuStylePillVisible()) {
            drawMenuStylePill(gui, mouseX, mouseY);
        }

        BarState state = ClientBarState.get();
        // a stale hover never survives an early return (no state = no strip
        // = no pill tooltip)
        stripTooltip = null;
        if (state == null) {
            gui.drawString(this.font, Component.translatable("ability_hotbar.menu.waiting"),
                    LIST_LEFT, listTop(), style.muted());
            return;
        }

        Bar target = targetBar(state);
        // the selected tab's provider bar (owner: the ISS tab shows its
        // spellbook's cells instead of the assignable bar's) - computed
        // once here so the strip, its chrome and the mouse agree
        stripPlan = providerPlan();
        drawHeader(gui, target);
        drawProviderTabs(gui, mouseX, mouseY);
        if (styleCyclerVisible()) {
            drawStylePill(gui, mouseX, mouseY);
        }
        drawSubcategoryTabs(gui, mouseX, mouseY);
        drawAbilityList(gui, mouseX, mouseY, state);
        drawSlotCells(gui, mouseX, mouseY, target);
        drawClearButton(gui, mouseX, mouseY, target);
        drawHintBar(gui);
        drawBarNav(gui, mouseX, mouseY, target);
        // The detail pane draws last (flush first): the rightmost third is
        // its own region, and nothing can ever paint over it (brief).
        gui.flush();
        drawDetailPane(gui, target);
        // the dragged ability's icon rides the cursor (owner's drag brief)
        if (dragAbilityId != null) {
            drawDragGhost(gui, mouseX, mouseY);
        }
        // The hovered provider pill's tooltip draws LAST (the owner's
        // provider toolbar brief): flushed first so it sits over the
        // detail pane and the drag ghost, never under them.
        if (stripTooltip != null && !stripTooltip.isEmpty()) {
            gui.flush();
            gui.renderComponentTooltip(this.font, stripTooltip, mouseX, mouseY);
        }
    }

    /** The drag affordance: the row's own icon at the cursor, 32px. */
    private void drawDragGhost(GuiGraphics gui, int mouseX, int mouseY) {
        if (dragAbilityId == null) {
            return;
        }
        var found = AbilityDiscovery.get(dragAbilityId);
        if (found.isEmpty()) {
            return;
        }
        // The documented icon order (core#62, hotbar#217): the provider's
        // answer wins, the definition's static icon is the fallback.
        ResourceLocation icon = IconProviders.iconFor(this.minecraft.player, dragAbilityId)
                .orElse(found.get().iconId());
        if (icon == null) {
            return;
        }
        gui.blit(icon, mouseX - 16, mouseY - 16, 0, 0, 32, 32, 32, 32);
    }

    /**
     * The header's right cluster: the slot meter alone - the used/total
     * count is gone and the bar chip moved beside the slot strip (owner's
     * nine-point brief).
     */
    private void drawHeader(GuiGraphics gui, @Nullable Bar target) {
        if (target == null || stripPlan != null) {
            return; // a provider strip shows its own bar, not this meter
        }
        MenuStyle style = style();
        float fraction = usedSlots(target) / (float) Math.max(1, target.slotCount());
        MenuChrome.meter(gui, this.width - 8 - METER_WIDTH, 8, METER_WIDTH, 7,
                fraction, style);
    }

    private Component barChipLabel() {
        List<ResourceLocation> bars = manageableBars();
        if (bars.isEmpty()) {
            return Component.empty();
        }
        return BarName.of(selectedBar == null ? bars.get(0) : selectedBar);
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
        int pillHeight = filterPillHeight();
        subStripScroll = clampSubScroll(subStripScroll, count, subViewportWidth(),
                subPillWidth());
        // the arrows step the strip one pill at a time (owner: buttons
        // before and after, wearing the bar-nav's own glyphs)
        drawSubStripArrow(gui, mouseX, mouseY, subStripX(), true, subStripScroll > 0);
        drawSubStripArrow(gui, mouseX, mouseY, subStripEnd() - BAR_NAV_SIZE, false,
                subStripScroll < clampSubScroll(Integer.MAX_VALUE, count, subViewportWidth(),
                        subPillWidth()));
        // the pills clip to their viewport: past it they scroll, never
        // overflow into the detail pane (owner's strip brief)
        int viewX = subViewportX();
        gui.enableScissor(viewX, FILTER_Y, viewX + subViewportWidth(), FILTER_Y + pillHeight);
        try {
            for (int i = 0; i < count; i++) {
                boolean all = i == 0;
                String typeTag = all ? null : types.get(i - 1);
                int[] rect = subTypeRect(i);
                boolean active = all ? subCategoryFilter == null : typeTag.equals(subCategoryFilter);
                boolean hovered = inside(mouseX, mouseY, rect[0], FILTER_Y, rect[1], pillHeight);
                MenuChrome.glass(gui, rect[0], FILTER_Y, rect[1], pillHeight, style,
                        active || hovered);
                Component label = all
                        ? Component.translatable("ability_hotbar.menu.subcategory_any")
                        : MenuLabels.subcategory(typeTag);
                // Provider chip icons (owner): the tab strip's identifier
                // rule on this strip - the All pill wears its category's
                // icon, or the All category's Nether Star
                int textX = rect[0] + 4;
                if (typeTag != null) {
                    MenuTabs.ChipIcon icon = MenuTabs.subCategoryIcon(typeTag).orElse(null);
                    if (icon != null) {
                        textX = drawIdentifier(gui, icon.iconId(), icon.icon(),
                                rect[0] + 4, FILTER_Y + (pillHeight - TAB_ICON) / 2, TAB_ICON);
                    }
                } else {
                    textX = drawAllSubcategoryIcon(gui, rect[0] + 4,
                            FILTER_Y + (pillHeight - TAB_ICON) / 2);
                }
                int budget = Math.max(4, rect[0] + rect[1] - 3 - textX);
                gui.drawString(this.font,
                        this.font.plainSubstrByWidth(label.getString(), budget),
                        textX, FILTER_Y + (pillHeight - 9) / 2,
                        active ? style.action() : hovered ? style.text() : style.muted());
            }
        } finally {
            gui.disableScissor();
        }
    }

    /**
     * The strip's arrow button: the bar-nav's 14px cell and glyph at the
     * row's height, dimmed and inert when it has nowhere to scroll.
     */
    private void drawSubStripArrow(GuiGraphics gui, int mouseX, int mouseY, int x,
            boolean left, boolean enabled) {
        MenuStyle style = style();
        int height = filterPillHeight();
        boolean hovered = enabled && inside(mouseX, mouseY, x, FILTER_Y, BAR_NAV_SIZE, height);
        MenuChrome.glass(gui, x, FILTER_Y, BAR_NAV_SIZE, height, style, hovered);
        if (!enabled) {
            gui.fill(x, FILTER_Y, x + BAR_NAV_SIZE, FILTER_Y + height,
                    MenuStyle.alpha(style.raised(), 60));
        }
        int glyph = !enabled ? MenuStyle.alpha(style.muted(), 140)
                : hovered ? MenuStyle.alpha(style.text(), 255)
                : MenuStyle.alpha(style.muted(), 255);
        drawBarNavGlyph(gui, x, FILTER_Y + (height - BAR_NAV_SIZE) / 2, left, glyph);
    }

    /**
     * The All subcategory pill's icon: its category's own (the selected
     * tab's identifier), or the All category's Nether Star when no
     * category is selected - a category without an icon stays text-only.
     */
    private int drawAllSubcategoryIcon(GuiGraphics gui, int x, int y) {
        if (selectedTab != null) {
            MenuTabs.MenuTab tab = MenuTabs.get(selectedTab).orElse(null);
            return tab != null && tab.hasIcon()
                    ? drawIdentifier(gui, tab.iconId(), tab.icon(), x, y, TAB_ICON)
                    : x;
        }
        return drawIdentifier(gui, null, new ItemStack(Items.NETHER_STAR), x, y, TAB_ICON);
    }

    /** The strip's left edge (right of the search pill). */
    private int subStripX() {
        return SEARCH_X + SEARCH_WIDTH + 6;
    }

    /** The pill viewport's left edge (after the prev arrow). */
    private int subViewportX() {
        return subStripX() + BAR_NAV_SIZE + 2;
    }

    /** The strip's right edge (the detail pane keeps its6px). */
    private int subStripEnd() {
        return detailX() - 6;
    }

    /** The visible pill width: the strip minus both arrows and their gaps. */
    private int subViewportWidth() {
        return Math.max(0, subStripEnd() - BAR_NAV_SIZE - 2 - subViewportX());
    }

    /**
     * One subcategory pill's step width: the hotbar slot pill's own cell
     * width (owner's brief - the strip matches the slots), with a
     * nine-slot bar's cell as the fallback while no bar is loaded.
     */
    private int subPillWidth() {
        BarState state = ClientBarState.get();
        Bar target = state == null ? null : targetBar(state);
        int slotCount = visibleSlotCount(target);
        int width = slotWidth(slotCount, this.width, clusterWidth());
        return width > 0 ? width : slotWidth(MAX_ROW_SLOTS, this.width, clusterWidth());
    }

    /**
     * One subcategory tab's rect (draw and hit share it), scroll-applied:
     * a fixed {@link #subPillWidth()} step drawn one pixel narrower - the
     * exact cell-to-cell parity with the hotbar slot pills, which draw at
     * {@code cellWidth - 1} (owner: the pills must match the slots).
     */
    private int[] subTypeRect(int index) {
        int step = subPillWidth();
        return new int[] {subViewportX() + index * step - subStripScroll, step - 1};
    }

    /** The pill under the point, or -1 when the point sits in a gap. */
    private int subPillAt(double mouseX, double mouseY, int count) {
        for (int i = 0; i < count; i++) {
            int[] rect = subTypeRect(i);
            if (inside(mouseX, mouseY, rect[0], FILTER_Y, rect[1], filterPillHeight())) {
                return i;
            }
        }
        return -1;
    }

    /** The arrow's click: one pill towards that end, when it can move. */
    private boolean stepSubStrip(double mouseX, double mouseY, boolean left, int count) {
        int x = left ? subStripX() : subStripEnd() - BAR_NAV_SIZE;
        if (!inside(mouseX, mouseY, x, FILTER_Y, BAR_NAV_SIZE, filterPillHeight())) {
            return false;
        }
        int next = subStripScroll + (left ? -subPillWidth() : subPillWidth());
        int clamped = clampSubScroll(next, count, subViewportWidth(), subPillWidth());
        if (clamped == subStripScroll) {
            return true; // inert but claimed: the press does nothing else
        }
        subStripScroll = clamped;
        return true;
    }

    /**
     * The subcategory tags with entries under the current tab (the strip's
     * tabs), in the tab's declared order when it declared one - listed
     * tags first, the rest alphabetical (the owner's reorder brief).
     */
    private List<String> subTypeTags() {
        List<String> order = selectedTab == null ? List.of()
                : MenuTabs.subCategoryOrder(selectedTab);
        return MenuQuery.subTypeTags(subTypeBasePool(), order);
    }

    /**
     * What the strip counts (owner's brief: empty subcategories are not
     * displayed): the rows after every OTHER filter - scope, search,
     * membership - but before the subcategory pick itself, so a pill whose
     * click would show nothing never draws, and the revalidation then
     * clears an active pick this base no longer carries.
     */
    private Map<ResourceLocation, AbilityDefinition> subTypeBasePool() {
        Map<ResourceLocation, AbilityDefinition> out = new HashMap<>();
        for (MenuQuery.Entry row : visibleRows(null)) {
            out.put(row.id(), row.definition());
        }
        return out;
    }

    /**
     * The header's registered-tab strip (hotbar#154): the permanent All tab
     * plus every tab a provider registered - drawn as **Icon/Item Stack +
     * Text** (the owner's identifier format), each shrinking to fit beside
     * the style pill. A registry with no tabs - or none whose list has an
     * entry (owner: empty tabs are not displayed) - draws nothing and the
     * pool stays unfiltered.
     */
    private void drawProviderTabs(GuiGraphics gui, int mouseX, int mouseY) {
        List<MenuTabs.MenuTab> tabs = visibleTabs();
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
            if (all) {
                // owner: the All category wears a Nether Star
                textX = drawIdentifier(gui, null, new ItemStack(Items.NETHER_STAR),
                        x + 3, STYLE_PILL_Y + 1, TAB_ICON);
            } else if (tab.hasIcon()) {
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
     * one uniform size, like the bar's own icons). The stack is the
     * registration preference (#80) and the sprite its fallback: some baked
     * models render nothing in any vanilla GUI context (skill books' study
     * book empties ItemDisplayContext.GUI through its applyTransform), so
     * the draw picks whichever identifier will actually produce pixels -
     * and when neither does, the tab stays text-only, never a broken box.
     */
    private int drawTabIcon(GuiGraphics gui, MenuTabs.MenuTab tab, int x, int y) {
        return drawIdentifier(gui, tab.iconId(), tab.icon(), x, y, TAB_ICON);
    }

    /**
     * One registered identifier at one size (owner: chip icons ride the
     * tab strip's rule): the stack is the draw preference when it renders
     * in a GUI, the sprite the fallback, and nothing resolves to a
     * text-only label - never a broken box. Returns the text x after the
     * icon, or x when no icon draws.
     */
    private int drawIdentifier(GuiGraphics gui, @Nullable ResourceLocation iconId,
            @Nullable ItemStack stack, int x, int y, int size) {
        boolean stackRenders = stack != null && !stack.isEmpty() && stackRendersInGui(stack);
        MenuTabs.IconTexture texture = stackRenders || iconId == null ? null
                : MenuTabs.resolvedIconTexture(iconId, rl -> Minecraft.getInstance()
                        .getResourceManager().getResource(rl).isPresent()).orElse(null);
        if (!stackRenders && texture == null) {
            return x; // nothing resolves: text-only, same padding as no icon
        }
        var pose = gui.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0.0);
            float scale = size / 16.0f;
            pose.scale(scale, scale, 1.0f);
            if (stackRenders) {
                gui.renderItem(stack, 0, 0);
            } else if (texture.sprite()) {
                gui.blitSprite(texture.drawWith(), 0, 0, 16, 16);
            } else {
                blitIconTexture(gui, texture.drawWith());
            }
        } finally {
            pose.popPose();
        }
        return x + size + 3;
    }

    /** Texture pixel sizes per id: the PNG header, read once and cached. */
    private static final Map<ResourceLocation, int[]> TEXTURE_SIZES = new ConcurrentHashMap<>();

    /**
     * One plain texture into the 16-unit icon box (the pose scales it to
     * the requested size). A texture <b>taller than wide</b> stacks its
     * frames - ARR's GUI sprites are 32x64 - so only the top square is
     * the icon: squeezing the whole file drew both frames, which read as
     * a broken chip (owner's report). Sizes come from the PNG header
     * (AbstractTexture exposes none), read once per id.
     */
    private static void blitIconTexture(GuiGraphics gui, ResourceLocation id) {
        int[] size = TEXTURE_SIZES.computeIfAbsent(id, AbilityMenuScreen::readPngSize);
        if (size[1] > size[0]) {
            int width = size[0];
            gui.blit(id, 0, 0, 16, 16, 0f, 0f, width, width, width, size[1]);
        } else {
            gui.blit(id, 0, 0, 0, 0, 16, 16, 16, 16);
        }
    }

    /** {@code {width, height}} from a PNG's IHDR, or the 16x16 fallback. */
    private static int[] readPngSize(ResourceLocation id) {
        int[] fallback = {16, 16};
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(id).orElse(null);
            if (resource == null) {
                return fallback;
            }
            try (java.io.InputStream in = resource.open()) {
                byte[] header = in.readNBytes(24);
                if (header.length < 24 || (header[0] & 0xFF) != 0x89) {
                    return fallback; // not a PNG: the old whole-file squeeze
                }
                int width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                        | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
                int height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                        | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
                return width > 0 && height > 0 ? new int[] {width, height} : fallback;
            }
        } catch (java.io.IOException | RuntimeException unreadable) {
            return fallback;
        }
    }

    /**
     * Whether vanilla GUI rendering would emit pixels for this stack. The
     * probe mirrors ItemRenderer.render exactly - the post-applyTransform
     * model (the handleCameraTransforms swap), its custom-renderer branch
     * and its quad lists - on a scratch pose the real render never sees.
     * Skill books' study book returns an empty, non-custom model for every
     * GUI context, so this is how the tab notices and falls back to its
     * sprite instead of silently drawing nothing.
     */
    private static boolean stackRendersInGui(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
        BakedModel resolved = model.applyTransform(ItemDisplayContext.GUI, new PoseStack(), false);
        if (resolved.isCustomRenderer()) {
            return true; // ItemRenderer.render takes the renderByItem branch
        }
        RandomSource random = RandomSource.create();
        if (!resolved.getQuads(null, null, random).isEmpty()) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (!resolved.getQuads(null, direction, random).isEmpty()) {
                return true;
            }
        }
        return false;
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

    /** The selected tab's provider plan for this frame, or null (assignable strip). */
    @Nullable
    private ContentPlan stripPlan;

    /**
     * The provider the strip's plan came from (the pill tooltip seam's
     * guarded call needs the bar id to name, and the provider itself) -
     * set together with {@link #stripPlan} in {@link #providerPlan()}.
     */
    @Nullable
    private ResourceLocation stripBarId;

    @Nullable
    private HotbarContentProvider stripProvider;

    /**
     * The hovered provider pill's tooltip lines for this frame (the
     * owner's provider toolbar brief): captured in the strip's draw and
     * rendered last, so nothing paints over it; null/empty = none.
     */
    @Nullable
    private List<Component> stripTooltip;

    /**
     * The assignable strip's visible cell count (owner's brief: the menu
     * respects the client's slot setting too): the bar's slots clamped by
     * {@code display.slotsPerBar} through HotbarConfig - the SAME rule
     * the HUD renderer and the Ctrl+wheel use, so the menu can never
     * show (or hit) a slot the HUD does not draw. The header meter and
     * the mode preview keep the bar's raw count: they describe the bar's
     * truth, not the edit window.
     */
    private static int visibleSlotCount(@Nullable Bar target) {
        return target == null ? 0
                : HotbarConfig.effectiveRenderCount(target.slotCount());
    }

    /**
     * The strip's cell count: the selected tab's provider bar when it
     * declares one (the owner's ISS directive), else the target bar's.
     * Every strip-derived layout (the strip itself, the list's bottom
     * edge, the pane's drag region) follows this one number, or the list
     * would overlap a strip of a different height.
     */
    private int stripSlotCount(@Nullable Bar target) {
        return stripPlan != null ? stripPlan.slotCount()
                : visibleSlotCount(target);
    }

    /**
     * The provider bar the selected tab declares (the owner's ISS
     * directive: the Spells tab shows the spellbook's cells, not the
     * assignable bar's), as a content plan - null when the tab keeps the
     * assignable bar, the provider is absent, or it has no content.
     */
    @Nullable
    private ContentPlan providerPlan() {
        stripBarId = null;
        stripProvider = null;
        ResourceLocation barId = selectedTab == null ? null
                : MenuTabs.barForTab(selectedTab).orElse(null);
        if (barId == null) {
            return null;
        }
        HotbarContentProvider provider = HotbarContentProviders.get(barId).orElse(null);
        if (provider == null) {
            return null;
        }
        stripBarId = barId;
        stripProvider = provider;
        ContentPlan plan = ContentPlan.build(barId, provider, -1);
        return plan.isEmpty() ? null : plan;
    }

    /** The bottom stack's chamfered slot cells: click selects the target slot. */
    private void drawSlotCells(GuiGraphics gui, int mouseX, int mouseY, @Nullable Bar target) {
        ContentPlan plan = stripPlan;
        stripTooltip = null;
        int slotCount = stripSlotCount(target);
        if (slotCount == 0) {
            return;
        }
        MenuStyle style = style();
        int cellWidth = slotWidth(slotCount, this.width, clusterWidth());
        int rowSlots = Math.min(slotCount, MAX_ROW_SLOTS);
        int y = slotY(slotCount);
        for (int i = 0; i < slotCount; i++) {
            int x = LIST_LEFT + (i % rowSlots) * cellWidth;
            int cellY = y + (i / rowSlots) * (menuCellHeight() + SLOT_GAP);
            boolean selected = plan == null && i == selectedSlot;
            boolean hovered = inside(mouseX, mouseY, x, cellY, cellWidth - 1, menuCellHeight());
            MenuChrome.glass(gui, x, cellY, cellWidth - 1, menuCellHeight(), style, false);
            if (selected) {
                // Owner's brief: selected reads DARKER than the hover lift,
                // not brighter - a dark chamfer over the base glass.
                MenuChrome.chamferFill(gui, x + 1, cellY + 1, cellWidth - 3, menuCellHeight() - 2,
                        Math.max(0, MenuChrome.CHAMFER - 1),
                        MenuStyle.alpha(style.background(),
                                HotbarClientConfig.SELECTION_ALPHA.get()));
            } else if (hovered) {
                MenuChrome.chamferFill(gui, x + 1, cellY + 1, cellWidth - 3, menuCellHeight() - 2,
                        Math.max(0, MenuChrome.CHAMFER - 1),
                        MenuStyle.alpha(style.raised(), HotbarClientConfig.HOVER_ALPHA.get()));
            }
            // the provider strip carries icons only (the HUD's provider
            // cells have no names or modes to line up beside them)
            var assignment = plan != null ? null : target.assignment(i);
            boolean filled = plan != null
                    ? plan.iconAt(i) != null
                    : assignment != null && assignment.isAssigned();
            boolean dropHover = plan == null && filled
                    && dragAbilityId != null && hovered;
            // The vanilla selector marks the slot on EVERY cell (owner):
            // an icon-sized box like the world hotbar - only the icon or
            // the number lives inside; a drag shows the bare box target
            int side = selectorSide(cellWidth);
            int boxX = x + 2;
            int boxY = cellY + (menuCellHeight() - side) / 2;
            if (!filled) {
                drawSelectionRing(gui, boxX, boxY, side);
                // the number sits CENTRED inside the selector, empty only
                String label = slotLabel(i + 1);
                gui.drawString(this.font, label,
                        boxX + Math.max(0, (side - this.font.width(label)) / 2),
                        boxY + Math.max(0, (side - 9) / 2),
                        selected ? style.text() : style.muted());
                continue;
            }
            if (dropHover) {
                // filled + dragged-over: the box alone is the read
                drawSelectionRing(gui, boxX, boxY, side);
                continue;
            }
            // Filled: the icon fills the selector's WINDOW (see
            // slotIconRect), the box then draws OVER it (owner: as the
            // ISS hotbar does); name lines follow, the mode line last
            ResourceLocation icon = plan != null ? plan.iconAt(i) : slotIcon(target, i);
            boolean iconDrawn = icon != null && HotbarClientConfig.SHOW_ICONS.get()
                    && slotIconFits(cellWidth);
            int nameX = boxX + side + 2;
            int nameWidth = Math.max(4, cellWidth - (side + 5));
            if (iconDrawn) {
                int[] iconRect = slotIconRect(side);
                gui.blit(icon, boxX + iconRect[0], boxY + iconRect[1],
                        0, 0, iconRect[2], iconRect[2], iconRect[2], iconRect[2]);
            }
            drawSelectionRing(gui, boxX, boxY, side);
            // The pill's hover tooltip (owner's provider toolbar brief):
            // only on the icon box, never mid-drag - extracted lazily for
            // this ONE cell, not 18x per frame
            if (plan != null && dragAbilityId == null
                    && inside(mouseX, mouseY, boxX, boxY, side, side)) {
                stripTooltip = ContentPlan.cellTooltip(stripBarId, stripProvider,
                        this.minecraft.player, i);
            }
            String modeLabel = plan != null ? null : slotModeLabel(target, i);
            // owner: the name and the mode wrap INTO each other - two
            // lines total, truncated to the cell; a provider cell shows
            // its own name when the provider ships one (ISS), else none
            List<FormattedCharSequence> nameLines = plan != null
                    ? providerNameLines(plan, i, nameWidth)
                    : this.font.split(slotText(target, i, modeLabel,
                            selected ? style.text() : style.muted()), nameWidth);
            // the block sits centred: its position follows how many
            // non-empty lines there actually are (owner) - never a fixed
            // offset, so one line reads centred and two fill the cell
            int lineCount = Math.min(nameLines.size(), 2);
            int nameY = cellY + Math.max(2, (menuCellHeight() - lineCount * 9) / 2);
            for (int line = 0; line < lineCount; line++) {
                gui.drawString(this.font, nameLines.get(line), nameX, nameY,
                        selected ? style.text() : style.muted());
                nameY += 9;
            }
        }
    }

    /**
     * A provider cell's name lines for its menu pill: the provider's own
     * name split to the cell's width, or none when it ships no name -
     * an icon-only provider's pill stays exactly as it was.
     */
    private List<FormattedCharSequence> providerNameLines(ContentPlan plan, int index,
            int nameWidth) {
        Component name = plan.nameAt(index);
        return name == null ? List.of() : this.font.split(name, nameWidth);
    }

    /**
     * The cell's text flow (owner's brief): the name and the mode wrap
     * INTO each other - two lines total, the overflow truncated to fit
     * the cell. The mode keeps its action colour inside the flow.
     */
    private Component slotText(Bar target, int slot, @Nullable String modeLabel,
            int nameColor) {
        MutableComponent text = Component.literal(slotName(target, slot))
                .withStyle(s -> s.withColor(TextColor.fromRgb(MenuStyle.rgb(nameColor))));
        if (modeLabel != null) {
            int modeColor = MenuStyle.rgb(style().action());
            text.append(" ").append(Component.literal(modeLabel)
                    .withStyle(s -> s.withColor(TextColor.fromRgb(modeColor))));
        }
        return text;
    }

    /**
     * The declared mode's player-facing name for a stored mode id: the
     * stored mode when still declared, the first declared (default) when
     * stale - the wire's own rule - and null for no context, no
     * declarations, or an ability with <b>one</b> mode (owner: the slot
     * pill joins the cell's two-line flow only when there is a mode to
     * choose between; a lone mode is the ability's only shape, not a
     * setting). Raw ids never render (#139).
     */
    @Nullable
    static String modeLabelFor(ResourceLocation abilityId, @Nullable String storedMode) {
        if (storedMode == null) {
            return null;
        }
        Optional<List<AbilityModes.Mode>> declared = AbilityModes.get(abilityId);
        if (declared.isEmpty() || declared.get().size() < 2) {
            return null;
        }
        AbilityModes.Mode mode = declared.get().stream()
                .filter(candidate -> candidate.id().equals(storedMode))
                .findFirst()
                .orElse(declared.get().get(0));
        return MenuLabels.named(mode.nameKey()).getString();
    }

    /**
     * The slotted mode's player-facing name, or null: no stored mode
     * context (native default applies - nothing to show) or no declared
     * modes. Resolution mirrors ActivationResolver's wire rule so the
     * cell shows exactly what an activation would send.
     */
    @Nullable
    private String slotModeLabel(Bar target, int slot) {
        var assignment = target.assignment(slot);
        if (assignment == null || !assignment.isAssigned()) {
            return null;
        }
        return modeLabelFor(assignment.abilityId(), assignment.mode());
    }

    /**
     * The slot of this ability on the target bar for mode cycling (the
     * selected slot wins when it holds it - the keybind's own slot), or
     * -1: no such slot, or the ability declares at most one mode (not
     * cycleable). Owner's brief: the row button previews that slot's
     * mode - clicking never writes the slot itself.
     */
    private static int modeSlotFor(@Nullable Bar target, ResourceLocation id) {
        if (target == null || !AbilityModes.get(id)
                .map(modes -> modes.size() > 1).orElse(false)) {
            return -1;
        }
        int first = -1;
        for (int i = 0; i < target.slotCount(); i++) {
            var assignment = target.assignment(i);
            if (assignment != null && assignment.isAssigned()
                    && id.equals(assignment.abilityId())) {
                if (i == target.selectedSlot()) {
                    return i;
                }
                if (first < 0) {
                    first = i;
                }
            }
        }
        return first;
    }

    /**
     * The mode pill's label: the stored mode's name, falling back to the
     * first declared (the default) for a not-yet-cycled slot so the pill
     * can start cycling from the get-go (owner's brief). Null only when
     * the ability declares no modes at all.
     */
    @Nullable
    private static String modePillLabel(ResourceLocation abilityId,
            @Nullable String storedMode) {
        String resolved = modeLabelFor(abilityId, storedMode);
        if (resolved != null) {
            return resolved;
        }
        return AbilityModes.get(abilityId)
                .flatMap(modes -> modes.isEmpty()
                        ? Optional.empty() : Optional.of(modes.get(0)))
                .map(mode -> MenuLabels.named(mode.nameKey()).getString())
                .orElse(null);
    }

    /**
     * The stored mode of a slotted assignment, or null (null-safe: the
     * slot's assignment can vanish between resolve and read; a null mode
     * makes the pill fall back to the default label anyway).
     */
    @Nullable
    private static String storedModeAt(@Nullable Bar bar, int slot) {
        var assignment = bar == null ? null : bar.assignment(slot);
        return assignment == null ? null : assignment.mode();
    }

    /**
     * The mode pill's rect for a row (draw and the click share it): left
     * of the Enable/Disable pill when present, else the row's right end,
     * on the bottom line beside the state text.
     */
    private int[] modePillRect(int rowY, int listWidth, String modeLabel,
            @Nullable int[] toggleRect) {
        int modeWidth = Math.max(28, this.font.width(modeLabel) + 10);
        int rightEdge = toggleRect == null ? LIST_LEFT + listWidth - 4 : toggleRect[0] - 4;
        return new int[] {rightEdge - modeWidth, rowY + menuRowHeight() - 18, modeWidth, 12};
    }

    /** The filled cell's display name (the raw inert id when its provider is gone). */
    private String slotName(Bar target, int slot) {
        var assignment = target.assignment(slot);
        if (assignment == null || !assignment.isAssigned()) {
            return "";
        }
        ResourceLocation id = assignment.abilityId();
        return AbilityDiscovery.get(id)
                .<String>map(d -> Component.translatable(d.displayNameKey()).getString())
                .orElseGet(id::toString);
    }

    /**
     * Slot numbers always render zero-padded (01..18), here and in the
     * keybind names. Package-private for the format fixture.
     */
    static String slotLabel(int slot1Based) {
        return String.format(Locale.ROOT, "%02d", slot1Based);
    }

    /**
     * The width of one slot cell (owner's uniform-pill brief): a row
     * shares everything left of the chip cluster, so cells fill the
     * horizontal space instead of capping - and EVERY bar divides by
     * {@link #MAX_ROW_SLOTS}, never by its own slot count: the assignable
     * nine, a shorter bar and a provider strip (the ISS spellbook cells)
     * all draw the same-size pills, the row just ending earlier. Floored
     * at 1px; both rows of an 18-slot bar still align. Package-private
     * for the geometry fixture.
     */
    static int slotWidth(int slotCount, int screenWidth, int reserved) {
        if (slotCount <= 0) {
            return 0;
        }
        return Math.max(1, (screenWidth - 16 - reserved) / MAX_ROW_SLOTS);
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
     * The stretched selection sprite's <b>window</b> in box coordinates
     * ({@code {x, y, w, h}}) - the shared solve ({@link SpriteDraw}),
     * grown one pixel on the left, right and bottom because the owner
     * observed a white remnant of the window surviving on exactly those
     * sides (the top edge is exact).
     */
    static int[] selectorWindow(int side) {
        return SpriteDraw.window(side, side);
    }

    /**
     * The selection sprite with its window <b>subtracted</b> (owner:
     * dynamically remove the white square): four scissored bands around
     * the window, so only the frame draws. Whatever pack overrides
     * {@code hud/hotbar_selection} - Excalibur's window is a white wash,
     * vanilla's is transparent - the pill's background shows through the
     * middle and the icon stays crisp. Shared with the bar's frames.
     */
    private static void drawSelectionRing(GuiGraphics gui, int x, int y, int side) {
        SpriteDraw.ring(gui, VANILLA_SELECTION, x, y, side, side, 0);
    }

    /**
     * The selector box's side: an icon-sized square inside the cell
     * (owner: like the world hotbar - only the icon lives in the box).
     */
    static int selectorSide(int cellWidth) {
        return Math.max(8, Math.min(menuCellHeight() - 4, cellWidth - 4));
    }

    /**
     * The filled cell's icon rect <b>inside the selector's window</b>,
     * as {@code {offsetX, offsetY, size}} (owner: tensura's icons
     * rendered 1px too high and 1px too small). The vanilla selection
     * sprite is 24x23 with a <b>16x16 window at (4,4)</b>; stretched to
     * {@code side} its window sits at {@code (4/24, 4/23)*side} and
     * measures {@code (16/24, 16/23)*side} - so the icon fills that
     * window (square, the smaller dimension) centred in it, instead of
     * being centred in the whole box where the deeper bottom ring left
     * it riding high with a gap beneath. Package-private for the
     * geometry fixture; the draw shares it.
     *
     * @param side the selector box's side ({@link #selectorSide(int)})
     * @return the icon's x/y offset inside the box and its size
     */
    static int[] slotIconRect(int side) {
        int windowX = Math.round(side * 4f / 24f);
        int windowY = Math.round(side * 4f / 23f);
        int windowW = Math.round(side * 16f / 24f);
        int windowH = Math.round(side * 16f / 23f);
        int size = Math.max(4, Math.min(windowW, windowH));
        return new int[] {
                windowX + (windowW - size) / 2,
                windowY + (windowH - size) / 2,
                size};
    }

    /**
     * The empty cell's number x offset: centred INSIDE the selector box.
     * Package-private for the geometry fixture (draw shares it).
     */
    static int slotLabelCenteredX(int cellWidth, int labelWidth) {
        return 2 + Math.max(0, (selectorSide(cellWidth) - labelWidth) / 2);
    }

    /**
     * The slot cell under the point, or -1: the click/drop side of
     * {@link #drawSlotCells}' geometry (owner's drag-to-assign brief), so a
     * release can name the target slot without re-deriving the layout.
     * Package-private for the geometry fixture.
     */
    static int slotCellAt(double mouseX, double mouseY, int slotCount,
            int screenWidth, int reserved, int screenHeight) {
        if (slotCount <= 0) {
            return -1;
        }
        int cellWidth = slotWidth(slotCount, screenWidth, reserved);
        int rowSlots = Math.min(slotCount, MAX_ROW_SLOTS);
        int y = screenHeight - 16 - stripHeight(slotCount);
        for (int i = 0; i < slotCount; i++) {
            int x = LIST_LEFT + (i % rowSlots) * cellWidth;
            int cellY = y + (i / rowSlots) * (menuCellHeight() + SLOT_GAP);
            if (inside(mouseX, mouseY, x, cellY, cellWidth - 1, menuCellHeight())) {
                return i;
            }
        }
        return -1;
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

    /**
     * The clear pill, right end of the slot line - the "Slot NN: name"
     * readout is gone (owner's brief: no selected-skill indicator); the
     * detail pane carries what the slot holds.
     */
    private void drawClearButton(GuiGraphics gui, int mouseX, int mouseY,
            @Nullable Bar target) {
        if (target == null || stripPlan != null) {
            return; // an editable-bar tool: absent while a provider strip shows
        }
        MenuStyle style = style();
        int[] clear = clearRect(visibleSlotCount(target));
        boolean hovered = inside(mouseX, mouseY, clear[0], clear[1], clear[2], clear[3]);
        MenuChrome.glass(gui, clear[0], clear[1], clear[2], clear[3], style, hovered);
        Component clearLabel = Component.translatable("ability_hotbar.menu.clear");
        int textX = clear[0] + (clear[2] - this.font.width(clearLabel)) / 2;
        gui.drawString(this.font, clearLabel, textX, clear[1] + 3,
                hovered ? style.text() : style.action());
    }

    /**
     * The clear pill below the bar chip (owner's brief): the same column
     * under the indicator, sharing the strip's band. Derived from the chip
     * rect so draw and hit cannot drift.
     */
    private int[] clearRect(int slotCount) {
        int[] chip = barChipRect(slotCount);
        return new int[] {chip[0], chip[1] + BAR_CHIP_HEIGHT + 2, CLEAR_WIDTH, 14};
    }

    private void drawAbilityList(GuiGraphics gui, int mouseX, int mouseY, BarState state) {
        List<MenuQuery.Entry> rows = visibleRows();
        MenuStyle style = style();
        // the list lives in the left two thirds; the rightmost third is
        // reserved for the detail pane (owner's nine-point brief)
        int listWidth = listWidth();
        Bar stripBar = targetBar(state);
        int listBottom = listBottom(stripSlotCount(stripBar));
        int visible = Math.max(0, (listBottom - listTop()) / menuRowHeight());
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() - visible)));
        if (rows.isEmpty()) {
            gui.drawString(this.font, Component.translatable("ability_hotbar.menu.no_results"),
                    LIST_LEFT, listTop(), style.muted());
            return;
        }
        int y = listTop();
        int hoveredIndex = -1;
        for (int i = scrollOffset;
                i < rows.size() && y + menuRowHeight() <= listBottom;
                i++, y += menuRowHeight()) {
            MenuQuery.Entry row = rows.get(i);
            boolean hovered = inside(mouseX, mouseY, LIST_LEFT, y, listWidth, menuRowHeight() - 2);
            if (hovered) {
                hoveredIndex = i;
            }
            if (row.id().equals(selectedRowId)) {
                // Selected rows read exactly like the clicked slot cell
                // (owner's brief): the dark chamfer shade, not the hover
                // lift - hover never overrides the selection.
                gui.fill(LIST_LEFT, y, LIST_LEFT + listWidth, y + menuRowHeight() - 2,
                        MenuStyle.alpha(style.background(),
                                HotbarClientConfig.SELECTION_ALPHA.get()));
                // the active row's left-bar marker (their list selection)
                gui.fill(LIST_LEFT, y, LIST_LEFT + 2, y + menuRowHeight() - 2, style.accent());
            } else if (hovered) {
                gui.fill(LIST_LEFT, y, LIST_LEFT + listWidth, y + menuRowHeight() - 2,
                        MenuStyle.alpha(style.raised(), HotbarClientConfig.HOVER_ALPHA.get()));
                // the active row's left-bar marker (their list selection)
                gui.fill(LIST_LEFT, y, LIST_LEFT + 2, y + menuRowHeight() - 2, style.accent());
            }
            // The documented icon order (core#62, hotbar#217): the provider's
            // answer wins, the static icon is the fallback, null draws nothing.
            ResourceLocation icon = IconProviders.iconFor(this.minecraft.player, row.id())
                    .orElse(row.definition().iconId());
            if (icon != null && HotbarClientConfig.SHOW_ICONS.get()) {
                // The brief's doubled size: the whole texture squeezed into
                // the one doubled box, exactly the bar-icons technique.
                gui.blit(icon, LIST_LEFT + 2, y, 0, 0, 32, 32, 32, 32);
            }
            Component displayName = Component.translatable(row.definition().displayNameKey());
            String name = displayName.getString();
            Optional<AbilityPresentation> presentation = presentationOf(row.id());
            Toggle toggle = presentation.map(AbilityPresentation::toggle).orElse(null);
            // The row's mastery meter (owner: beneath the name, value
            // inside): the presentation's first PROGRESS field, none -> none
            PresentationField mastery = presentation
                    .map(AbilityPresentation::fields).orElse(List.of()).stream()
                    .filter(field -> field.kind() == PresentationField.Kind.PROGRESS)
                    .findFirst().orElse(null);
            // REQ-CORE-12 row display: badge once declared, the meter
            // only once XP exists (until then the presentation's meter
            // keeps the row) - both ride the answered snapshot
            boolean coreMastery = CoreMasteryCompat.answered()
                    && CoreMasteryCompat.declared(row.id());
            double coreXp = coreMastery ? CoreMasteryCompat.xpOf(row.id()) : 0.0;
            boolean coreBar = coreMasteryBar(coreMastery, coreXp);
            if (coreBar) {
                mastery = null;
            }
            // The row's mode pill: slotted multi-mode abilities cycle
            // from here (owner's brief) - the label is the current (or
            // default) mode, the click sends the slot's cycle request.
            int modeSlot = modeSlotFor(stripBar, row.id());
            String modeLabel = modeSlot < 0 ? null
                    : modePillLabel(row.id(), rowModePreview.getOrDefault(row.id(),
                            storedModeAt(stripBar, modeSlot)));
            int[] rowToggle = toggle == null ? null : rowToggleButtonRect(y, listWidth);
            int[] modeRect = modeLabel == null ? null
                    : modePillRect(y, listWidth, modeLabel, rowToggle);
            // The unlock read drives the meter's state colour; unknown
            // never guesses a state (core's never-guess rule).
            Optional<Boolean> learned =
                    AbilityUnlocks.isLearned(this.minecraft.player, row.id());
            // The bottom-line meter: the generic mastery bar, the
            // presentation's progress, or a zero bar for a provably
            // known-but-unlearned art that still presents itself
            boolean knownBar = mastery == null && learned.isPresent() && !learned.get()
                    && presentation.map(p -> !p.fields().isEmpty()).orElse(false);
            boolean bottomBar = coreBar || mastery != null || knownBar;
            // the row's LIVE cost answer - repriced by tensura_iron_spells
            // through iss's projection when one is in force (hotbar#193);
            // the label rides the name line, the value the bottom line
            ResourceCost liveCost = RepresentativeCost.of(
                    LiveCosts.forDisplay(row.id(), row.definition()));
            String cost = liveCost == null ? "" : costText(liveCost);
            boolean costLine = !cost.isEmpty();
            // pills own the bottom line's right edge - the Cost: label and
            // its value align there, and the meter stops before the value
            int limitX = (modeRect != null ? modeRect[0]
                    : rowToggle != null ? rowToggle[0] : LIST_LEFT + listWidth) - 4;
            // Toggleable rows, mode pills, the meter and the cost line lift
            // the name so the bottom line owns its chrome (no overlap).
            int textY = toggle == null && modeRect == null && !bottomBar && !costLine
                    ? y + (menuRowHeight() - 9) / 2 : y + 6;
            gui.drawString(this.font, name, LIST_LEFT + 38, textY, style.text());
            if (coreMastery) {
                // the level badge AFTER the name (REQ-CORE-12), in the
                // mastery color - only for abilities whose snapshot has
                // arrived (never a guessed level)
                String badge = Component.translatable(
                        "ability_hotbar.menu.mastery_level",
                        CoreMasteryCompat.levelOf(row.id())).getString();
                gui.drawString(this.font, badge,
                        LIST_LEFT + 38 + this.font.width(name) + 4, textY, style.mastery());
            }
            if (costLine) {
                // the Cost: label right-aligned on the name line; the
                // value follows below it (owner: both right-aligned)
                String label = Component.translatable(
                        "ability_hotbar.menu.cost_label").getString();
                gui.drawString(this.font, label,
                        limitX - this.font.width(label), textY, style.muted());
            }
            if (bottomBar || costLine) {
                int meterLimit = costLine
                        ? costMeterLimit(limitX, this.font.width(cost)) : limitX;
                if (bottomBar) {
                    // the meter owns the bottom line's left side, clear of
                    // the pills, the Enable/Disable button and the cost
                    int[] bar = masteryBarRect(y, LIST_LEFT + 38, meterLimit);
                    float fraction = coreBar
                            ? (float) CoreMasteryCompat.progressOf(row.id())
                            : mastery != null ? mastery.progress() : 0f;
                    // the generic bar wears the mastery color (deliberately
                    // distinct from the domain meter's state colours); the
                    // presentation meter keeps its state colour as before
                    int stateColor = coreBar ? style.mastery()
                            : masteryBarColor(style, learned, fraction);
                    MenuChrome.meter(gui, bar[0], bar[1], bar[2], bar[3], fraction,
                            style, stateColor);
                    String barValue = null;
                    if (coreBar) {
                        barValue = Component.translatable("ability_hotbar.menu.mastery_bar",
                                CoreMasteryCompat.formatXp(coreXp)).getString();
                    } else if (mastery != null) {
                        barValue = mastery.value();
                    }
                    if (barValue != null) {
                        String value = this.font.plainSubstrByWidth(barValue, bar[2] - 4);
                        if (!value.isEmpty()) {
                            // the value rides INSIDE the bar, centred with the
                            // same margin above and below (owner's brief)
                            gui.drawString(this.font, value,
                                    bar[0] + (bar[2] - this.font.width(value)) / 2,
                                    bar[1] + (bar[3] - 9) / 2, style.text());
                        }
                    }
                }
                if (costLine) {
                    // the value right-aligned on the bottom line, under its
                    // label, at the pills' edge (owner: both right-aligned)
                    gui.drawString(this.font, cost,
                            limitX - this.font.width(cost), costValueY(y),
                            style.action());
                }
            }
            if (modeRect != null) {
                boolean modeHover = inside(mouseX, mouseY,
                        modeRect[0], modeRect[1], modeRect[2], modeRect[3]);
                MenuChrome.glass(gui, modeRect[0], modeRect[1],
                        modeRect[2], modeRect[3], style, modeHover);
                gui.drawString(this.font,
                        this.font.plainSubstrByWidth(modeLabel, modeRect[2] - 8),
                        modeRect[0] + 4, modeRect[1] + 2,
                        modeHover ? style.text() : style.action());
            }
            if (rowToggle != null) {
                boolean buttonHover = inside(mouseX, mouseY,
                        rowToggle[0], rowToggle[1], rowToggle[2], rowToggle[3]);
                MenuChrome.glass(gui, rowToggle[0], rowToggle[1],
                        rowToggle[2], rowToggle[3], style, buttonHover);
                Component buttonLabel = Component.translatable(toggle.enabled()
                        ? "ability_hotbar.menu.toggle_disable"
                        : "ability_hotbar.menu.toggle_enable");
                gui.drawString(this.font, buttonLabel,
                        rowToggle[0] + (rowToggle[2] - this.font.width(buttonLabel)) / 2,
                        rowToggle[1] + 2,
                        buttonHover ? style.text() : style.action());
            }
            // The unlock grey (#135's rule, the second state): a listed
            // ability the player is provably unlearned for wears the
            // unavailable tint; unknown stays undimmed (never-guess).
            if (UnlockRule.provenUnlearned(
                    AbilityUnlocks.isLearned(this.minecraft.player, row.id()))) {
                gui.fill(LIST_LEFT, y, LIST_LEFT + listWidth, y + menuRowHeight() - 2,
                        AbilityBarLayer.UNAVAILABLE_TINT);
            }
            // the cyan row underline (their list chrome)
            gui.fill(LIST_LEFT, y + menuRowHeight() - 2, LIST_LEFT + listWidth, y + menuRowHeight() - 1,
                    MenuStyle.alpha(style.accent(), 45));
        }
        if (hoveredIndex >= 0) {
            // The detail pane's sticky subject: it follows the hover and
            // keeps the last ability when the pointer leaves the list.
            ResourceLocation hoveredId = rows.get(hoveredIndex).id();
            if (!hoveredId.equals(detailId)) {
                detailId = hoveredId;
                detailScroll = 0;
            }
        }
    }

    /**
     * The detail pane's left edge: the rightmost
     * {@code menu.detailPaneWidthPercent} of the screen. Read through
     * core's diagnostics helper (headless fixtures run on the shipped
     * default), so the pure helper stays unit-testable.
     */
    static int detailX(int screenWidth) {
        int percent = ConfigDiagnostics.readOrDefault("menu.detailPaneWidthPercent",
                () -> HotbarClientConfig.DETAIL_PANE_WIDTH_PERCENT.get(), 33);
        return screenWidth - screenWidth * percent / 100;
    }

    private int detailX() {
        return detailX(this.width);
    }

    /**
     * The row-end Enable/Disable rect (owner's brief: the button lives in
     * the list now, on the bottom line beside the state text). Draw and
     * the click's hit test share it.
     */
    static int[] rowToggleButtonRect(int rowY, int listWidth) {
        return new int[] {LIST_LEFT + listWidth - 58, rowY + menuRowHeight() - 18, 56, 12};
    }

    /**
     * The row's mastery bar rect: the bottom line (under the name), from
     * the given start to the pills' edge - 150% of the original length
     * (capped at 144) and 13 tall, an odd height so the value line sits
     * with the same margin above and below (owner's brief).
     */
    static int[] masteryBarRect(int rowY, int startX, int limitX) {
        int width = Math.max(40, Math.min(144, limitX - startX));
        return new int[] {startX, rowY + menuRowHeight() - 18, width, 13};
    }

    /**
     * The bar's state colour (owner's brief): mastered reads gold (the
     * style's action), learned cyan (accent), known and unknown grey -
     * the state comes from unlocks plus the fraction, never a string.
     */
    static int masteryBarColor(MenuStyle style, Optional<Boolean> learned, float fraction) {
        if (fraction >= 1f) {
            return style.action();
        }
        return learned.orElse(false) ? style.accent() : style.muted();
    }

    /**
     * Whether the generic mastery bar owns the row's bottom line (the
     * hybrid REQ-CORE-12 settled on): only once the answered snapshot has
     * XP for the ability - until then the presentation's meter (tensura's
     * learning/mastery) keeps the row, and an unanswered or undeclared
     * ability shows neither (never a guessed fill).
     *
     * @param ready the ability is declared and its snapshot has arrived
     * @param xp    the stored own-target XP from that snapshot
     * @return true when the generic bar renders
     */
    static boolean coreMasteryBar(boolean ready, double xp) {
        return ready && xp > 0.0;
    }

    /**
     * The bonus as a signed whole percent ("+5%", "-10%", "0%") - the
     * pane's honesty format: the server's multiplier, rounded for the
     * eye, never re-derived.
     */
    static String bonusPercent(double multiplier) {
        long pct = Math.round((multiplier - 1.0) * 100.0);
        return pct > 0 ? "+" + pct + "%" : pct + "%";
    }

    /**
     * The effect axis's label: the lang key when one ships, else the
     * prettified enum name - never the raw key on screen.
     */
    static String axisLabel(String axis) {
        String key = "ability_hotbar.menu.axis."
                + axis.toLowerCase(java.util.Locale.ROOT);
        return I18n.exists(key) ? Component.translatable(key).getString()
                : MenuLabels.prettify(axis);
    }

    /** The cost value's baseline on the bottom line (meter-value aligned). */
    static int costValueY(int rowY) {
        return rowY + menuRowHeight() - 16;
    }

    /**
     * The meter's right edge when the cost value shares the line: the
     * meter stops 6px before the value (owner: the value keeps its
     * right-aligned column).
     */
    static int costMeterLimit(int limitX, int costWidth) {
        return Math.min(limitX, limitX - costWidth - 6);
    }
    /**
     * The rightmost third (owner's brief, superseding #27's no-sidebar
     * exclusion), reading name -> description -> status (replaced by the
     * mastery bonuses when the ability can earn core XP) -> activation:
     * wrapped and wheel-scrolled inside the pane. The tags/cost line,
     * Stats and Modes sections are gone (owner's drop brief), and the
     * toggle state and its button live in the list row. Sticky: the last
     * hovered ability stays when the pointer leaves the list; before any
     * hover the selected slot's ability shows. Drawn last in render(), so
     * nothing can paint over it (owner's layering brief).
     */
    private void drawDetailPane(GuiGraphics gui, @Nullable Bar target) {
        MenuStyle style = style();
        int x0 = detailX();
        int top = FILTER_Y;
        int bottom = listBottom(stripSlotCount(target));
        int paneW = this.width - 8 - x0;
        if (paneW < 70 || bottom - top < 40) {
            return;
        }
        MenuChrome.tooltip(gui, x0, top, paneW, bottom - top, style);
        // The whole pane clips to its own box (owner's brief: description
        // text can never run off screen), whatever any future line draws.
        gui.enableScissor(x0 + 1, top, x0 + paneW - 1, bottom);
        try {
        int textX = x0 + 5;
        int wrapW = paneW - 10;
        ResourceLocation id = paneAbilityId(target);
        if (id == null) {
            gui.drawString(this.font,
                    Component.translatable("ability_hotbar.menu.detail_hint"),
                    textX, top + 4, style.muted());
            return;
        }
        var found = AbilityDiscovery.get(id);
        if (found.isEmpty()) {
            // inert id: the raw vocabulary shows, flagged red as always
            gui.drawString(this.font, this.font.plainSubstrByWidth(id.toString(), wrapW),
                    textX, top + 4, INERT_COLOR);
            return;
        }
        AbilityDefinition definition = found.get();
        String name = this.font.plainSubstrByWidth(
                Component.translatable(definition.displayNameKey()).getString(), wrapW - 20);
        gui.drawString(this.font, name, textX, top + 4, style.text());
        gui.fill(textX, top + 14, textX + this.font.width(name), top + 15, style.accent());
        int lineY = top + 16;
        // The pane reads as ONE unit (owner's reorder brief): the name
        // above, then description, status - replaced by the mastery
        // bonuses for core-XP abilities - and activation last
        List<PaneRow> rows = new ArrayList<>();
        // the FULL description first (owner's order): wrapped and clipped
        // to the pane, scrollable with everything below it on overflow
        String description = definition.description().orElse(null);
        Component descriptionComponent = description == null ? null
                : descriptionComponent(description);
        if (descriptionComponent != null) {
            rows.add(new PaneRow.Header(
                    "ability_hotbar.menu.section.description"));
            rows.add(new PaneRow.Wrapped(
                    this.font.split(descriptionComponent, wrapW)));
        }
        // Status, or the mastery bonuses it BECOMES for an ability that
        // can earn core XP (owner: bonuses replace status there); the
        // live fields stand in while the snapshot has not arrived
        var bonuses = CoreMasteryCompat.bonusesOf(id);
        if (CoreMasteryCompat.declared(id) && !bonuses.isEmpty()) {
            // REQ-CORE-12: the server's current effect multipliers - only
            // axes its spec declares, so an undeclared ability shows
            // nothing rather than a fake x1.00
            rows.add(new PaneRow.Header("ability_hotbar.menu.section.bonuses"));
            for (var bonus : bonuses) {
                rows.add(new PaneRow.Line(this.font.plainSubstrByWidth(
                        Component.translatable("ability_hotbar.menu.bonus_line",
                                axisLabel(bonus.axis()),
                                bonusPercent(bonus.multiplier())).getString(),
                        wrapW), style.mastery()));
            }
        } else {
            Optional<AbilityPresentation> presentation = presentationOf(id);
            // Mastery meters live on the row now (owner: remove them from
            // the description section) - the pane keeps only text fields
            List<PresentationField> paneFields = presentation
                    .map(AbilityPresentation::fields).orElse(List.of()).stream()
                    .filter(field -> field.kind() != PresentationField.Kind.PROGRESS)
                    .toList();
            if (!paneFields.isEmpty()) {
                rows.add(new PaneRow.Header("ability_hotbar.menu.section.status"));
                for (PresentationField field : paneFields) {
                    rows.add(new PaneRow.FieldRow(field));
                }
            }
        }
        // NONE never mentions activation (owner's brief): a passive's
        // pane carries no Activation section at all - and it reads last
        if (definition.activationType() != ActivationType.NONE) {
            rows.add(new PaneRow.Header("ability_hotbar.menu.section.activation"));
            rows.add(new PaneRow.Line(this.font.plainSubstrByWidth(
                    Component.translatable("ability_hotbar.menu.activation_fmt",
                            Component.literal(MenuLabels.prettify(
                                    definition.activationType().name()))).getString(),
                    wrapW), style.action()));
        }

        int contentTop = lineY + 2;
        int contentHeight = 0;
        for (PaneRow row : rows) {
            contentHeight += row.height();
        }
        int visibleH = Math.max(9, bottom - 6 - contentTop);
        detailScrollMax = Math.max(0, contentHeight - visibleH);
        detailScroll = Math.max(0, Math.min(detailScroll, detailScrollMax));
        int cursor = contentTop - detailScroll;
        for (PaneRow row : rows) {
            if (cursor + row.height() > top && cursor < bottom) {
                drawPaneRow(gui, row, cursor, textX, wrapW, style);
            }
            cursor += row.height();
        }
        } finally {
            gui.disableScissor();
        }
    }

    /** One pane row drawn at its scrolled y inside the scissor. */
    private void drawPaneRow(GuiGraphics gui, PaneRow row, int y, int textX,
            int wrapW, MenuStyle style) {
        if (row instanceof PaneRow.Header header) {
            gui.drawString(this.font, Component.translatable(header.key()),
                    textX, y + 4, style.accent());
        } else if (row instanceof PaneRow.Line line) {
            gui.drawString(this.font, line.text(), textX, y, line.color());
        } else if (row instanceof PaneRow.Wrapped wrapped) {
            List<FormattedCharSequence> lines = wrapped.lines();
            for (int i = 0; i < lines.size(); i++) {
                gui.drawString(this.font, lines.get(i), textX, y + i * 9,
                        MenuStyle.alpha(style.muted(), 70));
            }
        } else if (row instanceof PaneRow.FieldRow field) {
            drawField(gui, field.field(), y, textX, wrapW, style);
        }
    }

    /**
     * A pane content row for the subsectioned info section (owner's
     * brief): a header, a single clipped line, a wrapped text block, or a
     * live presentation field - each carrying its own height so the
     * measure pass and the draw pass can never disagree.
     */
    private sealed interface PaneRow {

        int height();

        /** A section header: 4px lead-in under whatever precedes it. */
        record Header(String key) implements PaneRow {
            @Override
            public int height() {
                return 16;
            }
        }

        /** One clipped line in a fixed colour. */
        record Line(String text, int color) implements PaneRow {
            @Override
            public int height() {
                return 12;
            }
        }

        /** A wrapped block (the description): 9px lines. */
        record Wrapped(List<FormattedCharSequence> lines) implements PaneRow {
            @Override
            public int height() {
                return lines.size() * 9;
            }
        }

        /** A live presentation field (status/mastery meters). */
        record FieldRow(PresentationField field) implements PaneRow {
            @Override
            public int height() {
                return 12;
            }
        }
    }

    /**
     * The pane's subject: the sticky last-hovered ability, falling back to
     * the selected slot's ability before the first hover - the draw pass's
     * content anchor.
     */
    @Nullable
    private ResourceLocation paneAbilityId(@Nullable Bar target) {
        if (detailId != null) {
            return detailId;
        }
        if (target == null) {
            return null;
        }
        var assignment = target.assignment(selectedSlot);
        return assignment != null && assignment.isAssigned() ? assignment.abilityId() : null;
    }

    /** One live presentation field in the detail pane (issue #148). */
    private void drawField(GuiGraphics gui, PresentationField field, int y, int textX,
            int wrapW, MenuStyle style) {
        String label = MenuLabels.stat(field.labelKey()).getString() + ": ";
        int labelWidth = Math.min(this.font.width(label), wrapW / 2);
        gui.drawString(this.font, this.font.plainSubstrByWidth(label, labelWidth), textX, y,
                style.muted());
        int valueX = textX + labelWidth + 2;
        if (field.kind() == PresentationField.Kind.PROGRESS) {
            MenuChrome.meter(gui, valueX, y + 1, 40, 6, field.progress(), style);
            valueX += 44;
        }
        if (!field.value().isEmpty()) {
            gui.drawString(this.font,
                    this.font.plainSubstrByWidth(field.value(),
                            textX + wrapW - valueX - 2),
                    valueX, y, style.text());
        }
    }

    /** The live presentation for one ability (core's contract, issue #148). */
    private Optional<AbilityPresentation> presentationOf(ResourceLocation id) {
        return PresentationProviders.present(this.minecraft.player, id);
    }

    /**
     * The pane's description text: a translated key when the value is
     * key-shaped (ARR-style providers ship {@code <displayNameKey>.description}
     * keys - dotted, space-less), plain prose as-is otherwise (a single
     * word like "Wololo!" reads as prose, never a key), and nothing for
     * an untranslated key - a raw key never reaches the player.
     */
    @Nullable
    private static Component descriptionComponent(String description) {
        if (keyShaped(description)) {
            return I18n.exists(description) ? Component.translatable(description) : null;
        }
        return Component.literal(description);
    }

    /**
     * Whether a description value is a translation key rather than
     * prose: dotted (not leading) and space-less - every real key is
     * dotted, while prose carries spaces or stands alone as a word, so
     * single-token prose like "Wololo!" renders literally instead of
     * being swallowed by the key path. Package-private for the fixture.
     */
    static boolean keyShaped(String description) {
        return description.indexOf('.') > 0 && description.indexOf(' ') < 0;
    }

    /** The hint line, bottom-left (their hint-line pattern). */
    private void drawHintBar(GuiGraphics gui) {
        gui.drawString(this.font, Component.translatable("ability_hotbar.menu.hint"),
                LIST_LEFT, this.height - 13, style().muted());
    }

    /**
     * The bottom bar selector (owner's brief): the target-bar chip beside
     * the slot strip with the prev/next buttons below it, aligned to the
     * strip. Style-coloured triangle glyphs (the vanilla font ships no
     * arrow characters - verified against font/default.json). A single-bar
     * list draws the buttons dimmed and inert; no bar at all draws
     * nothing.
     */
    private void drawBarNav(GuiGraphics gui, int mouseX, int mouseY, @Nullable Bar target) {
        List<ResourceLocation> bars = manageableBars();
        if (bars.isEmpty() || stripPlan != null) {
            return; // the chip edits the assignable bar: hidden on a provider strip
        }
        MenuStyle style = style();
        int[] chip = barChipRect(visibleSlotCount(target));
        boolean chipHover = inside(mouseX, mouseY, chip[0], chip[1], chip[2], chip[3]);
        MenuChrome.glass(gui, chip[0], chip[1], chip[2], chip[3], style, chipHover);
        gui.drawString(this.font, barChipLabel(), chip[0] + 6, chip[1] + 3,
                chipHover ? style.text() : style.muted());
        boolean enabled = bars.size() >= 2;
        int[][] navs = barNavRects(chip);
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

    /**
     * The bar chip beside the slot strip (draw and hit share it): right of
     * the strip's end, clamped inside the frame - a nearly full-width bar
     * (12+ slots) necessarily tucks the chip over the tail cells, which the
     * frame clamp keeps visible.
     */
    private int[] barChipRect(int slotCount) {
        int chipWidth = Math.max(24, this.font.width(barChipLabel()) + 12);
        int rowSlots = Math.min(slotCount, MAX_ROW_SLOTS);
        int cellWidth = slotWidth(slotCount, this.width, clusterWidth());
        int stripEnd = LIST_LEFT + rowSlots * cellWidth;
        // reserve the strip band's right side for the whole cluster (chip +
        // clear column, vertical nav pair); a nearly full-width bar tucks
        // it over the tail cells - the frame clamp keeps it visible
        int maxX = this.width - 8 - clusterWidth();
        int x = Math.max(LIST_LEFT, Math.min(stripEnd + 8, maxX));
        return new int[] {x, slotY(slotCount) + 2, chipWidth, BAR_CHIP_HEIGHT};
    }

    /**
     * The prev/next buttons beside the chip column, rotated <b>vertical</b>
     * (owner's brief): prev over next, right of the chip/clear stack.
     * Draw and hit share it. Package-private for the geometry fixture.
     */
    static int[][] barNavRects(int[] chipRect) {
        int x = chipRect[0] + Math.max(chipRect[2], CLEAR_WIDTH) + 4;
        return new int[][] {
                {x, chipRect[1], BAR_NAV_SIZE, BAR_NAV_SIZE},
                {x, chipRect[1] + BAR_NAV_SIZE + BAR_NAV_GAP,
                        BAR_NAV_SIZE, BAR_NAV_SIZE}};
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
        // registered tabs (header line 2, hotbar#154): All + one per
        // registration that currently lists something - an empty tab
        // never draws, so it can never be hit either
        List<MenuTabs.MenuTab> tabs = visibleTabs();
        if (!tabs.isEmpty()) {
            int tabWidth = providerTabWidth(tabs);
            int tabX = LIST_LEFT;
            for (int i = -1; i < tabs.size(); i++) {
                if (inside(mouseX, mouseY, tabX, STYLE_PILL_Y, tabWidth - 2, 14)) {
                    // The subcategory filter belongs to its tab (owner's
                    // brief): stash this tab's choice, swap to the new
                    // tab's own - never leak one across the strip.
                    subFilterMemory.put(selectedTab,
                            subCategoryFilter == null ? "" : subCategoryFilter);
                    selectedTab = i < 0 ? null : tabs.get(i).id();
                    String remembered = subFilterMemory.get(selectedTab);
                    subCategoryFilter = remembered == null || remembered.isEmpty()
                            ? null : remembered;
                    if (subCategoryFilter != null
                            && !subTypeTags().contains(subCategoryFilter)) {
                        subCategoryFilter = null;
                    }
                    scrollOffset = 0;
                    return true;
                }
                tabX += tabWidth;
            }
        }
        // subcategory strip (beside the search bar): the arrows step it, a
        // press in the viewport defers to release so a drag can scroll it
        // (owner's brief: arrow buttons + drag-scrolling)
        List<String> subTypes = subTypeTags();
        if (!subTypes.isEmpty()) {
            int count = subTypes.size() + 1;
            subStripScroll = clampSubScroll(subStripScroll, count, subViewportWidth(),
                    subPillWidth());
            if (stepSubStrip(mouseX, mouseY, true, count)
                    || stepSubStrip(mouseX, mouseY, false, count)) {
                return true;
            }
            if (inside(mouseX, mouseY, subViewportX(), FILTER_Y, subViewportWidth(),
                    filterPillHeight())) {
                pressedSubPill = subPillAt(mouseX, mouseY, count);
                subStripArmed = true;
                subStripDragging = false;
                subStripPressX = mouseX;
                subStripScrollStart = subStripScroll;
                return true;
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
        // slot cells (bottom stack; two rows beyond nine - owner's brief):
        // a provider strip is a preview - it never retargets the bar, and
        // the editable-bar tools below are hidden while it shows
        int slotCount = visibleSlotCount(target);
        int cellWidth = slotWidth(slotCount, this.width, clusterWidth());
        int rowSlots = Math.min(slotCount, MAX_ROW_SLOTS);
        int y = slotY(slotCount);
        if (stripPlan == null) {
            for (int i = 0; i < slotCount; i++) {
                int cellX = LIST_LEFT + (i % rowSlots) * cellWidth;
                int cellY = y + (i / rowSlots) * (menuCellHeight() + SLOT_GAP);
                if (inside(mouseX, mouseY, cellX, cellY,
                        cellWidth - 1, menuCellHeight())) {
                    selectedSlot = i;
                    // a FILLED slot arms the reorder (owner: drag one slot
                    // onto another); empty cells and drag-to-assign off keep
                    // the plain select
                    var slotAssignment = target.assignment(i);
                    if (HotbarClientConfig.DRAG_TO_ASSIGN.get()
                            && slotAssignment != null
                            && slotAssignment.isAssigned()) {
                        pressedSlot = i;
                        pressStartX = mouseX;
                        pressStartY = mouseY;
                    }
                    return true;
                }
            }
        }
        // clear button (below the bar chip, owner's brief)
        if (target != null && stripPlan == null) {
            int[] clear = clearRect(visibleSlotCount(target));
            if (inside(mouseX, mouseY, clear[0], clear[1], clear[2], clear[3])) {
                sendAssignment("");
                return true;
            }
        }
        // bottom bar selector: the chip beside the strip cycles the bar,
        // the prev/next buttons below it step (owner's nine-point brief)
        List<ResourceLocation> navBars = manageableBars();
        if (!navBars.isEmpty() && stripPlan == null) {
            int[] chip = barChipRect(visibleSlotCount(target));
            if (inside(mouseX, mouseY, chip[0], chip[1], chip[2], chip[3])) {
                cycleBar();
                return true;
            }
            int[][] navs = barNavRects(chip);
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
        // press-drag inside the pane scrolls it (owner's brief); the rows
        // are x-bounded below, so the pane region has nothing else to catch
        BarState paneState = ClientBarState.get();
        Bar paneBar = paneState == null ? null : targetBar(paneState);
        int paneBottom = listBottom(stripSlotCount(paneBar));
        if (inside(mouseX, mouseY, detailX(), FILTER_Y,
                this.width - 8 - detailX(), paneBottom - FILTER_Y)) {
            detailDragging = true;
            detailDragRemainder = 0;
            return true;
        }
        // ability rows (left two thirds only: a click inside the detail
        // pane must never assign)
        List<MenuQuery.Entry> rows = visibleRows();
        int listBottom = listBottom(stripSlotCount(target));
        int relative = mouseX < detailX() - 6
                ? rowIndexAt((int) mouseY, listTop(), listBottom, menuRowHeight())
                : -1;
        if (relative >= 0) {
            int offset = relative + scrollOffset;
            if (offset < rows.size()) {
                MenuQuery.Entry row = rows.get(offset);
                int rowY = listTop() + relative * menuRowHeight();
                if (informationalGateApplies(row.definition(), informationalTab())) {
                    // info-only tab (owner's directive): rows never slot,
                    // cycle or toggle; NONE/UNKNOWN pass through to their
                    // toggle and cycle - the assignment gate owns slotting
                    return true;
                }
                // the row-end Enable/Disable owns its rect before assigning
                // (owner's brief: #148's button moved into the row)
                Toggle toggle = presentationOf(row.id())
                        .map(AbilityPresentation::toggle).orElse(null);
                if (toggle != null) {
                    int[] toggleRect = rowToggleButtonRect(rowY, listWidth());
                    if (inside(mouseX, mouseY, toggleRect[0], toggleRect[1],
                            toggleRect[2], toggleRect[3])) {
                        sendToggle(row.id());
                        return true;
                    }
                }
                // the mode pill steps the ROW's own preview (owner: it
                // never writes a hotbar slot - the wire path stays on
                // the keybind, this is menu-local display state)
                int modeSlot = selectedBar == null ? -1
                        : modeSlotFor(target, row.id());
                if (modeSlot >= 0) {
                    String modeLabel = modePillLabel(row.id(),
                            rowModePreview.getOrDefault(row.id(),
                                    storedModeAt(target, modeSlot)));
                    int[] toggleRect = toggle == null ? null
                            : rowToggleButtonRect(rowY, listWidth());
                    int[] modeRect = modeLabel == null ? null
                            : modePillRect(rowY, listWidth(), modeLabel, toggleRect);
                    if (modeRect != null && inside(mouseX, mouseY, modeRect[0],
                            modeRect[1], modeRect[2], modeRect[3])) {
                        cycleRowPreview(row.id(), target, modeSlot);
                        return true;
                    }
                }
                // arm the row: click-assign moves to release, past the
                // threshold it becomes a slot-cell drag (owner's brief) -
                // with drag-to-assign off, the press assigns directly
                if (!HotbarClientConfig.DRAG_TO_ASSIGN.get()) {
                    // the direct press-assign obeys the same gate (owner:
                    // locked/NONE/UNKNOWN neither click nor drag)
                    if (mayAssignToHotbar(row.id())) {
                        selectedRowId = row.id();
                        sendAssignment(row.id().toString());
                    }
                    return true;
                }
                pressedRowId = row.id();
                pressStartX = mouseX;
                pressStartY = mouseY;
                return true;
            }
        }
        return false;
    }

    /**
     * Steps the row's menu-local mode preview to the next declared mode
     * (owner: clicking the row's mode button must not change a hotbar
     * slot's mode - only what this menu row shows). The preview starts
     * from what the row already displays: the slot's stored mode, or the
     * declared default the pill falls back to.
     */
    private void cycleRowPreview(ResourceLocation id, Bar target, int modeSlot) {
        List<AbilityModes.Mode> modes = AbilityModes.get(id).orElse(List.of());
        if (modes.isEmpty()) {
            return;
        }
        String shown = rowModePreview.getOrDefault(id, storedModeAt(target, modeSlot));
        int index = 0; // the pill's fallback: the first declared mode
        for (int i = 0; i < modes.size(); i++) {
            if (modes.get(i).id().equals(shown)) {
                index = i;
                break;
            }
        }
        rowModePreview.put(id, modes.get((index + 1) % modes.size()).id());
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
        // the subcategory strip owns the wheel while the pointer is on it
        // (owner's brief: it scrolls - one pill a notch, clamped)
        List<String> subStripTypes = subTypeTags();
        if (!subStripTypes.isEmpty()
                && inside(mouseX, mouseY, subViewportX(), FILTER_Y, subViewportWidth(),
                        filterPillHeight())) {
            int subCount = subStripTypes.size() + 1;
            subStripScroll = clampSubScroll(
                    subStripScroll + (scrollY > 0 ? -subPillWidth() : subPillWidth()),
                    subCount, subViewportWidth(), subPillWidth());
            return true;
        }
        // the detail pane owns the wheel while the pointer sits in it (its
        // description scrolls only while it actually overflows - max is
        // computed by the draw pass)
        int x0 = detailX();
        BarState scrollState = ClientBarState.get();
        Bar scrollBar = scrollState == null ? null : targetBar(scrollState);
        int scrollBottom = listBottom(stripSlotCount(scrollBar));
        if (detailScrollMax > 0 && inside(mouseX, mouseY, x0, FILTER_Y,
                this.width - 8 - x0, scrollBottom - FILTER_Y)) {
            // owner's brief: configurable wheel speed (default 16 lines a
            // notch), clamped to the real overflow the draw pass measured
            int linesPerNotch = HotbarClientConfig.DESCRIPTION_SCROLL_LINES.get();
            detailScroll = Math.max(0, Math.min(detailScrollMax,
                    detailScroll + (scrollY > 0 ? -linesPerNotch : linesPerNotch)));
            return true;
        }
        if (scrollY > 0) {
            scrollOffset -= HotbarClientConfig.LIST_SCROLL_ROWS.get();
        } else if (scrollY < 0) {
            scrollOffset += HotbarClientConfig.LIST_SCROLL_ROWS.get();
        }
        return true; // the wheel belongs to the list while the menu is open
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (dragAbilityId != null) {
            return true; // the ghost rides the cursor; cells read the hover
        }
        if (subStripArmed) {
            // movement past the threshold promotes the press to a scroll
            // (owner: drag-scrolling the subcategory strip)
            if (!subStripDragging && Math.abs(mouseX - subStripPressX)
                    > HotbarClientConfig.DRAG_THRESHOLD_PX.get()) {
                subStripDragging = true;
            }
            if (subStripDragging) {
                subStripScroll = clampSubScroll(
                        subStripScrollStart - (int) (mouseX - subStripPressX),
                        subTypeTags().size() + 1, subViewportWidth(), subPillWidth());
            }
            return true;
        }
        if (pressedRowId != null) {
            // movement past the threshold promotes the press to a drag -
            // the owner's gate refuses locked and NONE/UNKNOWN rows either
            // way, and the refused press is consumed (no drag, no assign)
            if (Math.abs(mouseX - pressStartX) + Math.abs(mouseY - pressStartY)
                    > HotbarClientConfig.DRAG_THRESHOLD_PX.get()) {
                if (mayAssignToHotbar(pressedRowId)) {
                    dragAbilityId = pressedRowId;
                }
                pressedRowId = null;
            }
            return true;
        }
        if (pressedSlot >= 0) {
            // the same threshold promotes a slot press to a reorder drag,
            // and the source cell's ability becomes the ghost (owner)
            if (Math.abs(mouseX - pressStartX) + Math.abs(mouseY - pressStartY)
                    > HotbarClientConfig.DRAG_THRESHOLD_PX.get()) {
                dragSlot = pressedSlot;
                pressedSlot = -1;
                BarState slotState = ClientBarState.get();
                Bar slotBar = slotState == null ? null : targetBar(slotState);
                var slotAssignment = slotBar == null ? null : slotBar.assignment(dragSlot);
                if (slotAssignment != null && slotAssignment.abilityId() != null) {
                    dragAbilityId = slotAssignment.abilityId();
                }
            }
            return true;
        }
        if (detailDragging) {
            // pixels accumulate into whole lines (owner: drag was too
            // slow at one-to-one), the draw pass clamps the result to
            // the real overflow every frame
            int pixelsPerLine = HotbarClientConfig.DETAIL_DRAG_PIXELS_PER_LINE.get();
            detailDragRemainder += dragY;
            int lines = (int) detailDragRemainder / pixelsPerLine;
            if (lines != 0) {
                detailScroll = Math.max(0, detailScroll - lines);
                detailDragRemainder -= lines * pixelsPerLine;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        detailDragging = false;
        boolean subPress = subStripArmed;
        boolean subDragged = subStripDragging;
        int subPill = pressedSubPill;
        subStripArmed = false;
        subStripDragging = false;
        pressedSubPill = -1;
        if (subPress) {
            // press + release in place activates the pill; a drag that
            // scrolled consumes the release instead (owner: drag-scroll)
            if (!subDragged && subPill >= 0) {
                List<String> subTypes = subTypeTags();
                if (subPill - 1 < subTypes.size()) {
                    subCategoryFilter = subPill == 0 ? null : subTypes.get(subPill - 1);
                    scrollOffset = 0;
                }
            }
            return true;
        }
        int slotFrom = dragSlot;
        dragSlot = -1;
        int slotPress = pressedSlot;
        pressedSlot = -1;
        if (slotFrom >= 0) {
            // a slot drag resolves as a REORDER, never an assign (owner) -
            // and the ghost's id belonged to the source cell: drop it too
            dragAbilityId = null;
            dropReordered(mouseX, mouseY, slotFrom);
            return true;
        }
        if (slotPress >= 0) {
            return true; // press in place: the selection happened on press
        }
        ResourceLocation drag = dragAbilityId;
        ResourceLocation pressed = pressedRowId;
        dragAbilityId = null;
        pressedRowId = null;
        if (drag != null) {
            dropDragged(mouseX, mouseY, drag);
            return true;
        }
        if (pressed != null) {
            // press + release in place: the click-assign to the selected
            // slot (the row keeps the lit treatment) - the owner's gate
            // refuses locked and NONE/UNKNOWN rows here too
            if (mayAssignToHotbar(pressed)) {
                selectedRowId = pressed;
                sendAssignment(pressed.toString());
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * The drag's resolution (owner's brief): a slot cell under the point
     * takes the ability - explicitly, to that slot - and any other release
     * cancels the drag without assigning (a drag never degrades to a
     * click-assign).
     */
    private void dropDragged(double mouseX, double mouseY, ResourceLocation id) {
        BarState state = ClientBarState.get();
        Bar target = state == null ? null : targetBar(state);
        if (target == null || selectedBar == null) {
            return;
        }
        int slot = slotCellAt(mouseX, mouseY, visibleSlotCount(target),
                this.width, clusterWidth(), this.height);
        if (slot >= 0) {
            PacketDistributor.sendToServer(
                    new AssignAbilityPayload(slot, id.toString(), selectedBar));
        }
    }

    /**
     * The reorder's resolution (owner: drag one slot onto another): a slot
     * cell under the point swaps with the dragged one - the server moves
     * whole cells, stored modes included - and any other release cancels
     * the drag without touching the bar.
     */
    private void dropReordered(double mouseX, double mouseY, int from) {
        BarState state = ClientBarState.get();
        Bar target = state == null ? null : targetBar(state);
        if (target == null || selectedBar == null) {
            return;
        }
        int to = slotCellAt(mouseX, mouseY, visibleSlotCount(target),
                this.width, clusterWidth(), this.height);
        if (to >= 0 && to != from) {
            PacketDistributor.sendToServer(
                    new ReorderSlotsPayload(selectedBar, from, to));
        }
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
        // The subcategory rides its owning tab (owner's brief): the pair
        // survives reopen only when the saved tab does.
        HotbarClientConfig.MENU_SUBCATEGORY.set(subCategoryFilter == null ? ""
                : (selectedTab == null ? "" : selectedTab.toString())
                        + ";" + subCategoryFilter);
        HotbarClientConfig.MENU_TAB.set(selectedTab == null ? "" : selectedTab.toString());
        HotbarClientConfig.SPEC.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        // managing the bar must not freeze the world (menu.pauseGame:
        // an operator's choice for their pack)
        return HotbarClientConfig.PAUSE_GAME.get();
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
     * The selected tab's content handler (owner's handler directive)
     * scopes the source first - its ids resolved against core's
     * definitions, unknown ids dropped - visibility and accepts still
     * narrow on top.
     */
    private Map<ResourceLocation, AbilityDefinition> menuPool() {
        return menuPool(selectedTab == null ? null
                : MenuTabs.get(selectedTab).orElse(null));
    }

    /**
     * One tab's pool - content, then visibility, then the locked-mastery
     * hide - or the global pool for the All tab (null). No search, no
     * bar: this is what the tab can <em>ever</em> list, the base both
     * empty-checks read.
     */
    private Map<ResourceLocation, AbilityDefinition> menuPool(
            @Nullable MenuTabs.MenuTab tab) {
        Player player = Minecraft.getInstance().player;
        MenuTabs.TabContent content = tab == null ? null : tab.content();
        if (content == null) {
            return withoutLockedMasteries(
                    MenuQuery.visiblePool(AbilityDiscovery.entries(), player), player);
        }
        Map<ResourceLocation, AbilityDefinition> scoped = new HashMap<>();
        for (ResourceLocation id : content.entries(player)) {
            if (id != null) {
                AbilityDiscovery.get(id).ifPresent(definition -> scoped.put(id, definition));
            }
        }
        return withoutLockedMasteries(MenuQuery.visiblePool(scoped, player), player);
    }

    /**
     * The owner's hide brief: a greyed PASSIVE MASTERY row (NONE and
     * provably unlearned) is a locked mastery and never lists - the
     * cast-to-unlock rows appear only once unlocked. Unknown unlock state
     * stays (never-guess) and every other row keeps its grey; visibility,
     * tabs, counts and the list all read the same filtered pool, so they
     * can never disagree about a hidden row.
     */
    private static Map<ResourceLocation, AbilityDefinition> withoutLockedMasteries(
            Map<ResourceLocation, AbilityDefinition> pool, @Nullable Player player) {
        Map<ResourceLocation, AbilityDefinition> out = new LinkedHashMap<>(pool.size());
        pool.forEach((id, definition) -> {
            if (!hiddenLockedMastery(id, definition, player)) {
                out.put(id, definition);
            }
        });
        return out;
    }

    /**
     * Whether one row hides: only a PASSIVE MASTERY (NONE) can hide, and
     * only when the unlock read proves it unlearned - unknown never hides
     * (REQ-HOT-08's never-guess). Package-private for the fixture.
     */
    static boolean hiddenLockedMastery(ResourceLocation id, AbilityDefinition definition,
            @Nullable Player player) {
        return definition.activationType() == ActivationType.NONE
                && UnlockRule.provenUnlearned(AbilityUnlocks.isLearned(player, id));
    }

    /**
     * The registered tabs whose own list would show at least one entry
     * (owner's brief: empty tabs are not displayed): content, visibility
     * and membership with no search or bar - a tab vanishes only when it
     * can <em>never</em> list. The strip's draw and its hit-testing both
     * read this, so their boxes can never drift; an empty result hides
     * the strip whole (the All tab hides with it - every tab pool is a
     * subset of the global one, so an empty strip means an empty menu).
     */
    private List<MenuTabs.MenuTab> visibleTabs() {
        return MenuQuery.nonEmptyTabs(MenuTabs.all(), this::menuPool);
    }

    /**
     * A selected tab whose own list is empty drops to All before anything
     * reads it (owner's brief: empty tabs are not displayed - and never
     * stay selected). Its subcategory choice falls along, kept in the
     * memory for the tab's return - the click's own stash posture.
     */
    private void revalidateSelectedTab() {
        if (selectedTab == null || tabListsSomething(selectedTab)) {
            return;
        }
        subFilterMemory.put(selectedTab,
                subCategoryFilter == null ? "" : subCategoryFilter);
        selectedTab = null;
        subCategoryFilter = null;
    }

    /** Whether the registered tab id currently lists something. */
    private boolean tabListsSomething(ResourceLocation id) {
        for (MenuTabs.MenuTab tab : visibleTabs()) {
            if (tab.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private List<MenuQuery.Entry> visibleRows() {
        return visibleRows(subCategoryFilter);
    }

    /**
     * The list with an explicit subcategory pick (null = none): the
     * subcategory strip counts this very base minus its own filter, so a
     * pill can never advertise rows the list would not show.
     */
    private List<MenuQuery.Entry> visibleRows(@Nullable String subCategory) {
        // Visibility comes from core's contract via menuPool; unlock greys at
        // draw (#135); the selected tab narrows here (#154) - namespace
        // filtering moved into registered tabs, so that parameter is null.
        MenuTabs.MenuTab tab = selectedTab == null ? null
                : MenuTabs.get(selectedTab).orElse(null);
        return MenuQuery.visibleEntries(menuPool(), selectedBar,
                searchBox == null ? "" : searchBox.getValue(), null, subCategory,
                null, null,
                id -> true,
                tab == null ? null : tab.accepts(),
                definition -> Component.translatable(definition.displayNameKey()));
    }

    /**
     * Whether the selected tab is informational (rows inert to press,
     * drag, toggle and cycle - owner's directive: info only).
     */
    private boolean informationalTab() {
        return selectedTab != null && MenuTabs.get(selectedTab)
                .map(MenuTabs.MenuTab::informational).orElse(false);
    }

    /**
     * Whether the informational-tab gate applies to this row: the tab is
     * informational AND the ability is a casting row. Owner's settable
     * brief - {@link ActivationType.NONE} (a passive mastery) and
     * {@link ActivationType.UNKNOWN} are spared so their toggle and
     * cycle still work (whether they slot at all is the assignment
     * gate's answer); #154's inert-spells rule stays for every
     * activatable type under an informational tab. On an ordinary tab
     * nothing is ever gated. Package-private for the fixture.
     */
    static boolean informationalGateApplies(AbilityDefinition definition,
            boolean informationalTab) {
        if (!informationalTab) {
            return false;
        }
        ActivationType type = definition.activationType();
        return type != ActivationType.NONE && type != ActivationType.UNKNOWN;
    }

    /**
     * Whether a row may reach a hotbar slot by click or drag (owner's
     * brief): a locked ability (provably unlearned) and the
     * passive/undetermined types (NONE/UNKNOWN) neither click-assign nor
     * drag - a refused press is consumed, so it can never fall through to
     * an assign on release. Never-guess: unknown unlock state assigns as
     * before, and an id the registry no longer knows does not. The
     * clear-slot path never passes this gate (it sends an empty id).
     */
    private boolean mayAssignToHotbar(ResourceLocation id) {
        AbilityDefinition definition = AbilityDiscovery.get(id).orElse(null);
        if (definition == null) {
            return false;
        }
        return assignmentGateAllows(definition,
                UnlockRule.provenUnlearned(AbilityUnlocks.isLearned(this.minecraft.player, id)));
    }

    /**
     * The pure assignment gate: locked never reaches a slot, and neither
     * does the passive/undetermined pair (NONE/UNKNOWN). Package-private
     * for the fixture.
     */
    static boolean assignmentGateAllows(AbilityDefinition definition, boolean locked) {
        if (locked) {
            return false;
        }
        ActivationType type = definition.activationType();
        return type != ActivationType.NONE && type != ActivationType.UNKNOWN;
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

    /** The strip's row count: one row up to nine slots, two beyond (18 max). */
    static int rowsFor(int slotCount) {
        return slotCount <= MAX_ROW_SLOTS ? 1 : 2;
    }

    /** The strip's total height: rows of {@link #menuCellHeight()} plus their gaps. */
    static int stripHeight(int slotCount) {
        int rows = rowsFor(slotCount);
        return rows * menuCellHeight() + (rows - 1) * SLOT_GAP;
    }

    /** The bottom stack's slot-cell row top (shared with hit-testing). */
    private int slotY(int slotCount) {
        return this.height - 16 - stripHeight(slotCount);
    }

    /**
     * The list's bottom edge: clears the (possibly two-row) strip with the
     * same 6px breathing room the single row always had (22 + strip height).
     */
    private int listBottom(int slotCount) {
        return this.height - (22 + stripHeight(slotCount));
    }

    /** The right-two-thirds list width (draw and hit share it). */
    private int listWidth() {
        return detailX() - LIST_LEFT - 6;
    }

    /** The bottom cluster's width: chip/clear column + the vertical nav pair. */
    private int clusterWidth() {
        int chipWidth = Math.max(24, this.font.width(barChipLabel()) + 12);
        return Math.max(chipWidth, CLEAR_WIDTH) + 4 + BAR_NAV_SIZE;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** One declared cost as text: whole amounts drop the decimal point,
     * and the resource reads localised (the provider's convention key,
     * else its prettified path - owner: costs are localised). */
    private static String costText(ResourceCost cost) {
        double amount = cost.amount();
        String formatted = amount == Math.floor(amount) ? String.valueOf((long) amount)
                : String.valueOf(amount);
        return formatted + " " + MenuLabels.resource(cost.resourceId()).getString();
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
