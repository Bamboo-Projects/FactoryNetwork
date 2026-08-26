package dev.devpanda.factorynetwork.block;

/**
 * Die Maße eines Kabels — als reine Zahlen.
 *
 * <p><b>Ohne jeden Minecraft-Bezug</b>, damit sich die Geometrie in
 * gewöhnlichen Tests gegen die erzeugten Modelldateien prüfen lässt. Genau
 * diese Zahlen laufen sonst auseinander, ohne dass es jemand merkt: Minecraft
 * hält Modell und Trefferfläche getrennt.
 *
 * <p>Die Werte stammen aus Applied Energistics — dort ist ein ummanteltes
 * Kabel sechs Blockpixel stark und ein dichtes zehn. Dieselben Zahlen stehen
 * im Modellskript {@code tools/assets.py}; {@code CableLayoutTest} wacht
 * darüber, dass beide dasselbe sagen.
 */
public final class CableLayout {

    /** Das gewöhnliche Kabel: sechs Blockpixel, wie AE2s ummanteltes. */
    public static final int THIN = 6;

    /** Das dichte Kabel: zehn Blockpixel, wie AE2s dense. */
    public static final int DENSE = 10;

    /** Wo der Mantel beginnt — er sitzt mittig im Block. */
    public static int offset(int size) {
        return (16 - size) / 2;
    }

    /** Wo der Mantel endet. */
    public static int far(int size) {
        return offset(size) + size;
    }

    /**
     * Die Bahnen der Kanallinien, in Blockpixeln von der Blockkante.
     *
     * <p>Acht Bahnen in der Kabelmitte, je ein Viertel Blockpixel breit und
     * um ein Achtel versetzt — die ersten vier für die Kanäle eins bis vier,
     * die zweiten vier für fünf bis acht. Genau AE2s Aufteilung, nur dass eine
     * Bahn bei uns mehr als einen Kanal zählt.
     */
    public static double[] channelLanes() {
        return new double[] {7.00, 7.25, 7.50, 7.75, 8.00, 8.25, 8.50, 8.75};
    }

    /** Wie breit eine Kanallinie ist, in Blockpixeln. */
    public static final double LANE_WIDTH = 0.25;

    /**
     * Wie tief ein Anschluss aus der Blockfläche in den Block ragt.
     *
     * <p>Drei Blockpixel — genug, um ihn als eigenes Ding zu sehen und zu
     * treffen, und wenig genug, dass er beim dichten Kabel gerade an dessen
     * Mantel stößt: Dort beginnt der Kern schon bei drei.
     */
    public static final int PART_DEPTH = 3;

    /** Wie breit die Platte eines Anschlusses ist. */
    public static final int PART_WIDTH = 12;

    /** Wo die Platte beginnt — sie sitzt mittig auf der Fläche. */
    public static int partOffset() {
        return (16 - PART_WIDTH) / 2;
    }

    /**
     * <b>Den Stiel gibt es nicht mehr.</b>
     *
     * <p>Bis zum 26.08. saß zwischen Platte und Kabelkern ein grauer Kasten.
     * Seit die Fläche mit Anschluss einen gewöhnlichen Arm bekommt, trägt der
     * Arm diese Strecke — in der Farbe des Kabels, und am Kabel entsteht eine
     * sichtbare Kreuzung statt eines Fremdkörpers.
     */

    private CableLayout() {
    }
}
