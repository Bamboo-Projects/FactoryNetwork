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
 * Was das Netz gerade tut.
 *
 * <p>Rein lesend, und aus Daten, die ohnehin schon beim Öffnen übertragen
 * werden. Der Wert liegt darin, dass ein stehender Worker sichtbar wird — bis
 * jetzt erfuhr man davon nur, wenn eine Maschine nichts mehr bekam.
 */
import dev.devpanda.factorynetwork.client.ClientTraffic;
import dev.devpanda.factorynetwork.network.Bandwidth;
import dev.devpanda.factorynetwork.network.packet.TrafficPacket;

public class NetworkTabView {

    private static final int LINE = 10;

    /** Breite der Knöpfe an einer Zeile, die auf eine Wahl wartet. */
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
     * Um wie viele Zeilen der Inhalt nach oben geschoben ist.
     *
     * <p><b>Der Reiter war schon zu lang, bevor das Diagramm dazukam.</b>
     * Anschlüsse, Anzeigen, Worker, Flüssigkeiten, Anlagen, Abläufe, globale
     * Werte, Speicher — bei einem gewachsenen Netz reicht das Fenster für
     * die Hälfte. Vorher hörte die Liste einfach auf; jetzt kann man
     * weiterrollen.
     */
    private int scroll;

    /**
     * Wie weit der Inhalt beim letzten Zeichnen reichte.
     *
     * <p>Gemessen statt gerechnet: Wie hoch dieser Reiter wird, hängt an
     * acht Listen und einem Diagramm — eine Formel dafür wäre eine zweite
     * Wahrheit neben dem Zeichencode und liefe bei der nächsten Zeile
     * auseinander.
     */
    private int contentHeight;

    /**
     * Rollt, wenn es etwas zu rollen gibt.
     *
     * <p>Die Grenze kommt aus der zuletzt gezeichneten Höhe. Beim ersten
     * Bild ist sie null, also rollt nichts — dann steht ohnehin alles im
     * Fenster.
     */
    public boolean mouseScrolled(double delta) {
        int ueberhang = Math.max(0, contentHeight - height + LINE);
        int max = (ueberhang + LINE - 1) / LINE;
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        // Beschnitt, sonst zeichnet der gerollte Inhalt über die
        // Reiterleiste und die Statuszeile darunter.
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            renderContent(graphics, mouseX, mouseY);
        } finally {
            // Auch bei einem Fehler: Ein offener Beschnitt macht den ganzen
            // Bildschirm unsichtbar, nicht nur diesen Reiter.
            graphics.disableScissor();
        }
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int line = y + 3 - scroll * LINE;

        // Der Verkehr zuerst: „Was frisst wie viel" ist die Frage, mit der
        // man diesen Reiter aufmacht. Die Namensliste steht darunter — sie
        // beantwortet „was gibt es", und das weiß man meistens schon.
        line = section(graphics, line, "screen.factorynetwork.terminal.network.traffic");
        line = traffic(graphics, line);
        line += 2;
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

        // Die Anzeigewände: benannt, im Netz, und bis eben nirgends zu sehen.
        // Wer eine Wand beschriftet hatte, fand den Namen in keiner Liste
        // wieder und musste ihn sich merken.
        List<String> displays = ClientNetworkState.displays();
        if (!displays.isEmpty()) {
            line += 3;
            line = section(graphics, line, "screen.factorynetwork.terminal.network.displays");
            for (String display : displays) {
                if (line >= y + height - 40) {
                    break;
                }
                line = text(graphics, line, display, TerminalScreen.TEXT);
            }
        }

        line += 3;
        line = section(graphics, line, "screen.factorynetwork.terminal.network.workers");
        List<String> workers = ClientNetworkState.workers();
        if (workers.isEmpty()) {
            // Die Höhe muss zurück in die Zeile: Sonst zeichnet die nächste
            // Überschrift über diesen Text, und aus zwei Wörtern wird eines,
            // das es nicht gibt.
            line = text(graphics, line, Component.translatable(
                    "screen.factorynetwork.terminal.network.no_workers").getString(), 0x8B8B8B);
        } else {
            for (String worker : workers) {
                if (line > y + height - LINE) {
                    break;
                }
                // Der Zustand steht hinter dem Doppelpunkt und färbt die Zeile.
                int colour = worker.contains("HALTED") ? TerminalScreen.BAD
                        : worker.contains("WAITING") ? TerminalScreen.WARN
                        : worker.contains("RUNNING") ? TerminalScreen.GOOD
                        : TerminalScreen.TEXT_DIM;
                graphics.drawString(font, font.plainSubstrByWidth(worker, width - 6),
                        x + 3, line, colour, false);
                line += LINE;
            }
        }

