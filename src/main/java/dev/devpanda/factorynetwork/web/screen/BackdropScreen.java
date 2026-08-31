package dev.devpanda.factorynetwork.web.screen;

import dev.devpanda.factorynetwork.web.capture.FrameStore;
import dev.devpanda.factorynetwork.web.capture.WorldCapture;
import dev.devpanda.factorynetwork.web.mcef.FrameSchemes;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ein Bildschirm, hinter dem Minecraft weiterläuft — im Dokument, nicht
 * dahinter.
 *
 * <p><b>Warum Minecrafts Bild ein Element der Seite sein muss.</b> Die
 * naheliegende Idee wäre, den Browser durchsichtig zu machen und Minecraft
 * einfach darunter zu lassen. Für ein weiches Glas reicht das nicht: Nach der
 * CSS-Spezifikation umfasst das, was ein {@code backdrop-filter} filtert,
 * ausschließlich Inhalt <b>dieses Dokuments</b>. Was hinter dem Browserfenster
 * liegt, gehört nicht dazu — der Filter liefe über Transparenz und ergäbe
 * nichts.
 *
 * <p>Also wird Minecrafts Bild aufgenommen und als unterstes Element
 * eingesetzt. Ab da ist es für Chromium gewöhnlicher Seiteninhalt, und alles,
 * was CSS kann, gilt dafür.
 *
 * <p><b>Zwei Takte, die nichts miteinander zu tun haben.</b> Wie oft ein neuer
 * Hintergrund entsteht, entscheidet {@link Mode}. Wie oft der Browser malt,
 * entscheidet die Seite selbst — bei einem ruhenden Editor gar nicht. Ein
 * Hintergrund mit zwei Bildern je Sekunde bedeutet also nicht, dass irgendetwas
 * anderes mit zwei Bildern je Sekunde liefe.
 *
 * <p><b>Der Austausch geschieht ohne Neuladen.</b> Ein einzelner
 * JavaScript-Aufruf setzt die Adresse des Bildes neu; die Seite bleibt stehen,
 * mit allem, was darauf getippt wurde. Das ist ausdrücklich keine Brücke
 * zwischen Java und der Seite — es geht nur in eine Richtung, kennt genau
 * einen Befehl, und die Seite kann nichts zurückrufen.
 */
