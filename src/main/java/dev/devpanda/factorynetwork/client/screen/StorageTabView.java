package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientStorageView;
import dev.devpanda.factorynetwork.client.StorageSort;
import dev.devpanda.factorynetwork.network.packet.StorageActionPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

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

    private static final ResourceLocation GRID = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/storage_grid.png");
    private static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(
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
    private boolean searchFocused = true;
    private int scrollRow;

    public StorageTabView(TerminalScreen screen, Font font, int x, int y, int width, int height) {
        this.screen = screen;
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
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

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        drawSearch(graphics);
        drawSortButtons(graphics, mouseX, mouseY);

        // Grid background, as large as needed
        graphics.blit(GRID, gridX(), gridY(), 0, 0, COLUMNS * SLOT, ROWS * SLOT, 512, 512);

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

            graphics.renderItem(row.stack(), cellX, cellY);
            // The amount is shown as text beside it, not as a stack count: a
            // network stock goes well beyond sixty-four.
            String amount = ClientStorageView.shortAmount(row.amount());
            if (row.isFluid()) {
                // So that nobody reads three buckets where three thousand
                // millibuckets stand.
                amount = amount + row.unit();
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(font, amount,
                    cellX + 17 - font.width(amount), cellY + 9, 0xFFFFFF, true);
            graphics.pose().popPose();
        }

        drawScrollbar(graphics);
    }

    /** Where the three sort buttons sit — right next to the search field. */
    private int sortButtonX(int index) {
        return x + width - 4 - (StorageSort.values().length - index) * 14;
    }

    private void drawSortButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        for (StorageSort candidate : StorageSort.values()) {
            int bx = sortButtonX(candidate.ordinal());
            int by = y + 2;
            boolean active = ClientStorageView.sort() == candidate;
            graphics.blit(WIDGETS, bx, by, 64, active ? 16 : 0, 12, 12, 512, 512);

            // A letter on the button, the direction beneath it.
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 100);
            // Light on dark metal: the pressed button was previously labelled
            // almost black, because it used to be a light button.
            graphics.drawString(font, candidate.badge(), bx + 3, by + 2,
                    active ? TerminalScreen.TEXT : TerminalScreen.TEXT_DIM, false);
            if (active) {
                graphics.drawString(font, ClientStorageView.isDescending() ? "▾" : "▴",
                        bx + 8, by + 2, TerminalScreen.ACCENT, false);
            }
            graphics.pose().popPose();
        }
    }

    private void drawSearch(GuiGraphics graphics) {
        int fieldWidth = sortButtonX(0) - x - 6;
        Widgets.stretched(graphics, x + 2, y + 2, fieldWidth, 12, 0, 32, 96, 2);
        String shown = search.isEmpty()
                ? Component.translatable("screen.factorynetwork.terminal.search").getString()
                : search.toString();
        int colour = search.isEmpty() ? 0x6E7A70 : 0xA3D9A5;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);
        graphics.drawString(font, font.plainSubstrByWidth(shown, fieldWidth - 8),
                x + 5, y + 4, colour, false);
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = x + 5 + font.width(
                    font.plainSubstrByWidth(search.toString(), fieldWidth - 8));
            graphics.fill(cursorX, y + 3, cursorX + 1, y + 12, 0xFFA3D9A5);
        }
        graphics.pose().popPose();
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int trackX = gridX() + COLUMNS * SLOT + 2;
        int trackY = gridY();
        int trackH = ROWS * SLOT;

        // The track over the full height. The tile is fifteen pixels tall;
        // drawn once, it stuck in the top corner and looked like a forgotten
        // fragment.
        for (int y = 0; y < trackH; y += 15) {
            int height = Math.min(15, trackH - y);
            graphics.blit(WIDGETS, trackX, trackY + y, 108, 32, 12, height, 512, 512);
        }
        if (maxScroll() == 0) {
            return;
        }
        int thumbY = trackY + (trackH - 15) * scrollRow / maxScroll();
        graphics.blit(WIDGETS, trackX, thumbY, 96, 32, 12, 15, 512, 512);
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
        if (mouseX >= x + 2 && mouseX < sortButtonX(0) && mouseY >= y + 2 && mouseY < y + 14) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        int column = (int) ((mouseX - gridX()) / SLOT);
        int row = (int) ((mouseY - gridY()) / SLOT);
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
            return false;
        }
        int index = (scrollRow + row) * COLUMNS + column;
        List<ClientStorageView.Row> rows = ClientStorageView.rows();

        ItemStack carried = screen.getMenu().getCarried();
        if (!carried.isEmpty()) {
            // A full cursor deposits — right-click only one, as in the inventory.
            int amount = button == 1 ? 1 : carried.getCount();
            PacketDistributor.sendToServer(new StorageActionPacket(
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
        if (Screen.hasShiftDown()) {
            wanted = stack;
        }
        PacketDistributor.sendToServer(new StorageActionPacket(
                StorageActionPacket.Kind.EXTRACT, dev.devpanda.factorynetwork.storage.ItemKey.of(entry.stack()), wanted));
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
    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        ClientStorageView.Row row = rowAt(mouseX, mouseY);
        if (row == null) {
            return;
        }
        List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        lines.add(row.stack().getHoverName());
        String amount = String.format(java.util.Locale.GERMANY, "%,d", row.amount());
        lines.add(net.minecraft.network.chat.Component.literal(
                        row.isFluid() ? amount + " mB" : amount + " Stück")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** The entry under the cursor, or {@code null}. */
    private ClientStorageView.Row rowAt(double mouseX, double mouseY) {
        int column = (int) ((mouseX - gridX()) / SLOT);
        int row = (int) ((mouseY - gridY()) / SLOT);
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
        scrollRow = 0;
    }
}
