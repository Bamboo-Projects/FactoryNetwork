package dev.devpanda.factorynetwork.web.ide;

import dev.devpanda.factorynetwork.web.measure.DurationSamples;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Zerlegt A — die Strecke zwischen Tastendruck und fertigem Bild von Chromium.
 *
 * <p><b>Die Frage.</b> Nach Schritt G steht fest, dass A rund zweiundvierzig
 * Millisekunden dauert und nicht an der Auflösung hängt. Offen ist, <i>was</i>
 * darin steckt. „Chromium und Monaco" ist keine Antwort, sondern eine
 * Zusammenfassung von mindestens fünf Dingen: JavaScript, Layout, Zeichnen,
 * Zusammensetzen, und der Rückweg des fertigen Bildes vom Grafikspeicher in
 * den Hauptspeicher, den der fensterlose Betrieb erzwingt.
 *
 * <p><b>Der Weg dorthin führt über zwei Uhren, die nichts voneinander
 * wissen.</b> Die Seite misst mit ihrer eigenen, was zwischen Tastendruck und
 * fertig gezeichnetem Bild vergeht; Java misst, wann das Bild ankommt. Beide
 * Strecken beginnen am selben Ereignis. Was zwischen ihnen liegt, ist das,
 * wofür Chromium <i>nach</i> dem Zeichnen noch braucht — und genau das ist die
 * Zahl, die entscheidet, ob der nächste Hebel Monaco heißt oder der
 * Bildweg selbst.
 *
 * <p><b>Und Vergleichsseiten, weil eine Zahl allein nichts sagt.</b> Ein
 * nacktes Textfeld ist die Untergrenze dessen, was Texteingabe in diesem
 * Aufbau überhaupt kosten kann. Liegt Monaco weit darüber, ist es Monaco.
 * Liegt schon das Textfeld hoch, ist es der Aufbau.
 */
