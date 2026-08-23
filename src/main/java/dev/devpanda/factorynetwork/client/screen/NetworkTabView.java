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
        flows(graphics, line);
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
