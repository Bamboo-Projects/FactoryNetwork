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
 * Prüft die globalen Werte eines Programms.
 *
 * <p><b>Wie weit die Prüfung reicht, entscheidet der Bestand:</b> Die Sprache
 * hat keinen Typprüfer, und Typfehler fallen zur Laufzeit auf. Einen Prüfer
 * nur für globale Werte zu bauen, wäre eine Insel mit eigenen Regeln —
 * dieselbe Schreibweise würde an einer Stelle beanstandet und an der nächsten
 * nicht.
 *
 * <p>Gemeldet wird deshalb, was <b>ohne Typsystem entscheidbar</b> ist:
 *
 * <ul>
 *   <li>Der Anfangswert ist keine Rechnung. {@code global x = storage.count(…)}
 *       liefe wann? Beim Übernehmen, beim Serverstart, bei jedem Laden des
 *       Chunks? Ein Literal hat diese Frage nicht.
 *   <li>Kein Name doppelt.
 *   <li>Literal gegen Literal: {@code global modus = "tag"} und irgendwo
 *       {@code modus = 3}. Das ist der Fall, der in der Praxis vorkommt, weil
 *       er ein Vertipper ist.
 * </ul>
 *
 * <p>{@code modus = irgendeineFunktion()} bleibt offen, bis es läuft.
 */
public final class GlobalCheck {

    private GlobalCheck() {
    }

    /**
     * Die Art eines Literals, oder {@code null}.
     *
     * <p><b>Zahlen sind eine Art</b>, ganze wie gebrochene: Die Sprache
     * rechnet ohnehin mit beiden und wandelt zwischen ihnen. Wer
     * {@code menge = 2.5} an eine Null zuweist, meint keinen anderen Typ,
     * sondern eine genauere Zahl.
     */
    private static String literalKind(Expr expr) {
        return switch (expr) {
            case Expr.IntLit ignored -> "Zahl";
            case Expr.FloatLit ignored -> "Zahl";
            case Expr.StringLit ignored -> "Text";
            case Expr.BoolLit ignored -> "Wahrheitswert";
            case Expr.DurationLit ignored -> "Dauer";
            case null, default -> null;
        };
    }

    /**
     * Welche globalen Werte diese Datei erklärt, und von welcher Art.
     *
     * <p>Getrennt vom Prüfen, weil <b>alle Dateien einen Namensraum
     * teilen</b>: Ein Wert aus {@code werte.mf} wird in {@code main.mf}
     * zugewiesen, und ohne die Sammlung über alle Dateien hinweg wüsste die
     * Prüfung dort nichts von ihm. Dasselbe Muster wie bei
     * {@link NetworkCheck#localNames}.
     *
     * <p>Doppelte Namen meldet {@code Project} — dort ist bekannt, aus
     * welcher Datei der erste stammt.
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
     * Sucht, was ohne Typprüfer zu finden ist.
     *
     * @param kinds die Arten aller globalen Werte des Projekts, aus
     *              {@link #declaredKinds} über alle Dateien gesammelt
     */
    public static List<Diagnostic> run(Program program, Map<String, String> kinds) {
        List<Diagnostic> problems = new ArrayList<>();

        for (Decl declaration : program.declarations()) {
            if (!(declaration instanceof Decl.Global global)) {
                continue;
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
                case Decl.Fn fn -> checkBlock(fn.body(), kinds, problems, Set.of());
                case Decl.On on -> checkBlock(on.body(), kinds, problems, Set.of());
                case Decl.Multiblock multiblock -> {
                    for (Decl.Fn function : multiblock.functions()) {
                        checkBlock(function.body(), kinds, problems, Set.of());
                    }
                }
                default -> { }
            }
        }
        return problems;
    }

    /**
     * Wandert durch einen Block und alles, was darin verschachtelt ist.
     *
     * <p>Von Hand und ohne allgemeinen Besucher: Es gibt keinen, und einen für
     * diese eine Prüfung einzuführen hieße, ihn für die ganze Sprache zu
     * pflegen. Kommt eine Anweisungsart dazu, die einen Block enthält, fehlt
     * sie hier — der Preis ist eine Meldung, die ausbleibt, nicht eine
     * falsche.
     */
    private static void checkBlock(Block block, Map<String, String> kinds,
                                   List<Diagnostic> problems, Set<String> shadowedOutside) {
        if (block == null) {
            return;
        }
        // Ein let gilt ab seiner Zeile und nur bis zum Ende seines Blocks.
        // Deshalb eine eigene Menge je Block, die mit der von außen anfängt —
        // und die der Aufrufer nicht zurückbekommt.
        Set<String> shadowed = new HashSet<>(shadowedOutside);
        for (Stmt statement : block.statements()) {
            if (statement instanceof Stmt.Let let) {
                shadowed.add(let.name());
                continue;
            }
            checkStatement(statement, kinds, problems, shadowed);
        }
    }

    private static void checkStatement(Stmt statement, Map<String, String> kinds,
                                       List<Diagnostic> problems, Set<String> shadowed) {
        switch (statement) {
            case Stmt.Assign assign -> checkAssign(assign, kinds, problems, shadowed);
            case Stmt.If branch -> {
                checkBlock(branch.thenBody(), kinds, problems, shadowed);
                checkBlock(branch.elseBlock(), kinds, problems, shadowed);
                if (branch.elseIf() != null) {
                    checkStatement(branch.elseIf(), kinds, problems, shadowed);
                }
            }
            // Die Schleifenvariable verdeckt einen gleichnamigen globalen Wert
            // genauso wie ein let.
            case Stmt.For loop -> {
                Set<String> inner = new HashSet<>(shadowed);
                inner.add(loop.variable());
                checkBlock(loop.body(), kinds, problems, inner);
            }
            case Stmt.While loop -> checkBlock(loop.body(), kinds, problems, shadowed);
            default -> { }
        }
    }

    /**
     * Eine Zuweisung an einen globalen Wert.
     *
     * <p>Ein {@code let} gleichen Namens verdeckt den globalen Wert; eine
     * Zuweisung danach trifft die eigene Variable und nicht die Fabrik. Wer
     * das übergeht, meldet einen Fehler, den es nicht gibt — und das ist die
     * Sorte Meldung, die man abschaltet.
     */
    private static void checkAssign(Stmt.Assign assign, Map<String, String> kinds,
                                    List<Diagnostic> problems, Set<String> shadowed) {
        if (!(assign.target() instanceof Expr.Name name) || shadowed.contains(name.value())) {
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
