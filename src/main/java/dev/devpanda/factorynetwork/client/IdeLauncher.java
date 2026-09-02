package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.client.bench.LifecycleBenchmark;
import dev.devpanda.factorynetwork.client.bench.MonacoBenchmark;
import dev.devpanda.factorynetwork.client.bench.ProbeBenchmark;
import dev.devpanda.factorynetwork.client.bench.TypingBenchmark;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens the editor by itself when the client was started for a measurement.
 *
 * <p>Runs only when {@code fn.ide} is set — in the game an interface belongs
 * on a block and not on a switch. Which of the runs it is decides on further
 * properties: {@code fn.lifecycle}, {@code fn.probe}, {@code fn.typing},
 * {@code fn.idebench}, and otherwise the plain editor.
 *
 * <p><b>Why this waits for a world.</b> In the main menu Minecraft caps the
 * frame rate at sixty, and every time measured under it measures the cap and
 * not the cost.
 *
 * <p>This is the half of the old proof chain that belongs to this mod. The
 * other half — the render proof and the backdrop measurements — inspects the
 * web runtime's internals and now lives beside it in BambooCEF.
 */
public final class IdeLauncher {

    /** A short pause before the first screen — two seconds. */
    private static final int SETTLE_TICKS = 40;

    private static final boolean ENABLED = Boolean.getBoolean("fn.ide");

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/IdeLauncher");

    private static boolean opened;
    private static int ticksWaiting;
    private static boolean reopenKeyWasDown;

    private IdeLauncher() {
    }

    /** One step per client tick; does nothing unless {@code fn.ide} is set. */
    public static void tick() {
        if (!ENABLED) {
            return;
        }
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
            LifecycleBenchmark.start();
            LifecycleBenchmark.tick(client);
            return;
        }
        if (opened) {
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
        opened = true;
        boolean up;
        if (Boolean.getBoolean("fn.probe")) {
            // The marker for the first memory stage: Chromium is running, but
            // this screen has not yet opened a browser. After that, this state
            // does not occur again in this session.
            LOG.info("RAM:cef-ohne-browser — Marke gesetzt");
            up = ProbeBenchmark.open(client);
        } else if (Boolean.getBoolean("fn.typing")) {
            up = TypingBenchmark.open(client);
        } else if (Boolean.getBoolean("fn.idebench")) {
            up = MonacoBenchmark.open(client);
        } else {
            up = EditorApp.open(client);
        }
        if (!up) {
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
}
