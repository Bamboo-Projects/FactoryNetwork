package dev.devpanda.factorynetwork.compat.ars;

/**
 * Was diese Mod über Ars Nouveau weiß.
 *
 * <p>Dieselbe Tür wie {@code FnMekanism}, aus demselben Grund: Ohne die Mod
 * gibt es Source überhaupt nicht, und „Source ist noch nicht angebunden"
 * schickte den Spieler an die falsche Stelle.
 *
 * <p><b>Ohne geladene Modliste gilt „nicht installiert".</b> Ein Einheitstest
 * lädt kein FML — die Vorgabe ist die, die niemanden in die Irre schickt.
 */
public final class FnArs {

    /** So heißt die Mod in der Modliste. */
    public static final String MOD_ID = "ars_nouveau";

    private FnArs() {
    }

    /** Liegt Ars Nouveau in diesem Pack? */
    public static boolean installed() {
        try {
            net.neoforged.fml.ModList list = net.neoforged.fml.ModList.get();
            return list != null && list.isLoaded(MOD_ID);
        } catch (Throwable outsideTheGame) {
            return false;
        }
    }
}
