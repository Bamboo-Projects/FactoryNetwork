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
public record Diagnostic(Severity severity, Span span, String message, String hint,
                         String file, Fix fix) {

    /**
     * Ein Vorschlag, den eine Maschine anwenden kann.
     *
     * <p><b>Getrennt vom Hinweis, und mit eigener Stelle.</b> Der Hinweis ist
     * ein Satz für einen Menschen — „Meinst du {@code chemical}:?" —, und ein
     * Editor müsste ihn zerpflücken, um daraus eine Schnellkorrektur zu
     * bauen. Zwei Fassungen derselben Auskunft laufen auseinander; deshalb
     * steht sie hier zweimal in <i>einer</i> Meldung, einmal als Satz und
     * einmal als Ersetzung.
     *
     * <p>Die eigene Stelle ist nötig, weil sie selten die der Meldung ist:
     * Bei einer verschriebenen Ressourcenart unterstreicht die Meldung die
     * ganze Auswahl — {@code chemiacl:hydrogen} —, ersetzt wird aber nur das
     * Wort davor.
     */
    public record Fix(Span span, String text) {
    }

    /** Ohne Vorschlag — der Normalfall. */
    public Diagnostic(Severity severity, Span span, String message, String hint,
                      String file) {
        this(severity, span, message, hint, file, null);
    }

    /**
     * Ohne Datei — der Übersetzer einer einzelnen Datei weiß nicht, wie sie
     * heißt. Den Namen trägt {@link Project} nach, wenn er die Meldungen
     * mehrerer Dateien zusammenlegt.
     */
    public Diagnostic(Severity severity, Span span, String message, String hint) {
        this(severity, span, message, hint, "", null);
    }

    /** Dieselbe Meldung, aber mit Dateinamen. */
    public Diagnostic withFile(String file) {
        return new Diagnostic(severity, span, message, hint, file, fix);
    }

    /** Dieselbe Meldung, aber mit einem Vorschlag zum Anwenden. */
    public Diagnostic withFix(Span where, String text) {
        return new Diagnostic(severity, span, message, hint, file, new Fix(where, text));
    }

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
