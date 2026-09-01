package dev.devpanda.factorynetwork.web.api;

import com.mojang.logging.LogUtils;
import dev.devpanda.factorynetwork.web.runtime.BrowserSession;
import dev.devpanda.factorynetwork.web.view.ManagedTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.function.IntConsumer;

/**
 * Die Umsetzung einer Fläche auf eine Sitzung.
 *
 * <p><b>Paketprivat, und das ist der Punkt.</b> Nach außen gibt es
 * {@link WebSurface} — eine Schnittstelle ohne einen einzigen Typ aus
 * {@code org.cef}. Was darunter liegt, darf sich ändern, ohne dass fremder
 * Code neu übersetzt werden muss.
 *
 * <p><b>Der Tastenfilter sitzt hier und nicht beim Aufrufer.</b> Eine Fläche,
 * die selbst weiß, welche Tasten sie nimmt, kann eine ehrliche Antwort geben:
 * Wahr heißt genommen, falsch heißt „gehört dem Spiel". Läge die Entscheidung
 * beim Aufrufer, müsste jeder sie neu treffen — und jeder anders.
 */
final class SessionSurface implements WebSurface {

    private static final Logger LOG = LogUtils.getLogger();

    private static int nextId;

    private final BrowserSession session;
    private final KeyFilter keys;
    private final ResourceLocation location;
    private boolean closed;

    SessionSurface(BrowserSession session, KeyFilter keys) {
        this.session = session;
        this.keys = keys;
        this.location = ResourceLocation.fromNamespaceAndPath(
                "factorynetwork", "web_surface/" + nextId++);
        Minecraft.getInstance().getTextureManager()
                .register(location, new ManagedTexture(session::textureId));
    }

    @Override
    public int textureId() {
        return closed ? 0 : session.textureId();
    }

    @Override
    public ResourceLocation textureLocation() {
        return location;
    }

    @Override
    public int width() {
        return session.width();
    }

    @Override
    public int height() {
        return session.height();
    }

    @Override
    public void resize(int width, int height) {
        if (!closed) {
            session.resize(Math.clamp(width, 1, SurfaceSpec.MAX_EDGE),
                    Math.clamp(height, 1, SurfaceSpec.MAX_EDGE));
        }
    }

    @Override
    public String name() {
        return session.name();
    }

    @Override
    public boolean alive() {
        return !closed;
    }

    // ---- Eingaben -----------------------------------------------------------

    @Override
    public void mouseMoved(int x, int y, int modifiers) {
        if (!closed) {
            session.mouseMoved(x, y, modifiers);
        }
    }

    @Override
    public void mouseLeft(int x, int y) {
        if (!closed) {
            session.mouseLeft(x, y);
        }
    }

    @Override
    public void mousePressed(int x, int y, int button, int modifiers) {
        if (!closed) {
            session.mousePressed(x, y, button, modifiers, System.currentTimeMillis());
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button, int modifiers) {
        if (!closed) {
            session.mouseReleased(x, y, button, modifiers);
        }
    }

    @Override
    public void mouseScrolled(int x, int y, double amount, int modifiers) {
        if (!closed) {
            session.mouseScrolled(x, y, amount, modifiers);
        }
    }

    @Override
    public boolean keyPressed(int glfwKey, int scanCode, int modifiers) {
        if (closed || !keys.routes(glfwKey, modifiers)) {
            return false;
        }
        session.keyPressed(glfwKey, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean keyReleased(int glfwKey, int scanCode, int modifiers) {
        if (closed || !keys.routes(glfwKey, modifiers)) {
            return false;
        }
        session.keyReleased(glfwKey, scanCode, modifiers);
        return true;
    }

    /**
     * Ein Zeichen folgt seiner Taste.
     *
     * <p>Ein eigener Filter für Zeichen wäre eine zweite Regel für dieselbe
     * Frage — und eine, die niemand richtig hinbekommt: Zu einem getippten
     * Zeichen gibt es keine Tastennummer mehr, nur noch das Zeichen selbst.
     * Deshalb entscheidet hier, ob die Fläche überhaupt Tasten nimmt.
     */
    @Override
    public boolean charTyped(char typed, int modifiers) {
        if (closed || keys == KeyFilter.NONE) {
            return false;
        }
        session.charTyped(typed, modifiers);
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!closed) {
            session.setFocused(focused);
        }
    }

    @Override
    public void onCursor(IntConsumer sink) {
        session.onCursor(sink);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        // Erst abmelden, dann schließen: Andersherum bliebe eine Textur
        // angemeldet, deren Kennung ins Leere zeigt.
        Minecraft.getInstance().getTextureManager().release(location);
        try {
            session.close();
        } catch (Throwable broken) {
            LOG.warn("Die Fläche {} ließ sich nicht schließen", name(), broken);
        }
    }
}
