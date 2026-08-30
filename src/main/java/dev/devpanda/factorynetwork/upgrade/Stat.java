package dev.devpanda.factorynetwork.upgrade;

/** Die Werte, die Karten heben können. */
public enum Stat {

    /** Wie weit ein Funksignal trägt, in Blöcken. */
    RANGE,

    /**
     * Wie schnell eine Maschine arbeitet.
     *
     * <p>Anders als die Reichweite ist das kein Zuschlag in einer Einheit,
     * sondern ein Faktor: Die Karten zählt {@link Tuning}, ihr Schritt bleibt
     * null. Der Wert steht hier trotzdem, weil ein Steckplatz wissen muss,
     * was er annimmt.
     */
    SPEED,

    /** Wie viele Werkstücke ein Durchlauf zugleich fasst. */
    BATCH
}
