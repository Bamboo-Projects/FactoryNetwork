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
                case Decl.Fn function -> checkMoves(function.body(), view,
                        withParameters(local, function), problems);
                case Decl.On handler -> checkMoves(handler.body(), view,
                        with(local, handler.parameters()), problems);
                // In einer Vorlage meint ein Gerätename das Gerät der Anlage
                // und nicht eines im Netz. Deshalb zählen ihre Rollen mit.
                case Decl.Multiblock template -> {
                    for (Decl.Fn function : template.functions()) {
                        checkMoves(function.body(), view,
                                withParameters(with(local, template.devices()), function),
                                problems);
                    }
                }
                case Decl.Recipe recipe -> checkRecipe(recipe, view, local, problems);
                case Decl.Store store -> checkStore(store, view, local, problems);
                default -> { }
            }
        }
        return problems;
    }

    /**
     * Ein Rezept zeigt auf ein Gerät, das es geben muss.
     *
     * <p>Das ist der Grund, warum ein Rezept im Programm steht und nicht auf
     * einem Muster-Item: Ein Muster mit vertipptem Ziel merkt niemand, bis
     * die Fabrik stillsteht. Eine Warnung und kein Fehler — wer die Maschine
     * erst morgen hinstellt, darf das Rezept heute schon schreiben.
     */
    private static void checkRecipe(Decl.Recipe recipe, NetworkView view,
                                    Set<String> local, List<Diagnostic> problems) {
        checkRecipePower(recipe, problems);
        if (view.connectors().isEmpty() || local.contains(recipe.device())
                || view.connectors().contains(recipe.device())) {
            return;
        }
        String hint = view.closestConnector(recipe.device())
                .map(near -> "Meintest du „" + near + "“?")
                .orElseGet(() -> "Im Netz gibt es: "
                        + String.join(", ", view.connectors()));
        problems.add(new Diagnostic(Diagnostic.Severity.WARNING, recipe.span(),
                "Nichts im Netz heißt „" + recipe.device() + "“.", hint));
    }

    /**
     * {@code in 1000 power} — eine Zeile, die nichts tut.
     *
     * <p>Sie parst, weil {@code power} eine Auswahl ist wie jede andere. Nur
     * legt der Auftrag Gegenstände ein und füllt Flüssigkeiten auf; Strom
     * bekommt die Maschine über die <b>Stromverteilung</b>, und zwar je Tick
     * und nach ihrem eigenen Bedarf.
     *
     * <p><b>Und das bleibt so.</b> Eine Zahl im Rezept wäre geraten: Was eine
     * fremde Maschine je Durchgang zieht, hängt an ihren Upgrades und an der
     * Mod, und nachprüfbar ist es nirgends. Ein Warte-Gate auf den Netzvorrat
     * hätte obendrein dieselbe Verklemmung wie der verworfene
     * Nur-prüfen-Entwurf bei Flüssigkeiten: Die Verteilung senkt laufend
     * genau den Vorrat, auf den gewartet würde.
     *
     * <p>Eine Warnung und kein Fehler — die Zeile schadet nicht, sie hält
     * bloß nicht, was sie verspricht. Seit die Flüssigkeit daneben wirklich
     * eingefüllt wird, ist ihr Schweigen irreführender als vorher.
     */
    private static void checkRecipePower(Decl.Recipe recipe, List<Diagnostic> problems) {
        for (Decl.Recipe.Part part : recipe.inputs()) {
            if (part.selection() instanceof Expr.Selector selector
                    && selector.kind() == Expr.Selector.Kind.POWER) {
                problems.add(new Diagnostic(Diagnostic.Severity.WARNING, part.span(),
                        "Strom in einem Rezept tut nichts.",
                        "Die Maschine bekommt ihn über die Stromverteilung, nach ihrem "
                                + "eigenen Bedarf. Was hier stünde, wäre geraten. Die "
                                + "Zeile kann weg."));
            }
        }
    }

    /**
     * Ein Speicher an einem Gerät, das es nicht gibt.
     *
     * <p>Derselbe Vertipper wie beim Rezept, und hier fällt er noch schwerer
     * auf die Füße: Im Terminal stünde ein Bestand, dem eine Kiste fehlt, die
     * es nie gab. Niemand käme darauf, dafür im Programm nachzusehen.
     *
     * <p>Eine Warnung und kein Fehler — wer die Kiste erst morgen hinstellt,
     * darf die Zeile heute schon schreiben.
     */
    private static void checkStore(Decl.Store store, NetworkView view,
                                   Set<String> local, List<Diagnostic> problems) {
        if (view.connectors().isEmpty() || local.contains(store.device())
                || view.connectors().contains(store.device())) {
            return;
        }
        String hint = view.closestConnector(store.device())
                .map(near -> "Meintest du „" + near + "“?")
                .orElseGet(() -> "Im Netz gibt es: "
                        + String.join(", ", view.connectors()));
        problems.add(new Diagnostic(Diagnostic.Severity.WARNING, store.span(),
                "Nichts im Netz heißt „" + store.device() + "“.", hint));
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
            // „all" meint Gegenstände, wie ein Worker ohne Filter.
            case ITEM, TAG, ALL -> DeviceProfile.Access.Ability.ITEMS;
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
            //
            // Und über eine fremde Art erst recht nicht: Das Geräteprofil
            // probt Gegenstände, Flüssigkeiten und Energie — was eine Mod
            // sonst an einer Maschine erwartet, kann es nicht wissen. Das ist
            // die zweite Achse aus entscheidungen.md, und sie fehlt.
            case CHEMICAL, CUSTOM -> null;
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

    private static Set<String> with(Set<String> names, List<String> more) {
        Set<String> all = new java.util.HashSet<>(names);
        all.addAll(more);
        return all;
    }

    private static Set<String> withParameters(Set<String> names, Decl.Fn function) {
        Set<String> all = new java.util.HashSet<>(names);
        function.parameters().forEach(parameter -> all.add(parameter.name()));
        return all;
    }

    /**
     * Sucht die Gerätenamen in den {@code move}-Anweisungen eines Blocks.
     *
     * <p><b>Örtliche Namen werden ausgespart</b> — Parameter, {@code let},
     * Schleifenvariablen, globale Werte, Festwerte, Filter-Vorlagen, Gruppen
     * und die Rollen eines Multiblocks. Genau das war der Grund, warum es
     * diese Prüfung lange nicht gab: Eine, die vor richtigen Programmen
     * warnt, schaltet man ab.
     *
     * <p>Großzügig gerechnet: Ein {@code let} gilt für den ganzen Block und
     * nicht erst ab seiner Zeile. Eine Warnung zu wenig ist hier besser als
     * eine zu viel.
     */
    private static void checkMoves(dev.devpanda.factorynetwork.lang.ast.Block block,
                                   NetworkView view, Set<String> known,
                                   List<Diagnostic> problems) {
        if (block == null) {
            return;
        }
        Set<String> inner = new java.util.HashSet<>(known);
        for (dev.devpanda.factorynetwork.lang.ast.Stmt statement : block.statements()) {
            if (statement instanceof dev.devpanda.factorynetwork.lang.ast.Stmt.Let let) {
                inner.add(let.name());
            }
        }
        for (dev.devpanda.factorynetwork.lang.ast.Stmt statement : block.statements()) {
            checkStatementMoves(statement, view, inner, problems);
        }
    }

    private static void checkStatementMoves(dev.devpanda.factorynetwork.lang.ast.Stmt statement,
                                            NetworkView view, Set<String> known,
                                            List<Diagnostic> problems) {
        switch (statement) {
            case dev.devpanda.factorynetwork.lang.ast.Stmt.Move move -> {
                checkTarget(move.from(), view, known, problems);
                checkTarget(move.to(), view, known, problems);
            }
            case dev.devpanda.factorynetwork.lang.ast.Stmt.Let let ->
                    checkExprMoves(let.value(), view, known, problems);
            case dev.devpanda.factorynetwork.lang.ast.Stmt.Assign assign ->
                    checkExprMoves(assign.value(), view, known, problems);
            case dev.devpanda.factorynetwork.lang.ast.Stmt.Return ret ->
                    checkExprMoves(ret.value(), view, known, problems);
            case dev.devpanda.factorynetwork.lang.ast.Stmt.ExprStmt expr ->
                    checkExprMoves(expr.expr(), view, known, problems);
            case dev.devpanda.factorynetwork.lang.ast.Stmt.If branch -> {
                checkExprMoves(branch.condition(), view, known, problems);
                checkMoves(branch.thenBody(), view, known, problems);
                checkMoves(branch.elseBlock(), view, known, problems);
                if (branch.elseIf() != null) {
                    checkStatementMoves(branch.elseIf(), view, known, problems);
                }
            }
            case dev.devpanda.factorynetwork.lang.ast.Stmt.While loop -> {
                checkExprMoves(loop.condition(), view, known, problems);
                checkMoves(loop.body(), view, known, problems);
            }
            case dev.devpanda.factorynetwork.lang.ast.Stmt.For loop -> {
                Set<String> inner = new java.util.HashSet<>(known);
                inner.add(loop.variable());
                checkExprMoves(loop.iterable(), view, inner, problems);
                checkMoves(loop.body(), view, inner, problems);
            }
            default -> { }
        }
    }

    /** Ein {@code move} steht auch mitten in einem Ausdruck. */
    private static void checkExprMoves(Expr expr, NetworkView view, Set<String> known,
                                       List<Diagnostic> problems) {
        switch (expr) {
            case Expr.Move move -> {
                checkTarget(move.from(), view, known, problems);
                checkTarget(move.to(), view, known, problems);
                checkExprMoves(move.amount(), view, known, problems);
            }
            case Expr.Binary binary -> {
                checkExprMoves(binary.left(), view, known, problems);
                checkExprMoves(binary.right(), view, known, problems);
            }
            case Expr.Unary unary -> checkExprMoves(unary.operand(), view, known, problems);
            case Expr.Call call -> {
                checkExprMoves(call.callee(), view, known, problems);
                call.arguments().forEach(argument ->
                        checkExprMoves(argument.value(), view, known, problems));
            }
            case Expr.Member member -> checkExprMoves(member.target(), view, known, problems);
            // Das einzige Literal, das Ausdrücke enthält. Ohne diese Zeile
            // liefe ein falscher Gerätename in einer Liste durch — und eine
            // Liste von Zielen ist der offensichtliche Gebrauch.
            case Expr.ListLit list -> list.entries().forEach(entry ->
                    checkExprMoves(entry, view, known, problems));
            case null, default -> { }
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
            // Alles, was das Programm selbst benennt und was an derselben
            // Stelle stehen darf wie ein Gerät. Ohne die letzten drei würde
            // die Prüfung in einem move vor richtigen Programmen warnen.
            if (declaration instanceof Decl.Group
                    || declaration instanceof Decl.Multiblock
                    || declaration instanceof Decl.Global
                    || declaration instanceof Decl.Const
                    || declaration instanceof Decl.FilterTemplate) {
                names.add(declaration.name());
            }
        }
        return names;
    }
}
