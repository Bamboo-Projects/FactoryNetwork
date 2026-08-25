package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientCraftingState;
import dev.devpanda.factorynetwork.network.packet.CraftingActionPacket;
import dev.devpanda.factorynetwork.network.packet.CraftingStatePacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Fertigungsaufträge des Netzes.
 *
 * <p><b>Der Reiter war seit dem ersten Tag ausgegraut.</b> Er stand trotzdem
 * da, damit man sieht, wohin es geht — jetzt steht darin, was er versprochen
 * hat.
 *
 * <p>Bestellt wird nicht hier, sondern im Code: {@code craft(64 item:chest)}.
 * Das ist keine Sparsamkeit, sondern dieselbe Haltung wie überall in dieser
 * Mod — ein Netz tut nichts von selbst, und eine Bestellung ist etwas, das
 * man hinschreibt. Was dieser Reiter beiträgt, ist die Antwort darauf: was
 * daraus wurde und woran es hängt.
 */
public class CraftingTabView {

    private static final int LINE = 10;

    /** Breite des Abbruchknopfs. */
    private static final int CANCEL = 11;

    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int left, int top, int right, long id) {

        boolean covers(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + LINE;
        }
    }

    public CraftingTabView(Font font, int x, int y, int width, int height) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        hits.clear();
        List<CraftingStatePacket.Line> jobs = ClientCraftingState.jobs();
        if (jobs.isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "screen.factorynetwork.terminal.crafting.none"),
                    x + 3, y + 3, TerminalScreen.TEXT_DIM, false);
            graphics.drawString(font, Component.translatable(
                            "screen.factorynetwork.terminal.crafting.how"),
                    x + 3, y + 3 + LINE + 2, TerminalScreen.TEXT_FAINT, false);
            return;
        }

        int line = y + 3;
        for (CraftingStatePacket.Line job : jobs) {
            if (line > y + height - LINE) {
                break;
            }
            // Der Abbruchknopf ganz rechts: Er ist die einzige Wahl, die es
            // an einem Auftrag gibt, und darf nicht neben dem Text stehen,
            // dessen Länge sich ändert.
            int right = x + width - 4;
            boolean hovered = mouseX >= right - CANCEL && mouseX < right
                    && mouseY >= line - 1 && mouseY < line + LINE - 1;
            graphics.fill(right - CANCEL, line - 1, right, line + 9,
                    hovered ? TerminalScreen.BUTTON_HOVER : TerminalScreen.BUTTON);
            graphics.drawString(font, "×", right - CANCEL + 4, line,
                    TerminalScreen.TEXT, false);
            hits.add(new Hit(right - CANCEL, line - 1, right, job.id()));

            String head = job.done() + "/" + job.wanted() + " " + job.target();
            graphics.drawString(font, font.plainSubstrByWidth(head, width - CANCEL - 12),
                    x + 3, line, TerminalScreen.TEXT, false);
            line += LINE;

            // Der Grund steht darunter und eingerückt: Er gehört zum Auftrag
            // und ist doch nicht der Auftrag.
            String detail = job.detail().isEmpty() ? statusOf(job) : job.detail();
            if (line <= y + height - LINE) {
                graphics.drawString(font,
                        font.plainSubstrByWidth("§7" + detail, width - 16),
                        x + 9, line, TerminalScreen.TEXT_DIM, false);
                line += LINE;
            }
            line += 2;
        }
    }

    /** Ohne eigenen Grund steht dort der Zustand. */
    private static String statusOf(CraftingStatePacket.Line job) {
        return Component.translatable(
                "screen.factorynetwork.terminal.crafting.status."
                        + job.status().toLowerCase(java.util.Locale.ROOT)).getString();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Hit hit : hits) {
            if (hit.covers(mouseX, mouseY)) {
                PacketDistributor.sendToServer(new CraftingActionPacket(hit.id()));
                return true;
            }
        }
        return false;
    }
}
