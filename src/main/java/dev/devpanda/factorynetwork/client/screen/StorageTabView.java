package dev.devpanda.factorynetwork.client.screen;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import dev.devpanda.factorynetwork.platform.client.Keys;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientStorageView;
import dev.devpanda.factorynetwork.client.FnClientConfig;
import dev.devpanda.factorynetwork.client.SearchMode;
import dev.devpanda.factorynetwork.client.StorageSort;
import dev.devpanda.factorynetwork.compat.jei.JeiSearch;
import dev.devpanda.factorynetwork.network.packet.StorageActionPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The network's stock.
 *
 * <p>The cells look like slots and behave like slots, but are not: twenty
 * thousand types cannot be laid out as slots. Clicking therefore goes through
 * dedicated packets — and the server recomputes every time, because the
 * display can be a moment out of date.
 */
public class StorageTabView {

    private static final Identifier GRID = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/storage_grid.png");
    private static final Identifier WIDGETS = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/widgets.png");

    private static final int SLOT = 18;
    private static final int COLUMNS = dev.devpanda.factorynetwork.client.menu
            .TerminalLayout.GRID_COLUMNS;
    /**
     * Six rows, since the footer moved into the status line.
     *
     * <p>The room it freed up goes into fourteen more visible types — and not
     * into thin air.
     */
    private static final int ROWS = dev.devpanda.factorynetwork.client.menu
            .TerminalLayout.GRID_ROWS;

    private final TerminalScreen screen;
    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private final StringBuilder search = new StringBuilder();
    private boolean searchFocused;
    private int scrollRow;
    /** JEI's text as last seen, to notice when someone edits it there. */
    private String lastJeiText;

    public StorageTabView(TerminalScreen screen, Font font, int x, int y, int width, int height) {
        this.screen = screen;
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        SearchMode mode = FnClientConfig.searchMode();
        String start = FnClientConfig.rememberSearch() ? ClientStorageView.lastSearch() : "";
        if (mode.jeiSync() && JeiSearch.installed()) {
            String jei = JeiSearch.text();
            if (jei != null && !jei.isEmpty()) {
                start = jei;
            }
            lastJeiText = jei;
        }
        search.append(start);
        searchFocused = mode.autoFocus();
        ClientStorageView.setQuery(start);
    }

    private int gridX() {
        return x + 2;
    }

    private int gridY() {
        return y + 14;
    }

