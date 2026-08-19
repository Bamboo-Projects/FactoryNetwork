package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.NetworkStatePacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Was der Client über das Netzwerk weiß.
 *
 * <p>Der Editor braucht die Connectornamen für die Vervollständigung. Sie hier
 * zu halten statt bei jedem Tastendruck nachzufragen, ist der Unterschied
 * zwischen einer Vervollständigung, die sich flüssig anfühlt, und einer, die
 * hakt.
 */
public final class ClientNetworkState {

    private static String source = "";
    private static List<String> connectors = new ArrayList<>();
    private static List<String> workers = new ArrayList<>();

    public static void accept(NetworkStatePacket packet) {
        source = packet.source();
        connectors = new ArrayList<>(packet.connectors());
        workers = new ArrayList<>(packet.workers());
    }

    public static String source() {
        return source;
    }

    public static List<String> connectors() {
        return List.copyOf(connectors);
    }

    public static List<String> workers() {
        return List.copyOf(workers);
    }

    private ClientNetworkState() {
    }
}
