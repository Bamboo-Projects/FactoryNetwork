package dev.devpanda.factorynetwork.lang;

import java.util.Objects;

/**
 * Eine Meldung an den Spieler — der eigentliche Zweck dieses Übersetzers.
 *
 * <p>Der Text ist deutsch und richtet sich an jemanden, der kein Programmierer
 * ist. {@code hint} trägt den Vorschlag, der aus einer Fehlermeldung eine
 * Hilfe macht: „Meinst du den Connector gleichen Namens? Dann schreibe ihn in
 * Rückstriche."
 */
public record Diagnostic(Severity severity, Span span, String message, String hint) {

    public enum Severity {
        /** Blockiert die Übernahme. */
        ERROR,
        /** Läuft, ist aber vermutlich nicht gemeint. */
        WARNING
    }

    public Diagnostic {
        Objects.requireNonNull(severity);
        Objects.requireNonNull(span);
        Objects.requireNonNull(message);
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
        return prefix + " " + span + ": " + message + (hint == null ? "" : " " + hint);
    }
}
