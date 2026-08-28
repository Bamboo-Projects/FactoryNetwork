package dev.devpanda.factorynetwork.upgrade;

/**
 * Wie weit ein Funksignal trägt.
 *
 * <p>Die Zahlen stehen in {@code docs/fernzugriff.md} §3 und hier — sonst
 * nirgends. Wer sie ändert, ändert sie an beiden Stellen, und
 * {@code RangeTest} rechnet sie nach.
 */
public final class Range {

    /** Ein Mast ohne Karten trägt so weit. */
    public static final int MAST_BASE = 16;

    /**
     * Um so viel zählt eine Karte im Mast mehr als im Gerät.
     *
     * <p><b>Warum am Ort und nicht an der Karte:</b> Es ist dieselbe Karte.
     * Eine, die an zwei Orten verschieden viel hebt, widerspräche der Regel
     * aus dem Ausbausystem — dort hebt eine Karte ihren Wert, und der ist
     * einer. Das Verstärken ist eine Eigenschaft des Masts.
     */
    public static final int MAST_FACTOR = 2;

    /** Was {@link #reach} liefert, wenn es keine Grenze mehr gibt. */
    public static final int UNLIMITED = -1;

    /**
     * Wie weit dieser Mast dieses Gerät erreicht, in Blöcken.
     *
     * @return {@link #UNLIMITED}, wenn der Mast eine Grenzenlos-Karte trägt
     */
    public static int reach(Loadout mast, Loadout device) {
        if (mast.unlimited(Stat.RANGE)) {
            return UNLIMITED;
        }
        return MAST_BASE
                + mast.value(Stat.RANGE) * MAST_FACTOR
                + device.value(Stat.RANGE);
    }

    /**
     * Reicht es über diese Entfernung?
     *
     * <p>Die Grenze zählt noch dazu: Wer genau auf ihr steht, ist drin.
     */
    public static boolean covers(Loadout mast, Loadout device, double distance) {
        return covers(mast, device, true, distance);
    }

    /**
     * Reicht es bis dorthin — auch wenn das in einer anderen Welt liegt?
     *
     * <p><b>Über eine Dimensionsgrenze reicht nur die Grenzenlos-Karte</b>,
     * und dann ohne jede Rechnung. Zwischen zwei Dimensionen gibt es keinen
     * Abstand, den man messen könnte: Der Nether liegt nicht hundert Blöcke
     * von der Oberwelt entfernt, er liegt daneben und zugleich nirgends. Eine
     * Rechnung mit Koordinaten aus zwei Welten ergäbe eine Zahl ohne
     * Bedeutung.
     *
     * <p>Auch vier Reichweitenkarten helfen dort nicht. Reichweite ist eine
     * Strecke, und eine Dimensionsgrenze ist keine — das ist der Grund, die
     * Karte überhaupt zu bauen.
     *
     * @param sameLevel ob Mast und Gerät in derselben Dimension sind
     * @param distance der Abstand; bedeutungslos, wenn sie es nicht sind
     */
    public static boolean covers(Loadout mast, Loadout device, boolean sameLevel,
                                 double distance) {
        int found = reach(mast, device);
        if (!sameLevel) {
            return found == UNLIMITED;
        }
        return found == UNLIMITED || distance <= found;
    }

    private Range() {
    }
}
