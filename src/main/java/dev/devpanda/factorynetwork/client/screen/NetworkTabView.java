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

    /**
     * Wie hoch der Kopf ist: eine Zeile Zahlen und darunter die Kurve.
     *
     * <p>Sie war erst elf Pixel hoch und stand neben den Zahlen — bei sechzig
     * Pixeln Breite sah das nach einem Balken aus, nicht nach einem
     * Diagramm. Jetzt bekommt sie eine eigene Zeile über die ganze Breite:
     * Fünf Minuten Verlauf sind fünf Minuten, und die sieht man nur, wenn
     * sie Platz haben.
     */
    private static final int HEAD_HEIGHT = 40;

    /** Wie hoch die Kurve selbst ist. */
    private static final int SPARK_HEIGHT = 24;

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = y + 3 - scroll * LINE;

        // <b>Der Kopf trägt, was immer gilt.</b> Strom und Durchsatz stehen
        // an derselben Stelle, egal wie das Netz aussieht — wer hinsieht,
        // muss nicht erst suchen.
        int line = head(graphics, top);

        // <b>Links, was arbeitet. Rechts, was da ist.</b> Zwei Fragen, zwei
        // Spalten: „läuft es" und „was hängt dran". Vorher standen acht
        // Abschnitte untereinander, alle gleich gewichtet.
        int gap = 8;
        int columnWidth = (width - 6 - gap) / 2;
        int leftX = x + 3;
        int rightX = leftX + columnWidth + gap;

        int leftEnd = working(graphics, leftX, line, columnWidth);
        int rightEnd = present(graphics, rightX, line, columnWidth);

        // <b>Und unten, was klemmt.</b> Warnungen gehören zusammen und ans
        // Ende: Wer sie oben zwischen die Listen streut, muss den ganzen
        // Reiter lesen, um zu wissen, ob etwas fehlt.
        remember(warnings(graphics, Math.max(leftEnd, rightEnd) + 4));
    }

    /**
     * Der Kopf: Strom, Durchsatz mit Minikurve, Gesamtmenge.
     *
     * <p><b>Die große Kurve ist dafür gefallen.</b> Sie nahm ein Drittel der
     * Fläche und zeigte bei einem ruhigen Netz eine leere Box. Sechzig Pixel
     * reichen, um eine Spitze zu sehen — und wer die Zahlen will, liest sie
     * daneben.
     */
    private int head(GuiGraphics graphics, int line) {
        int right = x + width - 3;

        // Strom links: Wer sieht, dass nichts läuft, fragt zuerst danach.
        var supply = ClientFlowState.supply();
        String power = Component.translatable(
                        "screen.factorynetwork.terminal.network.power_short",
                        supply.stored(), supply.capacity(), supply.draw())
                .getString();
        // Rot, wenn der Vorrat unter ein Zehntel fällt: Das ist der Moment,
        // in dem gleich Geräte ausfallen — und der einzige Zustand am Strom,
        // den man sofort sehen muss.
        int powerColour = supply.capacity() > 0
                && supply.stored() * 10 < supply.capacity()
                        ? TerminalScreen.BAD : TerminalScreen.TEXT;
        graphics.drawString(font, power, x + 3, line + 3, powerColour, false);

        // Die Zahlen rechts in der Kopfzeile: jetzt und insgesamt.
        List<Integer> verlauf = ClientTraffic.perSecond();
        int peak = ClientTraffic.peak();
        int jetzt = verlauf.isEmpty() ? 0 : verlauf.get(verlauf.size() - 1);
        // Nicht nur, was fließt, sondern wovon. Eine Zahl ohne Maßstab
        // beantwortet die Frage nicht, die man im Kopf hat: Ist das viel?
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

        // Und darunter die Kurve über die ganze Breite.
        int sparkLeft = x + 3;
        int sparkTop = line + LINE + 3;
        int sparkBottom = sparkTop + SPARK_HEIGHT;
        graphics.fill(sparkLeft, sparkTop, right, sparkBottom, 0x22000000);

        // Eine feine Linie auf halber Höhe: Ohne sie ist nicht zu sehen, ob
        // eine Säule ein Viertel oder die Hälfte der Spitze erreicht.
        int middle = (sparkTop + sparkBottom) / 2;
        graphics.fill(sparkLeft, middle, right, middle + 1, 0x18FFFFFF);

        // Und die Grenze des Controllers als eigene Linie — aber nur, wenn
        // sie ins Bild passt. Ein Netz, das ein Prozent seiner Grenze nutzt,
        // bekäme sonst eine Linie am oberen Rand, die nichts erklärt.
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

        // Die Spitze am linken Rand der Kurve: der Maßstab, ohne den eine
        // Säule keine Höhe hat.
        String spitze = Bandwidth.perSecond(peak / Bandwidth.TICKS_PER_SECOND);
        graphics.drawString(font, spitze, sparkLeft + 2, sparkTop + 1,
                TerminalScreen.TEXT_FAINT, false);

        // Eine Linie darunter trennt den Kopf vom Inhalt.
        graphics.fill(x + 3, line + HEAD_HEIGHT, x + width - 3, line + HEAD_HEIGHT + 1,
                0x33FFFFFF);
        return line + HEAD_HEIGHT + 5;
    }

    /**
     * Die linke Spalte: was arbeitet.
     *
     * <p>Worker mit ihrem Verbrauch, darunter die Abläufe. Beides beantwortet
     * dieselbe Frage — läuft es, und wenn nicht, woran hängt es.
     */
    private int working(GuiGraphics graphics, int cx, int line, int cw) {
        List<String> workers = ClientNetworkState.workers();
        line = columnHead(graphics, cx, line, cw,
                "screen.factorynetwork.terminal.network.workers", workers.size());

        if (workers.isEmpty()) {
            line = dim(graphics, cx, line, "—");
        } else {
            // Der Verbrauch je Worker steht neben dem Worker: Eine eigene
            // Rangliste hätte dieselben Namen ein zweites Mal genannt.
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
                // Der Name steht vor dem Doppelpunkt; danach kommt der
                // Zustand, den die Farbe schon zeigt.
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

        // Die globalen Werte gehören zum Programm wie die Abläufe — und nur
        // hierhin, wenn es welche gibt.
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
     * Die rechte Spalte: was da ist.
     *
     * <p>Anschlüsse und Anzeigen, dazu Flüssigkeiten und Anlagen — aber nur,
     * wenn es sie gibt. Eine Überschrift mit „keine" darunter kostet zwei
     * Zeilen und sagt nichts.
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
                // Was fehlt oder mehrdeutig ist, sticht heraus — danach sucht
                // man, wenn eine Anlage nichts tut.
                int colour = plant.contains("?") || plant.contains("!")
                        ? TerminalScreen.WARN : TerminalScreen.TEXT;
                graphics.drawString(font, font.plainSubstrByWidth(plant, cw),
                        cx, line, colour, false);
                line += LINE;
            }
        }

        // Speicher und Datenträger: Sie sagen, was das Netz halten kann —
        // dieselbe Frage wie die Listen darüber.
        line = capacity(graphics, cx, line, cw);
        return line;
    }

    /**
     * Eine Überschrift mit Zahl.
     *
     * <p>„WORKER 3" sagt in einem Zeichen, was drei Zeilen Liste sagen
     * würden — und man sieht es, ohne die Liste zu lesen.
     */
    private int columnHead(GuiGraphics graphics, int cx, int line, int cw,
                           String key, int count) {
        graphics.drawString(font, Component.translatable(key), cx, line,
                TerminalScreen.TEXT_DIM, false);
        String zahl = String.valueOf(count);
        graphics.drawString(font, zahl, cx + cw - font.width(zahl), line,
                TerminalScreen.TEXT_DIM, false);
        // Eine feine Linie darunter fasst die Spalte zusammen.
        graphics.fill(cx, line + LINE, cx + cw, line + LINE + 1, 0x22FFFFFF);
        return line + LINE + 3;
    }

    /** Eine Namensliste, einspaltig — die Spalte ist schon eine Spalte. */
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

    /** Eine Zeile in einer Spalte, auf ihre Breite gekürzt. */
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
     * Der Fuß: was klemmt.
     *
     * <p>Gesammelt und nur, wenn es etwas gibt. Vorher stand „Kein
     * Serverschrank" — der Grund, warum gar nichts läuft — in derselben
     * Schrift wie eine Liste von Anlagennamen.
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