        List<String> fluids = ClientNetworkState.fluids();
        if (!fluids.isEmpty()) {
            line += 3;
            line = section(graphics, line, "screen.factorynetwork.terminal.network.fluids");
            for (String fluid : fluids) {
                if (line > y + height - LINE) {
                    break;
                }
                graphics.drawString(font, font.plainSubstrByWidth(fluid, width - 6),
                        x + 3, line, TerminalScreen.TEXT_DIM, false);
                line += LINE;
            }
        }

        List<String> plants = ClientNetworkState.plants();
        if (!plants.isEmpty()) {
            line += 3;
            line = section(graphics, line, "screen.factorynetwork.terminal.network.plants");
            for (String plant : plants) {
                if (line > y + height - LINE) {
                    break;
                }
                // Was fehlt oder mehrdeutig ist, sticht heraus — danach sucht
                // man, wenn eine Anlage nichts tut.
                int colour = plant.contains("fehlt") || plant.contains("mehrere")
                        ? TerminalScreen.WARN : TerminalScreen.TEXT_DIM;
                graphics.drawString(font, font.plainSubstrByWidth(plant, width - 6),
                        x + 3, line, colour, false);
                line += LINE;
            }
        }

        line += 3;
        line = section(graphics, line, "screen.factorynetwork.terminal.network.flows");
        // Erst der Strom, dann die Rechenleistung, dann die Abläufe. Wer
        // sieht, dass nichts läuft, fragt zuerst nach dem Strom.
        line = supply(graphics, line);
        // Wer sieht, dass drei anstehen, will als Nächstes wissen, wie viele
        // Plätze es gibt.
        line = text(graphics, line, Component.translatable(
                        dev.devpanda.factorynetwork.client.ClientFlowState.threads() == 0
                                ? "screen.factorynetwork.terminal.network.no_server"
                                : "screen.factorynetwork.terminal.network.threads",
                        dev.devpanda.factorynetwork.client.ClientFlowState.occupied(),
                        dev.devpanda.factorynetwork.client.ClientFlowState.threads(),
                        dev.devpanda.factorynetwork.client.ClientFlowState.queued())
                        .getString(),
                dev.devpanda.factorynetwork.client.ClientFlowState.threads() == 0
                        ? TerminalScreen.BAD
                        : dev.devpanda.factorynetwork.client.ClientFlowState.queued() > 0
                        ? TerminalScreen.WARN : TerminalScreen.TEXT_DIM);
        line = capacity(graphics, line);
        line = globals(graphics, line);
        remember(flows(graphics, line));
    }

    /**
     * Die globalen Werte des Programms.
     *
     * <p>Nur, wenn es welche gibt — ein leerer Abschnitt mit Überschrift
     * sagt nichts und kostet zwei Zeilen, die die Abläufe darunter brauchen.
     */
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
     * Merkt sich, wie weit der Inhalt reichte.
     *
     * <p>Ohne den Versatz gerechnet: Wie viel Platz der Inhalt braucht,
     * ändert sich nicht dadurch, dass man ihn verschiebt.
     */
    private void remember(int line) {
        contentHeight = line - (y + 3 - scroll * LINE);
    }

    /**
     * Speicher und Datenträger.
     *
     * <p>Nur, wenn es überhaupt einen Server gibt — sonst stünde dreimal
     * dieselbe Null da und die Zeile davor sagt es schon.
     *
     * <p>Die Programmgröße steht neben dem Platz, weil das die einzige
     * Grenze ist, die man beim Schreiben überschreitet, ohne es zu merken:
     * Wer eine Funktion ergänzt, zählt keine Anweisungen mit.
     */
    private int capacity(GuiGraphics graphics, int line) {
        var rechen = dev.devpanda.factorynetwork.client.ClientFlowState.compute();
        if (rechen.threads() == 0) {
            return line;
        }
        line = text(graphics, line, Component.translatable(
                "screen.factorynetwork.terminal.network.memory",
                rechen.occupied() + rechen.queued(), rechen.memory()).getString(),
                TerminalScreen.TEXT_DIM);
        return text(graphics, line, Component.translatable(
                "screen.factorynetwork.terminal.network.disk",
                rechen.program(), rechen.disk()).getString(),
                rechen.program() > rechen.disk() ? TerminalScreen.BAD : TerminalScreen.TEXT_DIM);
    }

    /**
     * Der Strom des Netzes.
     *
     * <p>Was eine Anlage an Strom zieht, sieht man ihr nicht an — und ein
     * Netz, das steht, weil der Vorrat leer ist, sieht aus wie eines mit
     * einem Fehler im Programm. Deshalb steht es hier ganz oben.
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
        // Die Abgabe steht nur in der laufenden Zeile: Ein Netz, das
        // hochfährt oder steht, gibt nichts ab, und eine Null dort wäre eine
        // Zahl, die nichts sagt.
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
     * Die Abläufe, die gerade warten.
     *
     * <p>Ein Ablauf, der sich gemeldet hat, bekommt zwei Knöpfe: weiterlaufen
     * lassen oder abbrechen. Das ist die Wahl, die die Sprache verspricht, und
     * sie muss dort stehen, wo der Spieler den Zustand sieht — nicht in einem
     * Befehl, den er erst nachschlagen muss.
     */
    private int flows(GuiGraphics graphics, int line) {
        buttons.clear();
        List<FlowStatePacket.Line> flows = ClientFlowState.flows();
        if (flows.isEmpty()) {
            return text(graphics, line, Component.translatable(
                    "screen.factorynetwork.terminal.network.no_flows").getString(), 0x8B8B8B);
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
            int room = stale ? width - 6 - 2 * BUTTON - 6 : width - 6;
            graphics.drawString(font, font.plainSubstrByWidth(label, room), x + 3, line,
                    colour, false);
            if (stale) {
                int right = x + width - 3;
                button(graphics, right - BUTTON, line, "keep", flow.id(), true);
                button(graphics, right - 2 * BUTTON - 3, line, "abort", flow.id(), false);
            }
            line += LINE;
        }
        return line;
    }

    /** Was in der Zeile steht: der Grund, sonst der Zustand. */
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

    /** Ein Knopf an einer STALE-Zeile, für den Klick gemerkt. */
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

    /** Wie hoch das Diagramm ist. */
    private static final int CHART_HEIGHT = 34;

    /**
     * Zeichnet den Verkehr: eine Kurve und darunter, wer sie verursacht.
     *
     * <p><b>Die Kurve zeigt Sekunden, nicht Ticks.</b> Fünf Minuten Verlauf
     * auf der Breite des Fensters — wer wissen will, was gerade passiert,
     * sieht rechts hin; wer wissen will, was vorhin war, links.
     *
     * <p><b>Der Maßstab wächst mit.</b> Ein fester Maßstab wäre bei einem
     * ruhigen Netz eine flache Linie am Boden und bei einem vollen eine
     * Wand. Die Spitze steht als Zahl darüber, sonst wüsste niemand, wie
     * hoch hoch ist.
     */
    private int traffic(GuiGraphics graphics, int line) {
        List<Integer> verlauf = ClientTraffic.perSecond();
        int peak = ClientTraffic.peak();

        // Die Spitze und die Gesamtmenge als Überschrift: Ohne Zahlen ist
        // eine Kurve eine Verzierung.
        graphics.drawString(font, Component.translatable(
                        "screen.factorynetwork.terminal.network.traffic.scale",
                        Bandwidth.perSecond(peak / Bandwidth.TICKS_PER_SECOND),
                        Bandwidth.total(ClientTraffic.total())),
                x + 3, line, TerminalScreen.TEXT_DIM, false);
        line += LINE + 2;

        int left = x + 3;
        int right = x + width - 3;
        int bottom = line + CHART_HEIGHT;
        graphics.fill(left, line, right, bottom, 0x22000000);

        // Eine Säule je Sekunde, von rechts nach links: Der jüngste Wert
        // steht am Rand, wo das Auge zuerst hinsieht.
        int columns = Math.min(verlauf.size(), right - left);
        for (int i = 0; i < columns; i++) {
            int wert = verlauf.get(verlauf.size() - 1 - i);
            int hoehe = Math.max(wert > 0 ? 1 : 0, wert * CHART_HEIGHT / peak);
            int cx = right - 1 - i;
            graphics.fill(cx, bottom - hoehe, cx + 1, bottom, 0xFF57C97A);
        }
        line = bottom + LINE;

        // Und darunter: wer die Kurve verursacht.
        List<TrafficPacket.Consumer> top = ClientTraffic.top();
        if (top.isEmpty()) {
            return text(graphics, line, Component.translatable(
                    "screen.factorynetwork.terminal.network.traffic.quiet").getString(),
                    0x8B8B8B);
        }
        for (TrafficPacket.Consumer one : top) {
            if (line >= y + height - LINE) {
                break;
            }
            graphics.drawString(font, font.plainSubstrByWidth(one.name(), width - 70),
                    x + 3, line, TerminalScreen.TEXT, false);
            String menge = Bandwidth.total(one.bytes());
            graphics.drawString(font, menge, x + width - 3 - font.width(menge), line,
                    TerminalScreen.TEXT_DIM, false);
            line += LINE;
        }
        return line;
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
