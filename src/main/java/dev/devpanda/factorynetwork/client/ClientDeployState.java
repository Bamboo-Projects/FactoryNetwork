package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.DeployResultPacket;

/**
 * Wie das letzte Übernehmen ausgegangen ist.
 *
 * <p>Nur zum Anzeigen, und nur bis zum nächsten Anschlag: Sobald jemand
 * weitertippt, ist die Meldung von vorhin nicht mehr wahr, und eine
 * stehengebliebene Erfolgsmeldung über geändertem Code ist schlimmer als
 * keine.
 */
public final class ClientDeployState {

    private static boolean accepted;
    private static String message = "";

    private ClientDeployState() {
    }

    public static void accept(DeployResultPacket packet) {
        accepted = packet.accepted();
        message = packet.message();
    }

    /** Vergisst die Meldung — beim ersten Anschlag nach dem Übernehmen. */
    public static void clear() {
        message = "";
    }

    public static boolean hasMessage() {
        return !message.isEmpty();
    }

    public static boolean wasAccepted() {
        return accepted;
    }

    public static String message() {
        return message;
    }
}
