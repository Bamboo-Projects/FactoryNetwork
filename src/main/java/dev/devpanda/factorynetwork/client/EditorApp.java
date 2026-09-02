package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.web.WebAssets;
import dev.devpanda.factorynetwork.web.api.FnWeb;
import dev.devpanda.factorynetwork.web.api.Keys;
import dev.devpanda.factorynetwork.web.api.SurfaceSpec;
import dev.devpanda.factorynetwork.web.api.WebSurface;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Die Entwicklungsumgebung von FactoryNetwork — über die CEF-API geöffnet.
 *
 * <p><b>FactoryNetwork ist hier ein Nutzer der Schnittstelle wie jede andere
 * Mod.</b> Der Editor greift nicht mehr in die Runtime, sondern öffnet einen
 * Web-Bildschirm über {@link FnWeb#openScreen}. Was er von der Runtime
 * braucht, sind nur die öffentliche API und der Helfer, der ein Bündel
 * auspackt — nichts aus {@code web.runtime}, {@code web.screen} oder
 * {@code web.view}.
 *
 * <p><b>Durchscheinend über der lebenden Welt.</b> Der frühere Editor fror
 * das Weltbild als halbaufgelöstes Standbild ein — ein Messgerät, das Monaco
 * ohne die laufende Welt vermessen sollte. Ausgeliefert wird die einfachere,
 * gewöhnliche Form: eine durchsichtige Seite über Minecrafts unscharfem Bild.
 */
public final class EditorApp {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/IDE");

    /** Das Verzeichnis der Weboberfläche im Klassenpfad. */
    private static final String MANIFEST = "assets/factorynetwork/web/ide/files.txt";
    /** Und die Seite selbst. */
    private static final String PAGE = "assets/factorynetwork/web/ide/index.html";
    /** Der Ordner unter dem Spielordner, in den ausgepackt wird. */
    private static final String UNPACKED = "factorynetwork-web";

    private EditorApp() {
    }

    /**
     * Packt aus, falls nötig, und öffnet den Editor als Vollbild.
     *
     * @return ob es geklappt hat
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
     * Packt Bündel und Seite aus und gibt die Adresse zurück.
     *
     * <p>Die Seite liegt neben dem Bündel und nicht darin — sie ist unser
     * Werk, nicht Teil dessen, was Monaco mitbringt. Beide müssen in denselben
     * Ordner, sonst lösen sich Monacos relative Adressen nicht auf.
     *
     * @return die {@code file:}-Adresse, oder {@code null}, wenn etwas fehlte
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
