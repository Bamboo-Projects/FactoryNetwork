package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.util.NameDistance;

import java.util.List;
import java.util.Optional;

/**
 * Was in der Welt steht, aus Sicht des Übersetzers.
 *
 * <p><b>Der Übersetzer allein kann einen Namen nicht prüfen.</b>
 * {@code display test} ist grammatisch tadellos, auch wenn keine Tafel so
 * heißt — und dann bleibt die Wand schwarz, ohne dass irgendwo etwas rot
 * wird. Das ist der Fehler, den man am längsten sucht: Er sieht nach einem
 * kaputten Netz aus und ist ein Tippfehler.
 *
 * <p>Deshalb bekommt der Übersetzer wahlweise einen Blick auf das echte Netz.
 * Wahlweise, weil es zwei Aufrufer gibt: Der Client hat die Namen aus dem
 * Netzzustand, der Server hat den Graphen — und ein Test hat nichts, und
 * soll trotzdem übersetzen können.
 *
 * <p>Was dabei herauskommt, sind <b>Warnungen und keine Fehler</b>. Eine
 * Wand, die man erst morgen baut, darf man heute schon ins Programm
 * schreiben.
 */
public interface NetworkView {

    /** Ein Blick, der nichts weiß — dann wird auch nichts geprüft. */
    NetworkView NONE = new NetworkView() {
        @Override
        public boolean knowsNetwork() {
            return false;
        }

        @Override
        public List<String> connectors() {
            return List.of();
        }

        @Override
        public List<String> displays() {
            return List.of();
        }
    };

    /**
     * Ob überhaupt etwas bekannt ist.
     *
     * <p>Nicht dasselbe wie „die Listen sind leer": Ein Netz ohne Connectoren
     * ist ein bekanntes Netz ohne Connectoren, und dann ist jeder Name darin
     * falsch. Ein unbekanntes Netz sagt zu keinem Namen etwas.
     */
    default boolean knowsNetwork() {
        return true;
    }

    /** Die Namen der Connectoren im Netz. */
    List<String> connectors();

    /** Die Namen der Anzeigewände im Netz. */
    List<String> displays();

    /** Der ähnlichste Connectorname, für „meintest du". */
    default Optional<String> closestConnector(String wanted) {
        return closest(wanted, connectors());
    }

    /** Und dasselbe für Anzeigen. */
    default Optional<String> closestDisplay(String wanted) {
        return closest(wanted, displays());
    }

    /**
     * Der ähnlichste Name aus einer Liste.
     *
     * <p>Ein Drittel der Länge Abstand, höchstens drei Zeichen: Weiter weg
     * ist kein Vertipper mehr, sondern ein anderer Name — und ein falscher
     * Vorschlag ist schlimmer als keiner.
     */
    private static Optional<String> closest(String wanted, List<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = NameDistance.between(wanted, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        // Die Schwelle steht in NameDistance und nicht hier: Sie gilt auch
        // für „meintest du" beim Übersetzen, und zwei Schwellen liefen
        // auseinander.
        return NameDistance.isCloseEnough(wanted, bestDistance)
                ? Optional.ofNullable(best) : Optional.empty();
    }
}
