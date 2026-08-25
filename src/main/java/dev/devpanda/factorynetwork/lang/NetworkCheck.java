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
                case Decl.FilterTemplate template ->
                        checkTemplateName(template, view, problems);
                default -> { }
            }
        }
        return problems;
    }

    /**
     * Eine Vorlage, die heißt wie ein Gerät im Netz.
     *
     * <p>Nur eine Warnung, und die Vorlage geht vor: Gerätenamen kommen aus
     * der Beschriftungspistole und nicht aus dem Programm. Hinge die
     * Bedeutung eines Programms daran, wie jemand später einen Connector
     * benennt, wäre es aus der Ferne nicht mehr zu lesen.
     */
    private static void checkTemplateName(Decl.FilterTemplate template, NetworkView view,
                                          List<Diagnostic> problems) {
        if (!view.connectors().contains(template.name())) {
            return;
        }
        problems.add(new Diagnostic(Diagnostic.Severity.WARNING, template.span(),
                "Die Vorlage „" + template.name() + "“ verdeckt das Gerät gleichen Namens.",
                "Wo der Name steht, ist die Vorlage gemeint. Das Gerät ist damit "
                        + "aus dem Programm nicht mehr erreichbar."));
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
                    checkSide(worker, entry.value(), view, problems);
                }
                default -> { }
            }
        }
    }

    /**
     * Passt die Seite, an der der Connector hängt, zu dem, was der Worker
     * bewegt?
     *
     * <p>Der Fehler dahinter ist der stillste von allen: Der Worker läuft,
     * bewegt nichts, und meldet nichts — denn „nichts bewegt" ist der
     * Normalfall. Wer die Maschine falsch herum ankabelt, sucht das im
     * Programm.
     *
     * <p><b>Geprüft wird die angeschlossene Seite</b>, nicht ob irgendeine
     * Seite es könnte. Sonst schweigt die Warnung genau in dem Fall, für den
     * sie gebaut ist.
     */
    private static void checkSide(Decl.Worker worker, Expr target, NetworkView view,
                                  List<Diagnostic> problems) {
        if (!(target instanceof Expr.Name name)) {
            return;
        }
        DeviceProfile profile = view.profile(name.value());
        if (!profile.reachable()) {
            return;
        }
        Expr.Selector.Kind kind = WorkerKind.of(worker);
        if (kind == null) {
            return;
        }
        DeviceProfile.Access.Ability needed = switch (kind) {
            case ITEM, TAG -> DeviceProfile.Access.Ability.ITEMS;
            // FLUIDTAG kommt hier nie an — WorkerKind.of nennt die
            // Ressource und nicht die Schreibweise. Der Fall steht trotzdem
            // da: Der Schalter ist erschöpfend, und eine stille Lücke wäre
            // eine ausbleibende Warnung.
            case FLUID, FLUIDTAG -> DeviceProfile.Access.Ability.FLUIDS;
            // Ein Strom-Worker verlangt einen Energiespeicher an der
            // angeschlossenen Seite. Das Profil weiß es bereits — die Warnung
            // fällt ohne Zusatzarbeit ab, weil die Geräteerkennung Energie
            // ohnehin probt.
            case POWER -> DeviceProfile.Access.Ability.ENERGY;
            // Chemikalien sind noch nicht angebunden; über sie wird nichts
            // behauptet, solange der Server sie nicht proben kann.
            case CHEMICAL -> null;
        };
        if (needed == null || profile.can(profile.connectedSide(), needed)) {
            return;
        }
        List<Side> elsewhere = profile.sidesWith(needed);
        String what = switch (needed) {
            case FLUIDS -> "Flüssigkeiten";
            case ENERGY -> "Strom";
            case ITEMS -> "Gegenstände";
        };
        String hint = elsewhere.isEmpty()
                ? "Diese Maschine nimmt an keiner Seite " + what + " an."
                : "An " + written(elsewhere) + " ginge es — häng den Connector dorthin.";
        problems.add(new Diagnostic(Diagnostic.Severity.WARNING, name.span(),
                "Der Connector „" + name.value() + "“ hängt "
                        + profile.connectedSide().written()
                        + " — dort nimmt die Maschine keine " + what + " an.",
                hint));
    }

    /** „Norden", „Norden und Süden", „Norden, Süden und oben". */
    private static String written(List<Side> sides) {
        List<String> words = sides.stream().map(Side::written).toList();
        if (words.size() == 1) {
            return words.get(0);
        }
        return String.join(", ", words.subList(0, words.size() - 1))
                + " und " + words.get(words.size() - 1);
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
