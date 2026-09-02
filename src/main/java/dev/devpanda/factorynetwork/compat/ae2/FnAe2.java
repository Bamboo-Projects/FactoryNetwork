package dev.devpanda.factorynetwork.compat.ae2;

import net.neoforged.fml.ModList;

/**
 * Whether Applied Energistics 2 is present in the pack.
 *
 * <p>The same caution as with {@code FnMekanism}: with no mod list loaded,
 * treat it as "not installed". A unit test loads no FML, and neither does a
 * data generator — and the default is the one that misleads no one.
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
