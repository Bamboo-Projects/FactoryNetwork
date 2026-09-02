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
 * The quick menu as a demonstration of the overlay interface.
 *
 * <p>An overlay in the top-left corner that gets Escape, the arrows and Enter
 * and leaves everything else to the player. Exactly the case the key filter
 * was built for — and the first caller of the interface that is not the
 * interface itself.
 */
public final class OverlayDemo {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/Overlay");
    private static final String PAGE = "assets/factorynetwork/web/overlay/menu.html";

    private static WebOverlay overlay;

    private OverlayDemo() {
    }

    /** Opens the menu, or closes it if it is open. */
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
            // The return path: what the page sends via window.fnSend lands
            // here — the menu's choice, which it could otherwise tell no one.
            overlay.surface().onMessage(message -> LOG.info("Overlay meldet: {}", message));
            overlay.focus(OverlayFocus.KEYBOARD);
        }
        return overlay;
    }

    public static WebOverlay current() {
        return overlay != null && overlay.alive() ? overlay : null;
    }
}
