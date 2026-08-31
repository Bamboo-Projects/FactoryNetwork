package dev.devpanda.factorynetwork.web;

/**
 * Wie sichtbar ein Browser gerade ist — und wie viele Bilder er dafür bekommt.
 *
 * <p><b>Kein Browser bekommt, was er will.</b> Chromium malt so schnell es
 * kann; bei einem Dutzend Anzeigen in einer Basis ist das die schnellste Art,
 * eine Bildrate zu ruinieren. Was ein Browser wirklich braucht, hängt nicht an
 * ihm, sondern daran, wer ihn ansieht: Ein Editor unter den Fingern braucht
 * jeden Tastenanschlag sofort, eine Tafel am anderen Ende der Halle nicht.
 *
 * <p><b>Nach oben ist hier nichts zu holen.</b> Gemessen liefert Chromium im
 * fensterlosen Betrieb <b>30,1 Bilder je Sekunde</b> — CEFs Voreinstellung.
 * Die JCEF-Fassung, die MCEF mitbringt, kennt weder {@code CefBrowserSettings}
 * noch ein {@code windowless_frame_rate}; {@code createBrowser} nimmt gar
 * keine Browser-Einstellungen entgegen. Diese Stufen können also nur
 * <b>drosseln</b>. {@link #FOREGROUND} liegt bewusst darüber: Die Stufe soll
 * heißen „so schnell wie es geht", und was das ist, entscheidet CEF.
 *
 * <p>Die übrigen Zahlen sind ein Anfang und keine Messung. Sie stehen hier,
 * damit die Drosselung von Beginn an existiert — nachträglich eingezogen wäre
 * sie ein Umbau jeder Aufrufstelle.
 */
public enum BrowserVisibility {

    /**
     * Vollbild, unter den Fingern des Spielers.
     *
     * <p>Die Sechzig ist keine erreichbare Zahl, sondern eine offene Tür:
     * Chromium liefert gemessen dreißig, und diese Stufe drosselt deshalb
     * nichts. Sollte eine spätere JCEF-Fassung die Bildrate freigeben, steht
     * hier schon, was dann gelten soll.
     */
    FOREGROUND(60),

    /** Offen und sichtbar, aber nicht das, worauf jemand tippt. */
    ACTIVE(30),

    /** Eine Fläche in der Welt, nah genug zum Lesen. */
    NEARBY(15),

    /** Zu weit weg, um Text zu erkennen — es reicht, dass sich etwas regt. */
    DISTANT(5),

    /**
     * Niemand sieht hin.
     *
     * <p>Null und nicht eins: Ein Browser, der niemandem etwas zeigt, soll
     * nichts kosten. Chromium wird zusätzlich als unsichtbar gemeldet — in
     * dieser JCEF-Fassung heißt das {@code setWindowVisibility(false)} —, dann
     * hören auch seine eigenen Zeitgeber auf.
     */
    HIDDEN(0);

    private final int framesPerSecond;

    BrowserVisibility(int framesPerSecond) {
        this.framesPerSecond = framesPerSecond;
    }

    public int framesPerSecond() {
        return framesPerSecond;
    }

    /** Der Abstand zwischen zwei Bildern in Nanosekunden, oder {@code -1} für nie. */
    public long frameIntervalNanos() {
        return framesPerSecond <= 0 ? -1L : 1_000_000_000L / framesPerSecond;
    }

    /** Soll dieser Browser überhaupt noch malen? */
    public boolean drawing() {
        return framesPerSecond > 0;
    }
}
