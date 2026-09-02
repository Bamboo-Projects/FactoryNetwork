package dev.devpanda.factorynetwork.compat.mekanism;

/**
 * What this mod knows about Mekanism, as long as it can do nothing with it.
 *
 * <p>The notation {@code chemical:mekanism/hydrogen} has been settled since
 * the draft, the integration has not (point 1.4). Until then there is exactly
 * one task: <b>the right message.</b>
 *
 * <p>And that hinges on a question no one had asked before: is Mekanism
 * present at all? "Chemicals are not wired up yet" sounds like a construction
 * site in this mod — but in a pack without Mekanism the chemicals don't exist
 * at all, and the player looks for the fault in the wrong place. These are two
 * different pieces of information, and they live here in one place because
 * otherwise they would live in three: in the compiler, in the runtime, and in
 * the editor's resolution display.
 *
 * <p><b>With no mod list loaded, treat it as "not installed".</b> A unit test
 * loads no FML, and neither does a data generator — the same caution as with
 * {@code FnConfig}, and the same direction: the default is the one that
 * misleads no one.
 */
public final class FnMekanism {

    /** This is the mod's name in the mod list. */
    public static final String MOD_ID = "mekanism";

    private FnMekanism() {
    }

    /** Is Mekanism present in this pack? */
    public static boolean installed() {
        try {
            net.neoforged.fml.ModList list = net.neoforged.fml.ModList.get();
            return list != null && list.isLoaded(MOD_ID);
        } catch (Throwable outsideTheGame) {
            // No FML — then there's no Mekanism either.
            return false;
        }
    }

    /** Why a chemical selection doesn't work right now. */
    public static String reason() {
        return installed()
                ? "Chemikalien sind noch nicht angebunden."
                : "Chemikalien brauchen Mekanism.";
    }

    /** And what the player can do about it. */
    public static String hint() {
        return installed()
                ? "Die Schreibweise steht, die Anbindung an Mekanism ist in Arbeit."
                : "chemical: spricht die Chemikalien von Mekanism an, und die Mod ist "
                        + "in diesem Pack nicht installiert.";
    }
}
