package dev.devpanda.factorynetwork.web.mcef;

import com.cinemamod.mcef.MCEF;
import dev.devpanda.factorynetwork.web.WebBackend;
import org.cef.CefApp;
import org.cef.CefClient;

/**
 * Woher Chromium kommt — in dieser Fassung: von MCEF.
 *
 * <p><b>Diese Klasse gibt es zweimal</b>, unter demselben Namen, in zwei
 * Quellverzeichnissen. Welche gebaut wird, entscheidet ein Schalter im
 * Buildskript:
 *
 * <pre>
 *   src/mcef/java     ohne Schalter — MCEF liefert Chromium wie bisher
 *   src/runtime/java  mit -Pfnruntime — unsere eigene Laufzeitumgebung
 * </pre>
 *
 * <p><b>Warum getrennte Quellverzeichnisse und nicht eine Verzweigung.</b>
 * MCEF und upstream java-cef bringen beide das vollständige Paket
 * {@code org.cef} mit — 170 Klassen, gleiche Namen, anderer Inhalt. Sie
 * können nicht nebeneinander im Klassenpfad liegen. Damit ist jede Lösung
 * innerhalb einer Übersetzungseinheit ausgeschlossen; die Trennung muss vor
 * den Compiler.
 *
 * <p>Alles, was von dieser Aufteilung nichts wissen muss, steht weiterhin in
 * {@code src/main/java} und fragt hier nach. Das sind vier Stellen: der
 * Browser, die Schema-Anmeldung, die Konsole und die Wahl des Unterbaus.
 */
public final class CefHost {

    /** Der Client, an dem Browser und Handler hängen. */
    public static CefClient client() {
        return MCEF.getClient().getHandle();
    }

    /** Die Anwendung, an der Schemata angemeldet werden. */
    public static CefApp app() {
        return MCEF.getApp().getHandle();
    }

    /** Der Unterbau, so wie ihn {@code WebSupport} anfordert. */
    public static WebBackend backend() {
        return McefBackend.create();
    }

    private CefHost() {
    }
}
