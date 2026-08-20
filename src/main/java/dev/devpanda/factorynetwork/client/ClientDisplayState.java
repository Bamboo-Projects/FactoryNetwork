package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.DisplayStatePacket;

import java.util.List;

/** Der letzte bekannte Stand der Anzeigen. */
public final class ClientDisplayState {

    private static List<DisplayStatePacket.Panel> panels = List.of();

    private ClientDisplayState() {
    }

    public static void accept(DisplayStatePacket packet) {
        panels = List.copyOf(packet.panels());
    }

    public static List<DisplayStatePacket.Panel> panels() {
        return panels;
    }
}
