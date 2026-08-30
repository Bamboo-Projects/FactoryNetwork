package dev.devpanda.factorynetwork.web;

import java.util.function.Supplier;

/**
 * Der eine Ort, an dem entschieden wird, worauf die Runtime läuft.
 *
 * <p>Heute gibt es genau einen Unterbau. Er steht trotzdem hier und nicht in
 * {@link WebRuntime}, aus einem Grund, der nichts mit Vorrat zu tun hat:
 * {@code WebRuntime} darf keinen MCEF-Typ nennen, sonst verlangt schon ihr
 * Laden die fremde Mod. Diese Klasse nennt ihn auch nicht — sie reicht ein
 * Lambda weiter, und erst dessen Aufruf lädt die Klasse, die es tut.
 */
public final class WebSupport {

    private WebSupport() {
    }

    /**
     * Fährt die Runtime hoch, falls es geht.
     *
     * <p>Beim ersten Bedarf zu rufen und nicht beim Start des Spiels: MCEF
     * fährt sich nebenher selbst hoch, und wer zu früh fragt, bekommt ein
     * „noch nicht" als „nein".
     */
    public static WebRuntimeStatus ensureStarted() {
        return WebRuntime.start(backend());
    }

    /** Noch einmal versuchen, wenn der letzte Grund einen zweiten Versuch wert war. */
    public static WebRuntimeStatus retry() {
        return WebRuntime.retry(backend());
    }

    private static Supplier<WebBackend> backend() {
        // Ein Lambda und kein Methodenverweis: Beide wären hier richtig, aber
        // der Rumpf macht sichtbar, dass die Klasse erst beim Aufruf geladen
        // wird — und genau darauf beruht, dass MCEF fehlen darf.
        return () -> dev.devpanda.factorynetwork.web.mcef.McefBackend.create();
    }
}
