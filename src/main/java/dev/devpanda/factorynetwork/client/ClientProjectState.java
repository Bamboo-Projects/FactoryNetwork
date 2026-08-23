package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.network.packet.ProjectStatePacket;

/**
 * Das Projekt am Client — was der Server sagt, und was gerade bearbeitet wird.
 *
 * <p><b>Zwei Stände, weil es zwei Wahrheiten gibt.</b> {@link #deployed()} ist
 * das Programm, das im Controller läuft. {@link #draft()} ist das, was im
 * Editor steht. Solange beide gleich sind, ist nichts offen; sobald sie
 * auseinandergehen, hat der Spieler ungesicherte Arbeit.
 *
 * <p><b>Der Entwurf liegt hier und nicht im Bildschirm.</b> Vorher hielt ihn
 * die Ansicht des Code-Reiters, und die wird bei jedem {@code init} neu
 * gebaut — beim Wechsel zwischen Terminal und großem Editorfenster wäre jede
 * ungesicherte Zeile weg gewesen.
 *
 * <p>Was hier noch fehlt, steht bewusst offen: Der Entwurf lebt nur, solange
 * der Client läuft. Ein Absturz oder ein Verlassen der Welt nimmt ihn mit.
 * Ihn auf dem Server zu halten ist Serverarbeit und ein eigener Schritt.
 */
public final class ClientProjectState {

    private static Project deployed = Project.of("");
    private static Project draft = deployed;

    private ClientProjectState() {
    }

    /**
     * Der Server meldet, was im Controller steht.
     *
     * <p>Der Entwurf zieht nur mit, wenn keine ungesicherte Arbeit darin
     * steht. Sonst gewinnt, was der Spieler getippt hat — eine Meldung, die
     * einem gerade Schreibenden den Text unter den Fingern austauscht, wäre
     * die schlimmere Antwort. Beim Übernehmen zeigt sich der Unterschied.
     */
    public static void accept(ProjectStatePacket packet) {
        boolean clean = !isDirty();
        deployed = packet.project();
        if (clean) {
            draft = deployed;
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
        draft = project;
    }

    /** Steht im Editor etwas anderes als im Controller? */
    public static boolean isDirty() {
        return !draft.files().equals(deployed.files());
    }
}
