package dev.devpanda.factorynetwork.lang.ast;

import dev.devpanda.factorynetwork.lang.Span;

import java.util.List;

/**
 * Expressions of Manifold.
 *
 * <p>Sealed, so that the compiler has to handle them exhaustively with
 * {@code switch} — a new expression immediately shows up at every place that
 * does not yet know it.
 */
public sealed interface Expr {

    Span span();

    // ---- Literals ---------------------------------------------------------

    record IntLit(long value, Span span) implements Expr {}

    record FloatLit(double value, Span span) implements Expr {}

    record StringLit(String value, Span span) implements Expr {}

    record BoolLit(boolean value, Span span) implements Expr {}

    /** Duration, already converted to ticks. */
    record DurationLit(long ticks, String written, Span span) implements Expr {}

    /**
     * A written-out list: {@code ["eisen", "gold"]}.
     *
     * <p>The reason it exists: a global list value has to start somewhere, and
     * almost always it starts empty. Without {@code []} you would have to begin
     * it with a placeholder and take that right back out again.
     *
     * <p>It is a literal like any other and yet the only one that contains
     * expressions. Anyone who has to walk over the elements — the network
     * checker, say — therefore has to step inside it.
     */
    record ListLit(java.util.List<Expr> entries, Span span) implements Expr {}

    // ---- Selectors --------------------------------------------------------

    /**
     * A selector expression like {@code item:iron_ingot} or {@code tag:c/ores}.
     *
     * <p>{@code namespace} is empty when none was written. What that means is
     * only decided by the compiler: without a wildcard {@code minecraft}, with
     * a wildcard all namespaces.
     */
    record Selector(Kind kind, String prefix, String namespace, String path, Span span)
            implements Expr {

        /**
         * The word before the colon, exactly as it stood.
         *
         * <p><b>It is the truth, {@link #kind()} is the shorthand.</b> Since
         * the resource kinds became an open registry, there are prefixes for
         * which there is no enum value — and for those {@link Kind#CUSTOM}
         * stands in. Anyone who needs the written text, for a message say,
         * takes this one and not the name of the enum.
         */
        public String prefix() {
            return prefix;
        }

        public enum Kind {
            ITEM, FLUID, CHEMICAL, TAG,

            /**
             * A tag over fluids, {@code fluidtag:c/molten}.
             *
             * <p><b>Its own kind and not a {@code tag:} that searches
             * both.</b> In several places the written text alone has to make
             * clear whether items or fluids are meant: {@code WorkerKind} picks
             * the execution path from it, {@code FilterKind} the sort of a
             * template, {@code move} the route. A tag that can match both makes
             * exactly this decision impossible.
             */
            FLUIDTAG,
            /**
             * Power.
             *
             * <p><b>The only one without a sort.</b> There is only FE, and
             * {@code power:} with an empty remainder would be a lie about the
             * form — that is why {@code power} stands alone and without a colon.
             * The path is empty.
             */
            POWER,
            /**
             * Everything that is there.
             *
             * <p>The selector that picks out nothing — {@code move all from
             * brecher to storage}. A worker without a {@code filter} always
             * did that; in a function there was no way to write it, and
             * {@code move} demands a selector.
             *
             * <p>It means <b>items</b>, like a worker without a filter. Fluids
             * are left out deliberately: whoever means them writes
             * {@code fluid:}, and nobody empties a tank by accident.
             */
            ALL,
            /**
             * A kind that a foreign mod has registered.
             *
             * <p>Which one, {@link Selector#prefix()} says. Here it only says
             * that it is none of the built-in ones — the core does not know it,
             * and that has been allowed since Aug 26.
             *
             * <p><b>It passes where the kind is asked for, and stops where
             * items are concerned.</b> A filter from {@code source:} is not an
             * item selector, and {@code FilterKind} therefore says "unknown"
             * instead of guessing.
             */
            CUSTOM
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

    /** A selector with a leading amount, e.g. {@code 64 item:iron_ingot}. */
    record Amount(Long count, Expr selection, Span span) implements Expr {}

    /**
     * {@code 1..5} — a range of whole numbers, both ends included.
     *
     * <p>At runtime a list, not its own value type: that way {@code count},
     * {@code where} and {@code sort} apply without any extra work, and
     * {@code for i in 1..5} falls out with them.
     */
    record Range(Expr from, Expr to, Span span) implements Expr {}

    /**
     * {@code move 64 item:iron_ore from chest to crusher_1} as an expression.
     *
     * <p><b>Why an expression and not just a statement:</b>
     * {@code crusher.insert(64 item:x)} and {@code move 64 item:x from
     * storage to crusher} are the same operation — {@code insertInto} calls
     * {@code move}. The one returned the amount that arrived, the other threw
     * it away.
     *
     * <p>The statement form stays alongside it ({@code Stmt.Move}): in a
     * sequence a {@code move} is one step, and that needs to resume after a
     * server restart.
     */
    record Move(Expr amount, Expr from, Expr to, Span span) implements Expr {}

    // ---- Names ------------------------------------------------------------

    record Name(String value, Span span) implements Expr {}

    /** {@code storage}, {@code crafting}, {@code world}, … */
    record Builtin(Kind kind, Span span) implements Expr {
        public enum Kind { STORAGE, CRAFTING, WORLD, NETWORK, WORKERS, MULTIBLOCKS }
    }

    /** The implicit element in {@code where(it.busy)}. */
    record It(Span span) implements Expr {}

    /** A name pattern like {@code furnace_*} in a group declaration. */
    record NamePattern(String pattern, Span span) implements Expr {}

    // ---- Composite --------------------------------------------------------

    record Member(Expr target, String name, Span span) implements Expr {}

    record Call(Expr callee, List<Argument> arguments, Span span) implements Expr {}

    /** An argument, optionally with a leading name ({@code strategy: least_filled}). */
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
     * <p>{@code timeout} and {@code elseBody} only occur together; without
     * {@code else} a value would stand there after the timeout that never
     * existed.
     */
    record Await(String eventName, Expr where, Expr timeout, Block elseBody, Span span)
            implements Expr {}

    /**
     * {@code m => storage.count(m.input) > 0}
     *
     * <p>Used only where {@code it} is not enough — with nested list
     * operations.
     */
    record Lambda(String parameter, Expr body, Span span) implements Expr {}

    /** Stands for an expression the parser could not read. */
    record Invalid(Span span) implements Expr {}
}
