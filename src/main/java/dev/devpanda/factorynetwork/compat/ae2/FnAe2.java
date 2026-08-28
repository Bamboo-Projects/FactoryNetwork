package dev.devpanda.factorynetwork.compat.ae2;

import net.neoforged.fml.ModList;

/**
 * Ob Applied Energistics 2 im Pack liegt.
 *
 * <p>Dieselbe Vorsicht wie bei {@code FnMekanism}: Ohne geladene Modliste
 * gilt „nicht installiert". Ein Einheitstest lädt kein FML, ein
 * Datengenerator auch nicht — und die Vorgabe ist die, die niemanden in die
 * Irre schickt.
 */
public final class FnAe2 {

    public static final String MOD_ID = "ae2";

    public static boolean installed() {
        ModList list = ModList.get();
        return list != null && list.isLoaded(MOD_ID);
    }

    private FnAe2() {
    }
}
