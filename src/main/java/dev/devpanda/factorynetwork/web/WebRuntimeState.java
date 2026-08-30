package dev.devpanda.factorynetwork.web;

/**
 * Warum es gerade einen Browser gibt — oder keinen.
 *
 * <p><b>Fünf Zustände und kein {@code boolean}.</b> „Web geht nicht" ist keine
 * Auskunft: Wer MCEF nicht installiert hat, braucht einen anderen Satz als
 * wer hinter einem Firmen-Proxy sitzt, und beide einen anderen als jemand auf
 * einer Plattform, für die es keine Binärdateien gibt. Ein zusammengefasstes
 * {@code false} macht aus drei Fragen eine unbeantwortbare.
 *
 * <p>Keiner dieser Zustände ist ein Fehler der Mod. Das Spiel läuft in jedem
 * von ihnen weiter — was fehlt, ist eine Oberfläche, nicht das Netz, nicht
 * die Maschinen, nicht die Sprache.
 */
public enum WebRuntimeState {

    /** Noch nicht versucht. Der erste Zustand jeder Sitzung. */
    NOT_STARTED,

    /**
     * MCEF liegt nicht in diesem Pack.
     *
     * <p>Der häufigste Fall und der harmloseste: Die Mod ist freiwillig, wie
     * Jade und JEI. Wer sie nachinstalliert, hat beim nächsten Start einen
     * Browser.
     */
    MOD_MISSING,

    /**
     * Für diese Plattform gibt es keine Binärdateien.
     *
     * <p>Kein Nachinstallieren hilft. Das muss anders klingen als ein
     * fehlender Download, sonst sucht jemand stundenlang an der falschen
     * Stelle.
     */
    UNSUPPORTED,

    /**
     * Chromium ist noch nicht auf der Platte.
     *
     * <p>MCEF lädt es beim ersten Start von einem eigenen Spiegel. Hinter
     * einem Proxy, ohne Netz oder wenn der Spiegel steht, bleibt es dabei —
     * und das ist ein Zustand, der beim nächsten Versuch anders ausgehen kann.
     */
    NOT_DOWNLOADED,

    /**
     * Der Start ist gescheitert.
     *
     * <p>Alles, was übrig bleibt: fehlende Systembibliotheken, ein
     * abgebrochener Download, eine kaputte Installation. Die Begründung steht
     * im {@link WebRuntimeStatus}.
     */
    FAILED,

    /** Es läuft. */
    READY;

    /** Kann in diesem Zustand ein Browser entstehen? */
    public boolean usable() {
        return this == READY;
    }

    /**
     * Lohnt ein zweiter Versuch?
     *
     * <p>Ein fehlender Download kann beim nächsten Mal da sein, eine fehlende
     * Plattform nie. Wer das nicht trennt, versucht es entweder ewig oder
     * einmal zu selten.
     */
    public boolean worthRetrying() {
        return this == NOT_DOWNLOADED || this == FAILED;
    }
}
