package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientStorageView;
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
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(font, amount,
                    cellX + 17 - font.width(amount), cellY + 9, 0xFFFFFF, true);
            graphics.pose().popPose();
        }

        drawScrollbar(graphics);
        drawFooter(graphics);
    }

    private void drawSearch(GuiGraphics graphics) {
        graphics.blit(WIDGETS, x + 2, y + 2, 0, 32, 96, 12, 512, 512);
        String shown = search.isEmpty()
                ? Component.translatable("screen.factorynetwork.terminal.search").getString()
                : search.toString();
        int colour = search.isEmpty() ? 0x6E7A70 : 0xA3D9A5;
        graphics.drawString(font, font.plainSubstrByWidth(shown, 88), x + 5, y + 4, colour, false);
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = x + 5 + font.width(font.plainSubstrByWidth(search.toString(), 88));
            graphics.fill(cursorX, y + 3, cursorX + 1, y + 12, 0xFFA3D9A5);
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int max = maxScroll();
        int trackX = gridX() + COLUMNS * SLOT + 2;
        int trackY = gridY();
        int trackH = ROWS * SLOT;
        graphics.blit(WIDGETS, trackX, trackY, 108, 32, 12, 15, 512, 512);
        if (max == 0) {
            return;
        }
        int thumbY = trackY + (trackH - 15) * scrollRow / max;
        graphics.blit(WIDGETS, trackX, thumbY, 96, 32, 12, 15, 512, 512);
    }

    private void drawFooter(GuiGraphics graphics) {
        int footerY = y + height - 10;
        Component text;
        if (ClientStorageView.isTruncated()) {
            text = Component.translatable("screen.factorynetwork.terminal.storage.truncated",
                    ClientStorageView.knownTypes(), ClientStorageView.totalTypes());
        } else {
            text = Component.translatable("screen.factorynetwork.terminal.storage.summary",
                    ClientStorageView.rows().size(),
                    ClientStorageView.shortAmount(ClientStorageView.totalCount()));
        }
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), width - 4),
                x + 2, footerY, TerminalScreen.TEXT_DIM, false);
    }

    // ---- Bedienung --------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Suchfeld
        if (mouseX >= x + 2 && mouseX < x + 98 && mouseY >= y + 2 && mouseY < y + 14) {
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
