package dev.devpanda.factorynetwork.compat.mekanism;

/**
 * Was diese Mod über Mekanism weiß, solange sie nichts davon kann.
 *
 * <p>Die Schreibweise {@code chemical:mekanism/hydrogen} steht seit dem
 * Entwurf, die Anbindung nicht (Punkt 1.4). Bis dahin gibt es genau eine
 * Aufgabe: <b>die richtige Meldung.</b>
 *
 * <p>Und die hängt an einer Frage, die vorher niemand gestellt hat: Ist
 * Mekanism überhaupt da? „Chemikalien sind noch nicht angebunden" klingt nach
 * einer Baustelle in dieser Mod — in einem Pack ohne Mekanism gibt es die
 * Chemikalien aber überhaupt nicht, und der Spieler sucht den Fehler an der
 * falschen Stelle. Das sind zwei verschiedene Auskünfte, und sie stehen hier
 * an einer Stelle, weil sie sonst an dreien stehen: im Übersetzer, in der
 * Laufzeit und in der Auflösungsanzeige des Editors.
 *
 * <p><b>Ohne geladene Modliste gilt „nicht installiert".</b> Ein Einheitstest
 * lädt kein FML, ein Datengenerator auch nicht — dieselbe Vorsicht wie bei
 * {@code FnConfig}, und dieselbe Richtung: Die Vorgabe ist die, die niemanden
 * in die Irre schickt.
 */
public final class FnMekanism {

    /** So heißt die Mod in der Modliste. */
    public static final String MOD_ID = "mekanism";

    private FnMekanism() {
    }

    /** Liegt Mekanism in diesem Pack? */
    public static boolean installed() {
        try {
            net.neoforged.fml.ModList list = net.neoforged.fml.ModList.get();
            return list != null && list.isLoaded(MOD_ID);
        } catch (Throwable outsideTheGame) {
            // Kein FML — dann ist auch kein Mekanism da.
            return false;
        }
    }

    /** Warum eine Chemikalien-Auswahl gerade nicht geht. */
    public static String reason() {
        return installed()
                ? "Chemikalien sind noch nicht angebunden."
                : "Chemikalien brauchen Mekanism.";
    }

    /** Und was der Spieler damit anfangen kann. */
    public static String hint() {
        return installed()
                ? "Die Schreibweise steht, die Anbindung an Mekanism ist in Arbeit."
                : "chemical: spricht die Chemikalien von Mekanism an, und die Mod ist "
                        + "in diesem Pack nicht installiert.";
    }
}
