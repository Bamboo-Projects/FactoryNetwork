package dev.devpanda.factorynetwork.upgrade;

/**
 * Die Karten und was sie heben.
 *
 * <p><b>Gleiche Karten addieren sich, statt in Stufen aufzurüsten.</b> Ein
 * Stufensystem — Reichweite I, II, III — macht die alte Karte wertlos, sobald
 * die neue da ist. Vier gleiche Karten in vier Plätzen halten den Wert an der
 * Zahl der Plätze fest, und die ist die eigentliche Entscheidung.
 */
public enum Card implements Upgrade {

    /** Acht Blöcke mehr Reichweite, je Stück. */
    RANGE("range_card", Stat.RANGE, 8, false),

    /**
     * Hebt die Reichweitengrenze ganz auf.
     *
     * <p>Eine Karte und kein Modul: Sie schafft nichts Neues, sie hebt eine
     * Grenze auf. Ihr Zahlenwert ist null, weil ihn niemand liest — wer
     * {@code unlimited} fragt, fragt {@code value} nicht mehr.
     */
    INFINITY("infinity_card", Stat.RANGE, 0, true),

    /**
     * Nimmt einer Maschine ein Fünftel ihrer Zeit und legt Strom drauf.
     *
     * <p>Ihr Schritt ist null: Sie addiert nichts, sie multipliziert — und
     * das rechnet {@link Tuning} aus der Stückzahl, nicht aus einer Summe.
     */
    ACCELERATION("acceleration_card", Stat.SPEED, 0, false),

    /** Legt einer Maschine ein Werkstück je Durchlauf zu. */
    BATCH("batch_card", Stat.BATCH, 0, false);

    private final String id;
    private final Stat stat;
    private final int step;
    private final boolean unlimited;

    Card(String id, Stat stat, int step, boolean unlimited) {
        this.id = id;
        this.stat = stat;
        this.step = step;
        this.unlimited = unlimited;
    }

    @Override
    public String id() {
        return id;
    }

    /** Worauf sie wirkt — genau ein Punkt, nie zwei. */
    public Stat stat() {
        return stat;
    }

    /** Um wie viel sie ihn hebt, je Stück. */
    public int step() {
        return step;
    }

    /** Ob sie die Grenze ganz aufhebt. */
    public boolean unlimited() {
        return unlimited;
    }
}
