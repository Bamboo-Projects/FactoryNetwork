package dev.devpanda.factorynetwork.client.panel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import dev.devpanda.factorynetwork.web.WebPage;
import dev.devpanda.factorynetwork.web.api.FnWeb;
import dev.devpanda.factorynetwork.web.api.SurfaceSpec;
import dev.devpanda.factorynetwork.web.api.WorldSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Web-Flächen der Tafel-Blöcke: wer lebt, wer wartet, wer zumacht.
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
 * <p><b>Seit die Fläche über die API kommt</b> ({@link FnWeb#openInWorld}),
 * hält diese Klasse nur noch das Wissen, das eine Browserlaufzeit nichts
 * angeht: welche Blöcke Tafeln sind, wie viele leben dürfen, und wann eine
 * zumacht. Das Zeichnen, die Textur und der Takt liegen in der
 * {@link WorldSurface}. Der Renderer des Blocks zeichnet nichts mehr; er
 * meldet nur, dass die Tafel im Bild ist.
 *
 * <p>Alles hier gehört dem Renderthread. Von woanders gerufen zu werden wäre
 * ein Fehler, kein Nebeneffekt — Chromiums Sitzungen leben in genau diesem
 * Thread.
 */
public final class WebPanels {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * Wie viele Flächen gleichzeitig leben dürfen.
     *
     * <p>Wer über der Grenze angefragt wird, bekommt nichts zu sehen, bis eine
     * andere zumacht. Das ist ehrlicher als eine Fläche, die die Bildrate der
     * übrigen frisst.
     *
     * <p>Die Zahl steht in der Konfiguration dieses Rechners und nicht hier:
     * Was der eine an Bildrate übrig hat, hat der andere nicht.
     */
    private static int maxLive() {
        return dev.devpanda.factorynetwork.FnClientConfig.webPanels();
    }

    /** Die Auflösung je Fläche. Ein Block in der Welt ist kein Vollbild. */
    private static final int RESOLUTION = 512;

    /** Nach so langer Blindheit macht eine Fläche zu. */
    private static final long IDLE_MILLIS = 5_000;

    /**
     * Wo die Vorderseite der Tafel liegt, von ihrer Blockmitte aus.
     *
     * <p>Die Tafel sitzt an der hinteren Kante ihres Blocks, zwei Pixel dick.
     * Der Wert und die Rechnung dahinter standen im Renderer und sind mit ihm
     * hierher gezogen — {@link #panelCenter} baut daraus den Mittelpunkt, den
     * die {@link WorldSurface} braucht.
     */
    private static final float FRONT = 0.5F - 2.0F / 16.0F;
    private static final float EPSILON = 0.001F;

    private static final Map<BlockPos, Panel> panels = new HashMap<>();
    private static String startPageUrl;

    private WebPanels() {
    }

    private static final class Panel {
        final WorldSurface surface;
        final String url;
        long seenAt;

        Panel(WorldSurface surface, String url) {
            this.surface = surface;
            this.url = url;
            this.seenAt = System.currentTimeMillis();
        }
    }

    /**
     * Der Renderer meldet: Diese Tafel ist im Bild.
     *
     * <p>Öffnet die Fläche, wenn es sie noch nicht gibt und Platz ist; sonst
     * frischt sie nur den Zeitstempel auf. Ein Wechsel der Adresse schließt
     * die alte Fläche — die neue entsteht beim nächsten Bild.
     */
    public static void seen(BlockPos pos, String url, String name, Direction facing) {
        Panel panel = panels.get(pos);
        if (panel != null) {
            panel.seenAt = System.currentTimeMillis();
            if (!java.util.Objects.equals(panel.url, url)) {
                close(pos, "andere Adresse");
            }
            return;
        }
        if (panels.size() >= maxLive()) {
            return;
        }
        open(pos, url, name, facing);
    }

    private static String startPage() {
        if (startPageUrl != null) {
            return startPageUrl;
        }
        startPageUrl = WebPage.unpack("assets/factorynetwork/web/panel/start.html", "panel-start.html");
        return startPageUrl;
    }

    /** Adresse mit der Kennung der Tafel dahinter — welche von fünf Seiten das ist. */
    private static String tagged(String url, BlockPos pos, String name) {
        String was = name == null || name.isBlank()
                ? pos.getX() + "," + pos.getY() + "," + pos.getZ()
                : name;
        return url + "#fn-panel=" + java.net.URLEncoder.encode(was,
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Der Mittelpunkt der Tafelfläche in Weltkoordinaten.
     *
     * <p><b>Nicht neu hergeleitet, sondern nachgespielt.</b> Es ist genau die
     * Kette, die der Renderer auf den Block legte: zur Blockmitte, um die
     * Hochachse gegen die Ausrichtung, dann sechs Sechzehntel nach hinten.
     * Der Ursprung durch diese Kette geschickt ergibt den Mittelpunkt — ohne
     * dass jemand die Drehung von Hand ausrechnet, und damit ohne den Fehler,
     * der das schon zweimal gekostet hat.
     */
    private static double[] panelCenter(BlockPos pos, Direction facing) {
        PoseStack stack = new PoseStack();
        stack.translate(0.5, 0.5, 0.5);
        stack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        stack.translate(0, 0, -FRONT + EPSILON);
        Vector3f center = stack.last().pose().transformPosition(new Vector3f(0, 0, 0));
        return new double[] {pos.getX() + center.x, pos.getY() + center.y, pos.getZ() + center.z};
    }

    private static void open(BlockPos pos, String wanted, String name, Direction facing) {
        if (!FnWeb.available()) {
            return;
        }
        String url = wanted == null || wanted.isBlank() ? startPage() : wanted;
        if (url == null) {
            return;
        }
        String label = name == null || name.isBlank()
                ? "Tafel " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                : name;
        double[] center = panelCenter(pos, facing);
        SurfaceSpec spec = SurfaceSpec.of(tagged(url, pos, name), RESOLUTION, RESOLUTION)
                .named(label);
        WorldSurface surface = FnWeb.openInWorld(spec, center[0], center[1], center[2],
                facing.toYRot(), 1.0f, 1.0f);
        if (surface == null) {
            return;
        }
        panels.put(pos.immutable(), new Panel(surface, wanted));
        LOG.info("Web-Fläche {} bei {} geöffnet: {} — offen: {}", label, pos, url, panels.size());
    }

    public static void tick() {
        if (panels.isEmpty()) {
            return;
        }
        long deadline = System.currentTimeMillis() - IDLE_MILLIS;
        List<BlockPos> gone = new ArrayList<>();
        for (Map.Entry<BlockPos, Panel> entry : panels.entrySet()) {
            if (entry.getValue().seenAt < deadline || !entry.getValue().surface.alive()) {
                gone.add(entry.getKey());
            }
        }
        for (BlockPos pos : gone) {
            close(pos, "niemand sieht hin");
        }
    }

    public static void closeAll() {
        for (BlockPos pos : List.copyOf(panels.keySet())) {
            close(pos, "die Welt wird verlassen");
        }
    }

    public static int count() {
        return panels.size();
    }

    private static void close(BlockPos pos, String why) {
        Panel panel = panels.remove(pos);
        if (panel == null) {
            return;
        }
        panel.surface.close();
        LOG.info("Web-Fläche bei {} geschlossen ({}) — offen: {}", pos, why, panels.size());
    }
}
