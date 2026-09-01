package dev.devpanda.factorynetwork.web.api;

import dev.devpanda.factorynetwork.web.BrowserManager;
import dev.devpanda.factorynetwork.web.WebRuntimeStatus;
import dev.devpanda.factorynetwork.web.WebSupport;
import dev.devpanda.factorynetwork.web.runtime.BrowserSession;

/**
 * Der Einstieg in die Web-Runtime.
 *
 * <p>Mit dieser Klasse erzeugt Mod-Code Web-Flächen: für einen Bildschirm,
 * für ein Overlay über dem Bild, für eine Fläche in der Welt. Was darunter
 * liegt — Chromium, seine Sitzungen, seine Texturen — bleibt drinnen.
 *
 * <pre>
 * WebSurface fläche = FnWeb.open(
 *         SurfaceSpec.of("https://example.org", 512, 512)
 *                 .named("Schnellmenü")
 *                 .keys(KeyFilter.only(GLFW.GLFW_KEY_ESCAPE)));
 * if (fläche != null) {
 *     // zeichnen über fläche.textureLocation(), schließen mit close()
 * }
 * </pre>
 *
 * <p><b>Drei Zusagen.</b>
 *
 * <ol>
 *   <li><b>Kein Typ aus {@code org.cef} in einer Signatur.</b> Wer diese
 *       Schnittstelle benutzt, übersetzt nicht gegen Chromium — und die
 *       Fassung darunter darf wechseln, ohne fremden Code zu brechen.</li>
 *   <li><b>Nichts wirft.</b> Fehlt die Laufzeitumgebung, kommt {@code null}
 *       zurück und {@link #status()} sagt warum. Ein fehlender Browser kostet
 *       eine Oberfläche und nicht den Client.</li>
 *   <li><b>Alles gehört dem Renderthread.</b> Chromiums Sitzungen leben dort.
 *       Ein Aufruf von woanders ist kein Zufallsfehler.</li>
 * </ol>
 *
 * <p><b>Fassung 1.</b> Was hier öffentlich steht, bleibt es; neue Angaben
 * kommen als weitere Methode an {@link SurfaceSpec} und nicht als weiterer
 * Parameter.
 */
public final class FnWeb {

    private FnWeb() {
    }

    /**
     * Öffnet eine Fläche.
     *
     * <p>Fährt die Laufzeitumgebung hoch, falls sie noch steht. Beim ersten
     * Aufruf dauert das ein paar hundert Millisekunden — deshalb beim ersten
     * Bedarf zu rufen und nicht beim Start des Spiels.
     *
     * @return die Fläche, oder {@code null}, wenn es gerade keine geben kann.
     *         Der Grund steht in {@link #status()}.
     */
    public static WebSurface open(SurfaceSpec spec) {
        if (!available()) {
            return null;
        }
        try {
            BrowserSession session = BrowserSession.open(spec.url(), spec.transparent(),
                    spec.width(), spec.height(), spec.visibility(), spec.name());
            return new SessionSurface(session, spec.keys());
        } catch (Throwable broken) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Die Fläche {} kam nicht zustande", spec.name(), broken);
            return null;
        }
    }

    /**
     * Kann gerade eine Fläche entstehen?
     *
     * <p>Fährt die Laufzeitumgebung dabei hoch, wenn sie noch nicht läuft.
     */
    public static boolean available() {
        return WebSupport.ensureStarted().usable();
    }

    /**
     * Warum es geht oder nicht geht.
     *
     * <p>Fünf Zustände und kein Wahrheitswert: Wem die Laufzeitumgebung fehlt,
     * braucht einen anderen Satz als wer hinter einem Proxy sitzt.
     */
    public static WebRuntimeStatus status() {
        return dev.devpanda.factorynetwork.web.WebRuntime.status();
    }

    /**
     * Wie viele Browser gerade leben — über alle Flächen hinweg.
     *
     * <p>Zum Nachsehen, nicht zum Steuern: Wer eine eigene Obergrenze braucht,
     * zählt seine eigenen Flächen. Diese Zahl enthält auch die von anderen.
     */
    public static int liveCount() {
        return BrowserManager.count();
    }
}
