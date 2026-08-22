package dev.devpanda.factorynetwork.network;

/**
 * Was ein Block am Netz an Kanal kostet.
 *
 * <p><b>Gerechnet wird in Vierteln.</b> Eine Anzeige soll ein Viertel kosten —
 * vier davon teilen sich einen Kanal —, und mit ganzen Zahlen ginge das nur
 * über Bruchrechnung an jeder Stelle. Ein Viertel als kleinste Einheit macht
 * daraus wieder ganze Zahlen, und die Anzeige rechnet am Ende einmal zurück.
 *
 * <p>Die Regel dahinter in einem Satz: <b>Was am Netz etwas tut, kostet einen
 * Kanal.</b> Eine Anzeige tut weniger — sie liest nur mit —, und ein Router
 * ist Kabel und kein Gerät.
 */
public final class Channels {

    /** Ein ganzer Kanal, in Vierteln. */
    public static final int WHOLE = 4;

    /** Ein Connector, und damit die Maschine dahinter. */
    public static final int CONNECTOR = WHOLE;

    /** Ein Laufwerk. Es stellt Platz bereit und ist damit ein Gerät wie jedes. */
    public static final int DRIVE = WHOLE;

    /** Ein Serverschrank. */
    public static final int RACK = WHOLE;

    /**
     * Eine Anzeige.
     *
     * <p>Ein Viertel: Sie liest nur mit und schiebt nichts. Vier an einer Wand
     * kosten zusammen einen Kanal — eine Leitstandwand soll kein halbes Netz
     * auffressen.
     */
    public static final int DISPLAY = 1;

    /** Ein Router. Er leitet weiter, wie ein Kabel — und Kabel kosten nichts. */
    public static final int ROUTER = 0;

    private static final String[] FRACTIONS = {"", "¼", "½", "¾"};

    private Channels() {
    }

    /** Rechnet ganze Kanäle in Viertel um. */
    public static int quarters(int whole) {
        return whole * WHOLE;
    }

    /**
     * Schreibt Viertel als Kanalzahl: {@code 49} wird zu {@code 12¼}.
     *
     * <p>Mit Bruchzeichen statt Komma, weil die Zahl klein ist und ein
     * „12,25" in einer Zeile mit „von 16" nach Messwert aussieht statt nach
     * Anzahl.
     */
    public static String format(int quarters) {
        int whole = quarters / WHOLE;
        int rest = quarters % WHOLE;
        if (rest == 0) {
            return String.valueOf(whole);
        }
        return (whole == 0 ? "" : String.valueOf(whole)) + FRACTIONS[rest];
    }
}
