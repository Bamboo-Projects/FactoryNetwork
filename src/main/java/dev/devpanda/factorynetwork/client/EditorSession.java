package dev.devpanda.factorynetwork.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.devpanda.bamboocef.web.api.WebSurface;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.network.packet.DeployProgramPacket;
import dev.devpanda.factorynetwork.terminal.TerminalTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/** One web editor, bound to the same open menu that authorizes the native editor. */
final class EditorSession {
    private static final Gson JSON = new Gson();
    private final Minecraft client;
    private final Screen parent;
    private final Screen screen;
    private final TerminalMenu menu;
    private final BlockPos terminal;
    private final WebSurface surface;
    private long revision;
    private int ticks;
    private boolean ready;
    private boolean deployPending;
    private boolean closed;
    private String lastState = "";
    private Project checkedProject;
    private long checkedNetwork = -1;
    private java.util.List<Diagnostic> problems = java.util.List.of();

    EditorSession(Minecraft client, Screen parent, TerminalMenu menu, WebSurface surface) {
        this.client = client;
        this.parent = parent;
        this.screen = client.screen;
        this.menu = menu;
        this.terminal = menu.position();
        this.surface = surface;
        surface.onMessage(this::receive);
    }

    private boolean connected() {
        return client.level != null && client.player != null
                && client.player.containerMenu == menu && menu.allows(TerminalTab.CODE);
    }

    private void receive(String message) {
        if (closed || !surface.alive() || !connected()) return;
        try {
            if (message.length() > 2 * 1024 * 1024) {
                throw new IllegalArgumentException("Die Editor-Nachricht ist zu groß.");
            }
            JsonObject request = JsonParser.parseString(message).getAsJsonObject();
            String type = request.get("type").getAsString();
            switch (type) {
                case "ready" -> { ready = true; publish(true); }
                case "change" -> {
                    long incoming = request.get("revision").getAsLong();
                    if (incoming <= revision) return;
                    // Echo rejected revisions too, so the page can restore the authoritative draft.
                    revision = incoming;
                    Project next = EditorProtocol.readProject(request.getAsJsonObject("files"),
                            ClientProjectState.draft(), ClientProjectState.locks());
                    ClientProjectState.setDraft(next);
                    ClientDeployState.clear();
                    deployPending = false;
                    ticks = 0;
                }
                case "check" -> publish(true);
                case "save" -> { ClientProjectState.flush(); publish(true); }
                case "deploy" -> {
                    if (deployPending) return;
                    var parsed = ClientProjectState.draft().parse(ClientNetworkView.INSTANCE);
                    if (parsed.hasErrors()) {
                        throw new IllegalArgumentException("Bitte die Compilerfehler vor dem Übernehmen beheben.");
                    }
                    ClientProjectState.flush();
                    ClientDeployState.clear();
                    deployPending = true;
                    PacketDistributor.sendToServer(DeployProgramPacket.of(terminal, ClientProjectState.draft()));
                    publish(true);
                }
                case "close" -> close(true);
                default -> throw new IllegalArgumentException("Unbekannte Editor-Aktion.");
            }
        } catch (RuntimeException invalid) {
            JsonObject error = new JsonObject();
            error.addProperty("type", "error");
            error.addProperty("revision", revision);
            error.addProperty("message", invalid.getMessage() == null ? "Ungültige Editor-Nachricht." : invalid.getMessage());
            surface.postMessage(JSON.toJson(error));
            publish(true);
        }
    }

    boolean tick() {
        if (closed) return false;
        if (!connected()) {
            publish(true);
            close(false);
            if (client.screen == screen) client.setScreen(null);
            return false;
        }
        if (!surface.alive() || client.screen != screen) {
            close(client.screen == null);
            return false;
        }
        if (ClientDeployState.hasMessage()) deployPending = false;
        if (++ticks >= 5) {
            ticks = 0;
            publish(false);
        }
        return true;
    }

    private void publish(boolean force) {
        if (!ready || closed || !surface.alive()) return;
        Project draft = ClientProjectState.draft();
        JsonObject state = new JsonObject();
        state.addProperty("type", "state");
        state.addProperty("revision", revision);
        state.addProperty("connected", connected());
        state.add("files", JSON.toJsonTree(draft.files()));
        state.add("locks", JSON.toJsonTree(ClientProjectState.locks()));
        state.addProperty("dirty", ClientProjectState.isDirty());
        state.addProperty("awaitingSave", ClientProjectState.awaitingSave());
        state.addProperty("saveError", ClientProjectState.saveError());
        state.addProperty("deployed", ClientProjectState.isDeployed());
        state.addProperty("deployPending", deployPending);
        JsonObject deploy = new JsonObject();
        deploy.addProperty("accepted", ClientDeployState.wasAccepted());
        deploy.addProperty("message", ClientDeployState.message());
        state.add("deploy", deploy);
        state.add("connectors", JSON.toJsonTree(ClientNetworkState.connectors()));
        state.add("displays", JSON.toJsonTree(ClientNetworkState.displays()));
        state.add("workers", JSON.toJsonTree(ClientNetworkState.workers()));
        state.addProperty("status", !ClientProjectState.saveError().isEmpty() ? ClientProjectState.saveError()
                : ClientProjectState.awaitingSave() ? "Warte auf Speicherbestätigung …"
                : ClientProjectState.isDirty() ? "Entwurf wird gespeichert …"
                : ClientProjectState.isDeployed() ? "Dieser Stand läuft im Netzwerk" : "Entwurf gespeichert · noch nicht übernommen");
        JsonArray diagnostics = new JsonArray();
        if (!draft.equals(checkedProject) || checkedNetwork != ClientNetworkState.revision()) {
            problems = draft.parse(ClientNetworkView.INSTANCE).diagnostics();
            checkedProject = draft;
            checkedNetwork = ClientNetworkState.revision();
        }
        for (Diagnostic diagnostic : problems) {
            JsonObject item = new JsonObject();
            item.addProperty("file", diagnostic.file());
            item.addProperty("start", diagnostic.span().start());
            item.addProperty("end", diagnostic.span().end());
            item.addProperty("line", diagnostic.span().line());
            item.addProperty("column", diagnostic.span().column());
            item.addProperty("severity", diagnostic.isError() ? "error" : "warning");
            item.addProperty("message", diagnostic.message());
            item.addProperty("hint", diagnostic.hint());
            diagnostics.add(item);
        }
        state.add("diagnostics", diagnostics);
        String encoded = JSON.toJson(state);
        if (force || !encoded.equals(lastState)) {
            surface.postMessage(encoded);
            lastState = encoded;
        }
    }

    void close(boolean restoreParent) {
        if (closed) return;
        boolean canRestore = connected();
        if (canRestore) ClientProjectState.flush();
        closed = true;
        surface.onMessage(null);
        surface.close();
        if (restoreParent && canRestore) client.setScreen(parent);
    }
}
