package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.ast.Stmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the global values of a program.
 *
 * <p><b>How far the check reaches is decided by what exists:</b> the language
 * has no type checker, and type errors surface at runtime. To build a checker
 * only for global values would be an island with its own rules — the same
 * spelling would be objected to in one place and not in the next.
 *
 * <p>What is reported is therefore what is <b>decidable without a type
 * system</b>:
 *
 * <ul>
 *   <li>The initial value is not a computation. {@code global x = storage.count(…)}
 *       would run when? On adoption, on server start, on every load of the
 *       chunk? A literal does not have this question.
 *   <li>No name twice.
 *   <li>Literal against literal: {@code global modus = "tag"} and somewhere
 *       {@code modus = 3}. That is the case that occurs in practice, because it
 *       is a typo.
 * </ul>
 *
 * <p>{@code modus = irgendeineFunktion()} stays open until it runs.
 */
public final class GlobalCheck {

    private GlobalCheck() {
    }

    /**
     * The kind of a literal, or {@code null}.
     *
     * <p><b>Numbers are one kind</b>, whole as well as fractional: the language
     * computes with both anyway and converts between them. Whoever assigns
     * {@code menge = 2.5} to a zero means no different type, but a more precise
     * number.
     */
    private static String literalKind(Expr expr) {
        return switch (expr) {
            case Expr.IntLit ignored -> "Zahl";
            case Expr.FloatLit ignored -> "Zahl";
            case Expr.StringLit ignored -> "Text";
            case Expr.BoolLit ignored -> "Wahrheitswert";
            case Expr.DurationLit ignored -> "Dauer";
            // Element-independent, and `[]` belongs to it: what stands in a
            // list the language does not know without a type checker — that it
            // is a list is enough to report an assignment of "tag" to it.
            case Expr.ListLit ignored -> "Liste";
            case null, default -> null;
        };
    }

