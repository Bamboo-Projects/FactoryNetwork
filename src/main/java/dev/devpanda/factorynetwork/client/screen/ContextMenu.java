package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.FnFonts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Ein kleines Menü an der Stelle, an der geklickt wurde.
 *
 * <p>Für die Handgriffe an einer Datei. Sie als Knöpfe in die Zeile zu legen
 * ginge nicht: Eine Zeile ist zwölf Pixel hoch, und drei Symbole darin sind
 * drei Flecken, die man nicht trifft.
 *
 * <p>Ein Eintrag, der gerade nicht geht — die letzte Datei löschen —, wird
 * matt gezeigt und nicht weggelassen. Ein Menü, dessen Einträge wandern, muss
 * man jedes Mal neu lesen.
 */
public class ContextMenu {

    /** Höhe eines Eintrags. */
    private static final int ROW = 12;

    private static final int PAD = 4;

    public record Entry(String key, boolean enabled, Runnable action) {
    }

    private final Font font;
    private final List<Entry> entries;
    private final int x;
    private final int y;
    private final int width;

    public ContextMenu(Font font, int x, int y, List<Entry> entries) {
        this.font = font;
        this.entries = entries;
        int widest = 0;
        for (Entry entry : entries) {
            widest = Math.max(widest, font.width(label(entry)));
        }
        this.width = widest + PAD * 2;
        this.x = x;
        this.y = y;
    }

    private static Component label(Entry entry) {
        return FnFonts.mono(Component.translatable(entry.key()));
    }

    private int height() {
        return entries.size() * ROW + 2;
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y + 1) {
            return -1;
        }
        int index = (int) Math.floor((mouseY - y - 1) / (double) ROW);
        return index >= 0 && index < entries.size() ? index : -1;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        int bottom = y + height();
        graphics.fill(x, y, x + width, bottom, 0xF00E1113);
        graphics.fill(x, y, x + width, y + 1, 0xFF3C4147);
        graphics.fill(x, bottom - 1, x + width, bottom, 0xFF3C4147);
        graphics.fill(x, y, x + 1, bottom, 0xFF3C4147);
        graphics.fill(x + width - 1, y, x + width, bottom, 0xFF3C4147);

        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int rowTop = y + 1 + i * ROW;
            if (i == hovered && entry.enabled()) {
                graphics.fill(x + 1, rowTop, x + width - 1, rowTop + ROW, 0xFF232B27);
            }
            graphics.drawString(font, label(entry), x + PAD, rowTop + 2,
                    entry.enabled() ? TerminalScreen.TEXT : TerminalScreen.TEXT_FAINT, false);
        }
    }

    /**
     * Führt aus, worauf geklickt wurde.
     *
     * @return ob der Klick im Menü lag — daran erkennt der Aufrufer, dass er
     *         ihn nicht noch einmal auswerten darf
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height()) {
            return false;
        }
        int index = rowAt(mouseX, mouseY);
        if (index >= 0 && entries.get(index).enabled()) {
            entries.get(index).action().run();
        }
        return true;
    }
}
