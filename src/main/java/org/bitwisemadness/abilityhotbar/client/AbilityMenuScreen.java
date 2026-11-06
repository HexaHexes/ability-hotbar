package org.bitwisemadness.abilityhotbar.client;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilityhotbar.config.HotbarClientConfig;
import org.bitwisemadness.abilityhotbar.config.HotbarConfig;
import org.bitwisemadness.abilityhotbar.network.AssignAbilityPayload;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.state.Bar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.jetbrains.annotations.Nullable;

/**
 * The hotbar ability setup screen - an actual gameplay menu, not config UI
 * (REQ-HOT-12, hotbar#27): the player picks which ability sits in each slot
 * of each <b>assignable</b> hotbar.
 *
 * <p>Bar tabs (the built-in window plus registered assignable bars, from
 * {@link MenuQuery#manageableBars}) select the target bar; a slot row
 * selects the target slot within it; the filtered ability list assigns to
 * that slot on click. Replace is assigning over an occupied slot, remove is
 * the clear button - both are the same server-validated
 * {@link AssignAbilityPayload} naming the target bar (ADR-012 id
 * targeting), so the screen is never authoritative: the snapshot echo
 * repaints it with server truth.</p>
 *
 * <p>Search, category and tag filters come from {@link MenuQuery}; the
 * chosen filters persist as CLIENT config ({@code menu.lastCategory}/{@code
 * menu.lastTag}) while bar contents stay server data. Registered
 * non-assignable bars are never listed and the skip-empty cycling option
 * does not apply here (ADR-012). Opens from the menu-key seam, closes with
 * the same key (when the search box is not focused) or Esc.</p>
 */
public final class AbilityMenuScreen extends Screen {

    private static final int ROW_HEIGHT = 16;
    private static final int LIST_LEFT = 8;
    private static final int LIST_TOP = 104;
    private static final int LIST_BOTTOM_PAD = 28;
    private static final int INERT_COLOR = 0xFFFF5555;

    private ResourceLocation selectedBar;
    private int selectedSlot;
    @Nullable
    private ResourceLocation categoryFilter;
    @Nullable
    private String tagFilter;
    private int scrollOffset;
    private EditBox searchBox;

    public AbilityMenuScreen() {
        super(Component.translatable("ability_hotbar.menu.title"));
        // Loaded once per open (a resize re-runs init, not this constructor),
        // so mid-session filters are not clobbered by the last saved pair.
        categoryFilter = parseFilter(HotbarClientConfig.MENU_CATEGORY.get());
        tagFilter = emptyToNull(HotbarClientConfig.MENU_TAG.get());
        BarState state = ClientBarState.get();
        List<ResourceLocation> bars = manageableBars();
        ResourceLocation active = state == null ? null : state.activeBarId();
        selectedBar = active != null && bars.contains(active) ? active
                : bars.isEmpty() ? null : bars.get(0);
    }

