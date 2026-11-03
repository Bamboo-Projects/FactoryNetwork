package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.BreakpointsPacket;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The breakpoints the player set in the editor's gutter, per file.
 *
 * <p>They belong to the editor session: closing the terminal takes them all
 * away, and a flow stopped at one goes on.
 */
public final class ClientBreakpoints {

    private static final Map<String, Set<Integer>> BY_FILE = new HashMap<>();

    private ClientBreakpoints() {
    }

    public static boolean has(String file, int line) {
        return file != null && BY_FILE.getOrDefault(file, Set.of()).contains(line);
    }

    /** Sets or takes away the breakpoint on this line and tells the controller. */
    public static void toggle(String file, int line) {
        if (file == null) {
            return;
        }
        Set<Integer> lines = BY_FILE.computeIfAbsent(file, key -> new TreeSet<>());
        if (!lines.remove(line)) {
            lines.add(line);
        }
        ClientPacketDistributor.sendToServer(new BreakpointsPacket(file, List.copyOf(lines)));
    }

    /** When the terminal closes: no breakpoint outlives the session. */
    public static void clear() {
        for (String file : BY_FILE.keySet()) {
            ClientPacketDistributor.sendToServer(new BreakpointsPacket(file, List.of()));
        }
        BY_FILE.clear();
    }
}
