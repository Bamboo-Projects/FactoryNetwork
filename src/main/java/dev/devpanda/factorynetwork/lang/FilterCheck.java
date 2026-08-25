package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prüft, ob eine Filter-Vorlage etwas auswählen kann.
 *
 * <p><b>Fehler und keine Warnungen</b>, anders als bei {@link EventCheck} und
 * {@link NetworkCheck}. Deren Begründung — ein Programm, das erst mit der
 * nächsten Datei vollständig wird, soll sich heute schon übernehmen lassen —
 * trägt hier nicht: Eine gemischte oder leere Vorlage wird durch keine
 * weitere Datei richtig. Sie ist an Ort und Stelle falsch, und was sie
 * auswählte, könnte niemand sagen.
 */
public final class FilterCheck {

    private FilterCheck() {
    }

    /**
     * Die Namen, die im Projekt schon etwas anderes tragen — Name auf Art.
     *
     * <p>Nur, was an denselben Stellen steht wie eine Vorlage: Worker,
     * Gruppen, Multiblocks, Funktionen und globale Werte stehen alle in
     * Ausdrücken. Eine Anzeige oder ein Ereignis nicht — die dürfen so heißen
     * wie eine Vorlage, ohne dass irgendwo unklar würde, was gemeint ist.
     */
    public static Map<String, String> declaredNames(Program program) {
        Map<String, String> found = new LinkedHashMap<>();
        for (Decl declaration : program.declarations()) {
            String kind = switch (declaration) {
                case Decl.Worker ignored -> "einen Worker";
                case Decl.Group ignored -> "eine Gruppe";
                case Decl.Multiblock ignored -> "einen Multiblock";
                case Decl.Fn ignored -> "eine Funktion";
                case Decl.Global ignored -> "einen globalen Wert";
                case Decl.Const ignored -> "einen Festwert";
                case null, default -> null;
            };
            if (kind != null) {
                found.put(declaration.name(), kind);
            }
        }
        return found;
    }

    /**
     * @param program das übersetzte Programm einer Datei
     * @param taken   was im ganzen Projekt schon einen Namen trägt, aus
     *                {@link #declaredNames}
     */
    public static List<Diagnostic> run(Program program, Map<String, String> taken) {
        List<Diagnostic> problems = new ArrayList<>();
        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.FilterTemplate template) {
                check(template, taken, problems);
            }
        }
        return problems;
    }

    private static void check(Decl.FilterTemplate template, Map<String, String> taken,
                              List<Diagnostic> problems) {
        String other = taken.get(template.name());
        if (other != null) {
            problems.add(new Diagnostic(Diagnostic.Severity.ERROR, template.span(),
                    "„" + template.name() + "“ steht schon für " + other + ".",
                    "Beide stehen in Ausdrücken — an einer Stelle wie "
                            + "„move " + template.name() + " …“ wäre nicht zu sagen, "
                            + "welches gemeint ist."));
        }

        FilterKind kind = FilterKind.of(template);
        if (kind == FilterKind.EMPTY) {
            problems.add(new Diagnostic(Diagnostic.Severity.ERROR, template.span(),
                    "Die Vorlage " + template.name() + " wählt nichts aus.",
                    template.excludes().isEmpty()
                            ? "In den Block gehört mindestens eine Auswahl, etwa tag:c/ores."
                            : "Es stehen nur Ausnahmen darin — es gibt nichts, "
                                    + "wovon sie abzögen."));
        } else if (kind == FilterKind.MIXED) {
            problems.add(new Diagnostic(Diagnostic.Severity.ERROR, template.span(),
                    "Eine Vorlage ist entweder für Gegenstände oder für Flüssigkeiten.",
                    "move schickt Wasser und Steine über verschiedene Wege. Eine "
                            + "Vorlage mit beidem wäre an jeder Stelle etwas anderes."));
        }

        for (Expr entry : FilterKind.entries(template)) {
            List<Expr.Selector> selectors = FilterKind.selectorsOf(entry);
            if (selectors.isEmpty()) {
                problems.add(noSelection(entry));
                continue;
            }
            for (Expr.Selector selector : selectors) {
                switch (selector.kind()) {
                    case CHEMICAL -> problems.add(new Diagnostic(Diagnostic.Severity.ERROR,
                            selector.span(),
                            dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.reason(),
                            dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.hint()));
                    case POWER -> problems.add(new Diagnostic(Diagnostic.Severity.ERROR,
                            selector.span(),
                            "Strom ist keine Auswahl, die sich sammeln lässt.",
                            "Ein Worker mit filter power fördert Energie — in eine "
                                    + "Vorlage gehört sie nicht."));
                    case ITEM, FLUID, TAG -> { }
                }
            }
        }
    }

    /**
     * Eine Zeile, die keine Auswahl ist.
     *
     * <p>Fast immer ein Name — der Versuch, eine Vorlage in eine andere zu
     * legen. Das sagt die Meldung auch, statt „hier fehlt eine Auswahl": Wer
     * es versucht, hat eine Absicht, und die verdient eine Antwort.
     */
    private static Diagnostic noSelection(Expr entry) {
        if (entry instanceof Expr.Name name) {
            return new Diagnostic(Diagnostic.Severity.ERROR, name.span(),
                    "Eine Vorlage darf keine andere Vorlage enthalten.",
                    "Schreib die Zeilen von " + name.value() + " hier noch einmal hin. "
                            + "Ineinandergelegte Vorlagen können sich gegenseitig "
                            + "enthalten, und dann wäre nicht mehr zu sagen, was sie "
                            + "auswählen.");
        }
        return new Diagnostic(Diagnostic.Severity.ERROR, entry.span(),
                "In einer Vorlage steht je Zeile eine Auswahl.",
                "Zum Beispiel item:iron_ore, tag:c/ores oder fluid:water.");
    }
}
