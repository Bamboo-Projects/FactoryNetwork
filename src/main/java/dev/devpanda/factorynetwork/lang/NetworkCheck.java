package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Prüft die Namen im Programm gegen das, was wirklich im Netz steht.
 *
 * <p><b>Der Anlass war eine schwarze Wand.</b> {@code display test { … }} war
 * grammatisch tadellos, der Übersetzer sagte „bereit", und die Tafel blieb
 * leer — weil sie in der Welt anders hieß. Der Hinweis stand auf der Tafel
 * selbst, und die hängt womöglich drei Räume weiter. Er gehört dorthin, wo
 * man den Namen tippt.
 *
 * <p>Dasselbe gilt für Connectoren: {@code from kiste_1} ist eine gültige
 * Zeile, auch wenn nichts so heißt.
 *
 * <p><b>Warnungen, keine Fehler.</b> Eine Wand, die man erst morgen baut,
 * darf man heute schon ins Programm schreiben — und ein Programm, das auf
 * einem Server ohne geladenes Netz übersetzt wird, soll nicht scheitern.
 */
public final class NetworkCheck {

    private NetworkCheck() {
    }

    /**
     * Sucht Namen, die es nicht gibt.
     *
     * @param program das übersetzte Programm einer Datei
     * @param view    was im Netz steht
     * @param local   Namen, die das Programm selbst vergibt — Gruppen und
     *                Multiblocks. Sie stehen an denselben Stellen wie
     *                Connectoren und sind trotzdem keine.
     */
    public static List<Diagnostic> run(Program program, NetworkView view, Set<String> local) {
        List<Diagnostic> problems = new ArrayList<>();
        if (!view.knowsNetwork()) {
            return problems;
        }
        for (Decl declaration : program.declarations()) {
            switch (declaration) {
                case Decl.Display display -> checkDisplay(display, view, problems);
                case Decl.Worker worker -> checkWorker(worker, view, local, problems);
                case Decl.Group group -> checkGroup(group, view, local, problems);
                default -> { }
            }
        }
        return problems;
    }

    private static void checkDisplay(Decl.Display display, NetworkView view,
                                     List<Diagnostic> problems) {
        if (view.displays().contains(display.name())) {
            return;
        }
        String hint = view.closestDisplay(display.name())
                .map(near -> "Meintest du „" + near + "“?")
                .orElseGet(() -> view.displays().isEmpty()
                        ? "Im Netz hängt keine benannte Anzeigewand. Rechtsklick auf "
                                + "eine Tafel gibt ihr einen Namen."
                        : "Im Netz gibt es: " + String.join(", ", view.displays()));
        problems.add(new Diagnostic(Diagnostic.Severity.WARNING, display.span(),
                "Keine Anzeigewand heißt „" + display.name() + "“ — sie bleibt schwarz.",
                hint));
    }

    private static void checkWorker(Decl.Worker worker, NetworkView view, Set<String> local,
                                    List<Diagnostic> problems) {
        for (Decl.Worker.Entry entry : worker.entries()) {
            switch (entry.kind()) {
                case FROM, TO, OVERFLOW -> {
                    checkTarget(entry.value(), view, local, problems);
                    checkTarget(entry.second(), view, local, problems);
                }
                default -> { }
            }
        }
    }

    private static void checkGroup(Decl.Group group, NetworkView view, Set<String> local,
                                   List<Diagnostic> problems) {
        for (Expr member : group.members()) {
            checkTarget(member, view, local, problems);
        }
    }

    /**
     * Ein Ziel muss ein Connector sein, eine Gruppe, ein Multiblock oder
     * etwas Eingebautes.
     *
     * <p>Ein Namensmuster wird übergangen: {@code ofen_*} passt vielleicht
     * auf nichts, und das ist kein Fehler, sondern eine leere Gruppe.
     */
    private static void checkTarget(Expr target, NetworkView view, Set<String> local,
                                    List<Diagnostic> problems) {
        if (!(target instanceof Expr.Name name)) {
            return;
        }
        if (local.contains(name.value()) || view.connectors().contains(name.value())) {
            return;
        }
        String hint = view.closestConnector(name.value())
                .map(near -> "Meintest du „" + near + "“?")
                .orElseGet(() -> view.connectors().isEmpty()
                        ? "Im Netz gibt es keinen benannten Connector."
                        : "Im Netz gibt es: " + String.join(", ", view.connectors()));
        problems.add(new Diagnostic(Diagnostic.Severity.WARNING, name.span(),
                "Nichts im Netz heißt „" + name.value() + "“.", hint));
    }

    /** Die Namen, die ein Programm selbst vergibt: Gruppen und Multiblocks. */
    public static Set<String> localNames(Program program) {
        Set<String> names = new java.util.HashSet<>();
        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.Group || declaration instanceof Decl.Multiblock) {
                names.add(declaration.name());
            }
        }
        return names;
    }
}