    /**
     * Which global values this file declares, and of what kind.
     *
     * <p>Separate from the checking, because <b>all files share one
     * namespace</b>: a value from {@code werte.mf} is assigned in {@code main.mf},
     * and without collecting across all files the check there would know nothing
     * of it. The same pattern as with {@link NetworkCheck#localNames}.
     *
     * <p>Duplicate names are reported by {@code Project} — there it is known
     * which file the first one comes from.
     */
    public static Map<String, String> declaredKinds(Program program) {
        Map<String, String> kinds = new HashMap<>();
        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.Global global) {
                String kind = literalKind(global.value());
                if (kind != null) {
                    kinds.putIfAbsent(global.name(), kind);
                }
            }
        }
        return kinds;
    }

    /**
     * The constants of this file, name to kind.
     *
     * <p>Separate from {@link #declaredKinds}, because a constant answers a
     * different question: for a global value it is about whether an assignment
     * fits the type — for a constant it is about there being no assignment at
     * all.
     */
    public static Map<String, String> declaredConstants(Program program) {
        Map<String, String> constants = new HashMap<>();
        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.Const constant) {
                String kind = literalKind(constant.value());
                constants.putIfAbsent(constant.name(), kind == null ? "Festwert" : kind);
            }
        }
        return constants;
    }

    /**
     * Searches for what can be found without a type checker.
     *
     * @param kinds the kinds of all global values of the project, collected from
     *              {@link #declaredKinds} across all files
     */
    public static List<Diagnostic> run(Program program, Map<String, String> kinds) {
        return run(program, kinds, Map.of());
    }

    /**
     * @param constants the constants of the project, collected from
     *                  {@link #declaredConstants} across all files
     */
    public static List<Diagnostic> run(Program program, Map<String, String> kinds,
                                       Map<String, String> constants) {
        List<Diagnostic> problems = new ArrayList<>();

        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.Const constant) {
                if (literalKind(constant.value()) == null) {
                    problems.add(new Diagnostic(Diagnostic.Severity.ERROR, constant.span(),
                            "Ein Festwert braucht einen festen Wert.",
                            "Eine Rechnung hätte keinen festen Zeitpunkt — schreib ein "
                                    + "Literal, etwa 64 oder \"tag\"."));
                }
                if (kinds.containsKey(constant.name())) {
                    problems.add(new Diagnostic(Diagnostic.Severity.ERROR, constant.span(),
                            "„" + constant.name() + "“ ist schon ein globaler Wert.",
                            "Ein Name gehört einer Erklärung. Der eine lässt sich ändern, "
                                    + "der andere nicht — beides zugleich geht nicht."));
                }
                continue;
            }
            if (!(declaration instanceof Decl.Global global)) {
                continue;
            }
            if (constants.containsKey(global.name())) {
                problems.add(new Diagnostic(Diagnostic.Severity.ERROR, global.span(),
                        "„" + global.name() + "“ ist schon ein Festwert.",
                        "Ein Name gehört einer Erklärung."));
            }
            if (literalKind(global.value()) == null) {
                problems.add(new Diagnostic(Diagnostic.Severity.ERROR, global.span(),
                        "Ein globaler Wert braucht einen festen Wert als Anfang.",
                        "Eine Rechnung hätte keinen festen Zeitpunkt: Liefe sie beim "
                                + "Übernehmen, beim Serverstart oder bei jedem Laden? "
                                + "Schreib ein Literal — etwa \"tag\" oder 0 — und rechne "
                                + "in einer Funktion."));
            }
        }

        for (Decl declaration : program.declarations()) {
            switch (declaration) {
                case Decl.Fn fn -> checkBlock(fn.body(), kinds, constants, problems, Set.of());
                case Decl.On on -> checkBlock(on.body(), kinds, constants, problems, Set.of());
                case Decl.Multiblock multiblock -> {
                    for (Decl.Fn function : multiblock.functions()) {
                        checkBlock(function.body(), kinds, constants, problems, Set.of());
                    }
                }
                default -> { }
            }
        }
        return problems;
    }

    /**
     * Walks through a block and everything nested within it.
     *
     * <p>By hand and without a general visitor: there is none, and introducing
     * one for this single check would mean maintaining it for the whole
     * language. If a kind of statement that contains a block is added, it is
     * missing here — the price is a message that fails to appear, not a wrong
     * one.
     */
    private static void checkBlock(Block block, Map<String, String> kinds,
                                   Map<String, String> constants,
                                   List<Diagnostic> problems, Set<String> shadowedOutside) {
        if (block == null) {
            return;
        }
        // A let applies from its line and only until the end of its block. So a
        // separate set per block, which starts with the one from outside — and
        // which the caller does not get back.
        Set<String> shadowed = new HashSet<>(shadowedOutside);
        for (Stmt statement : block.statements()) {
            if (statement instanceof Stmt.Let let) {
                shadowed.add(let.name());
                continue;
            }
            checkStatement(statement, kinds, constants, problems, shadowed);
        }
    }

    private static void checkStatement(Stmt statement, Map<String, String> kinds,
                                       Map<String, String> constants,
                                       List<Diagnostic> problems, Set<String> shadowed) {
        switch (statement) {
            case Stmt.Assign assign -> checkAssign(assign, kinds, constants, problems, shadowed);
            case Stmt.If branch -> {
                checkBlock(branch.thenBody(), kinds, constants, problems, shadowed);
                checkBlock(branch.elseBlock(), kinds, constants, problems, shadowed);
                if (branch.elseIf() != null) {
                    checkStatement(branch.elseIf(), kinds, constants, problems, shadowed);
                }
            }
            // The loop variable shadows a global value of the same name just
            // like a let.
            case Stmt.For loop -> {
                Set<String> inner = new HashSet<>(shadowed);
                inner.add(loop.variable());
                checkBlock(loop.body(), kinds, constants, problems, inner);
            }
            case Stmt.While loop -> checkBlock(loop.body(), kinds, constants, problems, shadowed);
            default -> { }
        }
    }

    /**
     * An assignment to a global value.
     *
     * <p>A {@code let} of the same name shadows the global value; an assignment
     * after it hits one's own variable and not the factory. Whoever overlooks
     * that reports an error that does not exist — and that is the sort of message
     * one turns off.
     */
    private static void checkAssign(Stmt.Assign assign, Map<String, String> kinds,
                                    Map<String, String> constants,
                                    List<Diagnostic> problems, Set<String> shadowed) {
        if (!(assign.target() instanceof Expr.Name name) || shadowed.contains(name.value())) {
            return;
        }
        // <b>A constant is not one if it can be written to.</b> That is the
        // only difference from a global value, and it is made here — not in the
        // runtime, where it would only surface at run time.
        if (constants.containsKey(name.value())) {
            problems.add(new Diagnostic(Diagnostic.Severity.ERROR, assign.span(),
                    "„" + name.value() + "“ ist ein Festwert und lässt sich nicht ändern.",
                    "Soll er sich ändern können, erkläre ihn mit global statt const."));
            return;
        }
        String declared = kinds.get(name.value());
        if (declared == null) {
            return;
        }
        String assigned = literalKind(assign.value());
        if (assigned == null || assigned.equals(declared)) {
            return;
        }
        problems.add(new Diagnostic(Diagnostic.Severity.WARNING, assign.span(),
                "„" + name.value() + "“ ist ein " + declared + ", hier steht ein "
                        + assigned + ".",
                "Der Typ eines globalen Werts kommt aus seinem Anfangswert."));
    }
}
