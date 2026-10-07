package dev.devpanda.factorynetwork.client;

import dev.devpanda.bamboocef.web.WebAssets;
import dev.devpanda.bamboocef.web.api.FnWeb;
import dev.devpanda.bamboocef.web.api.Keys;
import dev.devpanda.bamboocef.web.api.SurfaceSpec;
import dev.devpanda.bamboocef.web.api.WebSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.terminal.TerminalTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * FactoryNetwork's development environment — opened through the CEF API.
 *
 * <p><b>FactoryNetwork is a consumer of the interface here like any other
 * mod.</b> The editor no longer reaches into the runtime, but opens a web
 * screen through {@link FnWeb#openScreen}. All it needs from the runtime are
 * the public API and the helper that unpacks a bundle — nothing from
 * {@code web.runtime}, {@code web.screen} or {@code web.view}.
 *
 * <p><b>Translucent over the living world.</b> The earlier editor froze the
 * world image as a half-resolution still — a measuring rig meant to gauge
 * Monaco without the running world. What ships is the simpler, ordinary form:
 * a transparent page over Minecraft's blurred picture.
 */
public final class EditorApp {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/IDE");

    /** The directory of the web interface in the classpath. */
    private static final String MANIFEST = "assets/factorynetwork/web/ide/files.txt";
    /** The folder under the game folder into which it is unpacked. */
    private static final String UNPACKED = "factorynetwork-web";
    private static EditorSession active;

    private EditorApp() {
    }

    /**
     * Unpacks if necessary and opens the editor full screen.
     *
     * @return whether it succeeded
     */
    public static boolean open(Minecraft client) {
        String url = prepare(client, "demo.html");
        if (url == null) {
            return false;
        }
        SurfaceSpec spec = SurfaceSpec.of(url, 1280, 720)
                .named("Factory Network IDE")
                .keys(Keys.EDITOR)
                .transparent(true);
        WebSurface surface = FnWeb.openScreen(spec);
        if (surface == null) {
            LOG.warn("Der Editor ließ sich nicht öffnen: keine Web-Runtime");
            return false;
        }
        return true;
    }

    /** Opens the controller's real project, retaining its server-side terminal session. */
    public static boolean open(Minecraft client, Screen parent, TerminalMenu menu) {
        if (client.player == null || client.player.containerMenu != menu || !menu.allows(TerminalTab.CODE)) {
            return false;
        }
        if (!FnWeb.available()) return false;
        String url = prepare(client, "index.html");
        if (url == null) return false;
        if (active != null) active.close(false);
        WebSurface surface = FnWeb.openScreen(SurfaceSpec.of(url, 1280, 720)
                .named("Factory Network · Manifold").keys(Keys.EDITOR).transparent(true));
        if (surface == null) return false;
        active = new EditorSession(client, parent, menu, surface);
        return true;
    }

    public static void tick() {
        if (active != null && !active.tick()) active = null;
    }

    public static void clear() {
        if (active != null) active.close(false);
        active = null;
    }

    /**
     * Unpacks the bundle and the page and returns the address.
     *
     * <p>The page lies beside the bundle and not inside it — it is our work,
     * not part of what Monaco brings along. Both have to be in the same
     * folder, otherwise Monaco's relative addresses do not resolve.
     *
     * @return the {@code file:} address, or {@code null} if something was missing
     */
    private static String prepare(Minecraft client, String pageName) {
        Path into = client.gameDirectory.toPath().resolve(UNPACKED).resolve("ide");
        ClassLoader loader = EditorApp.class.getClassLoader();
        Path folder = WebAssets.unpack(loader, MANIFEST, into);
        if (folder == null) {
            return null;
        }
        Path page = folder.resolve(pageName);
        try {
            Files.createDirectories(folder);
            // Monaco is version-cached; our application changes independently.
            for (String name : new String[]{"index.html", "demo.html", "app.js", "editor-state.js", "ide.css", "worker-source.js"}) {
                try (InputStream stream = loader.getResourceAsStream("assets/factorynetwork/web/ide/" + name)) {
                    if (stream == null) {
                        LOG.warn("Die Editor-Datei fehlt im Klassenpfad: {}", name);
                        return null;
                    }
                    Files.copy(stream, folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException broken) {
            LOG.warn("Die Seite ließ sich nicht ablegen", broken);
            return null;
        }
        return page.toUri().toString();
    }
}