    @Override
    protected void init() {
        searchBox = new EditBox(this.font, 8, 20, 120, 16,
                Component.translatable("ability_hotbar.menu.search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("ability_hotbar.menu.search"));
        searchBox.setResponder(value -> this.scrollOffset = 0);
        addRenderableWidget(searchBox);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick); // background + the search box
        Component title = Component.translatable("ability_hotbar.menu.title");
        gui.drawString(this.font, title.getString(),
                (this.width - this.font.width(title)) / 2, 6, 0xFFFFFF);

        BarState state = ClientBarState.get();
        if (state == null) {
            gui.drawString(this.font, Component.translatable("ability_hotbar.menu.waiting").getString(),
                    LIST_LEFT, LIST_TOP, 0xA0A0A0);
            return;
        }

        drawCategoryTag(gui);
        drawBarTabs(gui, mouseX, mouseY);
        Bar target = targetBar(state);
        drawSlotRow(gui, mouseX, mouseY, target);
        drawSlotDetail(gui, target);
        drawAbilityList(gui, mouseX, mouseY, state);
        drawHoveredDetail(gui, mouseX, mouseY);
    }

    /** The category and tag cycle readouts (click cycles; render reads fresh). */
    private void drawCategoryTag(GuiGraphics gui) {
        Component category = categoryFilter == null
                ? Component.translatable("ability_hotbar.menu.category_any")
                : Component.translatable("ability_hotbar.menu.category_fmt", categoryFilter.toString());
        Component tag = tagFilter == null
                ? Component.translatable("ability_hotbar.menu.tag_any")
                : Component.translatable("ability_hotbar.menu.tag_fmt", tagFilter);
        gui.drawString(this.font, category.getString(), 132, 24, 0xFFE0B0);
        gui.drawString(this.font, tag.getString(), Math.max(132, this.width - 84), 24, 0xFFE0B0);
    }

    private void drawBarTabs(GuiGraphics gui, int mouseX, int mouseY) {
        List<ResourceLocation> bars = manageableBars();
        if (bars.isEmpty()) {
            return;
        }
        int tabWidth = Math.min(48, (this.width - 16) / bars.size());
        for (int i = 0; i < bars.size(); i++) {
            int x = LIST_LEFT + i * tabWidth;
            boolean active = bars.get(i).equals(selectedBar);
            boolean hovered = inside(mouseX, mouseY, x, 42, tabWidth - 1, 16);
            if (active) {
                gui.fill(x, 42, x + tabWidth - 1, 58, 0x6040A0FF);
            } else if (hovered) {
                gui.fill(x, 42, x + tabWidth - 1, 58, 0x30FFFFFF);
            }
            Component label = BarName.of(bars.get(i));
            gui.drawString(this.font, label, x + 2, 46, active ? 0xFFFFFF : 0xA0A0A0);
        }
    }

    private void drawSlotRow(GuiGraphics gui, int mouseX, int mouseY, @Nullable Bar target) {
        int slotCount = target == null ? 0 : target.slotCount();
        if (slotCount == 0) {
            return;
        }
        int slotWidth = Math.min(40, (this.width - 16) / slotCount);
        for (int i = 0; i < slotCount; i++) {
            int x = LIST_LEFT + i * slotWidth;
            boolean selected = i == selectedSlot;
            boolean hovered = inside(mouseX, mouseY, x, 64, slotWidth - 1, 16);
            if (selected) {
                gui.fill(x, 64, x + slotWidth - 1, 80, 0x60FFD060);
            } else if (hovered) {
                gui.fill(x, 64, x + slotWidth - 1, 80, 0x30FFFFFF);
            }
            gui.drawString(this.font, slotLabel(i + 1), x + 3, 68, selected ? 0xFFFFFF : 0xA0A0A0);
        }
    }

    /**
     * Slot numbers always render zero-padded (01..18), here and in the
     * keybind names. Package-private for the format fixture.
     */
    static String slotLabel(int slot1Based) {
        return String.format(Locale.ROOT, "%02d", slot1Based);
    }

    /** What the selected slot currently holds: name, empty, or the raw inert id. */
    private void drawSlotDetail(GuiGraphics gui, @Nullable Bar target) {
        if (target == null) {
            return;
        }
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
        int x = LIST_LEFT;
        int y = 86;
        gui.drawString(this.font, label.toString(), x, y, 0xA0A0A0);
        int nameX = x + this.font.width(label.toString());
        gui.drawString(this.font, name.getString(), nameX, y, inert ? INERT_COLOR : 0xFFFFFF);
        gui.drawString(this.font, Component.translatable("ability_hotbar.menu.clear").getString(),
                this.width - 64, 84, 0xFF8080);
    }

    private void drawAbilityList(GuiGraphics gui, int mouseX, int mouseY, BarState state) {
        List<MenuQuery.Entry> rows = visibleRows();
        int listWidth = this.width - 16;
        int listBottom = this.height - LIST_BOTTOM_PAD;
        int visible = Math.max(0, (listBottom - LIST_TOP) / ROW_HEIGHT);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() - visible)));
        if (rows.isEmpty()) {
            gui.drawString(this.font, Component.translatable("ability_hotbar.menu.no_results"),
                    LIST_LEFT, LIST_TOP, 0xA0A0A0);
            return;
        }
        int y = LIST_TOP;
        for (int i = scrollOffset; i < rows.size() && y < listBottom; i++, y += ROW_HEIGHT) {
            MenuQuery.Entry row = rows.get(i);
            boolean hovered = inside(mouseX, mouseY, LIST_LEFT, y, listWidth, ROW_HEIGHT - 2);
            if (hovered) {
                gui.fill(LIST_LEFT, y, LIST_LEFT + listWidth, y + ROW_HEIGHT - 2, 0x30FFFFFF);
            }
            ResourceLocation icon = row.definition().iconId();
            if (icon != null && HotbarClientConfig.SHOW_ICONS.get()) {
                gui.blit(icon, LIST_LEFT + 2, y, 0, 0, 16, 16, 16, 16);
            }
            Component displayName = Component.translatable(row.definition().displayNameKey());
            gui.drawString(this.font, displayName.getString(), LIST_LEFT + 22, y + 4, 0xFFFFFF);
            String cost = costText(row.definition());
            if (!cost.isEmpty()) {
                gui.drawString(this.font, cost,
                        LIST_LEFT + listWidth - this.font.width(cost), y + 4, 0xC0C0C0);
            }
        }
    }

    /** Hovered row detail: category, tags, cost, then the description if the provider supplied one. */
    private void drawHoveredDetail(GuiGraphics gui, int mouseX, int mouseY) {
        List<MenuQuery.Entry> rows = visibleRows();
        int listBottom = this.height - LIST_BOTTOM_PAD;
        int visible = Math.max(0, (listBottom - LIST_TOP) / ROW_HEIGHT);
        int index = (int) ((mouseY - LIST_TOP) / ROW_HEIGHT) + scrollOffset;
        if (mouseY < LIST_TOP || mouseY >= listBottom || index < 0 || index >= rows.size()
                || (mouseY - LIST_TOP) / ROW_HEIGHT >= visible) {
            return;
        }
        AbilityDefinition definition = rows.get(index).definition();
        StringBuilder meta = new StringBuilder();
        meta.append(definition.category());
        if (!definition.tags().isEmpty()) {
            meta.append("  ").append(String.join(", ", definition.tags()));
        }
        String cost = costText(definition);
        if (!cost.isEmpty()) {
            meta.append("  ").append(cost);
        }
        int y = this.height - 24;
        gui.drawString(this.font, meta.toString(), LIST_LEFT, y, 0xC0C0C0);
        String description = definition.data().get("description");
        if (description != null && !description.isBlank()) {
            int line = 0;
            for (var part : this.font.split(Component.literal(description), this.width - 16)) {
                gui.drawString(this.font, part, LIST_LEFT, y + 10 + line * 10, 0x808080);
                if (++line >= 1) {
                    break;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true; // the search box took it
        }
        searchBox.setFocused(false);
        if (button != 0 || ClientBarState.get() == null) {
            return false;
        }
        // category / tag cycle
        if (inside(mouseX, mouseY, 132, 20, 104, 16)) {
            cycleCategory();
            return true;
        }
        if (inside(mouseX, mouseY, Math.max(132, this.width - 84), 20, 76, 16)) {
            cycleTag();
            return true;
        }
        // bar tabs
        List<ResourceLocation> bars = manageableBars();
        int tabWidth = bars.isEmpty() ? 1 : Math.min(48, (this.width - 16) / bars.size());
        for (int i = 0; i < bars.size(); i++) {
            if (inside(mouseX, mouseY, LIST_LEFT + i * tabWidth, 42, tabWidth - 1, 16)) {
                if (!bars.get(i).equals(selectedBar)) {
                    selectedBar = bars.get(i);
                    selectedSlot = 0;
                    scrollOffset = 0;
                }
                return true;
            }
        }
        BarState state = ClientBarState.get();
        Bar target = targetBar(state);
        // slot row
        int slotCount = target == null ? 0 : target.slotCount();
        int slotWidth = slotCount == 0 ? 1 : Math.min(40, (this.width - 16) / slotCount);
        for (int i = 0; i < slotCount; i++) {
            if (inside(mouseX, mouseY, LIST_LEFT + i * slotWidth, 64, slotWidth - 1, 16)) {
                selectedSlot = i;
                return true;
            }
        }
        // clear button
        if (target != null && inside(mouseX, mouseY, this.width - 64, 82, 56, 20)) {
            sendAssignment("");
            return true;
        }
        // ability rows
        List<MenuQuery.Entry> rows = visibleRows();
        int listBottom = this.height - LIST_BOTTOM_PAD;
        int visible = Math.max(0, (listBottom - LIST_TOP) / ROW_HEIGHT);
        int offset = (int) ((mouseY - LIST_TOP) / ROW_HEIGHT) + scrollOffset;
        if (mouseY >= LIST_TOP && mouseY < listBottom && offset >= 0 && offset < rows.size()
                && (mouseY - LIST_TOP) / ROW_HEIGHT < visible) {
            sendAssignment(rows.get(offset).id().toString());
            return true;
        }
        return false;
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

    private void cycleCategory() {
        List<ResourceLocation> categories = MenuQuery.categories(AbilityDiscovery.entries());
        if (categories.isEmpty()) {
            categoryFilter = null;
            return;
        }
        int index = categoryFilter == null ? -1 : categories.indexOf(categoryFilter);
        categoryFilter = index + 1 >= categories.size() ? null : categories.get(index + 1);
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
        return MenuQuery.visibleEntries(AbilityDiscovery.entries(), selectedBar,
                searchBox == null ? "" : searchBox.getValue(), categoryFilter, tagFilter,
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