public final class ProbeBenchmark extends IdeScreen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/Probe");

    private static final long TYPING_NANOS = 45_000_000_000L;
    /**
     * Wie lange eine Speicherstufe steht.
     *
     * <p>Lang genug, dass die Messung von außen sie trifft: Der Aufruf, der
     * den Speicher abfragt, braucht selbst gut eine Sekunde. Beim ersten
     * Versuch standen alle fünf Stufen bei denselben achthundert Megabyte —
     * gemessen wurde jedes Mal derselbe Zustand.
     */
    private static final long RAM_NANOS = 20_000_000_000L;
    private static final int SETTLE_FRAMES = 40;

    private static boolean finished;

    /**
     * Was der Reihe nach gemessen wird.
     *
     * <p>Die Speicherstufen zuerst: Ist Monaco einmal geladen, gibt es den
     * leeren Zustand in dieser Sitzung nicht zurück.
     */
    private enum Step {
        // Nur noch die beiden Varianten, die im ersten Durchgang an der
        // fehlenden Editorhöhe gescheitert sind. Alles andere ist gemessen
        // und muss nicht wiederholt werden.
        MONACO_MIN("C Monaco minimal", "monaco-min", TYPING_NANOS, true),
        MONACO_FULL("D Monaco vollständig", "monaco-full", TYPING_NANOS, true),

        DONE("fertig", null, 0, false);

        final String label;
        final String variant;
        final long nanos;
        final boolean typing;

        Step(String label, String variant, long nanos, boolean typing) {
            this.label = label;
            this.variant = variant;
            this.nanos = nanos;
            this.typing = typing;
        }
    }

    private static final String TIPPTEXT = "    let vorrat = storage.count(x)";

    private final String pageBase;
    private Step step = Step.MONACO_MIN;
    private long stepStartedNanos;
    private long paintsAtStart;
    private int typedIndex;
    private int frameInStep;
    private int settleFrames;
    private boolean announced;

    private final DurationSamples frameTimes = new DurationSamples();
    private long lastFrameNanos;

    private ProbeBenchmark(String url, String pageBase) {
        super(url);
        this.pageBase = pageBase;
    }

    public static boolean finished() {
        return finished;
    }

    /** Öffnet die Messung — sie beginnt mit einer leeren Seite. */
    public static boolean open(Minecraft client) {
        String ide = prepare(client);
        if (ide == null) {
            return false;
        }
        // Die Messfläche liegt neben der Oberfläche und wird genauso ausgepackt.
        Path folder = client.gameDirectory.toPath()
                .resolve("factorynetwork-web").resolve("ide");
        Path probe = folder.resolve("probe.html");
        try (java.io.InputStream stream = ProbeBenchmark.class.getClassLoader()
                .getResourceAsStream("assets/factorynetwork/web/ide/probe.html")) {
            if (stream == null) {
                LOG.warn("Die Messfläche fehlt im Klassenpfad");
                return false;
            }
            java.nio.file.Files.copy(stream, probe,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException broken) {
            LOG.warn("Die Messfläche ließ sich nicht ablegen", broken);
            return false;
        }
        String base = probe.toUri().toString();
        client.setScreen(new ProbeBenchmark(base + "?v=monaco-min", base));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            frameTimes.record(now - lastFrameNanos);
        }
        lastFrameNanos = now;
        super.render(graphics, mouseX, mouseY, partialTick);
        advance();
    }

    private void advance() {
        if (step == Step.DONE || !hasSession()) {
            return;
        }
        if (!announced) {
            announced = true;
            LOG.info("=== LAUF {} — Browser {}x{} ===", System.currentTimeMillis() / 1000,
                    session().width(), session().height());
            stepStartedNanos = System.nanoTime();
            return;
        }
        if (pageLoading()) {
            return;
        }
        if (settleFrames > 0) {
            settleFrames--;
            if (settleFrames == 0) {
                stepStartedNanos = System.nanoTime();
                paintsAtStart = paints();
                texture().resetStats();
                session().resetLatency();
                frameTimes.reset();
                runScript("window.fnProbe && fnProbe.leeren()");
                if (step.typing) {
                    runScript("window.fnProbe && fnProbe.fokus()");
                    click(0.5, 0.4);
                }
            }
            return;
        }
        frameInStep++;
        if (step.typing && frameInStep % 8 == 0) {
            typeOneCharacter();
        }
        if (System.nanoTime() - stepStartedNanos < step.nanos) {
            return;
        }
        report();
        step = Step.values()[step.ordinal() + 1];
        frameInStep = 0;
        begin();
        if (step == Step.DONE) {
            LOG.info("Probemessung fertig.");
            finished = true;
            onClose();
        }
    }

    private void begin() {
        if (step == Step.DONE) {
            return;
        }
        // Die Seite neu laden statt einen zweiten Browser aufzumachen: Zwei
        // Browser teilten sich die Bildrate, und jede Zahl wäre halb so gut.
        runScript("location.href='" + pageBase + "?v=" + step.variant + "'");
        stepStartedNanos = System.nanoTime();
        settleFrames = SETTLE_FRAMES;
    }

    /**
     * Ein Zeichen auf dem vollen Weg, wie es von einer Tastatur käme.
     *
     * <p><b>Nur ein Zeichen zu schicken, reicht nicht.</b> Das erzeugt in
     * Chromium ein {@code input}-Ereignis, aber kein {@code keydown} — und die
     * Seite misst an {@code keydown}. Beim ersten Versuch stand deshalb in
     * jeder Zeile {@code n=0}: Java tippte, die Seite sah nichts.
     *
     * <p>Echte Eingabe kommt in drei Teilen: Taste herunter, Zeichen, Taste
     * herauf. Genau so wird hier getippt.
     */
    private void typeOneCharacter() {
        char zeichen = TIPPTEXT.charAt(typedIndex++ % TIPPTEXT.length());
        int taste = zeichen == ' ' ? GLFW.GLFW_KEY_SPACE
                : Character.isLetter(zeichen)
                    ? GLFW.GLFW_KEY_A + (Character.toUpperCase(zeichen) - 'A')
                    : GLFW.GLFW_KEY_PERIOD;
        int scanCode = GLFW.glfwGetKeyScancode(taste);
        keyPressed(taste, scanCode, 0);
        charTyped(zeichen, 0);
        keyReleased(taste, scanCode, 0);
    }

    private void click(double across, double down) {
        double x = width * across;
        double y = height * down;
        mouseMoved(x, y);
        mouseClicked(x, y, 0);
        mouseReleased(x, y, 0);
    }

    private void report() {
        double seconds = (System.nanoTime() - stepStartedNanos) / 1_000_000_000.0;
        if (!step.typing) {
            // Für die Speicherstufen genügt die Marke; gemessen wird von
            // außen. Sie steht am <b>Ende</b> der Stufe, nicht am Anfang —
            // dann ist alles geladen und eingeschwungen.
            LOG.info("{} — Marke gesetzt nach {} s, Browser {}x{}",
                    step.label, String.format(Locale.GERMANY, "%.0f", seconds),
                    session().width(), session().height());
            return;
        }
        long paints = paints() - paintsAtStart;
        var texture = texture();
        var toPaint = session().toPaint();
        var total = session().totalLatency();
        double fullFrame = (double) session().width() * session().height() * 4;

        LOG.info("=== {} ===", step.label);
        LOG.info(String.format(Locale.GERMANY,
                "%s: %d Bilder (%.1f/s), %.1f KB je Bild (%.1f %% eines Vollbilds), "
                        + "%.2f MB/s",
                step.label, paints, paints / seconds,
                texture.bytesPerUpload() / 1024.0,
                texture.bytesPerUpload() / fullFrame * 100.0,
                texture.uploadedBytes() / seconds / (1024.0 * 1024.0)));
        LOG.info(String.format(Locale.GERMANY,
                "%s: A Eingabe→Bild p50 %.1f ms, p95 %.1f ms, max %.1f ms (%d Messungen)",
                step.label, toPaint.percentile(50) / 1000.0,
                toPaint.percentile(95) / 1000.0, toPaint.slowest() / 1000.0,
                toPaint.count()));
        LOG.info(String.format(Locale.GERMANY,
                "%s: B Upload p50 %.1f ms, p95 %.1f ms | gesamt bis sichtbar p50 %.1f ms",
                step.label, texture.uploadTimes().percentile(50) / 1000.0,
                texture.uploadTimes().percentile(95) / 1000.0,
                total.percentile(50) / 1000.0));
        LOG.info(String.format(Locale.GERMANY,
                "%s: Minecrafts Bildzeit p50 %.1f ms, p95 %.1f ms",
                step.label, frameTimes.percentile(50) / 1000.0,
                frameTimes.percentile(95) / 1000.0));
        // Und jetzt die Sicht der Seite auf dieselbe Strecke.
        runScript("window.fnProbe && fnProbe.bericht('" + step.label + "')");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
