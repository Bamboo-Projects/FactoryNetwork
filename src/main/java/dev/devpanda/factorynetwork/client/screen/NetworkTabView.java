package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientFlowState;
import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.network.packet.FlowActionPacket;
import dev.devpanda.factorynetwork.network.packet.FlowStatePacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * What the network is doing right now.
 *
 * <p>Read-only, and from data that is transferred on open anyway. The value
 * lies in a stalled worker becoming visible — until now you only learned of
 * it when a machine stopped getting anything.
 */
import dev.devpanda.factorynetwork.client.ClientTraffic;
import dev.devpanda.factorynetwork.network.Bandwidth;
import dev.devpanda.factorynetwork.network.packet.TrafficPacket;

public class NetworkTabView {

    private static final int LINE = 10;

    /** Width of the buttons on a line that is waiting for a choice. */
    private static final int BUTTON = 34;

    private final List<Button> buttons = new ArrayList<>();

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

    /**
     * By how many lines the content is pushed up.
     *
     * <p><b>The tab was already too long before the chart came along.</b>
     * Connectors, displays, workers, fluids, plants, flows, globals, storage
     * — on a grown network the window has room for half. Before, the list
     * simply stopped; now you can scroll on.
     */
    private int scroll;

    /**
     * How far the content reached at the last draw.
     *
     * <p>Measured, not computed: how tall this tab becomes depends on eight
     * lists and a chart — a formula for it would be a second truth beside the
     * drawing code and would drift apart at the next line.
     */
    private int contentHeight;

