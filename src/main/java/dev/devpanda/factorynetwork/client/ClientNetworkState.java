package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec;
import dev.devpanda.factorynetwork.network.packet.NamedPlace;
import dev.devpanda.factorynetwork.network.packet.NetworkStatePacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Was der Client über das Netzwerk weiß.
 *
 * <p>Der Editor braucht die Namen aus dem Netz für die Vervollständigung. Sie
 * hier zu halten statt bei jedem Tastendruck nachzufragen, ist der
 * Unterschied zwischen einer Vervollständigung, die sich flüssig anfühlt, und
 * einer, die hakt.
 *
 * <p>Connectoren <b>und</b> Anzeigen: Beide tragen Namen, die im Programm
 * stehen müssen, und beide sollen vorgeschlagen werden. Der Quelltext lag
 * früher auch hier; er kommt jetzt über {@code ClientProjectState}.
 */
public final class ClientNetworkState {

    private static List<NamedPlace> connectors = new ArrayList<>();
    private static List<NamedPlace> displays = new ArrayList<>();
    private static List<String> workers = new ArrayList<>();
    private static List<String> plants = new ArrayList<>();
    private static List<String> fluids = new ArrayList<>();
    private static Map<String, DeviceProfile> profiles = Map.of();

    public static void accept(NetworkStatePacket packet) {
        connectors = new ArrayList<>(packet.connectors());
        displays = new ArrayList<>(packet.displays());
        workers = new ArrayList<>(packet.workers());
        plants = new ArrayList<>(packet.plants());
        fluids = new ArrayList<>(packet.fluids());

        Map<String, DeviceProfile> received = new HashMap<>();
        for (DeviceProfileCodec.Flat flat : packet.profiles()) {
            received.put(flat.name(), DeviceProfileCodec.fromFlat(flat));
        }
        profiles = received;
    }

    /**
     * Was hinter einem Connector steht.
     *
     * <p>Nie {@code null}: Wer nach einem Namen fragt, den es nicht gibt,
     * bekommt ein Profil, das über sich nichts sagt.
     */
    public static DeviceProfile profile(String connector) {
        return profiles.getOrDefault(connector, DeviceProfile.unreachable());
    }

    /** Die Namen der Connectoren. */
    public static List<String> connectors() {
        return connectors.stream().map(NamedPlace::name).toList();
    }

    /** Die Namen der Anzeigewände im Netz — für {@code display NAME { … }}. */
    public static List<String> displays() {
        return displays.stream().map(NamedPlace::name).toList();
    }

    /**
     * Wo ein Name im Netz hängt, oder {@code null}.
     *
     * <p>Connectoren und Anzeigen zusammen: Von der Stelle im Code aus ist
     * beides dasselbe — ein Name, hinter dem ein Block in der Welt steht.
     */
    public static net.minecraft.core.BlockPos placeOf(String name) {
        for (NamedPlace place : connectors) {
            if (place.name().equals(name)) {
                return place.pos();
            }
        }
        for (NamedPlace place : displays) {
            if (place.name().equals(name)) {
                return place.pos();
            }
        }
        return null;
    }

    public static List<String> workers() {
        return List.copyOf(workers);
    }

    public static List<String> plants() {
        return List.copyOf(plants);
    }

    public static List<String> fluids() {
        return List.copyOf(fluids);
    }

    private ClientNetworkState() {
    }
}
