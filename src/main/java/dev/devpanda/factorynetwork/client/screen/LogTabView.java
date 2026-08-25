package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientLogState;
import dev.devpanda.factorynetwork.network.packet.LogStatePacket;
import dev.devpanda.factorynetwork.runtime.LogLevel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Was das Netz zu sagen hatte.
 *
 * <p><b>Der Anlass:</b> {@code log()} gab es seit dem ersten Tag, und die
 * Zeilen gingen in eine Liste, die niemand las. Dasselbe galt für die
 * Hinweise der Laufzeit — „maintain ohne filter", „Der Knopf nennt keine
 * Funktion". Ein Programm, das nichts sagen kann, ist beim Suchen eines
 * Fehlers auf Raten angewiesen.
 *
 * <p>Das Jüngste steht unten, wie in jedem Protokoll: Wer nachsieht, will
 * wissen, was zuletzt geschah, und findet es dort, wo der Text aufhört.
 */
public class LogTabView {

    private static final int LINE = 10;

    /** Breite eines Filterknopfs. */
    private static final int FILTER = 40;

    /** Abstand zwischen Uhrzeit und Text. */
    private static final int TIME_WIDTH = 34;

    /**
     * Die Uhrzeit, ohne Datum.
     *
     * <p>Zweihundert Zeilen reichen selten über einen Tag; wo doch, trennt
     * die Datumszeile dazwischen.
     */
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final List<Filter> filters = new ArrayList<>();

    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /** Um wie viele Zeilen nach oben geblättert wurde. */
    private int scroll;

    public LogTabView(Font font, int x, int y, int width, int height) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        filters.clear();
        int line = y + 3;
        line = renderFilters(graphics, line, mouseX, mouseY);

        List<LogStatePacket.Line> visible = ClientLogState.visible();
        if (visible.isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "screen.factorynetwork.terminal.log.empty"),
                    x + 3, line + 4, TerminalScreen.TEXT_FAINT, false);
            return;
        }

        int rows = (y + height - line) / LINE;
        int first = Math.max(0, visible.size() - rows - scroll);
        int last = Math.min(visible.size(), first + rows);
        String lastDay = "";
        for (int i = first; i < last; i++) {
            LogStatePacket.Line entry = visible.get(i);
            String day = format(entry.time(), DAY);
            if (!day.equals(lastDay)) {
                lastDay = day;
                // Nur beim Wechsel: Ein Datum über jeder Zeile wäre Lärm.
                if (i > first || scroll > 0) {
                    graphics.drawString(font, day, x + 3, line, TerminalScreen.TEXT_FAINT, false);
                    line += LINE;
                }
            }
            line = renderEntry(graphics, line, entry);
        }
    }

    private int renderEntry(GuiGraphics graphics, int line, LogStatePacket.Line entry) {
        LogLevel level = LogLevel.of(entry.level());
        graphics.drawString(font, format(entry.time(), CLOCK), x + 3, line,
                TerminalScreen.TEXT_FAINT, false);

        int textX = x + 3 + TIME_WIDTH;
        // Die Herkunft in Klammern davor, gedämpft: Sie ist die Auskunft,
        // nach der man sucht, und nicht die, die man liest.
        if (!entry.source().isBlank()) {
            String source = "[" + entry.source() + "] ";
            graphics.drawString(font, source, textX, line, TerminalScreen.TEXT_DIM, false);
            textX += font.width(source);
        }
        int room = x + width - 3 - textX;
        graphics.drawString(font, font.plainSubstrByWidth(entry.text(), Math.max(20, room)),
                textX, line, colourOf(level), false);
        return line + LINE;
    }

    private static int colourOf(LogLevel level) {
        if (level == null) {
            return TerminalScreen.TEXT;
        }
        return switch (level) {
            case DEBUG -> TerminalScreen.TEXT_FAINT;
            case INFO -> TerminalScreen.TEXT;
            case WARN -> TerminalScreen.WARN;
            case ERROR -> 0xE05C5C;
        };
    }

    /**
     * Die vier Filterknöpfe.
     *
     * <p>Sie schalten nicht einzelne Stufen an und aus, sondern setzen eine
     * Untergrenze: Wer Warnungen sehen will, will die Fehler auch. Vier
     * Knöpfe mit je zwei Zuständen wären sechzehn Ansichten, von denen
     * fünfzehn niemand meint.
     */
    private int renderFilters(GuiGraphics graphics, int line, int mouseX, int mouseY) {
        int left = x + 3;
        for (LogLevel level : LogLevel.values()) {
            boolean active = ClientLogState.minimum() == level;
            boolean hovered = mouseX >= left && mouseX < left + FILTER
                    && mouseY >= line - 1 && mouseY < line + 9;
            graphics.fill(left, line - 1, left + FILTER, line + 9,
                    hovered ? TerminalScreen.BUTTON_HOVER : TerminalScreen.BUTTON);
            long count = ClientLogState.count(level);
            String label = Component.translatable(
                    "screen.factorynetwork.terminal.log." + level.key()).getString();
            if (count > 0) {
                label = label + " " + count;
            }
            graphics.drawString(font, font.plainSubstrByWidth(label, FILTER - 4),
                    left + 2, line, active ? TerminalScreen.ACCENT : TerminalScreen.TEXT_DIM,
                    false);
            filters.add(new Filter(left, line - 1, level));
            left += FILTER + 2;
        }
        return line + LINE + 4;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Filter filter : filters) {
            if (filter.hit(mouseX, mouseY)) {
                ClientLogState.setMinimum(filter.level());
                scroll = 0;
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double delta) {
        int rows = Math.max(1, (height - 20) / LINE);
        int max = Math.max(0, ClientLogState.visible().size() - rows);
        scroll = Math.max(0, Math.min(max, scroll + (int) Math.signum(delta)));
        return true;
    }

    private static String format(long time, DateTimeFormatter formatter) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
                .format(formatter);
    }

    /** Ein Filterknopf, für den Klick gemerkt. */
    private record Filter(int left, int top, LogLevel level) {

        boolean hit(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + FILTER
                    && mouseY >= top && mouseY < top + 10;
        }
    }
}
