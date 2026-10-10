package dev.devpanda.factorynetwork.client;

import net.neoforged.fml.ModList;

/**
 * Whether the web runtime is there at all.
 *
 * <p><b>BambooCEF is an extra, not a requirement.</b> It brings the web
 * editor and the web surfaces; the mod works without it, and the built-in
 * editor is the one every player has. On a system BambooCEF does not support,
 * or in a pack that leaves it out, nothing may reach for its classes — a
 * single touch would stop the client with a class that cannot be found.
 *
 * <p><b>So this class names none of them.</b> It asks the mod list, and
 * whoever wants the web side asks here first and then goes through
 * {@link WebBridge}, the one place that does name them.
 */
public final class WebSupport {

    /** This is the mod's name in the mod list. */
    public static final String MOD_ID = "bamboocef";

    private static Boolean installed;

    private WebSupport() {
    }

    /** Is BambooCEF present in this pack? */
    public static boolean installed() {
        Boolean known = installed;
        if (known == null) {
            known = ask();
            installed = known;
        }
        return known;
    }

    private static boolean ask() {
        try {
            ModList list = ModList.get();
            return list != null && list.isLoaded(MOD_ID);
        } catch (Throwable outsideTheGame) {
            // No mod list — then there is no web runtime either.
            return false;
        }
    }
}
