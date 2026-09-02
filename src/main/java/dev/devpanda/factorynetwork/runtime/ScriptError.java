package dev.devpanda.factorynetwork.runtime;

/**
 * A runtime error — following the line laid down in {@code sprache.md},
 * section 14: expected states are not errors; unexpected ones halt the
 * flow.
 *
 * <p>That is why this exception carries no stack trace: the player should read
 * what is going on, not where in the Java code it happened.
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
