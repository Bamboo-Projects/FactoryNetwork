package dev.devpanda.factorynetwork.lang.ast;

import dev.devpanda.factorynetwork.lang.Span;

import java.util.List;

/** Anweisungen von Manifold. */
public sealed interface Stmt {

    Span span();

    record Let(String name, Expr value, Span span) implements Stmt {}

    /** Zuweisung an einen bestehenden Namen oder ein Feld. */
    record Assign(Expr target, Expr value, Span span) implements Stmt {}

    record If(Expr condition, Block thenBody, Object elseBody, Span span) implements Stmt {
        /** {@code elseBody} ist entweder ein {@link Block}, ein {@link If} oder {@code null}. */
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
     * <p>{@code from} darf fehlen — dann ist die Quelle aus dem Zusammenhang
     * gemeint, etwa innerhalb eines Multiblocks.
     */
    record Move(Expr amount, Expr from, Expr to, Span span) implements Stmt {}

    record Emit(String eventName, List<Expr.Argument> arguments, Span span) implements Stmt {}

    record Sleep(Expr duration, Span span) implements Stmt {}

    /** Ein Ausdruck als Anweisung, etwa ein Aufruf. */
    record ExprStmt(Expr expr, Span span) implements Stmt {}

    /** Steht für eine Anweisung, die der Parser nicht lesen konnte. */
    record Invalid(Span span) implements Stmt {}
}
