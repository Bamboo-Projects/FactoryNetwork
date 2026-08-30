package dev.devpanda.factorynetwork.upgrade;

/**
 * Was Ausbauten aus einem Rezept machen.
 *
 * <p><b>Ohne Minecraft-Typen</b>, wie alles in diesem Paket: Die Rechnung ist
 * der Teil, an dem sich ein Gleichgewicht entscheidet, und sie gehört in
 * gewöhnliche Prüfläufe und nicht in eine Welt, die erst hochfahren muss.
 *
 * <p><b>Zwei Karten, zwei verschiedene Dinge.</b> Die Beschleunigung nimmt
 * Zeit weg und legt Strom drauf; der Stapel nimmt keine Zeit weg, sondern
 * legt Werkstücke dazu. Wer beides steckt, bekommt beides — die Faktoren
 * multiplizieren sich.
 *
 * <p><b>Warum die Beschleunigung überproportional kostet.</b> Ein Fünftel
 * weniger Zeit für die Hälfte mehr Strom: Nach vier Karten läuft die Maschine
 * zweieinhalbmal so schnell und verbraucht dreimal so viel. Wäre der Handel
 * fair, gäbe es keinen Grund, jemals eine zweite Maschine zu bauen — und
 * genau das soll eine Fabrik ja sein, eine Anlage aus vielen Teilen und nicht
 * ein aufgerüsteter Klotz.
 */
public final class Tuning {

    /**
     * Wie viele Karten je Art noch zählen.
     *
     * <p>Ein Steckplatz hält einen ganzen Stapel, und jedes Stück darin zählt
     * — bei der Reichweite ist das richtig, bei der Zeit wäre es das Ende
     * jedes Gleichgewichts: Vierundsechzig Karten drückten jedes Rezept auf
     * einen Tick, und der Fortschrittsbalken hätte nichts mehr zu zeigen.
     * Acht sind mehr, als in die Steckplätze einer Maschine einzeln passen,
     * und lassen trotzdem Raum, sie zu stapeln.
     */
    public static final int MOST = 8;

    /** Was eine Beschleunigungskarte von der Zeit übrig lässt. */
    private static final double KEEPS = 0.8;

    /** Und was sie zusätzlich auf den Strom legt. */
    private static final double COSTS = 0.5;

    private Tuning() {
    }

    public static Tuned of(Loadout loadout, int ticks, int energy) {
        int fast = Math.min(MOST, loadout.count(Card.ACCELERATION));
        int wide = Math.min(MOST, loadout.count(Card.BATCH));

        double factor = Math.pow(KEEPS, fast);
        // Aufrunden und mindestens ein Tick: Ein Durchlauf in null Ticks wäre
        // kein Durchlauf, sondern ein Sprung.
        int fasterTicks = Math.max(1, (int) Math.round(ticks * factor));

        int batch = 1 + wide;
        // Erst der Aufschlag für das Tempo, dann die Menge. Beides sind
        // Faktoren auf denselben Grundpreis.
        double price = energy * (1.0 + COSTS * fast) * batch;
        return new Tuned(fasterTicks, (int) Math.round(price), batch);
    }
}
