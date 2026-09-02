package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.web.api.WebOverlay;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The automated proof for the overlay: {@code -Dfn.overlay=true}.
 *
 * <p>Opens the quick menu as soon as the world is up, and writes every second
 * what cannot be seen from outside: whether a screen is open (Escape must not
 * open one), which focus the overlay has, and where the player stands (W must
 * move them). The keys themselves come from outside — real ones, through the
 * window, because only those pass through the mixin.
 */
public final class OverlayProof {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/OverlayProof");
    /** {@code true}: keyboard focus. {@code mouse}: mouse focus after five seconds. */
    private static final String MODE = System.getProperty("fn.overlay", "");
    private static final boolean ENABLED = !MODE.isEmpty() && !MODE.equals("false");
    private static final int SETTLE_TICKS = 60;
    private static final int MOUSE_AFTER_TICKS = SETTLE_TICKS + 100;
    private static final int REPORT_EVERY = 20;

    private static int ticks;
    private static boolean opened;
    private static boolean mouseGiven;

    private OverlayProof() {
    }

    public static void tick() {
        if (!ENABLED) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        ticks++;
        if (!opened) {
            if (ticks < SETTLE_TICKS) {
                return;
            }
            opened = true;
            WebOverlay overlay = OverlayDemo.open();
            LOG.info("Overlay-Nachweis: {}", overlay == null ? "kein Overlay zu haben" : "offen, " + overlay);
            return;
        }
        if (MODE.equals("mouse") && !mouseGiven && ticks >= MOUSE_AFTER_TICKS) {
            mouseGiven = true;
            WebOverlay overlay = OverlayDemo.current();
            if (overlay != null) {
                overlay.focus(dev.devpanda.factorynetwork.web.api.OverlayFocus.MOUSE);
                LOG.info("Overlay-Nachweis: Mausfokus gegeben — {}", overlay);
            }
        }
        if (ticks % REPORT_EVERY != 0) {
            return;
        }
        WebOverlay overlay = OverlayDemo.current();
        LOG.info("Overlay-Nachweis: Bildschirm={}, Fokus={}, Spieler={}",
                client.screen == null ? "keiner" : client.screen.getClass().getSimpleName(),
                overlay == null ? "weg" : overlay.focus(),
                String.format(java.util.Locale.ROOT, "%.2f/%.2f",
                        client.player.getX(), client.player.getZ()));
    }
}
