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

    /**
     * Wie es um einen Knoten steht.
     *
     * <p>Die ersten sechs stehen nach Dringlichkeit. Danach kommt, was nur
     * Auskunft gibt — angehängt und nicht eingeordnet, weil die Reihenfolge
     * über die Leitung geht: Der Zustand wird als laufende Nummer verschickt,
     * und eine Einordnung in der Mitte hieße, dass ein alter Client etwas
     * anderes liest, als ein neuer Server meint.
     */
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
        STARVED,
        /** Ein Laufwerk. Es stellt Platz bereit, statt welchen zu brauchen. */
        DRIVE,
        /** Ein Serverschrank. Ohne ihn rechnet das Netz nicht. */
        RACK,
        /** Eine Kreuzung. Sie leitet weiter und kostet selbst keinen Kanal. */
        ROUTER
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
