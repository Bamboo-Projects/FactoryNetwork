package dev.devpanda.factorynetwork.network;

/**
 * Was ein Block am Netz an Strom kostet, in FE je Tick.
 *
 * <p>Speicher hängt am Laufwerk, Rechenleistung am Schrank — Strom ist das
 * dritte Bein, und das einzige, das <b>laufend</b> etwas kostet statt nur
 * einmal beim Bauen. Ohne ihn ist ein fertiges Netz gratis.
 *
 * <p>Gezahlt wird für Bereitschaft, nicht für Arbeit. Ein Worker, der etwas
 * bewegt, kostet nicht mehr als einer, der wartet. Das ist absichtlich
 * grob: Verbrauch, der mit der Last schwankt, ist im Spiel nicht
 * nachzuvollziehen, und wer seine Anlage plant, will eine Zahl, die
 * stillsteht.
 *
 * <p>Die Zahlen sind gesetzt, nicht hergeleitet. Sie stehen alle hier und
 * lassen sich an einer Stelle ändern, wenn sich das Spiel anders anfühlt als
 * gedacht.
 */
public final class Power {

    /** Der Controller selbst. */
    public static final int CONTROLLER = 4;

    /** Ein Connector, und damit die Maschine dahinter. */
    public static final int CONNECTOR = 1;

    /**
     * Ein Anbau am Controller.
     *
     * <p>Er schafft Kanäle, statt welche zu verbrauchen — einen Kanal kostet
     * er deshalb nicht. Strom schon: Er ist Gerät am Netz wie Laufwerk und
     * Router, und ein Ausbau, der nichts kostet, ist keine Entscheidung.
     */
    public static final int EXTENSION = 1;

    /** Eine Anzeige. */
    public static final int DISPLAY = 1;

    /** Ein Router. Er schaltet aktiv und hat eine BlockEntity — Kabel nicht. */
    public static final int ROUTER = 1;

    /** Ein Laufwerk, leer. */
    public static final int DRIVE = 1;

    /** Und je eingesetzter Zelle noch einmal so viel. */
    public static final int PER_CELL = 1;

    /** Ein Serverschrank, leer. Zwei Blöcke hoch, aber ein Gerät. */
    public static final int RACK = 1;

    /**
     * Und je <b>laufendem</b> Einschub noch einmal doppelt so viel.
     *
     * <p>Nach Einschüben und nicht nach Bauteilen: Ein halb bestückter
     * Einschub rechnet nicht, also zahlt er auch nicht. Sonst kostete ein
     * vergessenes Rechenwerk in einem leeren Einschub dauerhaft Strom, ohne
     * je etwas zu tun.
     *
     * <p><b>Die Stufe spielt keine Rolle.</b> Ein großer Datenträger kostet
     * mehr in der Herstellung, nicht im Betrieb — der Preis für Ausbau steht
     * in der Rezeptkette, und ein zweiter Preis obendrauf würde nur die
     * Rechnung verkomplizieren, ohne eine Entscheidung zu ändern.
     */
    public static final int PER_SERVER = 2;

    /**
     * Kabel kosten nichts.
     *
     * <p>Eine Anlage hat Hunderte davon; würde jedes auch nur ein FE ziehen,
     * bestimmte die Länge der Leitung den Verbrauch und nicht das, was daran
     * hängt.
     */
    public static final int CABLE = 0;

    /**
     * Was der Controller puffern kann.
     *
     * <p>Groß genug, dass eine kurze Lücke im Netz nichts ausmacht, klein
     * genug, dass eine dauerhaft zu schwache Versorgung sich schnell zeigt.
     */
    public static final int CAPACITY = 20_000;

    /** Wie schnell er annimmt. */
    public static final int MAX_INPUT = 2_000;

    /**
     * Wie lange das Netz zum Hochfahren braucht, in Ticks.
     *
     * <p>Drei Sekunden, in denen es schon zieht und noch nichts tut. Ohne
     * diese Zeit wäre ein Stromausfall ein Flackern, das niemand bemerkt.
     */
    public static final int BOOT_TICKS = 60;

    private Power() {
    }

    /**
     * Ab wie viel gespeichertem Strom das Netz wieder hochfährt.
     *
     * <p><b>Der Vorrat muss für das Hochfahren reichen und noch etwas
     * übrig lassen</b>, sonst geht das Netz nach dem Hochfahren sofort wieder
     * aus: Eine Versorgung, die knapp unter dem Bedarf liegt, erzeugte sonst
     * ein Blinken im Halbminutentakt, das wie ein Fehler aussieht statt wie
     * zu wenig Strom.
     */
    public static int restartThreshold(int draw) {
        return Math.max(BOOT_TICKS * draw * 2, CAPACITY / 10);
    }
}
