package dev.devpanda.factorynetwork.client.screen;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import dev.devpanda.factorynetwork.block.entity.DisplayLine;
import dev.devpanda.factorynetwork.client.ClientDisplayState;
import dev.devpanda.factorynetwork.client.ClientStorageView;
import dev.devpanda.factorynetwork.network.packet.DisplayActionPacket;
import dev.devpanda.factorynetwork.network.packet.DisplayStatePacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The program's displays, from afar.
 *
 * <p>The same lines as on the display on the wall — the server sent them
 * already evaluated. The point is the trip it saves: someone who has spread a
 * plant across several rooms does not want to walk to every sign to see
 * whether something is stuck.
 *
 * <p>Buttons act here as they do there. What sits behind one only the server
 * knows; from here only the number goes out.
 */
public class DashboardsTabView {

    private static final int LINE = 10;
    /** A grid cell, the same size as in the storage tab. */
    private static final int SLOT = 18;

    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<Hit> hits = new ArrayList<>();

    /** A button at its place, remembered for the click. */
    private record Hit(int left, int top, int right, String display, int entry) {

        boolean covers(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + LINE;
        }
    }

    public DashboardsTabView(Font font, int x, int y, int width, int height) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        hits.clear();
        List<DisplayStatePacket.Panel> panels = ClientDisplayState.panels();
        if (panels.isEmpty()) {
            graphics.text(font, Component.translatable(
                            "screen.factorynetwork.terminal.dashboards.none"),
                    x + 3, y + 3, 0xFF8B8B8B, false);
            return;
        }

        int line = y + 3;
        for (DisplayStatePacket.Panel panel : panels) {
            if (line > y + height - LINE) {
                break;
            }
            graphics.text(font, "§8" + panel.name(), x + 3, line,
                    TerminalScreen.TEXT_DIM, false);
            line += LINE + 1;
            for (int i = 0; i < panel.shown().size() && line <= y + height - LINE; i++) {
                DisplayLine shown = panel.shown().get(i);
                if (shown.isGrid()) {
                    line = drawGrid(graphics, shown, line);
                    continue;
                }
                String content = shown.text();
                final int lineIndex = i;
                DisplayStatePacket.Button button = panel.buttons().stream()
                        .filter(candidate -> candidate.line() == lineIndex)
                        .findFirst().orElse(null);
                int textWidth = font.width(content);
                if (button != null) {
                    boolean hovered = mouseX >= x + 6 && mouseX < x + 9 + textWidth
                            && mouseY >= line - 1 && mouseY < line + LINE - 1;
                    graphics.fill(x + 6, line - 1, x + 9 + textWidth, line + 9,
                            hovered ? TerminalScreen.BUTTON_HOVER : TerminalScreen.BUTTON);
                    // The line is what gets hit, the entry is what gets sent.
                    hits.add(new Hit(x + 6, line - 1, x + 9 + textWidth, panel.name(),
                            button.entry()));
                    graphics.text(font, content, x + 8, line, TerminalScreen.TEXT, false);
                } else if (shown.icon() != null) {
                    // Half the size of an inventory icon, so it fits the
                    // line — the same icon as on the wall.
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(x + 6, line);
                    graphics.pose().scale(0.5F, 0.5F);
                    graphics.item(new ItemStack(shown.icon()), 0, 0);
                    graphics.pose().popMatrix();
                    graphics.text(font, font.plainSubstrByWidth(content, width - 22),
                            x + 16, line, TerminalScreen.TEXT, false);
                } else {
                    graphics.text(font, font.plainSubstrByWidth(content, width - 12),
                            x + 6, line, TerminalScreen.TEXT, false);
                }
                line += LINE;
            }
            line += 4;
        }
    }

    /**
     * An {@code items} grid, as many rows as the tab still has room for.
     *
     * <p>The same cells as the storage tab — icon and amount at the bottom
     * right. What does not fit is counted on a line below it, the way the
     * wall counts it in its last cell.
     *
     * @return where the next line starts
     */
    private int drawGrid(GuiGraphicsExtractor graphics, DisplayLine shown, int top) {
        int columns = Math.max(1, (width - 12) / SLOT);
        int rows = Math.max(0, (y + height - top - LINE) / SLOT);
        int fit = Math.min(shown.grid().size(), columns * rows);
        for (int c = 0; c < fit; c++) {
            int cellX = x + 6 + (c % columns) * SLOT;
            int cellY = top + (c / columns) * SLOT;
            DisplayLine.Cell cell = shown.grid().get(c);
            graphics.item(new ItemStack(cell.item()), cellX, cellY);
            String amount = ClientStorageView.shortAmount(cell.amount());
            graphics.text(font, amount, cellX + 17 - font.width(amount), cellY + 9,
                    0xFFFFFFFF, true);
        }
        int next = top + (int) Math.ceil(fit / (double) columns) * SLOT;
        if (fit < shown.grid().size()) {
            graphics.text(font, "… und " + (shown.grid().size() - fit) + " weitere",
                    x + 6, next, TerminalScreen.TEXT_DIM, false);
            next += LINE;
        }
        return next;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Hit hit : hits) {
            if (hit.covers(mouseX, mouseY)) {
                ClientPacketDistributor.sendToServer(
                        new DisplayActionPacket(hit.display(), hit.entry()));
                return true;
            }
        }
        return false;
    }
}
