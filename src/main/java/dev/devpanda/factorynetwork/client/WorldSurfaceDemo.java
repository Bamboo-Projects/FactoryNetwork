package dev.devpanda.factorynetwork.client;

import dev.devpanda.bamboocef.web.BrowserVisibility;
import dev.devpanda.bamboocef.web.WebPage;
import dev.devpanda.bamboocef.web.api.FnWeb;
import dev.devpanda.bamboocef.web.api.SurfaceSpec;
import dev.devpanda.bamboocef.web.api.WorldSurface;
import dev.devpanda.bamboocef.web.api.WorldSurfaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A surface in the world, three blocks in front of the player — demonstration
 * and automated proof of the world surfaces.
 *
 * <p>{@code /fnweb welt} places it, {@code -Dfn.world=true} does so by itself
 * as soon as the world is up, and writes every second whether it is alive. The
 * page is the clickable quick menu: whoever looks at it and right-clicks
 * chooses an entry, and the choice appears as "Weltfläche meldet:" in the log —
 * the proof that pointing, clicking and the return path work together.
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

    /** Places a surface in front of the player, or removes the last one. */
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
        // Three blocks in front of the eyes, and the surface looks back.
        // The direction comes from the yaw angle, not from the look vector:
        // whoever is looking straight up would otherwise have no horizontal
        // component, and the surface would land on their face.
        Vec3 eye = player.getEyePosition();
        float yaw = player.getYRot();
        double forwardX = -Math.sin(Math.toRadians(yaw));
        double forwardZ = Math.cos(Math.toRadians(yaw));
        Vec3 at = eye.add(forwardX * 3.0, 0.0, forwardZ * 3.0);
        float surfaceYaw = yaw + 180f;
        SurfaceSpec spec = SurfaceSpec.of(url, 512, 384)
                .named("Welt-Nachweis")
                .visibility(BrowserVisibility.NEARBY);
        surface = FnWeb.openInWorld(spec, at.x, at.y, at.z, surfaceYaw, 2.0f, 1.5f);
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
