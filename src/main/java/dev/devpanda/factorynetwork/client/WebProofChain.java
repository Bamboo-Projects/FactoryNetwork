package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.web.runtime.WebBenchmark;
import dev.devpanda.factorynetwork.web.runtime.WebSelfTest;
import dev.devpanda.factorynetwork.web.screen.BackdropBenchmark;
import dev.devpanda.factorynetwork.web.screen.BackdropProof;
import dev.devpanda.factorynetwork.web.screen.InteractionBenchmark;
import dev.devpanda.factorynetwork.web.screen.RenderProof;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings the proofs into an order that does not disturb itself.
 *
 * <p>Three runs want to use the same Chromium:
 *
 * <ol>
 *   <li>The <b>texture self-test</b> — does the picture reach the texture correctly?</li>
 *   <li>The <b>render proof</b> — does it reach the screen correctly?</li>
 *   <li>The <b>benchmark</b> — what does it cost?</li>
 * </ol>
 *
 * <p>None of them would work at the same time: two browsers share the frame
 * rate, and every benchmark would report half as the limit. The render proof
 * additionally needs a screen all to itself.
 *
 * <p>Runs only when {@code fn.benchmark} is set. In ordinary play no one gains
 * anything from a test image popping up when entering the world. By hand it
 * works any time with {@code /fnweb nachweis}.
 */
final class WebProofChain {

    /** A short pause between two browsers — two seconds. */
    private static final int SETTLE_TICKS = 40;

    private static final boolean ENABLED = Boolean.getBoolean("fn.benchmark");

    /**
     * Only the development environment, without the chain before it.
     *
     * <p>The proofs A through E together need over three minutes before the
     * first Monaco image appears. For a spike that needs several runs, that is
     * a lost quarter of an hour every time.
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
        // Without a world there is no meaningful background and the frame rate
        // is capped; the benchmark waits for it anyway.
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
        // The backdrop benchmark right after its proof: it needs the screen to
        // itself, and what comes after needs it just as much.
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
        // The interaction benchmark very last: it holds the screen to itself,
        // and the numbers benchmark before it needs it free.
        if (!interactionStarted && BackdropBenchmark.finished() && WebBenchmark.finished()) {
            interactionStarted = true;
            try {
                InteractionBenchmark.open(client);
            } catch (Exception broken) {
                LOG.warn("Interaktionsmessung ließ sich nicht öffnen", broken);
            }
        }
    }
    /** The short way: wait for the world, open the interface. */
    private static void tickIdeOnly() {
        Minecraft client = Minecraft.getInstance();
        // The lifecycle run opens and closes on its own; it must therefore not
        // wait for the one-time opening below.
        if (Boolean.getBoolean("fn.lifecycle")) {
            if (client.level == null) {
                return;
            }
            ticksWaiting++;
            if (ticksWaiting < SETTLE_TICKS) {
                return;
            }
            dev.devpanda.factorynetwork.web.ide.LifecycleBenchmark.start();
            dev.devpanda.factorynetwork.web.ide.LifecycleBenchmark.tick(client);
            return;
        }
        if (proofStarted) {
            reopenOnKey(client);
            return;
        }
        if (client.level == null) {
            return;
        }
        ticksWaiting++;
        if (ticksWaiting < SETTLE_TICKS) {
            return;
        }
        proofStarted = true;
        boolean opened;
        if (Boolean.getBoolean("fn.probe")) {
            // The marker for the first memory stage: Chromium is running, but
            // this screen has not yet opened a browser. After that, this state
            // does not occur again in this session.
            LOG.info("RAM:cef-ohne-browser — Marke gesetzt");
            opened = dev.devpanda.factorynetwork.web.ide.ProbeBenchmark.open(client);
        } else if (Boolean.getBoolean("fn.typing")) {
            opened = dev.devpanda.factorynetwork.web.ide.TypingBenchmark.open(client);
        } else if (Boolean.getBoolean("fn.idebench")) {
            opened = dev.devpanda.factorynetwork.web.ide.MonacoBenchmark.open(client);
        } else {
            opened = EditorApp.open(client);
        }
        if (!opened) {
            LOG.warn("Die Oberfläche ließ sich nicht öffnen");
        }
    }

    /**
     * Opens the interface again on F6.
     *
     * <p><b>What for.</b> The interface opens once at startup. But whoever
     * wants to check whether processes are left behind on repeated opening and
     * closing needs exactly that: repeated opening. Without a way back the
     * question cannot be answered.
     *
     * <p>Only in measurement mode, that is when {@code fn.ide} is set — in the
     * game an interface belongs on a block and not on a key.
     */
    private static void reopenOnKey(Minecraft client) {
        if (client.screen != null || client.level == null) {
            return;
        }
        long window = client.getWindow().getWindow();
        boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(window,
                org.lwjgl.glfw.GLFW.GLFW_KEY_F6) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        // Only the edge counts: held down, the interface would otherwise be
        // opened anew on every tick.
        if (down && !reopenKeyWasDown) {
            LOG.info("F6 — Oberfläche wird erneut geöffnet");
            EditorApp.open(client);
        }
        reopenKeyWasDown = down;
    }

    private static boolean reopenKeyWasDown;
}