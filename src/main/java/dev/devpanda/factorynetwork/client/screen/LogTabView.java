package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientLogState;
import dev.devpanda.factorynetwork.network.packet.ClearLogPacket;
import dev.devpanda.factorynetwork.network.packet.LogStatePacket;
import dev.devpanda.factorynetwork.runtime.LogLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

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

    /** Für die Zwischenablage: mit Tag, weil der Text allein weiterwandert. */
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss");

    private final List<Filter> filters = new ArrayList<>();

    /** Die Zeilen, wie sie zuletzt gezeichnet wurden — für den Klick. */
    private final List<Row> rows = new ArrayList<>();

    /** Die beiden Knöpfe rechts in der Filterzeile. */
    private final List<Action> actions = new ArrayList<>();

    /**
     * Was zuletzt geschah, und wie lange es noch dasteht.
     *
     * <p>Kopieren tut sichtbar nichts. Ohne Rückmeldung klickt man ein
     * zweites und ein drittes Mal und weiß immer noch nicht, ob es geklappt
     * hat.
     */
    private String note = "";
    private long noteUntil;

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
        actions.clear();
        rows.clear();
        int line = y + 3;
        line = renderFilters(graphics, line, mouseX, mouseY);

        List<LogStatePacket.Line> visible = ClientLogState.visible();
        if (visible.isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "screen.factorynetwork.terminal.log.empty"),
                    x + 3, line + 4, TerminalScreen.TEXT_FAINT, false);
            return;
        }

        int room = (y + height - line) / LINE;
        int first = Math.max(0, visible.size() - room - scroll);
        int last = Math.min(visible.size(), first + room);
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
            rows.add(new Row(line, entry));
            line = renderEntry(graphics, line, entry);
        }
        renderNote(graphics);
    }

    /** Die Rückmeldung unten, ein paar Sekunden lang. */
    private void renderNote(GuiGraphics graphics) {
        if (note.isEmpty() || System.currentTimeMillis() > noteUntil) {
            return;
        }
        graphics.drawString(font, note, x + 3, y + height - LINE,
                TerminalScreen.ACCENT, false);
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

        // Rechtsbündig und weg von den Filtern: „Leeren" ist der einzige
        // Knopf hier, der etwas wegnimmt, und er soll nicht neben dem sitzen,
        // den man oft drückt.
        int right = x + width - 3;
        right = renderAction(graphics, right, line, "clear", mouseX, mouseY);
        renderAction(graphics, right - 2, line, "copy", mouseX, mouseY);
        return line + LINE + 4;
    }

    /** Ein Knopf rechts; gibt zurück, wo der nächste enden darf. */
    private int renderAction(GuiGraphics graphics, int right, int line,
                             String key, int mouseX, int mouseY) {
        String label = Component.translatable(
                "screen.factorynetwork.terminal.log." + key).getString();
        int wide = font.width(label) + 6;
        int left = right - wide;
        boolean hovered = mouseX >= left && mouseX < right
                && mouseY >= line - 1 && mouseY < line + 9;
        graphics.fill(left, line - 1, right, line + 9,
                hovered ? TerminalScreen.BUTTON_HOVER : TerminalScreen.BUTTON);
        graphics.drawString(font, label, left + 3, line, TerminalScreen.TEXT_DIM, false);
        actions.add(new Action(left, line - 1, right, key));
        return left;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Filter filter : filters) {
            if (filter.hit(mouseX, mouseY)) {
                ClientLogState.setMinimum(filter.level());
                scroll = 0;
                return true;
            }
        }
        for (Action action : actions) {
            if (!action.hit(mouseX, mouseY)) {
                continue;
            }
            if ("clear".equals(action.key())) {
                PacketDistributor.sendToServer(new ClearLogPacket());
                scroll = 0;
                say("screen.factorynetwork.terminal.log.cleared");
            } else {
                copyAll();
            }
            return true;
        }
        // Ein Klick auf eine Zeile nimmt sie mit. Beim Melden eines Fehlers
        // ist genau sie die Zeile, die man braucht, und abtippen will sie
        // niemand.
        for (Row row : rows) {
            if (mouseY >= row.top() && mouseY < row.top() + LINE
                    && mouseX >= x && mouseX < x + width) {
                Minecraft.getInstance().keyboardHandler.setClipboard(plain(row.entry()));
                say("screen.factorynetwork.terminal.log.copied_line");
                return true;
            }
        }
        return false;
    }

    /** Das ganze sichtbare Protokoll, Zeile für Zeile. */
    private void copyAll() {
        StringBuilder out = new StringBuilder();
        for (LogStatePacket.Line entry : ClientLogState.visible()) {
            out.append(plain(entry)).append(System.lineSeparator());
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(out.toString());
        say("screen.factorynetwork.terminal.log.copied");
    }

    /**
     * Eine Zeile als Text.
     *
     * <p>Mit Datum, anders als auf dem Bildschirm: Was in einer Nachricht an
     * jemand anderen landet, hat keinen Reiter darüber, der den Tag verrät.
     */
    private static String plain(LogStatePacket.Line entry) {
        String stamp = format(entry.time(), STAMP);
        String source = entry.source().isBlank() ? "" : " [" + entry.source() + "]";
        return stamp + " " + entry.level() + source + " " + entry.text();
    }

    private void say(String key) {
        note = Component.translatable(key).getString();
        noteUntil = System.currentTimeMillis() + 3000;
    }

    public boolean mouseScrolled(double delta) {
        int room = Math.max(1, (height - 20) / LINE);
        int max = Math.max(0, ClientLogState.visible().size() - room);
        scroll = Math.max(0, Math.min(max, scroll + (int) Math.signum(delta)));
        return true;
    }

    private static String format(long time, DateTimeFormatter formatter) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
                .format(formatter);
    }

    /** Eine gezeichnete Zeile, für den Klick gemerkt. */
    private record Row(int top, LogStatePacket.Line entry) {
    }

    /** Ein Knopf rechts, für den Klick gemerkt. */
    private record Action(int left, int top, int right, String key) {

        boolean hit(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < right
                    && mouseY >= top && mouseY < top + 10;
        }
    }

    /** Ein Filterknopf, für den Klick gemerkt. */
    private record Filter(int left, int top, LogLevel level) {

        boolean hit(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + FILTER
                    && mouseY >= top && mouseY < top + 10;
        }
    }
}
