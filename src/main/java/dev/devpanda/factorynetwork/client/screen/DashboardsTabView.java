package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientDisplayState;
import dev.devpanda.factorynetwork.network.packet.DisplayActionPacket;
import dev.devpanda.factorynetwork.network.packet.DisplayStatePacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Anzeigen des Programms, aus der Ferne.
 *
 * <p>Dieselben Zeilen wie auf dem Display an der Wand — der Server hat sie
 * fertig ausgewertet geschickt. Der Sinn ist der Weg dorthin: Wer eine Anlage
 * über mehrere Kammern verteilt hat, will nicht zu jedem Schild laufen, um zu
 * sehen, ob etwas klemmt.
 *
 * <p>Knöpfe wirken hier wie dort. Was hinter einem steckt, weiß nur der
 * Server; von hier geht allein die Nummer.
 */
public class DashboardsTabView {

    private static final int LINE = 10;

    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<Hit> hits = new ArrayList<>();

    /** Ein Knopf an seiner Stelle, für den Klick gemerkt. */
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

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        hits.clear();
        List<DisplayStatePacket.Panel> panels = ClientDisplayState.panels();
        if (panels.isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "screen.factorynetwork.terminal.dashboards.none"),
                    x + 3, y + 3, 0x8B8B8B, false);
            return;
        }

        int line = y + 3;
        for (DisplayStatePacket.Panel panel : panels) {
            if (line > y + height - LINE) {
                break;
            }
            graphics.drawString(font, "§8" + panel.name(), x + 3, line,
                    TerminalScreen.TEXT_DIM, false);
            line += LINE + 1;
            for (int i = 0; i < panel.lines().size() && line <= y + height - LINE; i++) {
                String content = panel.lines().get(i);
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
                    // Getroffen wird die Zeile, geschickt der Eintrag.
                    hits.add(new Hit(x + 6, line - 1, x + 9 + textWidth, panel.name(),
                            button.entry()));
                    graphics.drawString(font, content, x + 8, line, TerminalScreen.TEXT, false);
                } else {
                    graphics.drawString(font, font.plainSubstrByWidth(content, width - 12),
                            x + 6, line, TerminalScreen.TEXT, false);
                }
                line += LINE;
            }
            line += 4;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Hit hit : hits) {
            if (hit.covers(mouseX, mouseY)) {
                PacketDistributor.sendToServer(
                        new DisplayActionPacket(hit.display(), hit.entry()));
                return true;
            }
        }
        return false;
    }
}
