package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.network.packet.ProjectStatePacket;
import dev.devpanda.factorynetwork.network.packet.SaveDraftPacket;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The project on the client — what runs, and what is currently being edited.
 *
 * <p><b>Two states, because there are two truths.</b> {@link #deployed()} is
 * the program in the controller. {@link #draft()} is what stands in the
 * editor. As long as both are equal, nothing is pending.
 *
 * <p><b>The draft goes to the server.</b> Before, it lived only here: a crash,
 * a kick, an accidental leave from the world — and half an hour of work was
 * gone. Deploying would have saved it, but deploying only works with
 * error-free code, and in the middle of a change it never is.
 *
 * <p>It is sent one second after the last keystroke, not on every one. A
 * keystroke is one packet, a second of typing is five to ten — and no one
 * minds losing a second. Ctrl+S sends immediately, and so does closing the
 * window.
 *
 * <p><b>The draft lives here and not in the screen.</b> The views are rebuilt
 * on every {@code init}; when switching between the terminal and the large
 * editor window every unsaved line would otherwise be gone.
 */
public final class ClientProjectState {

    /** How long to wait after the last keystroke, in ticks. */
    private static final int QUIET_TICKS = 20;

    private static Project deployed = Project.of("");
    private static Project draft = deployed;

    /** Where the controller stands whose project this is. */
    private static BlockPos controller;

    /**
     * Which file someone else is currently editing, and who.
     *
     * <p>One's own are not in it — that you are writing yourself is not news.
     */
    private static java.util.Map<String, String> locks = java.util.Map.of();

    /** What last went to the server — whether anything is pending hangs on it. */
    private static Project sent = deployed;

    /** How many ticks have passed since the last change. */
    private static int quiet;

    private ClientProjectState() {
    }

    /**
     * The server reports what stands in the controller.
     *
     * <p>The draft only moves along if nothing unsaved lies here. Otherwise
     * what the player typed wins — a message that swaps out the text under the
     * fingers of someone who is writing right now would be the worse answer.
     *
     * <p>With two players at one controller the file lock takes hold as well:
     * the server only accepts what the sender is allowed to hold, and reports
     * in {@code locks} what someone else holds. The second one sees the other
     * player's file, can read it and not change it.
     */
    public static void accept(ProjectStatePacket packet) {
        controller = packet.controller();
        locks = java.util.Map.copyOf(packet.locks());
        deployed = packet.project();
        if (!isDirty()) {
            draft = packet.draftProject();
            sent = draft;
        }
    }

    /** What runs in the controller. */
    public static Project deployed() {
        return deployed;
    }

    /** What stands in the editor. */
    public static Project draft() {
        return draft;
    }

    public static void setDraft(Project project) {
        if (project.files().equals(draft.files())) {
            return;
        }
        draft = project;
        quiet = 0;
    }

    /** Who holds this file, or {@code null}. */
    public static String heldBy(String file) {
        return locks.get(file);
    }

    /** Does something other than in the controller stand in the editor? */
    public static boolean isDeployed() {
        return draft.files().equals(deployed.files());
    }

    /** Is there something here that the server does not have yet? */
    public static boolean isDirty() {
        return !draft.files().equals(sent.files());
    }

    /**
     * Counts the quiet after the last keystroke and then saves.
     *
     * <p>Called from the client tick, not from the screen: it keeps running
     * even when someone closes the window in the same second in which they
     * typed the last character.
     */
    public static void tick() {
        if (!isDirty() || controller == null) {
            return;
        }
        if (++quiet < QUIET_TICKS) {
            return;
        }
        flush();
    }

    /** Sends immediately whatever is pending. */
    public static void flush() {
        if (!isDirty() || controller == null) {
            return;
        }
        PacketDistributor.sendToServer(SaveDraftPacket.of(controller, draft));
        sent = draft;
        quiet = 0;
    }

    /**
     * Forgets everything.
     *
     * <p>When leaving a world: the next controller is a different one, and its
     * project must not begin with the last one.
     */
    public static void clear() {
        deployed = Project.of("");
        draft = deployed;
        sent = deployed;
        controller = null;
        locks = java.util.Map.of();
        quiet = 0;
    }
}
