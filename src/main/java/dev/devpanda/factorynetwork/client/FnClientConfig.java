package dev.devpanda.factorynetwork.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings that belong to the player, not to the server.
 *
 * <p>Registered from the common constructor; it touches no client classes,
 * and a dedicated server simply never loads a client config.
 */
public final class FnClientConfig {

    private static final ModConfigSpec.EnumValue<SearchMode> SEARCH_MODE;
    private static final ModConfigSpec.BooleanValue REMEMBER_SEARCH;
    private static final ModConfigSpec.BooleanValue SHOW_SLOT_NUMBERS;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("terminal");
        SEARCH_MODE = builder
                .comment("Wie sich die Suche im Lager verhält: ob sie beim Öffnen die",
                        "Tastatur bekommt und ob sie ihren Text mit JEI teilt.",
                        "Wird auch über den Knopf neben der Suche umgeschaltet.")
                .defineEnum("searchMode", SearchMode.AUTO_FOCUS);
        REMEMBER_SEARCH = builder
                .comment("Ob die Suche beim nächsten Öffnen des Terminals noch dasteht.")
                .define("rememberSearch", true);
        builder.pop();
        builder.push("blockGuis");
        SHOW_SLOT_NUMBERS = builder
                .comment("Ob fremde Block-GUIs die Fachnummern für slots(n) zeigen.",
                        "Wird über den Knopf # über der GUI oder mit Strg+I umgeschaltet.")
                .define("showSlotNumbers", false);
        builder.pop();
        SPEC = builder.build();
    }

    private FnClientConfig() {
    }

    public static SearchMode searchMode() {
        return SPEC.isLoaded() ? SEARCH_MODE.get() : SearchMode.AUTO_FOCUS;
    }

    public static void setSearchMode(SearchMode mode) {
        if (SPEC.isLoaded()) {
            SEARCH_MODE.set(mode);
            SPEC.save();
        }
    }

    public static boolean showSlotNumbers() {
        return SPEC.isLoaded() && SHOW_SLOT_NUMBERS.get();
    }

    public static void setShowSlotNumbers(boolean show) {
        if (SPEC.isLoaded()) {
            SHOW_SLOT_NUMBERS.set(show);
            SPEC.save();
        }
    }

    public static boolean rememberSearch() {
        return !SPEC.isLoaded() || REMEMBER_SEARCH.get();
    }
}
