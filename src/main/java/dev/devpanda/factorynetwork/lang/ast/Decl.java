package dev.devpanda.factorynetwork.lang.ast;

import dev.devpanda.factorynetwork.lang.Span;

import java.util.List;

/**
 * Declarations — the other half of the language.
 *
 * <p>What stands here holds permanently and may be reordered and optimized by
 * the system. Statements, by contrast, run in the order in which they stand.
 */
public sealed interface Decl {

    String name();

    Span span();

    // ---- Worker -----------------------------------------------------------

    record Worker(String name, List<Entry> entries, Span span) implements Decl {

        /** A single entry. The compiler checks which are missing or duplicated. */
        public record Entry(Kind kind, Expr value, Expr second, Span span) {
            public enum Kind {
                FROM, TO, FILTER, MAINTAIN, RATE, WHEN, PRIORITY, STRATEGY, OVERFLOW
            }
        }

        public Entry entry(Entry.Kind kind) {
            return entries.stream().filter(e -> e.kind() == kind).findFirst().orElse(null);
        }
    }

    // ---- Group ------------------------------------------------------------

    record Group(String name, List<Expr> members, String strategy, Span span) implements Decl {}

    // ---- Multiblock -------------------------------------------------------

    record Multiblock(String name, List<String> devices, List<Fn> functions, Span span)
            implements Decl {}

    // ---- Event ------------------------------------------------------------

    record Event(String name, List<Param> parameters, Span span) implements Decl {}

    /** A parameter with a type annotation, e.g. {@code amount: Int}. */
    record Param(String name, String type, Span span) {}

    // ---- Display ----------------------------------------------------------

    record Display(String name, List<Entry> entries, Span span) implements Decl {

        public record Entry(Kind kind, String label, Expr value, Span span) {
            public enum Kind { TITLE, ROW, TEXT, PROGRESS, INDICATOR, LIST, BUTTON, SCALE }
        }
    }

    /**
     * {@code recipe erz_mahlen at brecher { in 1 item:iron_ore out 2 item:iron_dust }}
     *
     * <p>What a machine can do that the network cannot read on its own. The
     * furnace family and our own press work without this; everything else
     * cannot be read generically (see {@code entscheidungen.md},
     * "Processing-Rezepte"), and so the player writes it down.
     *
     * <p><b>Not a template item, but a line in the program.</b> It stands next
     * to the workers, travels with the file to VS Code, can be versioned, and
     * a recipe at a device that does not exist reports itself on adoption.
     */
    /**
     * {@code store kiste_1 { … }} — a foreign inventory counts toward network storage.
     *
     * <p>The storage bus, the way AE2 has it, only without its own block. The
     * connector is attached to the chest anyway; this line says that its
     * contents belong to the network: {@code storage.count(…)} sees them, a job
     * counts on them, a worker {@code to storage} may land there.
     *
     * <p><b>In the program and not in a window</b>, for the same reason as with
     * {@code recipe}: a filter you only see in-game cannot be versioned, does
     * not travel with the file to VS Code, and a typo in it goes unnoticed. The
     * rationale is in {@code speicherbus.md}.
     *
     * @param priority where things are stored first; the cells sit at 0
     * @param filter   what may go in, or {@code null} for everything
     */
    record Store(String device, long priority, Expr filter, Span span) implements Decl {

        @Override
        public String name() {
            return device;
        }
    }

    record Recipe(String name, String device, List<Part> inputs, List<Part> outputs,
                  Span span) implements Decl {

        /**
         * This much of that selector.
         *
         * <p>The amount is always written out, even the one. A recipe without
         * amounts would be ambiguous exactly where it costs the most.
         */
        public record Part(long amount, Expr selection, Span span) {
        }
    }

    // ---- Function and event block -----------------------------------------

    record Fn(String name, List<Param> parameters, Block body, Span span) implements Decl {}

    /**
     * {@code on redstone_changed(sensor, strength) { … }}
     *
     * <p>No types here: they are known from the event declaration.
     */
    record On(String name, List<String> parameters, Block body, Span span) implements Decl {}

    // ---- Global value -----------------------------------------------------

    /**
     * {@code global modus = "tag"} — a value that all files see.
     *
     * <p><b>The only declaration without braces.</b> All others gather entries
     * in a block; this one declares a value, and for that a single line is the
     * more honest form.
     *
     * <p><b>And no {@code let} at the top level.</b> A program consists only of
     * declarations, there is no main program that starts running on load — a
     * {@code let} out there would look like a statement that nobody executes.
     * {@code global} also says what it is: something that everyone sees.
     *
     * @param value the initial value. A literal, and not a computation: when
     *              would it run? On adoption, on server start, on every load
     *              of the chunk? A literal does not have this question.
     */
    record Global(String name, Expr value, Span span) implements Decl {}

    /**
     * {@code const rate = 64} — a value that never changes.
     *
     * <p>Declared on one line like {@link Global}, and with the same demand on
     * the value: a literal, no computation. Two differences — it cannot be
     * written to, and it is not persisted, because a value that stands in the
     * program comes back from the program.
     */
    record Const(String name, Expr value, Span span) implements Decl {}

    // ---- Filter template --------------------------------------------------

    /**
     * {@code filter ore_factory { … }} — a selector with a name.
     *
     * <p>It stands everywhere a written selector stands: in the worker, in
     * {@code move}, in {@code count}. Two things the language can do with it
     * that it could not before — name the same selector in several places
     * without repeating it, and merge several selectors into one. A worker
     * takes only one {@code filter} line.
     *
     * <p><b>Bare lines instead of {@code members}.</b> A group has two kinds of
     * lines — {@code members} and {@code strategy} — and therefore needs a word
     * to tell them apart. A template has only one kind; a second word would be
     * ceremony without a job.
     *
     * @param includes what belongs, one entry per line
     * @param excludes what drops back out — the lines with {@code except}.
     *                 <b>First everything together, then the exceptions</b>, so
     *                 that the order of the lines does not matter and nobody has
     *                 to carry an intermediate state while reading.
     */
    record FilterTemplate(String name, List<Expr> includes, List<Expr> excludes, Span span)
            implements Decl {}

    /** Stands for a declaration the parser could not read. */
    record Invalid(String name, Span span) implements Decl {}
}
