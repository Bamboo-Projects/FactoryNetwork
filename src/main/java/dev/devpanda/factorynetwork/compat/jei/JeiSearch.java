package dev.devpanda.factorynetwork.compat.jei;

import mezz.jei.api.runtime.IJeiRuntime;

/**
 * JEI's search text, for the terminal's synced search modes.
 *
 * <p>Only call this where JEI is loaded; {@link #installed()} says so without
 * touching a JEI class. The runtime arrives through the plugin once JEI has
 * started and leaves again with the world.
 */
public final class JeiSearch {

    private static IJeiRuntime runtime;

    private JeiSearch() {
    }

    public static boolean installed() {
        return net.neoforged.fml.ModList.get().isLoaded("jei");
    }

    static void attach(IJeiRuntime value) {
        runtime = value;
    }

    /** JEI's current filter text, or {@code null} while JEI is not running. */
    public static String text() {
        return runtime == null ? null : runtime.getIngredientFilter().getFilterText();
    }

    public static void setText(String text) {
        if (runtime != null && !text.equals(runtime.getIngredientFilter().getFilterText())) {
            runtime.getIngredientFilter().setFilterText(text);
        }
    }
}
