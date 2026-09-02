package dev.devpanda.factorynetwork.lang;

import java.util.Objects;

/**
 * A message to the player — the real purpose of this compiler.
 *
 * <p>The text is German and addressed to someone who is not a programmer.
 * {@code hint} carries the suggestion that turns an error message into help:
 * "Did you mean the connector of the same name? Then write it in backticks."
 */
public record Diagnostic(Severity severity, Span span, String message, String hint,
                         String file, Fix fix) {

    /**
     * A suggestion that a machine can apply.
     *
     * <p><b>Separate from the hint, and with its own location.</b> The hint is
     * a sentence for a human — "Did you mean {@code chemical}:?" —, and an
     * editor would have to take it apart to build a quick fix from it. Two
     * versions of the same information drift apart; that is why it stands here
     * twice within <i>one</i> message, once as a sentence and once as a
     * replacement.
     *
     * <p>The own location is needed because it is rarely the one of the
     * message: for a misspelled resource kind the message underlines the whole
     * selector — {@code chemiacl:hydrogen} —, but only the word before it is
     * replaced.
     */
    public record Fix(Span span, String text) {
    }

    /** Without a suggestion — the normal case. */
    public Diagnostic(Severity severity, Span span, String message, String hint,
                      String file) {
        this(severity, span, message, hint, file, null);
    }

    /**
     * Without a file — the compiler of a single file does not know what it is
     * called. {@link Project} adds the name afterwards, when it merges the
     * messages of several files.
     */
    public Diagnostic(Severity severity, Span span, String message, String hint) {
        this(severity, span, message, hint, "", null);
    }

    /** The same message, but with a file name. */
    public Diagnostic withFile(String file) {
        return new Diagnostic(severity, span, message, hint, file, fix);
    }

    /** The same message, but with a suggestion to apply. */
    public Diagnostic withFix(Span where, String text) {
        return new Diagnostic(severity, span, message, hint, file, new Fix(where, text));
    }

    public enum Severity {
        /** Blocks acceptance of the program. */
        ERROR,
        /** Runs, but is probably not what was meant. */
        WARNING
    }

    public Diagnostic {
        Objects.requireNonNull(severity);
        Objects.requireNonNull(span);
        Objects.requireNonNull(message);
        file = file == null ? "" : file;
    }

    public static Diagnostic error(Span span, String message) {
        return new Diagnostic(Severity.ERROR, span, message, null);
    }

    public static Diagnostic error(Span span, String message, String hint) {
        return new Diagnostic(Severity.ERROR, span, message, hint);
    }

    public static Diagnostic warning(Span span, String message) {
        return new Diagnostic(Severity.WARNING, span, message, null);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        String prefix = severity == Severity.ERROR ? "Fehler" : "Warnung";
        String where = file.isEmpty() ? "" : file + " ";
        return prefix + " " + where + span + ": " + message
                + (hint == null ? "" : " " + hint);
    }
}
