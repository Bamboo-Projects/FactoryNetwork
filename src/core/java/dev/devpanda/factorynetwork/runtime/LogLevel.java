package dev.devpanda.factorynetwork.runtime;

/**
 * How serious a log entry is.
 *
 * <p><b>Four and no more.</b> Every additional level costs the player a
 * decision when writing and when reading, and nobody draws the line between
 * "notice" and "info" the same way twice anyway. The four here are the ones
 * everyone already knows.
 *
 * <p>The declaration order is the ranking: {@code DEBUG} at the bottom,
 * {@code ERROR} at the top. The filter in the terminal shows everything from
 * a given level upwards, and for that the levels must be comparable.
 */
public enum LogLevel {

    /**
     * Intermediate states while hunting a bug.
     *
     * <p>Hidden in the terminal by default: a {@code debug} inside a loop
     * writes a hundred lines per second, and afterwards nobody can find the
     * one message that mattered.
     */
    DEBUG("debug", "§8"),

    /** What went well. The plain {@code log()} ends up here too. */
    INFO("info", "§f"),

    /** Something is wrong, but the factory keeps running. */
    WARN("warn", "§e"),

    /** Something has come to a halt. */
    ERROR("error", "§c");

    private final String key;
    private final String colour;

    LogLevel(String key, String colour) {
        this.key = key;
        this.colour = colour;
    }

    /** The name under which the level appears in code and in the language file. */
    public String key() {
        return key;
    }

    /** The colour in the terminal. */
    public String colour() {
        return colour;
    }

    /** The level for a name, or {@code null}. */
    public static LogLevel of(String name) {
        for (LogLevel level : values()) {
            if (level.key.equals(name)) {
                return level;
            }
        }
        return null;
    }

    /** Is this level at least as serious as that one? */
    public boolean atLeast(LogLevel other) {
        return ordinal() >= other.ordinal();
    }
}
