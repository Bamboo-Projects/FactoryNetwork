package dev.devpanda.factorynetwork.lang.ast;

import dev.devpanda.factorynetwork.lang.Span;

import java.util.List;

/**
 * Deklarationen — die andere Hälfte der Sprache.
 *
 * <p>Was hier steht, gilt dauerhaft und darf vom System umgestellt und
 * optimiert werden. Anweisungen dagegen laufen in der Reihenfolge, in der sie
 * dastehen.
 */
public sealed interface Decl {

    String name();

    Span span();

    // ---- Worker -----------------------------------------------------------

    record Worker(String name, List<Entry> entries, Span span) implements Decl {

        /** Eine einzelne Angabe. Welche fehlen oder doppelt sind, prüft der Übersetzer. */
        public record Entry(Kind kind, Expr value, Expr second, Span span) {
            public enum Kind {
                FROM, TO, FILTER, MAINTAIN, RATE, WHEN, PRIORITY, STRATEGY, OVERFLOW
            }
        }

        public Entry entry(Entry.Kind kind) {
            return entries.stream().filter(e -> e.kind() == kind).findFirst().orElse(null);
        }
    }

    // ---- Gruppe -----------------------------------------------------------

    record Group(String name, List<Expr> members, String strategy, Span span) implements Decl {}

    // ---- Multiblock -------------------------------------------------------

    record Multiblock(String name, List<String> devices, List<Fn> functions, Span span)
            implements Decl {}

    // ---- Ereignis ---------------------------------------------------------

    record Event(String name, List<Param> parameters, Span span) implements Decl {}

    /** Ein Parameter mit Typangabe, etwa {@code amount: Int}. */
    record Param(String name, String type, Span span) {}

    // ---- Display ----------------------------------------------------------

    record Display(String name, List<Entry> entries, Span span) implements Decl {

        public record Entry(Kind kind, String label, Expr value, Span span) {
            public enum Kind { TITLE, ROW, TEXT, PROGRESS, INDICATOR, LIST, BUTTON }
        }
    }

    // ---- Funktion und Ereignisblock ---------------------------------------

    record Fn(String name, List<Param> parameters, Block body, Span span) implements Decl {}

    /**
     * {@code on redstone_changed(sensor, strength) { … }}
     *
     * <p>Hier stehen keine Typen: Sie sind durch die Ereignisdeklaration
     * bekannt.
     */
    record On(String name, List<String> parameters, Block body, Span span) implements Decl {}

    /** Steht für eine Deklaration, die der Parser nicht lesen konnte. */
    record Invalid(String name, Span span) implements Decl {}
}
