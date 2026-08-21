package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.analyser.AnalyserData;
import dev.devpanda.factorynetwork.network.packet.AnalyserDataPacket;

/**
 * Das Netz, wie der Analysator es zuletzt gemeldet hat.
 *
 * <p>Nur zum Zeichnen. Was hier steht, war beim letzten Paket wahr — bei
 * einem Werkzeug zur Fehlersuche ist das genau richtig, denn es kommt
 * mehrmals je Sekunde nach.
 */
public final class ClientAnalyserState {

    private static AnalyserData data = AnalyserData.empty();
    private static long lastUpdate;

    private ClientAnalyserState() {
    }

    public static void accept(AnalyserDataPacket packet) {
        data = new AnalyserData(packet.nodes(), packet.links(), packet.summary());
        lastUpdate = System.currentTimeMillis();
    }

    public static AnalyserData data() {
        return data;
    }

    /**
     * Ist das Bild noch frisch?
     *
     * <p>Bleibt der Nachschub aus — weil das Werkzeug weggelegt wurde oder
     * der Controller verschwunden ist —, verschwindet die Anzeige von selbst,
     * statt ein altes Netz zu zeigen.
     */
    public static boolean isFresh() {
        return System.currentTimeMillis() - lastUpdate < 2000;
    }

    public static void clear() {
        data = AnalyserData.empty();
        lastUpdate = 0;
    }
}