    /**
     * Scrolls, if there is anything to scroll.
     *
     * <p>The limit comes from the last drawn height. On the first frame it is
     * zero, so nothing scrolls — and then everything is in the window anyway.
     */
    public boolean mouseScrolled(double delta) {
        int ueberhang = Math.max(0, contentHeight - height + LINE);
        int max = (ueberhang + LINE - 1) / LINE;
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        // Clip, otherwise the scrolled content draws over the tab bar and the
        // status line below it.
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            renderContent(graphics, mouseX, mouseY);
        } finally {
            // Even on an error: an open clip makes the whole screen invisible,
            // not just this tab.
            graphics.disableScissor();
        }
    }

    /**
     * How tall the head is: one line of numbers and the curve below it.
     *
     * <p>It was first eleven pixels tall and sat beside the numbers — at
     * sixty pixels wide that looked like a bar, not a chart. Now it gets its
     * own line across the full width: five minutes of history are five
     * minutes, and you only see them when they have room.
     */
    private static final int HEAD_HEIGHT = 40;

    /** How tall the curve itself is. */
    private static final int SPARK_HEIGHT = 24;

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = y + 3 - scroll * LINE;

        // <b>The head carries what always holds.</b> Power and throughput sit
        // in the same place, no matter how the network looks — whoever looks
        // doesn't have to search first.
        int line = head(graphics, top);

        // <b>On the left, what works. On the right, what is there.</b> Two
        // questions, two columns: "is it running" and "what is attached".
        // Before, eight sections stood one below the other, all weighted the
        // same.
        int gap = 8;
        int columnWidth = (width - 6 - gap) / 2;
        int leftX = x + 3;
        int rightX = leftX + columnWidth + gap;

        int leftEnd = working(graphics, leftX, line, columnWidth);
        int rightEnd = present(graphics, rightX, line, columnWidth);

        // <b>And at the bottom, what is stuck.</b> Warnings belong together
        // and at the end: scatter them among the lists up top and you have to
        // read the whole tab to know whether something is missing.
        remember(warnings(graphics, Math.max(leftEnd, rightEnd) + 4));
    }

    /**
     * The head: power, throughput with a mini-curve, total amount.
     *
     * <p><b>The big curve was dropped for this.</b> It took a third of the
     * area and showed an empty box on a quiet network. Sixty pixels are
     * enough to see a spike — and whoever wants the numbers reads them beside
     * it.
     */
    private int head(GuiGraphics graphics, int line) {
        int right = x + width - 3;

        // Power on the left: whoever sees that nothing is running asks about that first.
        var supply = ClientFlowState.supply();
        String power = Component.translatable(
                        "screen.factorynetwork.terminal.network.power_short",
                        supply.stored(), supply.capacity(), supply.draw())
                .getString();
        // Red when the reserve drops below a tenth: this is the moment when
        // devices are about to fail — and the only power state you have to
        // see at once.
        int powerColour = supply.capacity() > 0
                && supply.stored() * 10 < supply.capacity()
                        ? TerminalScreen.BAD : TerminalScreen.TEXT;
        graphics.drawString(font, power, x + 3, line + 3, powerColour, false);

        // The numbers on the right in the header: now and in total.
        List<Integer> verlauf = ClientTraffic.perSecond();
        int peak = ClientTraffic.peak();
        int jetzt = verlauf.isEmpty() ? 0 : verlauf.get(verlauf.size() - 1);
        // Not only what flows, but out of how much. A number without a scale
        // doesn't answer the question you have in mind: is that a lot?
        int capacity = ClientTraffic.capacity();
        String rate = capacity > 0
                ? Bandwidth.usage(jetzt / Bandwidth.TICKS_PER_SECOND, capacity)
                : Bandwidth.perSecond(jetzt / Bandwidth.TICKS_PER_SECOND);
        graphics.drawString(font, rate, right - font.width(rate), line + 3,
                TerminalScreen.TEXT, false);
        String gesamt = Bandwidth.total(ClientTraffic.total());
        graphics.drawString(font, gesamt,
                right - 8 - font.width(rate) - font.width(gesamt), line + 3,
                TerminalScreen.TEXT_DIM, false);

        // And below it the curve across the full width.
        int sparkLeft = x + 3;
        int sparkTop = line + LINE + 3;
        int sparkBottom = sparkTop + SPARK_HEIGHT;
        graphics.fill(sparkLeft, sparkTop, right, sparkBottom, 0x22000000);

        // A fine line at half height: without it there is no seeing whether a
        // bar reaches a quarter or half of the peak.
        int middle = (sparkTop + sparkBottom) / 2;
        graphics.fill(sparkLeft, middle, right, middle + 1, 0x18FFFFFF);

        // And the controller's limit as its own line — but only if it fits in
        // the frame. A network that uses one percent of its limit would
        // otherwise get a line at the top edge that explains nothing.
        int limit = capacity * Bandwidth.TICKS_PER_SECOND;
        if (capacity > 0 && limit <= peak) {
            int y = sparkBottom - Math.max(1, limit * SPARK_HEIGHT / peak);
            graphics.fill(sparkLeft, y, right, y + 1, 0x66FF5555);
        }

        int columns = Math.min(verlauf.size(), right - sparkLeft);
        for (int i = 0; i < columns; i++) {
            int wert = verlauf.get(verlauf.size() - 1 - i);
            int hoehe = Math.max(wert > 0 ? 1 : 0, wert * SPARK_HEIGHT / peak);
            int cx = right - 1 - i;
            graphics.fill(cx, sparkBottom - hoehe, cx + 1, sparkBottom, 0xFF57C97A);
        }

        // The peak at the left edge of the curve: the scale, without which a
        // bar has no height.
        String spitze = Bandwidth.perSecond(peak / Bandwidth.TICKS_PER_SECOND);
        graphics.drawString(font, spitze, sparkLeft + 2, sparkTop + 1,
                TerminalScreen.TEXT_FAINT, false);

        // A line below it separates the head from the content.
        graphics.fill(x + 3, line + HEAD_HEIGHT, x + width - 3, line + HEAD_HEIGHT + 1,
                0x33FFFFFF);
        return line + HEAD_HEIGHT + 5;
    }

    /**
     * The left column: what works.
     *
     * <p>Workers with their consumption, and below them the flows. Both
     * answer the same question — is it running, and if not, what is it stuck
     * on.
     */
    private int working(GuiGraphics graphics, int cx, int line, int cw) {
        List<String> workers = ClientNetworkState.workers();
        line = columnHead(graphics, cx, line, cw,
                "screen.factorynetwork.terminal.network.workers", workers.size());

        if (workers.isEmpty()) {
            line = dim(graphics, cx, line, "—");
        } else {
            // The consumption per worker sits next to the worker: a separate
            // ranking would have named the same names a second time.
            var verbrauch = new java.util.HashMap<String, Long>();
            for (TrafficPacket.Consumer one : ClientTraffic.top()) {
                verbrauch.put(one.name(), one.bytes());
            }
            for (String worker : workers) {
                if (line > y + height - LINE) {
                    break;
                }
                int colour = worker.contains("HALTED") ? TerminalScreen.BAD
                        : worker.contains("WAITING") ? TerminalScreen.WARN
                        : worker.contains("RUNNING") ? TerminalScreen.GOOD
                        : TerminalScreen.TEXT_DIM;
                // The name comes before the colon; after it comes the state,
                // which the colour already shows.
                String name = worker.contains(":")
                        ? worker.substring(0, worker.indexOf(':')) : worker;
                String menge = verbrauch.containsKey(name)
                        ? Bandwidth.total(verbrauch.get(name)) : "";
                int platz = cw - 6 - font.width(menge);
                graphics.drawString(font, font.plainSubstrByWidth(worker, platz),
                        cx, line, colour, false);
                if (!menge.isEmpty()) {
                    graphics.drawString(font, menge, cx + cw - font.width(menge), line,
                            TerminalScreen.TEXT_DIM, false);
                }
                line += LINE;
            }
        }

        List<FlowStatePacket.Line> flows = ClientFlowState.flows();
        line += 5;
        line = columnHead(graphics, cx, line, cw,
                "screen.factorynetwork.terminal.network.flows", flows.size());
        line = flows(graphics, cx, line, cw);

        // The globals belong to the program like the flows — and only here,
        // if there are any.
        List<String> werte = ClientFlowState.globals();
        if (!werte.isEmpty()) {
            line += 5;
            line = columnHead(graphics, cx, line, cw,
                    "screen.factorynetwork.terminal.network.globals", werte.size());
            for (String wert : werte) {
                if (line > y + height - LINE) {
                    break;
                }
                graphics.drawString(font, font.plainSubstrByWidth(wert, cw),
                        cx, line, TerminalScreen.TEXT_DIM, false);
                line += LINE;
            }
        }
        return line;
    }

    /**
     * The right column: what is there.
     *
     * <p>Connectors and displays, plus fluids and plants — but only if they
     * exist. A heading with "none" beneath it costs two lines and says
     * nothing.
     */
    private int present(GuiGraphics graphics, int cx, int line, int cw) {
        List<String> connectors = ClientNetworkState.connectors();
        line = columnHead(graphics, cx, line, cw,
                "screen.factorynetwork.terminal.network.connectors", connectors.size());
        line = names(graphics, cx, line, cw, connectors);

        List<String> displays = ClientNetworkState.displays();
        if (!displays.isEmpty()) {
            line += 5;
            line = columnHead(graphics, cx, line, cw,
                    "screen.factorynetwork.terminal.network.displays", displays.size());
            line = names(graphics, cx, line, cw, displays);
        }

        List<String> fluids = ClientNetworkState.fluids();
        if (!fluids.isEmpty()) {
            line += 5;
            line = columnHead(graphics, cx, line, cw,
                    "screen.factorynetwork.terminal.network.fluids", fluids.size());
            line = names(graphics, cx, line, cw, fluids);
        }

        List<String> plants = ClientNetworkState.plants();
        if (!plants.isEmpty()) {
            line += 5;
            line = columnHead(graphics, cx, line, cw,
                    "screen.factorynetwork.terminal.network.plants", plants.size());
            for (String plant : plants) {
                if (line > y + height - LINE) {
                    break;
                }
                // What is missing or ambiguous stands out — that is what you
                // look for when a plant does nothing.
                int colour = plant.contains("?") || plant.contains("!")
                        ? TerminalScreen.WARN : TerminalScreen.TEXT;
                graphics.drawString(font, font.plainSubstrByWidth(plant, cw),
                        cx, line, colour, false);
                line += LINE;
            }
        }

        // Memory and disk: they say what the network can hold — the same
        // question as the lists above.
        line = capacity(graphics, cx, line, cw);
        return line;
    }

    /**
     * A heading with a number.
     *
     * <p>"WORKER 3" says at a glance what three lines of list would say — and
     * you see it without reading the list.
     */
    private int columnHead(GuiGraphics graphics, int cx, int line, int cw,
                           String key, int count) {
        graphics.drawString(font, Component.translatable(key), cx, line,
                TerminalScreen.TEXT_DIM, false);
        String zahl = String.valueOf(count);
        graphics.drawString(font, zahl, cx + cw - font.width(zahl), line,
                TerminalScreen.TEXT_DIM, false);
        // A fine line below it sums up the column.
        graphics.fill(cx, line + LINE, cx + cw, line + LINE + 1, 0x22FFFFFF);
        return line + LINE + 3;
    }

    /** A list of names, single-column — the column is already a column. */
    private int names(GuiGraphics graphics, int cx, int line, int cw, List<String> items) {
        if (items.isEmpty()) {
            return dim(graphics, cx, line, "—");
        }
        for (String item : items) {
            if (line > y + height - LINE) {
                break;
            }
            graphics.drawString(font, font.plainSubstrByWidth(item, cw),
                    cx, line, TerminalScreen.TEXT, false);
            line += LINE;
        }
        return line;
    }

    /** A line in a column, cut to its width. */
    private int columnLine(GuiGraphics graphics, int cx, int line, int cw,
                           String content, int colour) {
        graphics.drawString(font, font.plainSubstrByWidth(content, cw),
                cx, line, colour, false);
        return line + LINE;
    }

    private int dim(GuiGraphics graphics, int cx, int line, String content) {
        graphics.drawString(font, content, cx, line, 0x8B8B8B, false);
        return line + LINE;
    }

    /**
     * The foot: what is stuck.
     *
     * <p>Collected, and only when there is something. Before, "No server
     * rack" — the reason nothing runs at all — stood in the same type as a
     * list of plant names.
     */
    private int warnings(GuiGraphics graphics, int line) {
        List<Component> found = new ArrayList<>();
        if (ClientFlowState.threads() == 0) {
            found.add(Component.translatable(
                    "screen.factorynetwork.terminal.network.no_server"));
        } else if (ClientFlowState.queued() > 0) {
            found.add(Component.translatable(
                    "screen.factorynetwork.terminal.network.threads",
                    ClientFlowState.occupied(), ClientFlowState.threads(),
                    ClientFlowState.queued()));
        }
        if (found.isEmpty()) {
            return line;
        }
        graphics.fill(x + 3, line, x + width - 3, line + 1, 0x33FFFFFF);
        line += 4;
        for (Component one : found) {
            graphics.drawString(font, one, x + 3, line,
                    ClientFlowState.threads() == 0 ? TerminalScreen.BAD
                            : TerminalScreen.WARN, false);
            line += LINE;
        }
        return line;
    }

    private int globals(GuiGraphics graphics, int line) {
        List<String> werte = dev.devpanda.factorynetwork.client.ClientFlowState.globals();
        if (werte.isEmpty()) {
            return line;
        }
        line += 3;
        line = section(graphics, line, "screen.factorynetwork.terminal.network.globals");
        for (String wert : werte) {
            line = text(graphics, line, wert, TerminalScreen.TEXT_DIM);
        }
        return line;
    }

    /**
     * Remembers how far the content reached.
     *
     * <p>Computed without the offset: how much room the content needs does
     * not change just because you shift it.
     */
    private void remember(int line) {
        contentHeight = line - (y + 3 - scroll * LINE);
    }

    /**
     * Memory and disk.
     *
     * <p>Only if there is a server at all — otherwise the same zero would
     * stand there three times, and the line before it already says so.
     *
     * <p>The program size sits next to the space, because it is the only
     * limit you exceed while writing without noticing: whoever adds a
     * function does not count the instructions along the way.
     */
    private int capacity(GuiGraphics graphics, int cx, int line, int cw) {
        var rechen = dev.devpanda.factorynetwork.client.ClientFlowState.compute();
        if (rechen.threads() == 0) {
            return line;
        }
        line += 5;
        line = columnLine(graphics, cx, line, cw, Component.translatable(
                "screen.factorynetwork.terminal.network.memory",
                rechen.occupied() + rechen.queued(), rechen.memory()).getString(),
                TerminalScreen.TEXT_DIM);
        return columnLine(graphics, cx, line, cw, Component.translatable(
                "screen.factorynetwork.terminal.network.disk",
                rechen.program(), rechen.disk()).getString(),
                rechen.program() > rechen.disk() ? TerminalScreen.BAD : TerminalScreen.TEXT_DIM);
    }

    /**
     * The network's power.
     *
     * <p>How much power a plant draws you can't tell by looking at it — and a
     * network that has stalled because the reserve is empty looks like one
     * with a bug in the program. That is why it stands right at the top here.
     */
    private int supply(GuiGraphics graphics, int line) {
        var strom = dev.devpanda.factorynetwork.client.ClientFlowState.supply();
        var zustand = dev.devpanda.factorynetwork.network.NetworkPower.State
                .values()[Math.min(strom.state(),
                        dev.devpanda.factorynetwork.network.NetworkPower.State.values().length - 1)];
        String schluessel = switch (zustand) {
            case RUNNING -> "screen.factorynetwork.terminal.network.power";
            case BOOTING -> "screen.factorynetwork.terminal.network.power_booting";
            case OFF -> "screen.factorynetwork.terminal.network.power_off";
        };
        int colour = switch (zustand) {
            case RUNNING -> TerminalScreen.TEXT_DIM;
            case BOOTING -> TerminalScreen.WARN;
            case OFF -> TerminalScreen.BAD;
        };
        // The output is shown only on the running line: a network that is
        // booting or stalled gives nothing off, and a zero there would be a
        // number that says nothing.
        if (zustand == dev.devpanda.factorynetwork.network.NetworkPower.State.RUNNING) {
            return text(graphics, line, Component.translatable(schluessel, strom.draw(),
                    strom.supplied(), grouped(strom.stored()),
                    grouped(strom.capacity())).getString(), colour);
        }
        return text(graphics, line, Component.translatable(schluessel, strom.draw(),
                grouped(strom.stored()), grouped(strom.capacity())).getString(), colour);
    }

    private static String grouped(int value) {
        return String.format(java.util.Locale.GERMANY, "%,d", value);
    }

    /**
     * The flows that are currently waiting.
     *
     * <p>A flow that has reported in gets two buttons: let it run on or abort
     * it. That is the choice the language promises, and it has to sit where
     * the player sees the state — not in a command they must first look up.
     */
    private int flows(GuiGraphics graphics, int cx, int line, int cw) {
        buttons.clear();
        List<FlowStatePacket.Line> flows = ClientFlowState.flows();
        if (flows.isEmpty()) {
            return dim(graphics, cx, line, "—");
        }
        for (FlowStatePacket.Line flow : flows) {
            if (line > y + height - LINE) {
                break;
            }
            boolean stale = "STALE".equals(flow.status());
            int colour = stale ? TerminalScreen.WARN
                    : "FAILED".equals(flow.status()) ? TerminalScreen.BAD
                    : "QUEUED".equals(flow.status()) ? TerminalScreen.WARN
                    : "RUNNING".equals(flow.status()) ? TerminalScreen.GOOD
                    : TerminalScreen.TEXT_DIM;
            String label = flow.entry() + " — " + describe(flow);
            int room = stale ? cw - 2 * BUTTON - 6 : cw;
            graphics.drawString(font, font.plainSubstrByWidth(label, room), cx, line,
                    colour, false);
            if (stale) {
                int right = cx + cw;
                button(graphics, right - BUTTON, line, "keep", flow.id(), true);
                button(graphics, right - 2 * BUTTON - 3, line, "abort", flow.id(), false);
            }
            line += LINE;
        }
        return line;
    }

    /** What the line shows: the reason, otherwise the state. */
    private static String describe(FlowStatePacket.Line flow) {
        return flow.detail().isBlank() ? flow.status().toLowerCase(java.util.Locale.ROOT)
                : flow.detail();
    }

    private void button(GuiGraphics graphics, int left, int top, String key, long id,
            boolean keep) {
        String label = Component.translatable(
                "screen.factorynetwork.terminal.network.flow." + key).getString();
        graphics.fill(left, top - 1, left + BUTTON, top + 9, TerminalScreen.BUTTON);
        graphics.drawString(font, font.plainSubstrByWidth(label, BUTTON - 4),
                left + 2, top, TerminalScreen.TEXT, false);
        buttons.add(new Button(left, top - 1, id, keep));
    }

    /** A button on a STALE line, remembered for the click. */
    private record Button(int left, int top, long id, boolean keep) {

        boolean hit(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + BUTTON
                    && mouseY >= top && mouseY < top + 10;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Button candidate : buttons) {
            if (candidate.hit(mouseX, mouseY)) {
                PacketDistributor.sendToServer(
                        new FlowActionPacket(candidate.id(), candidate.keep()));
                return true;
            }
        }
        return false;
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
