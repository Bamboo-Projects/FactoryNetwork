package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Stmt;
import dev.devpanda.factorynetwork.lang.parse.Parser;

import java.util.Set;

/**
 * Expressions the editor may evaluate on its own, to show their value under
 * the mouse — and the parsing of one written alone.
 *
 * <p><b>Only reading.</b> Hovering must never move an item, press a button
 * or send a redstone signal. So an expression is allowed when every part of
 * it only looks: names, literals, selections, arithmetic, and the members
 * that answer a question ({@code count}, {@code items}, {@code energy} …).
 * A function of the program is not called — nobody can see from outside
 * whether it changes something.
 */
public final class ReadOnly {

    /** Members that only answer; {@code redstone} only without an argument. */
    private static final Set<String> READING = Set.of(
            "count", "items", "energy", "online", "name", "measure", "members",
            "where", "sort", "sum", "first", "plus", "without", "rest", "slots",
            "power", "capacity", "redstone", "amount", "item", "fluid", "chemical");

    /** Free functions that only compute. */
    private static final Set<String> PURE_FUNCTIONS = Set.of(
            "min", "max", "abs", "round", "floor", "ceil");

    private ReadOnly() {
    }

    /** One expression as written in the editor, or {@code null} if it is none. */
    public static Expr parse(String written) {
        if (written == null || written.isBlank() || written.indexOf('\n') >= 0) {
            return null;
        }
        Parser.ParseResult result = Parser.parse("fn __probe() {\n    return " + written + "\n}");
        if (result.hasErrors() || result.program().declarations().isEmpty()
                || !(result.program().declarations().get(0) instanceof Decl.Fn fn)
                || fn.body().statements().size() != 1
                || !(fn.body().statements().get(0) instanceof Stmt.Return ret)
                || ret.value() == null) {
            return null;
        }
        return ret.value();
    }

    /** Whether evaluating this changes nothing in the world or the program. */
    public static boolean onlyReads(Expr expr) {
        return switch (expr) {
            case Expr.IntLit ignored -> true;
            case Expr.FloatLit ignored -> true;
            case Expr.StringLit ignored -> true;
            case Expr.BoolLit ignored -> true;
            case Expr.DurationLit ignored -> true;
            case Expr.It ignored -> true;
            case Expr.Name ignored -> true;
            case Expr.NamePattern ignored -> true;
            case Expr.Builtin ignored -> true;
            case Expr.Selector ignored -> true;
            case Expr.Except except -> onlyReads(except.base())
                    && except.exclusions().stream().allMatch(ReadOnly::onlyReads);
            case Expr.Condition condition -> onlyReads(condition.base());
            case Expr.Amount amount -> onlyReads(amount.selection());
            case Expr.ListLit list -> list.entries().stream().allMatch(ReadOnly::onlyReads);
            case Expr.Range range -> onlyReads(range.from()) && onlyReads(range.to());
            case Expr.Unary unary -> onlyReads(unary.operand());
            case Expr.Binary binary -> onlyReads(binary.left()) && onlyReads(binary.right());
            case Expr.Member member -> READING.contains(member.name()) && onlyReads(member.target());
            case Expr.Call call -> callOnlyReads(call);
            default -> false;
        };
    }

    private static boolean callOnlyReads(Expr.Call call) {
        boolean arguments = call.arguments().stream().allMatch(argument -> onlyReads(argument.value()));
        if (call.callee() instanceof Expr.Member member) {
            if ("redstone".equals(member.name()) && !call.arguments().isEmpty()) {
                return false;
            }
            return READING.contains(member.name()) && onlyReads(member.target()) && arguments;
        }
        if (call.callee() instanceof Expr.Name name) {
            return PURE_FUNCTIONS.contains(name.value()) && arguments;
        }
        return false;
    }
}
