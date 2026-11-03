package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.EvaluatePacket;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The current value of an expression under the mouse in the editor.
 *
 * <p>Asked like the device snapshot: only once the pointer has rested a
 * quarter second, and again at most once a second while it stays — a count
 * that changes while you look is the point of looking.
 */
public final class LiveValues {

    private static final long DELAY = 250;
    private static final long REFRESH = 1000;

    private record Known(String value, long at) {
    }

    private static final Map<String, Known> known = new HashMap<>();
    /** Who waits for an answer outside the Java editor — the web editor. */
    private static final Map<String, Consumer<String>> waiting = new HashMap<>();

    private static String pending = "";
    private static long since;
    private static long asked;

    private LiveValues() {
    }

    /**
     * The value to show for this expression, or {@code null} while none is
     * known. Called every frame while the pointer is over it.
     */
    public static String hovering(String expression) {
        long now = System.currentTimeMillis();
        if (!expression.equals(pending)) {
            pending = expression;
            since = now;
        } else if (now - since >= DELAY && now - asked >= REFRESH) {
            asked = now;
            ClientPacketDistributor.sendToServer(new EvaluatePacket(expression));
        }
        Known value = known.get(expression);
        return value == null ? null : value.value();
    }

    /** Asks once, for the web editor; the answer goes to {@code answer}. */
    public static void ask(String expression, Consumer<String> answer) {
        waiting.put(expression, answer);
        ClientPacketDistributor.sendToServer(new EvaluatePacket(expression));
    }

    public static void accept(String expression, String value) {
        known.put(expression, new Known(value, System.currentTimeMillis()));
        Consumer<String> answer = waiting.remove(expression);
        if (answer != null) {
            answer.accept(value);
        }
    }

    /** When the terminal closes, the values no longer hold. */
    public static void clear() {
        known.clear();
        waiting.clear();
        pending = "";
    }
}
