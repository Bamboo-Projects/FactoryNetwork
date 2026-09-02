package dev.devpanda.factorynetwork.client;

import dev.devpanda.bamboocef.web.WebAssets;
import dev.devpanda.bamboocef.web.api.FnWeb;
import dev.devpanda.bamboocef.web.api.Keys;
import dev.devpanda.bamboocef.web.api.SurfaceSpec;
import dev.devpanda.bamboocef.web.api.WebSurface;
import net.minecraft.client.Minecraft;
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
    /** And the page itself. */
    private static final String PAGE = "assets/factorynetwork/web/ide/index.html";
    /** The folder under the game folder into which it is unpacked. */
    private static final String UNPACKED = "factorynetwork-web";

    private EditorApp() {
    }

    /**
     * Unpacks if necessary and opens the editor full screen.
     *
     * @return whether it succeeded
     */
    public static boolean open(Minecraft client) {
        String url = prepare(client);
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

    /**
     * Unpacks the bundle and the page and returns the address.
     *
     * <p>The page lies beside the bundle and not inside it — it is our work,
     * not part of what Monaco brings along. Both have to be in the same
     * folder, otherwise Monaco's relative addresses do not resolve.
     *
     * @return the {@code file:} address, or {@code null} if something was missing
     */
    private static String prepare(Minecraft client) {
        Path into = client.gameDirectory.toPath().resolve(UNPACKED).resolve("ide");
        ClassLoader loader = EditorApp.class.getClassLoader();
        Path folder = WebAssets.unpack(loader, MANIFEST, into);
        if (folder == null) {
            return null;
        }
        Path page = folder.resolve("index.html");
        try {
            Files.createDirectories(folder);
            try (InputStream stream = loader.getResourceAsStream(PAGE)) {
                if (stream == null) {
                    LOG.warn("Die Seite fehlt im Klassenpfad: {}", PAGE);
                    return null;
                }
                Files.copy(stream, page, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException broken) {
            LOG.warn("Die Seite ließ sich nicht ablegen", broken);
            return null;
        }
        return page.toUri().toString();
    }
}
