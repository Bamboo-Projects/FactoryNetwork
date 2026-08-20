package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientNetworkState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Was das Netz gerade tut.
 *
 * <p>Rein lesend, und aus Daten, die ohnehin schon beim Öffnen übertragen
 * werden. Der Wert liegt darin, dass ein stehender Worker sichtbar wird — bis
 * jetzt erfuhr man davon nur, wenn eine Maschine nichts mehr bekam.
 */
public class NetworkTabView {

    private static final int LINE = 10;

    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public NetworkTabView(Font font, int x, int y, int width, int height) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        int line = y + 3;

        line = section(graphics, line, "screen.factorynetwork.terminal.network.connectors");
        List<String> connectors = ClientNetworkState.connectors();
        if (connectors.isEmpty()) {
            line = text(graphics, line, Component.translatable(
                    "screen.factorynetwork.terminal.no_connectors").getString(), 0x8B8B8B);
        } else {
            // In zwei Spalten, sonst ist die Liste nach sechs Namen am Ende.
            for (int i = 0; i < connectors.size() && line < y + height - 40; i += 2) {
                String left = connectors.get(i);
                String right = i + 1 < connectors.size() ? connectors.get(i + 1) : "";
                graphics.drawString(font, font.plainSubstrByWidth(left, width / 2 - 4),
                        x + 3, line, TerminalScreen.TEXT, false);
                if (!right.isEmpty()) {
                    graphics.drawString(font, font.plainSubstrByWidth(right, width / 2 - 4),
                            x + width / 2, line, TerminalScreen.TEXT, false);
                }
                line += LINE;
            }
        }

        line += 3;
        line = section(graphics, line, "screen.factorynetwork.terminal.network.workers");
        List<String> workers = ClientNetworkState.workers();
        if (workers.isEmpty()) {
            text(graphics, line, Component.translatable(
                    "screen.factorynetwork.terminal.network.no_workers").getString(), 0x8B8B8B);
        } else {
            for (String worker : workers) {
                if (line > y + height - LINE) {
                    break;
                }
                // Der Zustand steht hinter dem Doppelpunkt und färbt die Zeile.
                int colour = worker.contains("HALTED") ? 0xA03030
                        : worker.contains("WAITING") ? 0x8A6A20
                        : worker.contains("RUNNING") ? 0x2F6B33
                        : TerminalScreen.TEXT_DIM;
                graphics.drawString(font, font.plainSubstrByWidth(worker, width - 6),
                        x + 3, line, colour, false);
                line += LINE;
            }
        }
    }

    private int section(GuiGraphics graphics, int line, String key) {
        graphics.drawString(font, Component.translatable(key), x + 3, line,
                TerminalScreen.TEXT_DIM, false);
        return line + LINE + 1;
    }

    private int text(GuiGraphics graphics, int line, String content, int colour) {
        graphics.drawString(font, content, x + 3, line, colour, false);
        return line + LINE;
    }
}
