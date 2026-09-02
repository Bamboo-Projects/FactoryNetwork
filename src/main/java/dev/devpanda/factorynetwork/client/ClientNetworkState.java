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
 * What the client knows about the network.
 *
 * <p>The editor needs the names from the network for completion. Keeping them
 * here instead of asking again on every keystroke is the difference between a
 * completion that feels fluid and one that stutters.
 *
 * <p>Connectors <b>and</b> displays: both carry names that have to appear in
 * the program, and both should be suggested. The source used to live here too;
 * it now comes through {@code ClientProjectState}.
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
     * What stands behind a connector.
     *
     * <p>Never {@code null}: whoever asks for a name that does not exist gets
     * a profile that says nothing about itself.
     */
    public static DeviceProfile profile(String connector) {
        return profiles.getOrDefault(connector, DeviceProfile.unreachable());
    }

    /**
     * A profile from an answer to a request.
     *
     * <p><b>Otherwise two pieces of information would contradict each other.</b>
     * The answer carries the fresh state with it; if that were available only
     * on hover, the tooltip would say "tank" after a machine swap while the
     * warning in the line still reckons with the chest. Stored here, the
     * suggestion list and the check see the same thing.
     */
    public static void updateProfile(String connector, DeviceProfile profile) {
        Map<String, DeviceProfile> next = new HashMap<>(profiles);
        next.put(connector, profile);
        profiles = next;
    }

    /** The names of the connectors. */
    public static List<String> connectors() {
        return connectors.stream().map(NamedPlace::name).toList();
    }

    /** The names of the display walls in the network — for {@code display NAME { … }}. */
    public static List<String> displays() {
        return displays.stream().map(NamedPlace::name).toList();
    }

    /**
     * Where a name hangs in the network, or {@code null}.
     *
     * <p>Connectors and displays together: from the spot in the code both are
     * the same — a name behind which a block stands in the world.
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
