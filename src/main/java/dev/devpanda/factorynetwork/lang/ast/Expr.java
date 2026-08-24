package dev.devpanda.factorynetwork.lang.ast;

import dev.devpanda.factorynetwork.lang.Span;

import java.util.List;

/**
 * Ausdrücke von Manifold.
 *
 * <p>Versiegelt, damit der Übersetzer sie mit {@code switch} vollständig
 * behandeln muss — ein neuer Ausdruck fällt sofort an jeder Stelle auf, die
 * ihn noch nicht kennt.
 */
public sealed interface Expr {

    Span span();

    // ---- Literale ---------------------------------------------------------

    record IntLit(long value, Span span) implements Expr {}

    record FloatLit(double value, Span span) implements Expr {}

    record StringLit(String value, Span span) implements Expr {}

    record BoolLit(boolean value, Span span) implements Expr {}

    /** Zeitangabe, schon in Ticks umgerechnet. */
    record DurationLit(long ticks, String written, Span span) implements Expr {}

    // ---- Auswahl ----------------------------------------------------------

    /**
     * Ein Auswahlausdruck wie {@code item:iron_ingot} oder {@code tag:c/ores}.
     *
     * <p>{@code namespace} ist leer, wenn keiner dastand. Was das bedeutet,
     * entscheidet erst der Übersetzer: ohne Platzhalter {@code minecraft}, mit
     * Platzhalter alle Namensräume.
     */
    record Selector(Kind kind, String namespace, String path, Span span) implements Expr {

        public enum Kind {
            ITEM, FLUID, CHEMICAL, TAG,
            /**
             * Strom.
             *
             * <p><b>Der einzige ohne Sorte.</b> Es gibt nur FE, und
             * {@code power:} mit leerem Rest wäre eine Lüge über die Form —
             * deshalb steht {@code power} allein und ohne Doppelpunkt. Der
             * Pfad ist leer.
             */
            POWER
        }

        public boolean hasPattern() {
            return path.indexOf('*') >= 0;
        }

        public boolean hasNamespace() {
            return namespace != null && !namespace.isEmpty();
        }
    }

    /** {@code tag:c/ores except item:ancient_debris} */
    record Except(Expr base, List<Expr> exclusions, Span span) implements Expr {}

    /** Eine Auswahl mit vorangestellter Menge, etwa {@code 64 item:iron_ingot}. */
    record Amount(Long count, Expr selection, Span span) implements Expr {}

    // ---- Namen ------------------------------------------------------------

    record Name(String value, Span span) implements Expr {}

    /** {@code storage}, {@code crafting}, {@code world}, … */
    record Builtin(Kind kind, Span span) implements Expr {
        public enum Kind { STORAGE, CRAFTING, WORLD, NETWORK, WORKERS, MULTIBLOCKS }
    }

    /** Das implizite Element in {@code where(it.busy)}. */
    record It(Span span) implements Expr {}

    /** Ein Namensmuster wie {@code furnace_*} in einer Gruppendeklaration. */
    record NamePattern(String pattern, Span span) implements Expr {}

    // ---- Zusammengesetzt --------------------------------------------------

    record Member(Expr target, String name, Span span) implements Expr {}

    record Call(Expr callee, List<Argument> arguments, Span span) implements Expr {}

    /** Ein Argument, wahlweise mit vorangestelltem Namen ({@code strategy: least_filled}). */
    record Argument(String name, Expr value, Span span) {
        public boolean isNamed() {
            return name != null;
        }
    }

    record Binary(Op op, Expr left, Expr right, Span span) implements Expr {
        public enum Op {
            ADD("+"), SUB("-"), MUL("*"), DIV("/"), MOD("%"),
            EQ("=="), NEQ("!="), LT("<"), LTE("<="), GT(">"), GTE(">="),
            AND("&&"), OR("||");

            private final String written;

            Op(String written) {
                this.written = written;
            }

            public String written() {
                return written;
            }
        }
    }

    record Unary(Op op, Expr operand, Span span) implements Expr {
        public enum Op { NOT, NEGATE }
    }

    /**
     * {@code await BatchFinished where id == jobId timeout 30s else { … }}
     *
     * <p>{@code timeout} und {@code elseBody} kommen nur gemeinsam vor; ohne
     * {@code else} stünde nach Ablauf ein Wert da, den es nie gab.
     */
    record Await(String eventName, Expr where, Expr timeout, Block elseBody, Span span)
            implements Expr {}

    /**
     * {@code m => storage.count(m.input) > 0}
     *
     * <p>Gebraucht nur dort, wo {@code it} nicht reicht — bei verschachtelten
     * Listenoperationen.
     */
    record Lambda(String parameter, Expr body, Span span) implements Expr {}

    /** Steht für einen Ausdruck, den der Parser nicht lesen konnte. */
    record Invalid(Span span) implements Expr {}
}
