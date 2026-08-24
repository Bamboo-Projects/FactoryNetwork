package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.DeviceSnapshotPacket;
import dev.devpanda.factorynetwork.network.packet.DeviceSnapshotRequestPacket;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Was der Client über den Inhalt der Geräte weiß.
 *
 * <p><b>Erst nach einer Viertelsekunde fragen.</b> Sonst schickt jede
 * Mausbewegung über eine Zeile mit drei Namen drei Anfragen. Bis die Antwort
 * da ist, steht im Tooltip, was ohnehin bekannt ist — Identität, Seiten,
 * Fächerzahl.
 *
 * <p>Die Antwort bleibt bis zum Schließen des Terminals liegen, wird beim
 * erneuten Zeigen aber neu geholt: Ein zweites Mal hinsehen heißt meistens
 * nachsehen, ob sich etwas geändert hat.
 */
public final class ClientDeviceState {

    /** So lange muss der Zeiger stillhalten, in Millisekunden. */
    private static final long DELAY = 250;

    private static final Map<String, DeviceSnapshotPacket> snapshots = new HashMap<>();

    private static String pending = "";
    private static long since;

    private ClientDeviceState() {
    }

    /**
     * Meldet, dass der Zeiger auf diesem Gerät steht.
     *
     * <p>Wird bei jedem Bild aufgerufen; die Anfrage geht erst, wenn der
     * Zeiger lange genug stillhält.
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

    /** Der Zeiger steht auf keinem Gerät mehr. */
    public static void notHovering() {
        pending = "";
        since = 0;
    }

    /** Was zuletzt über ein Gerät ankam, oder {@code null}. */
    public static DeviceSnapshotPacket snapshot(String connector) {
        return snapshots.get(connector);
    }

    public static void accept(DeviceSnapshotPacket packet) {
        snapshots.put(packet.connector(), packet);
    }

    /** Beim Schließen des Terminals: Der Inhalt von vorhin gilt nicht mehr. */
    public static void clear() {
        snapshots.clear();
        notHovering();
    }
}
