package dev.devpanda.factorynetwork.compat.thaumaturge;

import org.jetbrains.annotations.Nullable;

/**
 * Thaumaturge's gate: is it there, and where is the side that knows it.
 *
 * <p>The same shape as {@code FnArs}: the class touching Thaumaturge lives in
 * the {@code thaumaturge} source set and is found through ServiceLoader, so
 * this one loads in every pack.
 */
public final class FnThaumaturge {

    public static final String MOD_ID = "thaumaturge";

    private static EssentiaBridge bridge;
    private static boolean looked;

    private FnThaumaturge() {
    }

    /** The Thaumaturge side, or {@code null} without the mod or in a build without it. */
    public static @Nullable EssentiaBridge bridge() {
        if (!looked) {
            looked = true;
            if (installed()) {
                bridge = java.util.ServiceLoader
                        .load(EssentiaBridge.class, FnThaumaturge.class.getClassLoader())
                        .findFirst().orElse(null);
            }
        }
        return bridge;
    }

    public static boolean installed() {
        try {
            net.neoforged.fml.ModList list = net.neoforged.fml.ModList.get();
            return list != null && list.isLoaded(MOD_ID);
        } catch (Throwable outsideTheGame) {
            return false;
        }
    }
}
