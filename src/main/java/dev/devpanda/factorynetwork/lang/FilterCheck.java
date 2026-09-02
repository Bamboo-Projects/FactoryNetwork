package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks whether a filter template can select anything.
 *
 * <p><b>Errors and not warnings</b>, unlike with {@link EventCheck} and
 * {@link NetworkCheck}. Their rationale — a program that only becomes complete
 * with the next file should already be adoptable today — does not carry here: a
 * mixed or empty template is not made right by any further file. It is wrong on
 * the spot, and what it selected nobody could say.
 */
public final class FilterCheck {

    private FilterCheck() {
    }

    /**
     * The names that already carry something else in the project — name to kind.
     *
     * <p>Only what stands in the same places as a template: workers, groups,
     * multiblocks, functions, and global values all stand in expressions. A
     * display or an event does not — those may be called the same as a template
     * without it becoming unclear anywhere what is meant.
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
     * @param program the compiled program of a file
     * @param taken   what already carries a name in the whole project, from
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
                    // With Mekanism a chemical is a selector like any other.
                    // Without, it stays an error, and the message points at the
                    // mod list instead of at this mod.
                    case CHEMICAL -> {
                        if (!dev.devpanda.factorynetwork.compat.mekanism.FnMekanism
                                .installed()) {
                            problems.add(new Diagnostic(Diagnostic.Severity.ERROR,
                                    selector.span(),
                                    dev.devpanda.factorynetwork.compat.mekanism.FnMekanism
                                            .reason(),
                                    dev.devpanda.factorynetwork.compat.mekanism.FnMekanism
                                            .hint()));
                        }
                    }
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
     * A line that is not a selector.
     *
     * <p>Almost always a name — the attempt to put one template inside another.
     * The message says that too, instead of "a selector is missing here":
     * whoever tries it has an intent, and that deserves an answer.
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
