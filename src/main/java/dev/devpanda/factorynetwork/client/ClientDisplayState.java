package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.DisplayStatePacket;

import java.util.List;

/** The last known state of the displays. */
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
