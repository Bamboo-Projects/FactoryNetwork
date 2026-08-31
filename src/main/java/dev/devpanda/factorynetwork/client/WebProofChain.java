package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.web.mcef.WebBenchmark;
import dev.devpanda.factorynetwork.web.mcef.WebSelfTest;
import dev.devpanda.factorynetwork.web.screen.BackdropBenchmark;
import dev.devpanda.factorynetwork.web.screen.BackdropProof;
import dev.devpanda.factorynetwork.web.screen.InteractionBenchmark;
import dev.devpanda.factorynetwork.web.screen.RenderProof;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bringt die Nachweise in eine Reihenfolge, die sich nicht selbst stört.
 *
 * <p>Drei Läufe wollen dasselbe Chromium benutzen:
 *
 * <ol>
 *   <li>Der <b>Textur-Selbsttest</b> — kommt das Bild richtig in die Textur?</li>
 *   <li>Der <b>Bildnachweis</b> — kommt es richtig auf den Schirm?</li>
 *   <li>Die <b>Messung</b> — was kostet das?</li>
 * </ol>
 *
 * <p>Gleichzeitig ginge keiner davon: Zwei Browser teilen sich die Bildrate,
 * und jede Messung berichtete die Hälfte als Grenze. Der Bildnachweis braucht
 * zusätzlich einen Bildschirm für sich allein.
 *
 * <p>Läuft nur, wenn {@code fn.benchmark} gesetzt ist. Im gewöhnlichen Spiel
 * hat niemand etwas davon, dass beim Betreten der Welt ein Prüfbild aufpoppt.
 * Von Hand geht es jederzeit mit {@code /fnweb nachweis}.
 */
final class WebProofChain {

    /** Kurze Ruhe zwischen zwei Browsern — zwei Sekunden. */
    private static final int SETTLE_TICKS = 40;

    private static final boolean ENABLED = Boolean.getBoolean("fn.benchmark");

    /**
     * Nur die Entwicklungsumgebung, ohne die Kette davor.
     *
     * <p>Die Nachweise aus A bis E brauchen zusammen über drei Minuten, bevor
     * das erste Monaco-Bild erscheint. Für einen Spike, der mehrere Läufe
     * braucht, ist das jedes Mal eine verlorene Viertelstunde.
     */
    private static final boolean IDE_ONLY = Boolean.getBoolean("fn.ide");

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/WebProofChain");

    private static boolean proofStarted;
    private static boolean backdropStarted;
    private static boolean backdropMeasured;
    private static boolean interactionStarted;
    private static int ticksWaiting;

    private WebProofChain() {
    }

    static void tick() {
        if (IDE_ONLY) {
            tickIdeOnly();
            return;
        }
        if (!ENABLED) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        // Ohne Welt gibt es keinen sinnvollen Hintergrund und die Bildrate ist
        // gedeckelt; die Messung wartet ohnehin darauf.
        if (client.level == null || !WebSelfTest.finished()) {
            return;
        }
        ticksWaiting++;
        if (ticksWaiting < SETTLE_TICKS) {
            return;
        }
        if (!proofStarted) {
            proofStarted = true;
            ticksWaiting = 0;
            RenderProof.openIfPossible(client);
            return;
        }
        if (!backdropStarted && RenderProof.finished()) {
            backdropStarted = true;
            ticksWaiting = 0;
            BackdropProof.openIfPossible(client);
            return;
        }
        // Die Hintergrundmessung direkt nach ihrem Nachweis: Sie braucht den
        // Bildschirm für sich, und was danach kommt, braucht ihn ebenso.
        if (!backdropMeasured && BackdropProof.finished()) {
            backdropMeasured = true;
            ticksWaiting = 0;
            try {
                BackdropBenchmark.open(client);
            } catch (Exception broken) {
                LOG.warn("Hintergrundmessung ließ sich nicht öffnen", broken);
            }
            return;
        }
        // Die Interaktionsmessung ganz zuletzt: Sie hält den Bildschirm für
        // sich, und die Zahlenmessung davor braucht ihn frei.
        if (!interactionStarted && BackdropBenchmark.finished() && WebBenchmark.finished()) {
            interactionStarted = true;
            try {
                InteractionBenchmark.open(client);
            } catch (Exception broken) {
                LOG.warn("Interaktionsmessung ließ sich nicht öffnen", broken);
            }
        }
    }
    /** Der kurze Weg: Welt abwarten, Oberfläche öffnen. */
    private static void tickIdeOnly() {
        if (proofStarted) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        ticksWaiting++;
        if (ticksWaiting < SETTLE_TICKS) {
            return;
        }
        proofStarted = true;
        boolean measuring = Boolean.getBoolean("fn.idebench");
        boolean opened = measuring
                ? dev.devpanda.factorynetwork.web.ide.MonacoBenchmark.open(client)
                : dev.devpanda.factorynetwork.web.ide.IdeScreen.open(client);
        if (!opened) {
            LOG.warn("Die Oberfläche ließ sich nicht öffnen");
        }
    }
}