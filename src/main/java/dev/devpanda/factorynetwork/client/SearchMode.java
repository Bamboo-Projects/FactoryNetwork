package dev.devpanda.factorynetwork.client;

import net.minecraft.network.chat.Component;

/**
 * How the storage search behaves, as in Applied Energistics.
 *
 * <p>Two independent questions: does the search field take the keyboard as
 * soon as the terminal opens, and does it share its text with JEI's search
 * in both directions. The four modes are their combinations, cycled by one
 * button.
 */
public enum SearchMode {

    AUTO_FOCUS("auto", true, false),
    MANUAL("manual", false, false),
    AUTO_FOCUS_JEI("auto_jei", true, true),
    MANUAL_JEI("manual_jei", false, true);

    private final String key;
    private final boolean autoFocus;
    private final boolean jeiSync;

    SearchMode(String key, boolean autoFocus, boolean jeiSync) {
        this.key = key;
        this.autoFocus = autoFocus;
        this.jeiSync = jeiSync;
    }

    public boolean autoFocus() {
        return autoFocus;
    }

    public boolean jeiSync() {
        return jeiSync;
    }

    public Component title() {
        return Component.translatable("screen.factorynetwork.terminal.search_mode." + key);
    }

    public SearchMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
