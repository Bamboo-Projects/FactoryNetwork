package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.network.packet.ProjectStatePacket;

/**
 * Das Projekt, wie der Server es zuletzt gemeldet hat.
 *
 * <p>Der Ausgangspunkt für den Editor. Was der Spieler danach tippt, steht
 * nur im Editor — bis er übernimmt.
 */
public final class ClientProjectState {

    private static Project project = Project.of("");

    private ClientProjectState() {
    }

    public static void accept(ProjectStatePacket packet) {
        project = packet.project();
    }

    public static Project project() {
        return project;
    }
}
