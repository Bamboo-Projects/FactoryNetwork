package dev.devpanda.factorynetwork.web.mcef;

import dev.devpanda.factorynetwork.web.WebBackend;
import org.cef.CefApp;
import org.cef.CefClient;

/**
 * Woher Chromium kommt — in dieser Fassung: aus unserer eigenen
 * Laufzeitumgebung.
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
 */
public final class CefHost {

    /** Der Client, an dem Browser und Handler hängen. */
    public static CefClient client() {
        return FnCefRuntime.client();
    }

    /** Die Anwendung, an der Schemata angemeldet werden. */
    public static CefApp app() {
        return FnCefRuntime.app();
    }

    /** Der Unterbau, so wie ihn {@code WebSupport} anfordert. */
    public static WebBackend backend() {
        return FnRuntimeBackend.create();
    }

    private CefHost() {
    }
}
