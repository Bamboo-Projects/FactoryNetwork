package dev.devpanda.factorynetwork.compat.ars;

/**
 * What this mod knows about Ars Nouveau.
 *
 * <p>The same door as {@code FnMekanism}, for the same reason: without the mod
 * there is no Source at all, and "Source is not wired up yet" would send the
 * player to the wrong place.
 *
 * <p><b>With no mod list loaded, treat it as "not installed".</b> A unit test
 * loads no FML — the default is the one that misleads no one.
 */
public final class FnArs {

    /** This is the mod's name in the mod list. */
    public static final String MOD_ID = "ars_nouveau";

    private FnArs() {
    }

    /** Is Ars Nouveau present in this pack? */
    public static boolean installed() {
        try {
            net.neoforged.fml.ModList list = net.neoforged.fml.ModList.get();
            return list != null && list.isLoaded(MOD_ID);
        } catch (Throwable outsideTheGame) {
            return false;
        }
    }
}
