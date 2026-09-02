package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.web.api.WebSurface;
import dev.devpanda.factorynetwork.web.api.WorldSurfaces;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Das Fadenkreuz als Zeiger auf Flächen in der Welt.
 *
 * <p><b>Zielen mit dem Kopf.</b> Wohin die Kamera schaut, dorthin zeigt der
 * Zeiger; ein Rechtsklick klickt dort. Der Spieler bleibt in der Welt und
 * kann nebenbei laufen — es gibt keinen Modus, aus dem man wieder heraus
 * müsste.
 *
 * <p>Jeden Takt sucht {@link WorldSurfaces#pick} die nächste getroffene
 * Fläche und schickt ihr die Zeigerbewegung; so folgt die Hervorhebung einer
 * Schaltfläche dem Blick. Verlässt der Blick die Fläche, bekommt sie ein
 * „Zeiger weg".
 *
 * <p>Alles im Renderthread — Kamera, Flächen und Chromiums Sitzungen leben
 * dort.
 */
public final class WorldPointer {

    /** So weit reicht der Zeiger, in Blöcken. Etwas über der Baureichweite. */
    private static final double REACH = 5.0;

    private static WebSurface hovered;
    private static int hoverX;
    private static int hoverY;

    private WorldPointer() {
    }

    /** Je Takt: den Blickstrahl auf die Flächen legen. */
    public static void tick() {
        if (WorldSurfaces.count() == 0) {
            clearHover();
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.screen != null) {
            clearHover();
            return;
        }
        Vec3 eye = client.gameRenderer.getMainCamera().getPosition();
        Vector3f look = client.gameRenderer.getMainCamera().getLookVector();

        WorldSurfaces.Pick pick = WorldSurfaces.pick(eye.x, eye.y, eye.z,
                look.x(), look.y(), look.z(), REACH);
        if (pick == null) {
            clearHover();
            return;
        }
        if (pick.surface() != hovered) {
            clearHover();
            hovered = pick.surface();
        }
        hoverX = pick.x();
        hoverY = pick.y();
        hovered.mouseMoved(hoverX, hoverY, 0);
    }

    /**
     * Ein Rechtsklick, während der Blick auf einer Fläche liegt.
     *
     * <p>Als linker Mausklick an die Seite — das ist der Klick, auf den eine
     * Schaltfläche im Web reagiert.
     *
     * @return ob eine Fläche den Klick bekam (dann gehört er nicht dem Spiel)
     */
    public static boolean click() {
        if (hovered == null || !hovered.alive()) {
            return false;
        }
        hovered.mousePressed(hoverX, hoverY, 0, 0);
        hovered.mouseReleased(hoverX, hoverY, 0, 0);
        return true;
    }

    private static void clearHover() {
        if (hovered != null && hovered.alive()) {
            hovered.mouseLeft(hoverX, hoverY);
        }
        hovered = null;
    }
}
