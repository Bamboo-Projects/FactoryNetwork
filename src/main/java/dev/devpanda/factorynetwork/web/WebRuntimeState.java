package dev.devpanda.factorynetwork.web;

/**
 * Warum es gerade einen Browser gibt — oder keinen.
 *
 * <p><b>Fünf Zustände und kein {@code boolean}.</b> „Web geht nicht" ist keine
 * Auskunft: Wem die Laufzeitumgebung fehlt, der braucht einen anderen Satz als
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
     * Die Laufzeitumgebung liegt nicht neben dem Spiel.
     *
     * <p>Chromium wiegt 379 Megabyte und steckt deshalb nicht im Jar dieser
     * Mod. Wer es nachlädt, hat beim nächsten Start einen Browser.
     *
     * <p><b>Wird heute noch von niemandem gesetzt.</b> Der Zustand wartet auf
     * die Auslieferung der Laufzeitumgebung; bis dahin endet ein fehlender
     * Ordner in {@link #FAILED} mit der Zeile, die den Bauweg nennt.
     */
    RUNTIME_MISSING,

    /**
     * Für diese Plattform gibt es keine Binärdateien.
     *
     * <p>Kein Nachinstallieren hilft. Das muss anders klingen als ein
     * fehlender Download, sonst sucht jemand stundenlang an der falschen
     * Stelle.
     *
     * <p><b>Wird heute noch von niemandem gesetzt</b>, aus demselben Grund wie
     * {@link #RUNTIME_MISSING}. Gebaut ist bisher nur {@code windows-x86_64}.
     */
    UNSUPPORTED,

    /**
     * Chromium ist noch nicht auf der Platte.
     *
     * <p>Vorgesehen für den Nachlademechanismus: Hinter einem Proxy, ohne Netz
     * oder wenn die Ablage steht, bleibt es dabei — und das ist ein Zustand,
     * der beim nächsten Versuch anders ausgehen kann.
     *
     * <p><b>Wird heute noch von niemandem gesetzt</b>, aus demselben Grund wie
     * {@link #RUNTIME_MISSING}.
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
