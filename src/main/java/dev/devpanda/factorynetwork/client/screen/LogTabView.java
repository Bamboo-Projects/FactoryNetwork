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
 * What the network had to say.
 *
 * <p><b>The occasion:</b> {@code log()} existed from day one, and the lines
 * went into a list nobody read. The same went for the runtime's hints —
 * "maintain ohne filter", "Der Knopf nennt keine Funktion". A program that can
 * say nothing is reduced to guesswork when hunting for a fault.
 *
 * <p>The most recent stands at the bottom, as in every log: whoever looks
 * wants to know what happened last, and finds it where the text ends.
 */
public class LogTabView {

    private static final int LINE = 10;

    /** Width of a filter button. */
    private static final int FILTER = 40;

    /** The gap between the time and the text. */
    private static final int TIME_WIDTH = 34;

    /**
     * The time of day, without a date.
     *
     * <p>Two hundred lines rarely span more than a day; where they do, the
     * date line in between separates them.
     */
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** For the clipboard: with the date, because the text travels on by itself. */
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss");

    private final List<Filter> filters = new ArrayList<>();

    /** The lines as they were last drawn — for the click. */
    private final List<Row> rows = new ArrayList<>();

    /** The two buttons on the right in the filter row. */
    private final List<Action> actions = new ArrayList<>();

    /**
     * What happened last, and how long it stays on screen.
     *
     * <p>Copying visibly does nothing. Without feedback you click a second and
     * a third time and still do not know whether it worked.
     */
    private String note = "";
    private long noteUntil;

    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /** By how many lines the view has been scrolled up. */
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
                // Only on a change: a date above every line would be noise.
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

    /** The feedback at the bottom, for a few seconds. */
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
        // The origin in brackets in front, muted: it is the information you
        // search for, not the one you read.
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
     * The four filter buttons.
     *
     * <p>They do not switch individual levels on and off, but set a lower
     * bound: whoever wants to see warnings wants the errors too. Four buttons
     * with two states each would be sixteen views, fifteen of which nobody
     * means.
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

        // Right-aligned and away from the filters: "Clear" is the only button
        // here that takes something away, and it should not sit next to the
        // one you press often.
        int right = x + width - 3;
        right = renderAction(graphics, right, line, "clear", mouseX, mouseY);
        renderAction(graphics, right - 2, line, "copy", mouseX, mouseY);
        return line + LINE + 4;
    }

    /** A button on the right; returns where the next one may end. */
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
        // A click on a line takes it along. When reporting a fault it is
        // exactly the line you need, and nobody wants to type it out by hand.
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

    /** The whole visible log, line by line. */
    private void copyAll() {
        StringBuilder out = new StringBuilder();
        for (LogStatePacket.Line entry : ClientLogState.visible()) {
            out.append(plain(entry)).append(System.lineSeparator());
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(out.toString());
        say("screen.factorynetwork.terminal.log.copied");
    }

    /**
     * A line as text.
     *
     * <p>With a date, unlike on screen: what ends up in a message to someone
     * else has no tab above it that gives away the day.
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

    /** A drawn line, remembered for the click. */
    private record Row(int top, LogStatePacket.Line entry) {
    }

    /** A button on the right, remembered for the click. */
    private record Action(int left, int top, int right, String key) {

        boolean hit(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < right
                    && mouseY >= top && mouseY < top + 10;
        }
    }

    /** A filter button, remembered for the click. */
    private record Filter(int left, int top, LogLevel level) {

        boolean hit(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + FILTER
                    && mouseY >= top && mouseY < top + 10;
        }
    }
}
