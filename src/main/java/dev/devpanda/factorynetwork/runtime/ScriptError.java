package dev.devpanda.factorynetwork.runtime;

/**
 * Ein Fehler zur Laufzeit — nach der Linie aus {@code sprache.md},
 * Abschnitt 14: Erwartbare Zustände sind keine Fehler, unerwartete halten den
 * Ablauf an.
 *
 * <p>Deshalb trägt diese Ausnahme keinen Stapel: Der Spieler soll lesen, was
 * los ist, nicht wo im Java-Code es passierte.
 */
public class ScriptError extends RuntimeException {

    private final String hint;

    public ScriptError(String message) {
        this(message, null);
    }

    public ScriptError(String message, String hint) {
        super(message, null, false, false);
        this.hint = hint;
    }

    public String hint() {
        return hint;
    }

    @Override
    public String toString() {
        return getMessage() + (hint == null ? "" : " " + hint);
    }
}
