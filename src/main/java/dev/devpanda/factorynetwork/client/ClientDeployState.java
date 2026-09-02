package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.DeployResultPacket;

/**
 * How the last deploy turned out.
 *
 * <p>For display only, and only until the next keystroke: as soon as someone
 * keeps typing, the earlier message is no longer true, and a leftover success
 * message about changed code is worse than none.
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

    /** Forgets the message — at the first keystroke after the deploy. */
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
