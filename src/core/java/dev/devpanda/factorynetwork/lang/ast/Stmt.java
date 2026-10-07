package dev.devpanda.factorynetwork.lang.ast;

import dev.devpanda.factorynetwork.lang.Span;

import java.util.List;

/** Statements of Manifold. */
public sealed interface Stmt {

    Span span();

    record Let(String name, Expr value, Span span) implements Stmt {}

    /** Assignment to an existing name or a field. */
    record Assign(Expr target, Expr value, Span span) implements Stmt {}

    record If(Expr condition, Block thenBody, Object elseBody, Span span) implements Stmt {
        /** {@code elseBody} is either a {@link Block}, an {@link If}, or {@code null}. */
        public Block elseBlock() {
            return elseBody instanceof Block block ? block : null;
        }

        public If elseIf() {
            return elseBody instanceof If nested ? nested : null;
        }
    }

    record For(String variable, Expr iterable, Block body, Span span) implements Stmt {}

    record While(Expr condition, Block body, Span span) implements Stmt {}

    record Return(Expr value, Span span) implements Stmt {}

    record Break(Span span) implements Stmt {}

    record Continue(Span span) implements Stmt {}

    /**
     * {@code move 64 item:iron_ore from chest to crusher_1}
     *
     * <p>{@code from} may be omitted — then the source is meant from context,
     * for instance inside a multiblock.
     */
    record Move(Expr amount, Expr from, Expr to, Span span) implements Stmt {}

    record Emit(String eventName, List<Expr.Argument> arguments, Span span) implements Stmt {}

    record Sleep(Expr duration, Span span) implements Stmt {}

    /** An expression as a statement, e.g. a call. */
    record ExprStmt(Expr expr, Span span) implements Stmt {}

    /** Stands for a statement the parser could not read. */
    record Invalid(Span span) implements Stmt {}
}
