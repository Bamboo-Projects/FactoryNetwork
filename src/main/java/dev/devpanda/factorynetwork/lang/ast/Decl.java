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

    // ---- Globaler Wert ----------------------------------------------------

    /**
     * {@code global modus = "tag"} — ein Wert, den alle Dateien sehen.
     *
     * <p><b>Die einzige Deklaration ohne Klammern.</b> Alle anderen sammeln
     * Angaben in einem Block; diese erklärt einen Wert, und dafür ist eine
     * Zeile die ehrlichere Form.
     *
     * <p><b>Und kein {@code let} auf oberster Ebene.</b> Ein Programm besteht
     * nur aus Deklarationen, es gibt kein Hauptprogramm, das beim Laden
     * losläuft — ein {@code let} draußen sähe aus wie eine Anweisung, die
     * niemand ausführt. {@code global} sagt außerdem, was es ist: etwas, das
     * alle sehen.
     *
     * @param value der Anfangswert. Ein Literal, und keine Rechnung: Wann
     *              liefe die? Beim Übernehmen, beim Serverstart, bei jedem
     *              Laden des Chunks? Ein Literal hat diese Frage nicht.
     */
    record Global(String name, Expr value, Span span) implements Decl {}

    /**
     * {@code const rate = 64} — ein Wert, der sich nie ändert.
     *
     * <p>Wie {@link Global} in einer Zeile erklärt, und mit demselben
     * Anspruch an den Wert: ein Literal, keine Rechnung. Zwei Unterschiede —
     * schreiben lässt er sich nicht, und er wird nicht gespeichert, weil ein
     * Wert, der im Programm steht, aus dem Programm wiederkommt.
     */
    record Const(String name, Expr value, Span span) implements Decl {}

    // ---- Filter-Vorlage ---------------------------------------------------

    /**
     * {@code filter ore_factory { … }} — eine Auswahl mit einem Namen.
     *
     * <p>Sie steht überall, wo eine geschriebene Auswahl steht: im Worker, in
     * {@code move}, in {@code count}. Zwei Dinge kann die Sprache dadurch,
     * die sie vorher nicht konnte — dieselbe Auswahl an mehreren Stellen
     * nennen, ohne sie zu wiederholen, und mehrere Auswahlen zu einer
     * zusammenlegen. Ein Worker nimmt nur eine {@code filter}-Zeile.
     *
     * <p><b>Nackte Zeilen statt {@code members}.</b> Eine Gruppe hat zwei
     * Arten von Zeilen — {@code members} und {@code strategy} — und braucht
     * deshalb ein Wort zur Unterscheidung. Eine Vorlage hat nur eine Art; ein
     * zweites Wort wäre Zeremonie ohne Aufgabe.
     *
     * @param includes was dazugehört, je Zeile ein Eintrag
     * @param excludes was wieder herausfällt — die Zeilen mit {@code except}.
     *                 <b>Erst alles zusammen, dann die Ausnahmen</b>, damit
     *                 die Reihenfolge der Zeilen gleichgültig ist und niemand
     *                 beim Lesen einen Zwischenstand mitführen muss.
     */
    record FilterTemplate(String name, List<Expr> includes, List<Expr> excludes, Span span)
            implements Decl {}

    /** Steht für eine Deklaration, die der Parser nicht lesen konnte. */
    record Invalid(String name, Span span) implements Decl {}
}
