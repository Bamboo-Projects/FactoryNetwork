package dev.devpanda.factorynetwork.client.panel;

import com.mojang.logging.LogUtils;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.web.BrowserVisibility;
import dev.devpanda.factorynetwork.web.WebRuntime;
import dev.devpanda.factorynetwork.web.WebSupport;
import dev.devpanda.factorynetwork.web.runtime.BrowserSession;
import dev.devpanda.factorynetwork.client.render.SessionTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Web-Flächen in der Welt: wer lebt, wer wartet, wer zumacht.
 *
 * <p><b>Drei gleichzeitig, und das ist gemessen.</b> Von den fünf
 * Hilfsprozessen eines Browsers hängen drei an Chromium und nicht an der
 * Seite; eine zweite Fläche kostet also weit weniger als die erste. Trotzdem
 * gibt es eine Grenze, denn was skaliert, ist der Renderer je Seite und der
 * Upload je Bild.
 *
 * <p><b>Die Sichtbarkeit steuert kein Ereignis, sondern ein Zeitstempel.</b>
 * Der Renderer läuft nur für Blöcke, die jemand sieht — genau das ist die
 * Information, die hier gebraucht wird. Wer in einer Sekunde nicht gezeichnet
 * wurde, wird auch nicht angesehen; wer länger als {@link #IDLE_MILLIS} nicht
 * gezeichnet wurde, macht zu. Eine Regel deckt damit ab, was sonst drei
 * Ereignisse bräuchte: Chunk entladen, Block abgebaut, Dimension gewechselt.
 *
 * <p>Alles hier gehört dem Renderthread. Von woanders gerufen zu werden wäre
 * ein Fehler, kein Nebeneffekt — Chromiums Sitzungen leben in genau diesem
 * Thread.
 *
 * <p><b>Und deshalb steht die Klasse hier und nicht unter {@code web}.</b> Sie
 * kennt Blockpositionen, den Texturverwalter und die Kennung dieser Mod — drei
 * Dinge, die eine Browserlaufzeit nichts angehen. Was sie von der Laufzeit
 * braucht, ist eine Sitzung und eine Texturkennung; die Grenze verläuft genau
 * dort.
 */
public final class WebPanels {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * Wie viele Flächen gleichzeitig leben dürfen.
     *
     * <p>Wer als vierter angefragt wird, bekommt nichts zu sehen, bis eine
     * andere zumacht. Das ist ehrlicher als eine vierte Fläche, die die
     * Bildrate der drei ersten frisst.
     */
    public static final int MAX_LIVE = 3;

    /** Die Auflösung je Fläche. Ein Block in der Welt ist kein Vollbild. */
    private static final int RESOLUTION = 512;

    /** Nach so langer Blindheit macht eine Fläche zu. */
    private static final long IDLE_MILLIS = 5_000;

    /** Ab dieser Entfernung genügt ein langsamerer Takt. */
    private static final double NEARBY_RANGE = 12.0;

    private static final Map<BlockPos, Panel> panels = new HashMap<>();
    private static int nextId;
    private static String startPageUrl;

    private WebPanels() {
    }

    /** Eine lebende Fläche: Sitzung, Textur, Adresse, Zeitstempel. */
    private static final class Panel {
        final BrowserSession session;
        final ResourceLocation texture;
        String url;
        long seenAt;

        Panel(BrowserSession session, ResourceLocation texture, String url) {
            this.session = session;
            this.texture = texture;
            this.url = url;
            this.seenAt = System.currentTimeMillis();
        }
    }

    /**
     * Die Textur für eine Fläche — und der Auftrag, sie am Leben zu halten.
     *
     * <p>Öffnet bei Bedarf einen Browser, meldet in jedem Fall, dass diese
     * Stelle gerade angesehen wird.
     *
     * @param distance Entfernung zum Betrachter, für den Takt
     * @return wo die Textur liegt, oder {@code null}, wenn es (noch) keine
     *         gibt — dann zeichnet der Renderer nichts
     */
    public static ResourceLocation textureFor(BlockPos pos, String url, double distance) {
        Panel panel = panels.get(pos);
        if (panel != null) {
            panel.seenAt = System.currentTimeMillis();
            if (!java.util.Objects.equals(panel.url, url)) {
                // Die Adresse hat sich geändert. Die Sitzung kennt kein
                // Nachladen, also macht die Fläche zu und geht im nächsten
                // Bild neu auf — bei einer Änderung, die ein Mensch auslöst,
                // ist das billig genug.
                close(pos, "andere Adresse");
                return null;
            }
            panel.session.setVisibility(distance <= NEARBY_RANGE
                    ? BrowserVisibility.NEARBY : BrowserVisibility.DISTANT);
            return panel.texture;
        }
        if (panels.size() >= MAX_LIVE) {
            return null;
        }
        return open(pos, url);
    }

    /**
     * Die mitgelieferte Startseite, ausgepackt neben dem Spiel.
     *
     * <p>Ausgepackt und nicht aus dem Jar geladen: Chromium liest keine
     * Dateien aus einem Archiv, und ein eigenes Schema dafür wäre mehr Aufwand
     * als eine Datei, die einmal je Sitzung entsteht.
     */
    private static String startPage() {
        if (startPageUrl != null) {
            return startPageUrl;
        }
        Path target = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("factorynetwork").resolve("web").resolve("panel-start.html");
        try (InputStream stream = WebPanels.class.getClassLoader()
                .getResourceAsStream("assets/factorynetwork/web/panel/start.html")) {
            if (stream == null) {
                LOG.warn("Die Startseite fehlt im Klassenpfad");
                return null;
            }
            Files.createDirectories(target.getParent());
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException broken) {
            LOG.warn("Die Startseite ließ sich nicht ablegen", broken);
            return null;
        }
        startPageUrl = target.toUri().toString();
        return startPageUrl;
    }

    private static ResourceLocation open(BlockPos pos, String wanted) {
        if (!WebSupport.ensureStarted().usable()) {
            return null;
        }
        String url = wanted == null || wanted.isBlank() ? startPage() : wanted;
        if (url == null) {
            return null;
        }
        try {
            BrowserSession session = BrowserSession.open(url, false,
                    RESOLUTION, RESOLUTION, BrowserVisibility.NEARBY);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    FactoryNetwork.MOD_ID, "web_panel/" + nextId++);
            Minecraft.getInstance().getTextureManager()
                    .register(location, new SessionTexture(session::textureId));
            panels.put(pos.immutable(), new Panel(session, location, wanted));
            LOG.info("Web-Fläche bei {} geöffnet: {} — offen: {}", pos, url, panels.size());
            return location;
        } catch (Throwable broken) {
            LOG.warn("Die Web-Fläche bei {} kam nicht zustande", pos, broken);
            return null;
        }
    }

    /**
     * Räumt auf, was niemand mehr ansieht.
     *
     * <p>Im Takt des Spiels zu rufen, aus dem Renderthread.
     */
    public static void tick() {
        if (panels.isEmpty()) {
            return;
        }
        long deadline = System.currentTimeMillis() - IDLE_MILLIS;
        List<BlockPos> gone = new ArrayList<>();
        for (Map.Entry<BlockPos, Panel> entry : panels.entrySet()) {
            if (entry.getValue().seenAt < deadline) {
                gone.add(entry.getKey());
            }
        }
        for (BlockPos pos : gone) {
            close(pos, "niemand sieht hin");
        }
    }

    /** Macht alle zu — beim Verlassen einer Welt und beim Beenden. */
    public static void closeAll() {
        for (BlockPos pos : List.copyOf(panels.keySet())) {
            close(pos, "die Welt wird verlassen");
        }
    }

    /** Wie viele gerade leben. */
    public static int count() {
        return panels.size();
    }

    private static void close(BlockPos pos, String why) {
        Panel panel = panels.remove(pos);
        if (panel == null) {
            return;
        }
        // <b>Erst abmelden, dann schließen.</b> Andersherum bliebe eine
        // Textur angemeldet, deren Kennung ins Leere zeigt — und der nächste
        // Bindevorgang holte sich eine gelöschte.
        Minecraft.getInstance().getTextureManager().release(panel.texture);
        try {
            panel.session.close();
        } catch (Throwable broken) {
            LOG.warn("Die Web-Fläche bei {} ließ sich nicht schließen", pos, broken);
        }
        LOG.info("Web-Fläche bei {} geschlossen ({}) — offen: {}, Browser gesamt: {}",
                pos, why, panels.size(), WebRuntime.isAvailable()
                        ? dev.devpanda.factorynetwork.web.BrowserManager.count() : 0);
    }
}