public class BackdropScreen extends BrowserScreen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/BackdropScreen");

    /** Wie oft ein neuer Hintergrund entsteht. */
    public enum Mode {

        /**
         * Einmal beim Öffnen, danach nie wieder.
         *
         * <p>Für einen Editor womöglich genug: Wer schreibt, sieht ohnehin auf
         * den Text und nicht durch ihn hindurch. Und ein Standbild kostet nach
         * dem ersten Bild exakt nichts.
         */
        STATIC(0),

        /** Zweimal je Sekunde — sichtbar ruckelnd, aber lebendig. */
        LOW_2(2),

        /** Fünfmal je Sekunde. */
        LOW_5(5),

        /** Zehnmal je Sekunde. */
        LOW_10(10);

        private final int framesPerSecond;

        Mode(int framesPerSecond) {
            this.framesPerSecond = framesPerSecond;
        }

        public int framesPerSecond() {
            return framesPerSecond;
        }

        public long intervalNanos() {
            return framesPerSecond <= 0 ? Long.MAX_VALUE : 1_000_000_000L / framesPerSecond;
        }
    }

    /**
     * Die Seite: Minecrafts Bild unten, Glas darüber.
     *
     * <p>Bewusst ohne Skript für den Bildwechsel — der kommt von außen. Eine
     * Seite, die selbst in kurzen Abständen nachfragt, fragte auch dann, wenn
     * es nichts Neues gibt.
     */
    private static final String PAGE = """
            <!doctype html><html lang="de"><head><meta charset="utf-8"><style>
              html,body{margin:0;width:100%;height:100%;overflow:hidden;
                        background:#0a0b10;color:#e8eaf2;
                        font:15px/1.6 "Segoe UI",system-ui,sans-serif}
              #minecraft-background{position:fixed;inset:0;width:100%;height:100%;
                                    object-fit:cover}
              .desktop{position:relative;height:100%;padding:48px;
                       display:grid;gap:24px;
                       grid-template-columns:repeat(2,minmax(0,1fr));
                       grid-template-rows:repeat(2,minmax(0,1fr));
                       box-sizing:border-box}
              .glass{background:rgba(20,20,30,0.35);
                     border:1px solid rgba(255,255,255,0.15);
                     border-radius:14px;padding:20px;
                     box-shadow:0 8px 32px rgba(0,0,0,0.35)}
              .g1{backdrop-filter:blur(18px)}
              .g2{backdrop-filter:blur(18px) saturate(140%)}
              .g3{backdrop-filter:blur(40px) saturate(120%)}
              .g4{background:rgba(20,20,30,0.35)}
              h2{margin:0 0 8px;font-size:16px;font-weight:600}
              p{margin:0;font-size:13px;color:#b9bfd0}
              code{font-family:Consolas,monospace;color:#7dd3a0}
            </style></head><body>
              <img id="minecraft-background" alt="">
              <main class="desktop">
                <section class="glass g1"><h2>Nur Weichzeichner</h2>
                  <p><code>blur(18px)</code></p></section>
                <section class="glass g2"><h2>Weichzeichner und Sättigung</h2>
                  <p><code>blur(18px) saturate(140%)</code></p></section>
                <section class="glass g3"><h2>Starker Weichzeichner</h2>
                  <p><code>blur(40px) saturate(120%)</code></p></section>
                <section class="glass g4"><h2>Halbdurchsichtig ohne Filter</h2>
                  <p>Zum Vergleich — hier wird nichts gefiltert.</p></section>
              </main>
            <script>
              // Der einzige Zweck: die Adresse von außen setzbar machen, ohne
              // die Seite neu zu laden. Kein Nachfragen, keine Rückrichtung.
              window.fnSetBackground = function (url) {
                document.getElementById('minecraft-background').src = url;
              };
            </script></body></html>
            """;

    private final FrameStore store = new FrameStore();
    private final WorldCapture capture = new WorldCapture();

    private Mode mode;
    private double scale;
    private WorldCapture.Format format;

    private boolean schemeReady;
    private long lastCaptureNanos;
    private long captures;

    protected BackdropScreen(Component title, String url, Mode mode,
                             double scale, WorldCapture.Format format) {
        super(title, url, false);
        this.mode = mode;
        this.scale = scale;
        this.format = format;
    }

    /** Legt die Seite ab und öffnet den Bildschirm. */
    public static void open(Minecraft client, Mode mode, double scale,
                            WorldCapture.Format format) throws Exception {
        Path file = Files.createTempFile("fn-backdrop-screen", ".html");
        Files.writeString(file, PAGE, StandardCharsets.UTF_8);
        file.toFile().deleteOnExit();
        client.setScreen(new BackdropScreen(Component.literal("Hintergrund"),
                file.toUri().toString(), mode, scale, format));
    }

    @Override
    protected void init() {
        super.init();
        if (!schemeReady) {
            schemeReady = FrameSchemes.register(store);
            if (!schemeReady) {
                LOG.warn("Chromium hat das Schema nicht angenommen — kein Hintergrund");
            }
        }
        // <b>Nach einer Größenänderung ist der alte Hintergrund falsch.</b> Er
        // hätte das Seitenverhältnis von vorher, und object-fit würde ihn
        // beschneiden oder strecken. Der nächste Durchgang nimmt neu auf.
        lastCaptureNanos = 0;
    }

    /**
     * Nimmt auf, wenn es an der Zeit ist.
     *
     * <p>Der einzige Punkt im ganzen Ablauf, an dem Minecrafts Bild noch
     * unberührt im Puffer steht — und an dem der eigene Browser noch nicht
     * darin ist.
     */
    @Override
    protected void beforeDrawing() {
        if (!schemeReady || !hasSession()) {
            return;
        }
        long now = System.nanoTime();
        boolean first = lastCaptureNanos == 0;
        if (!first && now - lastCaptureNanos < mode.intervalNanos()) {
            return;
        }
        lastCaptureNanos = now;
        long generation = capture.captureInto(store, scale, format);
        if (generation == 0) {
            return;
        }
        captures++;
        showBackground(generation);
    }

    /**
     * Setzt die Adresse des Bildes neu.
     *
     * <p>Die Nummer in der Adresse ist das, was Chromium zum Nachladen bringt:
     * Ohne sie hielte es die Adresse für dieselbe und zeigte sein
     * zwischengespeichertes Bild weiter. Der Handler schickt zusätzlich ein
     * {@code no-store} mit — welches der beiden Mittel allein genügt, ist eine
     * eigene Messung wert.
     */
    private void showBackground(long generation) {
        String script = "window.fnSetBackground && window.fnSetBackground('"
                + backgroundUrlFor(generation) + "');";
        runScript(script);
    }

    /**
     * Welche Adresse für dieses Bild gesetzt wird.
     *
     * <p>Überschreibbar, damit sich prüfen lässt, was ohne die Nummer
     * geschieht — das ist die Frage nach Chromiums Zwischenspeicher, und sie
     * beantwortet sich nur, indem man die Nummer wegnimmt.
     */
    protected String backgroundUrlFor(long generation) {
        return FrameSchemes.urlFor(generation);
    }

    public long captures() {
        return captures;
    }

    public WorldCapture capture() {
        return capture;
    }

    public FrameStore store() {
        return store;
    }

    protected void setMode(Mode next) {
        this.mode = next;
        this.lastCaptureNanos = 0;
    }

    protected void setScale(double next) {
        this.scale = next;
        this.lastCaptureNanos = 0;
    }

    protected void setFormat(WorldCapture.Format next) {
        this.format = next;
        this.lastCaptureNanos = 0;
    }

    protected Mode mode() {
        return mode;
    }

    protected double scale() {
        return scale;
    }

    protected WorldCapture.Format format() {
        return format;
    }

    @Override
    public void removed() {
        super.removed();
        capture.close();
        store.clear();
    }
}
