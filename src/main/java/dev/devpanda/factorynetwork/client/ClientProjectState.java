package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.network.packet.ProjectStatePacket;
import dev.devpanda.factorynetwork.network.packet.SaveDraftPacket;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Das Projekt am Client — was läuft, und was gerade bearbeitet wird.
 *
 * <p><b>Zwei Stände, weil es zwei Wahrheiten gibt.</b> {@link #deployed()}
 * ist das Programm im Controller. {@link #draft()} ist das, was im Editor
 * steht. Solange beide gleich sind, ist nichts offen.
 *
 * <p><b>Der Entwurf geht zum Server.</b> Vorher lebte er nur hier: Ein
 * Absturz, ein Kick, ein versehentliches Verlassen der Welt — und eine halbe
 * Stunde Arbeit war weg. Übernehmen hätte sie gesichert, aber Übernehmen geht
 * nur bei fehlerfreiem Code, und mitten in einer Änderung ist er das nie.
 *
 * <p>Geschickt wird eine Sekunde nach dem letzten Anschlag, nicht bei jedem.
 * Ein Anschlag ist ein Paket, eine Sekunde Tippen sind fünf bis zehn — und
 * niemand verliert eine Sekunde ungern. Strg+S schickt sofort, und das
 * Schließen des Fensters auch.
 *
 * <p><b>Der Entwurf liegt hier und nicht im Bildschirm.</b> Die Ansichten
 * werden bei jedem {@code init} neu gebaut; beim Wechsel zwischen Terminal
 * und großem Editorfenster wäre sonst jede ungesicherte Zeile weg.
 */
public final class ClientProjectState {

    /** So lange nach dem letzten Anschlag wird gewartet, in Ticks. */
    private static final int QUIET_TICKS = 20;

    private static Project deployed = Project.of("");
    private static Project draft = deployed;

    /** Wo der Controller steht, dessen Projekt das ist. */
    private static BlockPos controller;

    /** Was zuletzt zum Server ging — daran hängt, ob etwas offen ist. */
    private static Project sent = deployed;

    /** Wie viele Ticks seit der letzten Änderung vergangen sind. */
    private static int quiet;

    private ClientProjectState() {
    }

    /**
     * Der Server meldet, was im Controller steht.
     *
     * <p>Der Entwurf zieht nur mit, wenn hier nichts Ungesichertes liegt.
     * Sonst gewinnt, was der Spieler getippt hat — eine Meldung, die einem
     * gerade Schreibenden den Text unter den Fingern austauscht, wäre die
     * schlimmere Antwort.
     *
     * <p>Damit ist auch gesagt, was bei zwei Spielern an einem Controller
     * passiert: Der zweite sieht den Entwurf des ersten, solange er selbst
     * nicht tippt. Sobald beide tippen, gewinnt der, der zuletzt übernimmt.
     * Eine Dateisperre wäre die richtige Antwort und steht noch aus.
     */
    public static void accept(ProjectStatePacket packet) {
        controller = packet.controller();
        deployed = packet.project();
        if (!isDirty()) {
            draft = packet.draftProject();
            sent = draft;
        }
    }

    /** Was im Controller läuft. */
    public static Project deployed() {
        return deployed;
    }

    /** Was im Editor steht. */
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

    /** Steht im Editor etwas anderes als im Controller? */
    public static boolean isDeployed() {
        return draft.files().equals(deployed.files());
    }

    /** Liegt hier etwas, das der Server noch nicht hat? */
    public static boolean isDirty() {
        return !draft.files().equals(sent.files());
    }

    /**
     * Zählt die Ruhe nach dem letzten Anschlag und sichert dann.
     *
     * <p>Vom Client-Tick aufgerufen, nicht vom Bildschirm: Er läuft auch
     * weiter, wenn jemand das Fenster in derselben Sekunde zumacht, in der
     * er das letzte Zeichen getippt hat.
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

    /** Schickt sofort, was offen ist. */
    public static void flush() {
        if (!isDirty() || controller == null) {
            return;
        }
        PacketDistributor.sendToServer(SaveDraftPacket.of(controller, draft));
        sent = draft;
        quiet = 0;
    }

    /**
     * Vergisst alles.
     *
     * <p>Beim Verlassen einer Welt: Der nächste Controller ist ein anderer,
     * und sein Projekt darf nicht mit dem letzten anfangen.
     */
    public static void clear() {
        deployed = Project.of("");
        draft = deployed;
        sent = deployed;
        controller = null;
        quiet = 0;
    }
}
