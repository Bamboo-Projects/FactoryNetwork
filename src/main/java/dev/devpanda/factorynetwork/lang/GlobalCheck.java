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
     * Die Festwerte dieser Datei, Name auf Art.
     *
     * <p>Getrennt von {@link #declaredKinds}, weil ein Festwert eine andere
     * Frage beantwortet: Bei einem globalen Wert geht es darum, ob eine
     * Zuweisung zum Typ passt — bei einem Festwert darum, dass es sie gar
     * nicht geben darf.
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
     * Sucht, was ohne Typprüfer zu finden ist.
     *
     * @param kinds die Arten aller globalen Werte des Projekts, aus
     *              {@link #declaredKinds} über alle Dateien gesammelt
     */
    public static List<Diagnostic> run(Program program, Map<String, String> kinds) {
        return run(program, kinds, Map.of());
    }

    /**
     * @param constants die Festwerte des Projekts, aus
     *                  {@link #declaredConstants} über alle Dateien gesammelt
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
     * Wandert durch einen Block und alles, was darin verschachtelt ist.
     *
     * <p>Von Hand und ohne allgemeinen Besucher: Es gibt keinen, und einen für
     * diese eine Prüfung einzuführen hieße, ihn für die ganze Sprache zu
     * pflegen. Kommt eine Anweisungsart dazu, die einen Block enthält, fehlt
     * sie hier — der Preis ist eine Meldung, die ausbleibt, nicht eine
     * falsche.
     */
    private static void checkBlock(Block block, Map<String, String> kinds,
                                   Map<String, String> constants,
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
            // Die Schleifenvariable verdeckt einen gleichnamigen globalen Wert
            // genauso wie ein let.
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
     * Eine Zuweisung an einen globalen Wert.
     *
     * <p>Ein {@code let} gleichen Namens verdeckt den globalen Wert; eine
     * Zuweisung danach trifft die eigene Variable und nicht die Fabrik. Wer
     * das übergeht, meldet einen Fehler, den es nicht gibt — und das ist die
     * Sorte Meldung, die man abschaltet.
     */
    private static void checkAssign(Stmt.Assign assign, Map<String, String> kinds,
                                    Map<String, String> constants,
                                    List<Diagnostic> problems, Set<String> shadowed) {
        if (!(assign.target() instanceof Expr.Name name) || shadowed.contains(name.value())) {
            return;
        }
        // <b>Ein Festwert ist keiner, wenn er sich schreiben lässt.</b> Das
        // ist der einzige Unterschied zu einem globalen Wert, und er wird
        // hier gemacht — nicht in der Laufzeit, wo er erst beim Laufen
        // auffiele.
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
