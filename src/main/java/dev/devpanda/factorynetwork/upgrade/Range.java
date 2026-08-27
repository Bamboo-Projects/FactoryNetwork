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
        int found = reach(mast, device);
        return found == UNLIMITED || distance <= found;
    }

    private Range() {
    }
}
