package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.web.WebPage;
import dev.devpanda.factorynetwork.web.api.FnWeb;
import dev.devpanda.factorynetwork.web.api.Keys;
import dev.devpanda.factorynetwork.web.api.OverlayFocus;
import dev.devpanda.factorynetwork.web.api.SurfaceSpec;
import dev.devpanda.factorynetwork.web.api.WebOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Das Schnellmenü als Vorführung der Overlay-Schnittstelle.
 *
 * <p>Ein Overlay in der linken oberen Ecke, das Escape, die Pfeile und Enter
 * bekommt und dem Spieler alles andere lässt. Genau der Fall, für den der
 * Tastenfilter gebaut ist — und der erste Aufrufer der Schnittstelle, der
 * nicht die Schnittstelle selbst ist.
 */
public final class OverlayDemo {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/Overlay");
    private static final String PAGE = "assets/factorynetwork/web/overlay/menu.html";

    private static WebOverlay overlay;

    private OverlayDemo() {
    }

    /** Öffnet das Menü, oder schließt es, wenn es offen ist. */
    public static WebOverlay toggle() {
        if (overlay != null && overlay.alive()) {
            overlay.close();
            overlay = null;
            return null;
        }
        return open();
    }

    public static WebOverlay open() {
        String url = WebPage.unpack(PAGE, "overlay-menu.html");
        if (url == null) {
            return null;
        }
        SurfaceSpec spec = SurfaceSpec.of(url, 240, 190)
                .named("Schnellmenü")
                .keys(Keys.CLOSE.or(Keys.ARROWS).or(Keys.CONFIRM))
                .transparent(true);
        overlay = FnWeb.openOverlay(spec, 12, 12);
        if (overlay != null) {
            // Der Rückweg: Was die Seite über window.fnSend schickt, landet
            // hier — die Wahl des Menüs, die es sonst niemandem sagen könnte.
            overlay.surface().onMessage(message -> LOG.info("Overlay meldet: {}", message));
            overlay.focus(OverlayFocus.KEYBOARD);
        }
        return overlay;
    }

    public static WebOverlay current() {
        return overlay != null && overlay.alive() ? overlay : null;
    }
}
