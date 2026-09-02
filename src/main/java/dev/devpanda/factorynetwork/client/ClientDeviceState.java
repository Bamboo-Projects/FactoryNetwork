package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.DeviceSnapshotPacket;
import dev.devpanda.factorynetwork.network.packet.DeviceSnapshotRequestPacket;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * What the client knows about the contents of the devices.
 *
 * <p><b>Only ask after a quarter second.</b> Otherwise every mouse movement
 * over a line with three names sends three requests. Until the answer is
 * there, the tooltip shows what is known anyway — identity, sides, slot count.
 *
 * <p>The answer stays around until the terminal is closed, but is fetched
 * anew on the next hover: looking a second time usually means checking whether
 * something has changed.
 */
public final class ClientDeviceState {

    /** How long the pointer must hold still, in milliseconds. */
    private static final long DELAY = 250;

    private static final Map<String, DeviceSnapshotPacket> snapshots = new HashMap<>();

    private static String pending = "";
    private static long since;

    private ClientDeviceState() {
    }

    /**
     * Reports that the pointer is over this device.
     *
     * <p>Called every frame; the request only goes out once the pointer holds
     * still long enough.
     */
    public static void hovering(String connector) {
        long now = System.currentTimeMillis();
        if (!connector.equals(pending)) {
            pending = connector;
            since = now;
            return;
        }
        if (since > 0 && now - since >= DELAY) {
            since = 0;
            PacketDistributor.sendToServer(new DeviceSnapshotRequestPacket(connector));
        }
    }

    /** The pointer is no longer over any device. */
    public static void notHovering() {
        pending = "";
        since = 0;
    }

    /** What last arrived about a device, or {@code null}. */
    public static DeviceSnapshotPacket snapshot(String connector) {
        return snapshots.get(connector);
    }

    public static void accept(DeviceSnapshotPacket packet) {
        snapshots.put(packet.connector(), packet);
        // The profile from the answer is fresher than the one from opening. It
        // belongs not only in the hover, but also where the suggestion list
        // and the check read it — otherwise two places claim different things
        // about the same device.
        ClientNetworkState.updateProfile(packet.connector(),
                dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec
                        .fromFlat(packet.profile()));
    }

    /** When the terminal is closed: the earlier contents no longer hold. */
    public static void clear() {
        snapshots.clear();
        notHovering();
    }
}
