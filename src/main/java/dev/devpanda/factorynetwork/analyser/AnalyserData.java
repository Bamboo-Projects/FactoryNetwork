package dev.devpanda.factorynetwork.analyser;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Das Netz, wie der Analysator es zeigt.
 *
 * <p>Knoten und Verbindungen mit je einer Bewertung. Gerechnet wird auf dem
 * Server, gezeichnet auf dem Client — dieselbe Aufteilung wie bei den
 * Anzeigen, und aus demselben Grund: Der Graph ist Serverzustand, und ihn zu
 * spiegeln hieße, ihn zweimal zu pflegen.
 */
public record AnalyserData(List<Node> nodes, List<Link> links, Summary summary) {

    /** Wie es um einen Knoten steht. Die Reihenfolge ist die Dringlichkeit. */
    public enum NodeState {
        /** Der Controller selbst. */
        CONTROLLER,
        /** Ein Gerät mit Kanal — alles in Ordnung. */
        DEVICE,
        /** Eine Anzeige. Sie braucht keinen Kanal. */
        DISPLAY,
        /** Hängt am Netz, hat aber keinen Namen. */
        UNNAMED,
        /** Der Name ist mehrfach vergeben — alle davon sind unbrauchbar. */
        DUPLICATE,
        /** Am Netz, aber ohne freien Kanal auf dem Weg zum Controller. */
        STARVED
    }

    /** Wie es um eine Kabelstrecke steht. */
    public enum LinkState {
        /** Noch Luft. */
        FREE,
        /** Drei Viertel oder mehr belegt. */
        TIGHT,
        /** Kein Kanal mehr frei — hier klemmt es. */
        FULL
    }

    public record Node(BlockPos pos, NodeState state, String label) {}

    /** {@code load} und {@code capacity} zeigen die Kanäle dieser Strecke. */
    public record Link(BlockPos from, BlockPos to, LinkState state, int load, int capacity) {}

    /**
     * Was die Zusammenfassung sagt, wenn man das Werkzeug in die Luft hält.
     *
     * <p>Absichtlich Zahlen und keine Sätze: Der Client baut daraus seine
     * Zeilen und übersetzt sie, statt fertigen Text zu bekommen.
     */
    public record Summary(int devices, int cables, int starved, int unnamed,
                          int duplicates, int tightLinks, int fullLinks) {

        public boolean isHealthy() {
            return starved == 0 && duplicates == 0 && fullLinks == 0;
        }
    }

    public static AnalyserData empty() {
        return new AnalyserData(List.of(), List.of(),
                new Summary(0, 0, 0, 0, 0, 0, 0));
    }
}
