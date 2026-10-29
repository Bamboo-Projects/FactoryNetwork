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

    private static SourceBridge bridge;
    private static boolean looked;

    /**
     * The Ars Nouveau side, or {@code null}.
     *
     * <p>Null without the mod, and null where this build carries no Ars side
     * at all; see {@code ChemicalBridge} for why that can happen.
     */
    public static SourceBridge bridge() {
        if (!looked) {
            looked = true;
            if (installed()) {
                bridge = java.util.ServiceLoader
                        .load(SourceBridge.class, FnArs.class.getClassLoader())
                        .findFirst().orElse(null);
            }
        }
        return bridge;
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
