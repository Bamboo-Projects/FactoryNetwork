package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.web.BrowserVisibility;
import dev.devpanda.factorynetwork.web.WebPage;
import dev.devpanda.factorynetwork.web.api.FnWeb;
import dev.devpanda.factorynetwork.web.api.SurfaceSpec;
import dev.devpanda.factorynetwork.web.api.WorldSurface;
import dev.devpanda.factorynetwork.web.api.WorldSurfaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Eine Fläche in der Welt, drei Blöcke vor dem Spieler — Vorführung und
 * maschineller Nachweis der Weltflächen.
 *
 * <p>{@code /fnweb welt} stellt sie hin, {@code -Dfn.world=true} tut das von
 * selbst, sobald die Welt steht, und schreibt jede Sekunde, ob sie lebt. Die
 * Seite ist das anklickbare Schnellmenü: Wer es anschaut und rechtsklickt,
 * wählt einen Eintrag, und die Wahl steht als „Weltfläche meldet:" im
 * Protokoll — der Beweis, dass Zeigen, Klicken und der Rückweg zusammenspielen.
 */
public final class WorldSurfaceDemo {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/WorldProof");
    private static final String PAGE = "assets/factorynetwork/web/overlay/menu.html";
    private static final boolean PROOF = Boolean.getBoolean("fn.world");
    private static final int SETTLE_TICKS = 60;
    private static final int REPORT_EVERY = 20;

    private static WorldSurface surface;
    private static int ticks;
    private static boolean placed;

    private WorldSurfaceDemo() {
    }

    /** Stellt eine Fläche vor den Spieler, oder nimmt die letzte weg. */
    public static WorldSurface toggle() {
        if (surface != null && surface.alive()) {
            surface.close();
            surface = null;
            return null;
        }
        return place();
    }

    public static WorldSurface place() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        String url = WebPage.unpack(PAGE, "welt-menu.html");
        if (url == null) {
            return null;
        }
        // Drei Blöcke vor den Augen, und die Fläche schaut zurück.
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 at = eye.add(look.x * 3.0, 0.0, look.z * 3.0);
        float yaw = player.getYRot() + 180f;
        SurfaceSpec spec = SurfaceSpec.of(url, 512, 384)
                .named("Welt-Nachweis")
                .visibility(BrowserVisibility.NEARBY);
        surface = FnWeb.openInWorld(spec, at.x, at.y, at.z, yaw, 2.0f, 1.5f);
        if (surface != null) {
            surface.surface().onMessage(message -> LOG.info("Weltfläche meldet: {}", message));
        }
        return surface;
    }

    public static void tick() {
        if (!PROOF) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        ticks++;
        if (!placed) {
            if (ticks < SETTLE_TICKS) {
                return;
            }
            placed = true;
            WorldSurface placedSurface = place();
            LOG.info("Welt-Nachweis: {}", placedSurface == null ? "keine Fläche zu haben"
                    : "offen, " + placedSurface + ", Spieler bei "
                            + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f yaw %.0f",
                            client.player.getX(), client.player.getY(), client.player.getZ(),
                            client.player.getYRot()));
            return;
        }
        if (ticks % REPORT_EVERY == 0) {
            LOG.info("Welt-Nachweis: lebt={}, Weltflächen offen: {}",
                    surface != null && surface.alive(), WorldSurfaces.count());
        }
    }
}
