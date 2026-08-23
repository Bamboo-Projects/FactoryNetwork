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
 * Der Bestand des Netzwerks.
 *
 * <p>Die Felder sehen aus wie Slots und verhalten sich wie Slots, sind aber
 * keine: Zwanzigtausend Arten lassen sich nicht als Slots anlegen. Geklickt
 * wird deshalb über eigene Nachrichten — und der Server rechnet jedes Mal
 * nach, weil die Anzeige einen Moment alt sein kann.
 */
public class StorageTabView {

    private static final ResourceLocation GRID = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/storage_grid.png");
    private static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/widgets.png");

    private static final int SLOT = 18;
    private static final int COLUMNS = 14;
    private static final int ROWS = 5;

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

        // Rasterhintergrund, so gross wie gebraucht
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
            // Die Menge steht als Text daneben, nicht als Stapelzahl: Ein
            // Netzbestand geht weit über vierundsechzig hinaus.
            String amount = ClientStorageView.shortAmount(row.amount());
            if (row.isFluid()) {
                // Damit niemand drei Eimer liest, wo dreitausend Millibucket
                // stehen.
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

    /** Wo die drei Sortierknöpfe sitzen — rechts neben dem Suchfeld. */
    private int sortButtonX(int index) {
        return x + width - 4 - (StorageSort.values().length - index) * 14;
    }

    private void drawSortButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        for (StorageSort candidate : StorageSort.values()) {
            int bx = sortButtonX(candidate.ordinal());
            int by = y + 2;
            boolean active = ClientStorageView.sort() == candidate;
            graphics.blit(WIDGETS, bx, by, 64, active ? 16 : 0, 12, 12, 512, 512);

            // Auf dem Knopf ein Buchstabe, darunter die Richtung.
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 100);
            graphics.drawString(font, candidate.badge(), bx + 3, by + 2,
                    active ? 0x303030 : 0x6E6E6E, false);
            if (active) {
                graphics.drawString(font, ClientStorageView.isDescending() ? "▾" : "▴",
                        bx + 8, by + 2, 0x303030, false);
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

        // Die Rinne über die volle Höhe. Die Kachel ist fünfzehn Pixel hoch;
        // einmal gezeichnet klebte sie oben in der Ecke und sah aus wie ein
        // vergessenes Bruchstück.
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
     * Was der Reiter in der Statuszeile links stehen hat.
     *
     * <p>Früher stand das als eigene Fußzeile unter dem Raster. Die
     * Statuszeile gibt es jetzt für alle Reiter, und zwei Zeilen
     * übereinander wären eine zu viel.
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

    /** Geht keine neue Art mehr hinein? */
    public boolean isFull() {
        return ClientStorageView.freeTypes() == 0 && ClientStorageView.freeFluidTypes() == 0;
    }

    /**
     * Der freie Platz, als Erklärung beim Zeigen.
     *
     * <p>In der Zeile selbst ist dafür kein Raum — und solange noch Platz
     * ist, ist die Zahl auch keine Nachricht. Wird es eng, tritt sie als
     * Warnung an die Stelle der Zusammenfassung.
     */
    public Component roomHint() {
        return Component.translatable("screen.factorynetwork.terminal.storage.room",
                ClientStorageView.freeTypes(), ClientStorageView.freeFluidTypes());
    }

    // ---- Bedienung --------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Sortierknöpfe zuerst — sie liegen im Bereich des Suchfelds
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
            // Voller Zeiger legt ab — rechts nur eines, wie im Inventar.
            int amount = button == 1 ? 1 : carried.getCount();
            PacketDistributor.sendToServer(new StorageActionPacket(
                    StorageActionPacket.Kind.INSERT, carried.getItem(), amount));
            return true;
        }
        if (index >= rows.size()) {
            return false;
        }
        ClientStorageView.Row entry = rows.get(index);
        if (entry.isFluid()) {
            // Eine Flüssigkeit lässt sich nicht auf den Mauszeiger nehmen.
            // Der Eimer ist hier nur ein Bild; ihn zu entnehmen hieße, einen
            // Gegenstand aus dem Nichts zu holen.
            return true;
        }
        // Vertraute Belegung: links ein Stapel, rechts ein halber.
        int stack = entry.stack().getMaxStackSize();
        int wanted = button == 1 ? Math.max(1, stack / 2) : stack;
        if (Screen.hasShiftDown()) {
            wanted = stack;
        }
        PacketDistributor.sendToServer(new StorageActionPacket(
                StorageActionPacket.Kind.EXTRACT, entry.stack().getItem(), wanted));
        return true;
    }

    /**
     * Was unter dem Zeiger liegt, mit genauer Menge.
     *
     * <p>Wird nach allem anderen gezeichnet, sonst verschwindet es hinter dem
     * Raster. Für Flüssigkeiten ist es mehr als eine Annehmlichkeit: Im Raster
     * steht eine gekürzte Zahl, und der Unterschied zwischen drei Eimern und
     * dreitausend Millibucket gehört gesagt.
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

    /** Der Eintrag unter dem Zeiger, oder {@code null}. */
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

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!searchFocused) {
            return false;
        }
        if (key == 259 && search.length() > 0) {
            search.deleteCharAt(search.length() - 1);
            applySearch();
            return true;
        }
        return false;
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
