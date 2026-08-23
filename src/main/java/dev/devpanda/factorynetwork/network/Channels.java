package dev.devpanda.factorynetwork.network;

/**
 * Was ein Block am Netz an Kanal kostet.
 *
 * <p>Die Regel in einem Satz: <b>Was am Netz etwas tut, kostet einen
 * Kanal.</b> Was nur weiterleitet oder nur mitliest, kostet keinen.
 *
 * <p><b>Es gibt keine halben Kanäle.</b> Eine Anzeige kostete einmal ein
 * Viertel, damit vier an einer Wand sich einen teilen — gerechnet wurde
 * deshalb überall in Vierteln, und im Spiel stand dann „12¼ von 16". Ein
 * Viertelkanal ist nichts, was jemand nachvollzieht: Ein Kanal ist eine
 * Leitung, und eine halbe Leitung gibt es nicht. Die Anzeige kostet jetzt
 * gar keinen, und alle Zahlen sind wieder ganze.
 */
public final class Channels {

    /** Ein Connector, und damit die Maschine dahinter. */
    public static final int CONNECTOR = 1;

    /** Ein Laufwerk. Es stellt Platz bereit und ist damit ein Gerät wie jedes. */
    public static final int DRIVE = 1;

    /** Ein Serverschrank. */
    public static final int RACK = 1;

    /**
     * Eine Anzeige: nichts.
     *
     * <p>Sie liest mit und schiebt nichts — dieselbe Begründung wie beim
     * Router, der nur weiterleitet. Wer sich eine Wand aus zwölf Tafeln in
     * die Halle baut, soll dafür nicht ein Netz opfern; sie kostet Strom,
     * und das ist der Preis.
     */
    public static final int DISPLAY = 0;

    /** Ein Router. Er leitet weiter, wie ein Kabel — und Kabel kosten nichts. */
    public static final int ROUTER = 0;

    private Channels() {
    }
}
