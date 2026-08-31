package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.web.mcef.WebSelfTest;
import dev.devpanda.factorynetwork.web.screen.RenderProof;
import net.minecraft.client.Minecraft;

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

    private static boolean started;
    private static int ticksSinceSelfTest;

    private WebProofChain() {
    }

    static void tick() {
        if (!ENABLED || started || !WebSelfTest.finished() || RenderProof.finished()) {
            return;
        }
        ticksSinceSelfTest++;
        if (ticksSinceSelfTest < SETTLE_TICKS) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        // Ohne Welt gibt es keinen sinnvollen Hintergrund und die Bildrate ist
        // gedeckelt; die Messung wartet ohnehin darauf.
        if (client.level == null) {
            return;
        }
        started = true;
        RenderProof.openIfPossible(client);
    }
}
