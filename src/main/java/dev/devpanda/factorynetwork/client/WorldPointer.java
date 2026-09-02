package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.web.api.WebSurface;
import dev.devpanda.factorynetwork.web.api.WorldSurfaces;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The crosshair as a pointer at surfaces in the world.
 *
 * <p><b>Aiming with the head.</b> Wherever the camera looks, that is where the
 * pointer points; a right-click clicks there. The player stays in the world
 * and can walk about at the same time — there is no mode you would have to
 * leave again.
 *
 * <p>Every tick {@link WorldSurfaces#pick} finds the nearest hit surface and
 * sends it the pointer movement; this way the highlighting of a button follows
 * the gaze. If the gaze leaves the surface, it gets a "pointer gone".
 *
 * <p>Everything on the render thread — camera, surfaces and Chromium's
 * sessions live there.
 */
public final class WorldPointer {

    /** This far the pointer reaches, in blocks. A little beyond the build reach. */
    private static final double REACH = 5.0;

    private static WebSurface hovered;
    private static int hoverX;
    private static int hoverY;

    private WorldPointer() {
    }

    /** Each tick: cast the gaze ray onto the surfaces. */
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
     * A right-click while the gaze lies on a surface.
     *
     * <p>As a left mouse click to the page — that is the click a button on the
     * web responds to.
     *
     * @return whether a surface got the click (then it does not belong to the game)
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
