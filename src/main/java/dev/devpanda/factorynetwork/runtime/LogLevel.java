package dev.devpanda.factorynetwork.runtime;

/**
 * Wie ernst ein Eintrag im Protokoll ist.
 *
 * <p><b>Vier und nicht mehr.</b> Jede weitere Stufe kostet den Spieler eine
 * Entscheidung beim Schreiben und beim Lesen, und die Grenze zwischen
 * „notice" und „info" zieht ohnehin niemand zweimal gleich. Die vier hier
 * sind die, die jeder schon kennt.
 *
 * <p>Die Reihenfolge ist die Rangfolge: {@code DEBUG} zuunterst,
 * {@code ERROR} zuoberst. Der Filter im Terminal zeigt alles ab einer Stufe,
 * und dafür muss sie vergleichbar sein.
 */
public enum LogLevel {

    /**
     * Zwischenstände beim Suchen eines Fehlers.
     *
     * <p>Im Terminal voreingestellt ausgeblendet: Ein {@code debug} in einer
     * Schleife schreibt hundert Zeilen je Sekunde, und danach findet niemand
     * mehr die eine Meldung, auf die es ankam.
     */
    DEBUG("debug", "§8"),

    /** Was gut lief. Auch das schlichte {@code log()} landet hier. */
    INFO("info", "§f"),

    /** Etwas stimmt nicht, die Fabrik läuft aber weiter. */
    WARN("warn", "§e"),

    /** Etwas ist stehen geblieben. */
    ERROR("error", "§c");

    private final String key;
    private final String colour;

    LogLevel(String key, String colour) {
        this.key = key;
        this.colour = colour;
    }

    /** Der Name, unter dem die Stufe im Code und in der Sprachdatei steht. */
    public String key() {
        return key;
    }

    /** Die Farbe im Terminal. */
    public String colour() {
        return colour;
    }

    /** Die Stufe zu einem Namen, oder {@code null}. */
    public static LogLevel of(String name) {
        for (LogLevel level : values()) {
            if (level.key.equals(name)) {
                return level;
            }
        }
        return null;
    }

    /** Ist diese Stufe mindestens so ernst wie jene? */
    public boolean atLeast(LogLevel other) {
        return ordinal() >= other.ordinal();
    }
}