    private int maxScroll() {
        int rows = (ClientStorageView.rows().size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows - ROWS);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawSearch(graphics);
        // Someone typed in JEI's field while the terminal is open: follow it.
        if (FnClientConfig.searchMode().jeiSync() && JeiSearch.installed()) {
            String jei = JeiSearch.text();
            if (jei != null && !jei.equals(lastJeiText)) {
                lastJeiText = jei;
                if (!jei.contentEquals(search)) {
                    search.setLength(0);
                    search.append(jei);
                    applySearch();
                }
            }
        }
        drawSortButtons(graphics, mouseX, mouseY);

        // Grid background, one tile per cell. The texture holds eight rows and
        // the grid has nine; drawn as one piece, the last row stayed empty.
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, GRID,
                        gridX() + column * SLOT, gridY() + row * SLOT, 0, 0, SLOT, SLOT, 512, 512);
            }
        }

        List<ClientStorageView.Row> rows = ClientStorageView.rows();
        int first = scrollRow * COLUMNS;
        for (int index = 0; index < COLUMNS * ROWS; index++) {
            int position = first + index;
            if (position >= rows.size()) {
                break;
            }
            ClientStorageView.Row row = rows.get(position);
            int cellX = gridX() + (index % COLUMNS) * SLOT + 1;
            int cellY = gridY() + (index / COLUMNS) * SLOT + 1;

            graphics.item(row.stack(), cellX, cellY);
            // The amount is shown as text beside it, not as a stack count: a
            // network stock goes well beyond sixty-four.
            String amount = ClientStorageView.shortAmount(row.amount());
            if (row.isFluid()) {
                // So that nobody reads three buckets where three thousand
                // millibuckets stand.
                amount = amount + row.unit();
            }
            graphics.pose().pushMatrix();
            // Since 26.1 later strokes lie above earlier ones; no depth step needed.
            graphics.text(font, amount,
                    cellX + 17 - font.width(amount), cellY + 9, 0xFFFFFFFF, true);
            graphics.pose().popMatrix();
        }

        drawScrollbar(graphics);
    }

    /** Where the three sort buttons sit — right next to the search field. */
    private int sortButtonX(int index) {
        return x + width - 4 - (StorageSort.values().length - index) * 14;
    }

    private void drawSortButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (StorageSort candidate : StorageSort.values()) {
            int bx = sortButtonX(candidate.ordinal());
            int by = y + 2;
            boolean active = ClientStorageView.sort() == candidate;
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, WIDGETS, bx, by, 64, active ? 16 : 0, 12, 12, 512, 512);

            // A letter on the button, the direction beneath it.
            graphics.pose().pushMatrix();
            // Since 26.1 later strokes lie above earlier ones; no depth step needed.
            // Light on dark metal: the pressed button was previously labelled
            // almost black, because it used to be a light button.
            graphics.text(font, candidate.badge(), bx + 3, by + 2,
                    active ? TerminalScreen.TEXT : TerminalScreen.TEXT_DIM, false);
            if (active) {
                graphics.text(font, ClientStorageView.isDescending() ? "▾" : "▴",
                        bx + 8, by + 2, TerminalScreen.ACCENT, false);
            }
            graphics.pose().popMatrix();
        }

        // The search mode, left of the sort buttons. The letter says whether
        // the field takes the keyboard on opening, green whether JEI follows.
        // A second letter does not fit: at twelve pixels it ran into the first.
        SearchMode mode = FnClientConfig.searchMode();
        int mx = sortButtonX(-1);
        int my = y + 2;
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, WIDGETS, mx, my, 64, 0, 12, 12, 512, 512);
        graphics.text(font, mode.autoFocus() ? "F" : "S", mx + 3, my + 2,
                mode.jeiSync() ? TerminalScreen.ACCENT : TerminalScreen.TEXT, false);
    }

    private void drawSearch(GuiGraphicsExtractor graphics) {
        int fieldWidth = sortButtonX(-1) - x - 6;
        Widgets.stretched(graphics, x + 2, y + 2, fieldWidth, 12, 0, 32, 96, 2);
        String shown = search.isEmpty()
                ? Component.translatable("screen.factorynetwork.terminal.search").getString()
                : search.toString();
        int colour = search.isEmpty() ? 0xFF6E7A70 : 0xFFA3D9A5;
        graphics.pose().pushMatrix();
        // Since 26.1 later strokes lie above earlier ones; no depth step needed.
        graphics.text(font, font.plainSubstrByWidth(shown, fieldWidth - 8),
                x + 5, y + 4, colour, false);
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = x + 5 + font.width(
                    font.plainSubstrByWidth(search.toString(), fieldWidth - 8));
            graphics.fill(cursorX, y + 3, cursorX + 1, y + 12, 0xFFA3D9A5);
        }
        graphics.pose().popMatrix();
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int trackX = gridX() + COLUMNS * SLOT + 2;
        int trackY = gridY();
        int trackH = ROWS * SLOT;

        // One recessed groove over the full height, not a column of tiles: a
        // track drawn from fifteen-pixel pieces looked like a row of slots.
        graphics.fill(trackX, trackY, trackX + 12, trackY + trackH, 0xFF2A332E);
        graphics.fill(trackX + 1, trackY + 1, trackX + 11, trackY + trackH - 1, 0xFF0B0F0D);
        if (maxScroll() == 0) {
            return;
        }
        int thumbH = 15;
        int thumbY = trackY + 1 + (trackH - 2 - thumbH) * scrollRow / maxScroll();
        graphics.fill(trackX + 2, thumbY, trackX + 10, thumbY + thumbH, 0xFF39443D);
        graphics.fill(trackX + 2, thumbY, trackX + 10, thumbY + 1, 0xFF5D6862);
        graphics.fill(trackX + 4, thumbY + 6, trackX + 8, thumbY + 7, 0xFF5D6862);
        graphics.fill(trackX + 4, thumbY + 8, trackX + 8, thumbY + 9, 0xFF5D6862);
    }

    /**
     * What the tab shows on the left of the status line.
     *
     * <p>This used to sit as its own footer below the grid. The status line
     * now exists for all tabs, and two lines on top of each other would be one
     * too many.
     */
    public Component statusText() {
        if (isFull()) {
            return Component.translatable("screen.factorynetwork.terminal.storage.no_room");
        }
        if (ClientStorageView.isTruncated()) {
            return Component.translatable("screen.factorynetwork.terminal.storage.truncated",
                    ClientStorageView.knownTypes(), ClientStorageView.totalTypes());
        }
        return Component.translatable("screen.factorynetwork.terminal.storage.summary",
                ClientStorageView.rows().size(),
                ClientStorageView.shortAmount(ClientStorageView.totalCount()));
    }

    /** No room for another type? */
    public boolean isFull() {
        return ClientStorageView.freeTypes() == 0 && ClientStorageView.freeFluidTypes() == 0;
    }

    /**
     * The free space, as an explanation on hover.
     *
     * <p>There is no room for it in the line itself — and as long as there is
     * still space, the number is not news either. When it gets tight, it
     * takes the place of the summary as a warning.
     */
    public Component roomHint() {
        return Component.translatable("screen.factorynetwork.terminal.storage.room",
                ClientStorageView.freeTypes(), ClientStorageView.freeFluidTypes());
    }

    // ---- Controls ---------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Sort buttons first — they lie within the area of the search field
        for (StorageSort candidate : StorageSort.values()) {
            int bx = sortButtonX(candidate.ordinal());
            if (mouseX >= bx && mouseX < bx + 12 && mouseY >= y + 2 && mouseY < y + 14) {
                ClientStorageView.setSort(candidate);
                scrollRow = 0;
                return true;
            }
        }
        int modeX = sortButtonX(-1);
        if (mouseX >= modeX && mouseX < modeX + 12 && mouseY >= y + 2 && mouseY < y + 14) {
            SearchMode next = FnClientConfig.searchMode().next();
            FnClientConfig.setSearchMode(next);
            if (next.jeiSync() && JeiSearch.installed()) {
                JeiSearch.setText(search.toString());
                lastJeiText = search.toString();
            }
            return true;
        }
        if (mouseX >= x + 2 && mouseX < modeX && mouseY >= y + 2 && mouseY < y + 14) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        // Floor, not a cast: a cast rounds -0.5 to row 0, and so the strip
        // above the grid, where the sort buttons sit, counted as the first row.
        int column = (int) Math.floor((mouseX - gridX()) / SLOT);
        int row = (int) Math.floor((mouseY - gridY()) / SLOT);
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
            return false;
        }
        int index = (scrollRow + row) * COLUMNS + column;
        List<ClientStorageView.Row> rows = ClientStorageView.rows();

        ItemStack carried = screen.getMenu().getCarried();
        if (!carried.isEmpty()) {
            // A full cursor deposits — right-click only one, as in the inventory.
            int amount = button == 1 ? 1 : carried.getCount();
            ClientPacketDistributor.sendToServer(new StorageActionPacket(
                    StorageActionPacket.Kind.INSERT, dev.devpanda.factorynetwork.storage.ItemKey.of(carried), amount));
            return true;
        }
        if (index >= rows.size()) {
            return false;
        }
        ClientStorageView.Row entry = rows.get(index);
        if (entry.isFluid()) {
            // A fluid cannot be picked up onto the mouse cursor. The bucket
            // here is only an image; taking it out would mean fetching an
            // item out of nothing.
            return true;
        }
        // Familiar bindings: left a full stack, right a half one.
        int stack = entry.stack().getMaxStackSize();
        int wanted = button == 1 ? Math.max(1, stack / 2) : stack;
        // Shift: a full stack straight into the inventory, as in any container.
        StorageActionPacket.Kind kind = StorageActionPacket.Kind.EXTRACT;
        if (Keys.shift()) {
            wanted = stack;
            kind = StorageActionPacket.Kind.EXTRACT_TO_INVENTORY;
        }
        ClientPacketDistributor.sendToServer(new StorageActionPacket(
                kind, dev.devpanda.factorynetwork.storage.ItemKey.of(entry.stack()), wanted));
        return true;
    }

    /**
     * What lies under the cursor, with the exact amount.
     *
     * <p>Drawn after everything else, otherwise it disappears behind the grid.
     * For fluids it is more than a convenience: the grid shows a shortened
     * number, and the difference between three buckets and three thousand
     * millibuckets deserves to be said.
     */
    public void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // The sort buttons say what they do, not what lies beneath them.
        for (StorageSort candidate : StorageSort.values()) {
            int bx = sortButtonX(candidate.ordinal());
            if (mouseX >= bx && mouseX < bx + 12 && mouseY >= y + 2 && mouseY < y + 14) {
                graphics.setTooltipForNextFrame(font, List.of(candidate.title(),
                                Component.translatable("screen.factorynetwork.terminal.sort.hint")
                                        .withStyle(net.minecraft.ChatFormatting.GRAY)),
                        java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        int modeX = sortButtonX(-1);
        if (mouseX >= modeX && mouseX < modeX + 12 && mouseY >= y + 2 && mouseY < y + 14) {
            graphics.setTooltipForNextFrame(font, List.of(
                            Component.translatable("screen.factorynetwork.terminal.search_mode",
                                    FnClientConfig.searchMode().title()),
                            Component.translatable("screen.factorynetwork.terminal.search_mode.hint")
                                    .withStyle(net.minecraft.ChatFormatting.GRAY)),
                    java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        ClientStorageView.Row row = rowAt(mouseX, mouseY);
        if (row == null) {
            return;
        }
        String amount = String.format(java.util.Locale.GERMANY, "%,d", row.amount());
        net.minecraft.network.chat.Component stored = Component.translatable(
                        "screen.factorynetwork.terminal.stored", row.isFluid() ? amount + " mB" : amount)
                .withStyle(net.minecraft.ChatFormatting.GRAY);
        List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        if (row.isFluid()) {
            lines.add(row.stack().getHoverName());
        } else {
            // The item's own tooltip, as in any inventory: enchantments,
            // stored energy, the guide hint. The stock goes right below the name.
            lines.addAll(Screen.getTooltipFromItem(net.minecraft.client.Minecraft.getInstance(), row.stack()));
        }
        lines.add(Math.min(1, lines.size()), stored);
        // Which mod it comes from, unless another mod (JEI, say) already added it.
        String namespace = row.stack().getItem().builtInRegistryHolder().key().identifier().getNamespace();
        var container = net.neoforged.fml.ModList.get().getModContainerById(namespace);
        String modName = container.isPresent() ? container.get().getModInfo().getDisplayName() : namespace;
        boolean named = false;
        for (net.minecraft.network.chat.Component line : lines) {
            named |= line.getString().equals(modName);
        }
        if (!named) {
            lines.add(Component.literal(modName).withStyle(
                    net.minecraft.ChatFormatting.BLUE, net.minecraft.ChatFormatting.ITALIC));
        }
        graphics.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    /** The entry under the cursor, or {@code null}. */
    private ClientStorageView.Row rowAt(double mouseX, double mouseY) {
        // Floor, not a cast: a cast rounds -0.5 to row 0, and so the strip
        // above the grid, where the sort buttons sit, counted as the first row.
        int column = (int) Math.floor((mouseX - gridX()) / SLOT);
        int row = (int) Math.floor((mouseY - gridY()) / SLOT);
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
            return null;
        }
        int index = (scrollRow + row) * COLUMNS + column;
        List<ClientStorageView.Row> rows = ClientStorageView.rows();
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }
        scrollRow = Mth.clamp(scrollRow - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    /**
     * Keys for the search bar.
     *
     * <p><b>As long as it is focused for typing, all keys belong to it.</b>
     * Before, it handed back everything it did not need itself, and a window
     * with an inventory closes on the inventory key — so after the "e" in
     * "iron ore" you were standing in the world again. Escape releases the bar
     * instead of closing the window: the first Escape ends the search, the
     * second the terminal.
     */
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!searchFocused) {
            return false;
        }
        if (key == 256) {
            searchFocused = false;
            return true;
        }
        if (key == 259 && search.length() > 0) {
            search.deleteCharAt(search.length() - 1);
            applySearch();
        }
        return true;
    }

    public boolean charTyped(char character, int modifiers) {
        if (!searchFocused || Character.isISOControl(character)) {
            return false;
        }
        search.append(character);
        applySearch();
        return true;
    }

    private void applySearch() {
        ClientStorageView.setQuery(search.toString());
        ClientStorageView.rememberSearch(search.toString());
        if (FnClientConfig.searchMode().jeiSync() && JeiSearch.installed()) {
            JeiSearch.setText(search.toString());
            lastJeiText = search.toString();
        }
        scrollRow = 0;
    }
}
