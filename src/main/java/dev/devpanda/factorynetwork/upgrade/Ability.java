package dev.devpanda.factorynetwork.upgrade;

/**
 * The abilities a module can unlock.
 *
 * <p><b>It is named after the ability, not after the module</b>, because
 * {@code Module} is taken in Java as of version 9: {@code java.lang.Module}
 * is available in every file without an import. A custom type of the same
 * name does work — the import wins — but it trips up every reader and every
 * tool. The item is still called the wireless module.
 */
public enum Ability implements Upgrade {

    /** On the network without a cable — for the display. */
    WIRELESS("wireless_module");

    private final String id;

    Ability(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
