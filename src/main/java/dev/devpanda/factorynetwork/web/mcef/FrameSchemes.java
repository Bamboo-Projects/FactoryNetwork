package dev.devpanda.factorynetwork.web.mcef;

import com.cinemamod.mcef.MCEF;
import dev.devpanda.factorynetwork.web.capture.FrameStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Meldet Chromium an, wer {@code mc://frame/…} beantwortet.
 *
 * <p><b>Nach der Initialisierung, nicht davor — und das ist belegt.</b> MCEF
 * meldet sein eigenes {@code mod://} unmittelbar nach {@code CefUtil.init()}
 * an, im selben Aufruf, in dem es Chromium hochfährt. Ein Schema-Handler
 * lässt sich also nachträglich anmelden; das ist keine Vermutung, sondern der
 * Weg, den MCEF selbst geht.
 *
 * <p><b>Was das nicht beweist.</b> MCEF benutzt {@code mod://} als
 * <b>Hauptdokument</b>. Ob Chromium ein solches Schema auch als Bild
 * <b>innerhalb</b> einer Seite lädt, die von woanders kommt, ist eine andere
 * Frage — ein Schema ohne Anmeldung in {@code onRegisterCustomSchemes} gilt
 * als nicht-standardisiert, hat keine Herkunft und darf weniger. Genau das
 * prüft der Nachweis, bevor irgendetwas darauf gebaut wird.
 *
 * <p>Anmelden lässt es sich nur einmal je Sitzung; ein zweiter Versuch
 * ersetzte den ersten und wäre stillschweigend ein anderer Ablageort.
 */
public final class FrameSchemes {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/FrameSchemes");

    private static boolean registered;

    private FrameSchemes() {
    }

    /**
     * Meldet den Ablageort an, falls noch nicht geschehen.
     *
     * @return ob Chromium die Anmeldung angenommen hat
     */
    public static synchronized boolean register(FrameStore store) {
        if (registered) {
            return true;
        }
        try {
            boolean accepted = MCEF.getApp().getHandle().registerSchemeHandlerFactory(
                    FrameScheme.SCHEME, FrameScheme.DOMAIN,
                    (browser, frame, url, request) -> new FrameScheme(store));
            registered = accepted;
            LOG.info("Schema {}://{} angemeldet: {}",
                    FrameScheme.SCHEME, FrameScheme.DOMAIN, accepted);
            return accepted;
        } catch (Throwable broken) {
            LOG.warn("Schema {}://{} ließ sich nicht anmelden",
                    FrameScheme.SCHEME, FrameScheme.DOMAIN, broken);
            return false;
        }
    }

    /**
     * Dieselbe Adresse ohne Nummer — für die Probe auf den Zwischenspeicher.
     *
     * <p>Chromium sieht dann jedes Mal dieselbe Adresse. Ob es trotzdem
     * nachlädt, entscheidet allein der Kopf {@code Cache-Control: no-store},
     * den der Ablageort mitschickt.
     */
    public static final String SCHEME_URL_WITHOUT_GENERATION =
            FrameScheme.SCHEME + "://" + FrameScheme.DOMAIN + "/current";

    /** Die Adresse für das neueste Bild, mit Nummer gegen den Zwischenspeicher. */
    public static String urlFor(long generation) {
        return FrameScheme.SCHEME + "://" + FrameScheme.DOMAIN + "/current?v=" + generation;
    }

    public static boolean isRegistered() {
        return registered;
    }
}
