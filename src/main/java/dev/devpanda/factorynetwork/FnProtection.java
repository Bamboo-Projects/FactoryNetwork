package dev.devpanda.factorynetwork;

import java.util.UUID;

/**
 * Wer ein Programm ändern darf.
 *
 * <p>Bis hierher jeder: Wer an ein Terminal kam, konnte das Programm einer
 * fremden Fabrik überschreiben. Im Einzelspieler ist das richtig, auf einem
 * Server nicht — und es fiel nirgends auf, weil ein überschriebenes Programm
 * keine Meldung hinterlässt, sondern nur eine Anlage, die plötzlich etwas
 * anderes tut.
 *
 * <p><b>Standard bleibt „jeder".</b> Eine Mod, die nach einem Update
 * Fabriken sperrt, an denen zwei Leute gemeinsam bauen, hat dasselbe Problem
 * in die andere Richtung. Wer schützen will, stellt es ein — der
 * Serverbetreiber kennt seine Spieler, die Mod nicht.
 *
 * <p>Geschützt sind die zwei Wege, über die ein <b>Programm</b> geändert
 * wird: es übernehmen und den Entwurf speichern. Zusehen, Bestände lesen und
 * Knöpfe drücken bleibt allen offen — das ist Benutzen und nicht Umbauen.
 *
 * <p><b>Nicht dabei: die Beschriftungspistole.</b> Einen Connector
 * umzubenennen bricht Programme genauso, ist aber eine Handlung in der Welt
 * wie das Abbauen eines Blocks — und dafür gibt es Schutzmods, die es besser
 * können als eine Logistikmod. Was hier steht, schützt das Programm, nicht
 * das Grundstück.
 */
public final class FnProtection {

    /** Wie streng es zugeht. */
    public enum Mode {
        /** Jeder darf. Der Stand vor dieser Einstellung. */
        OFF,
        /** Nur wer den Controller gesetzt hat — und Operatoren. */
        OWNER,
        /** Nur Operatoren. Für Server, auf denen die Fabrik allen gehört. */
        OPS
    }

    private FnProtection() {
    }

    /**
     * Darf dieser Spieler das Programm dieses Controllers ändern?
     *
     * <p>Ohne Minecraft-Typen, damit die Frage prüfbar ist: Ein Rechtefehler
     * ist der eine Fehler, den man nicht ausprobieren möchte.
     *
     * @param owner wer den Controller gesetzt hat, oder {@code null}
     * @param operator ob der Spieler auf dem Server Operatorrechte hat
     */
    public static boolean mayEdit(Mode mode, UUID owner, UUID player, boolean operator) {
        if (operator) {
            return true;
        }
        return switch (mode) {
            case OFF -> true;
            // Ein Controller ohne Besitzer gehört allen: aus einer Welt von
            // vorher, oder von einem Befehl gesetzt. Ihn niemandem
            // zuzuordnen wäre eine Sperre, die niemand aufheben kann.
            case OWNER -> owner == null || owner.equals(player);
            case OPS -> false;
        };
    }
}
