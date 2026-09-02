package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks the names in the program against what really stands in the network.
 *
 * <p><b>The occasion was a black wall.</b> {@code display test { … }} was
 * grammatically flawless, the compiler said "ready", and the board stayed
 * empty — because it was called something else in the world. The hint stood on
 * the board itself, and that may hang three rooms away. It belongs where you
 * type the name.
 *
 * <p>The same holds for connectors: {@code from kiste_1} is a valid line, even
 * when nothing is called that.
 *
 * <p><b>Warnings, not errors.</b> A wall you only build tomorrow, you may
 * already write into the program today — and a program compiled on a server
 * without a loaded network should not fail.
 */
public final class NetworkCheck {

    private NetworkCheck() {
    }

    /**
     * Searches for names that do not exist.
     *
     * @param program the compiled program of a file
     * @param view    what stands in the network
     * @param local   names the program assigns itself — groups and multiblocks.
     *                They stand in the same places as connectors and are still
     *                not connectors.
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
                // In a template a device name means the device of the plant
                // and not one in the network. That is why its roles count too.
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
     * A recipe points at a device that has to exist.
     *
     * <p>That is the reason a recipe stands in the program and not on a template
     * item: a template with a mistyped target nobody notices until the factory
     * stands still. A warning and not an error — whoever only sets the machine
     * up tomorrow may write the recipe today.
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
     * {@code in 1000 power} — a line that does nothing.
     *
     * <p>It parses, because {@code power} is a selector like any other. Only,
     * the job inserts items and tops up fluids; power the machine gets over the
     * <b>power distribution</b>, and at that per tick and according to its own
     * demand.
     *
     * <p><b>And it stays that way.</b> A number in the recipe would be a guess:
     * what a foreign machine draws per pass hangs on its upgrades and on the
     * mod, and it is verifiable nowhere. A wait gate on the network supply would
     * on top of that have the same deadlock as the discarded check-only design
     * for fluids: the distribution continuously lowers exactly the supply that
     * would be waited on.
     *
     * <p>A warning and not an error — the line does no harm, it just does not
     * keep what it promises. Since the fluid next to it is now really filled in,
     * its silence is more misleading than before.
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
     * A store at a device that does not exist.
     *
     * <p>The same typo as with the recipe, and here it comes down even harder:
     * in the terminal there would stand a stock missing a chest that never
     * existed. Nobody would think to look for that in the program.
     *
     * <p>A warning and not an error — whoever only sets the chest up tomorrow
     * may write the line today.
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
     * A template that is called like a device in the network.
     *
     * <p>Only a warning, and the template takes precedence: device names come
     * from the label gun and not from the program. If the meaning of a program
     * hung on how someone later names a connector, it could no longer be read
     * from afar.
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
     * Does the side the connector hangs on fit what the worker moves?
     *
     * <p>The error behind this is the quietest of all: the worker runs, moves
     * nothing, and reports nothing — because "nothing moved" is the normal case.
     * Whoever wires the machine up the wrong way round searches for that in the
     * program.
     *
     * <p><b>The connected side is checked</b>, not whether any side could do it.
     * Otherwise the warning stays silent in exactly the case it was built for.
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
            // "all" means items, like a worker without a filter.
            case ITEM, TAG, ALL -> DeviceProfile.Access.Ability.ITEMS;
            // FLUIDTAG never arrives here — WorkerKind.of names the resource
            // and not the spelling. The case still stands there: the switch is
            // exhaustive, and a silent gap would be a warning that fails to
            // appear.
            case FLUID, FLUIDTAG -> DeviceProfile.Access.Ability.FLUIDS;
            // A power worker demands an energy store on the connected side. The
            // profile already knows it — the warning falls out without extra
            // work, because device detection probes energy anyway.
            case POWER -> DeviceProfile.Access.Ability.ENERGY;
            // Chemicals are not yet connected; nothing is claimed about them as
            // long as the server cannot probe them.
            //
            // And about a foreign kind even less so: the device profile probes
            // items, fluids, and energy — what a mod otherwise expects at a
            // machine it cannot know. That is the second axis from
            // entscheidungen.md, and it is missing.
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

    /** "North", "North and South", "North, South and up". */
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
     * Searches for the device names in the {@code move} statements of a block.
     *
     * <p><b>Local names are left out</b> — parameters, {@code let}, loop
     * variables, global values, constants, filter templates, groups, and the
     * roles of a multiblock. That was exactly the reason this check did not
     * exist for a long time: one that warns about correct programs gets turned
     * off.
     *
     * <p>Generously reckoned: a {@code let} applies to the whole block and not
     * only from its line on. One warning too few is better here than one too
     * many.
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

    /** A {@code move} also stands in the middle of an expression. */
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
            // The only literal that contains expressions. Without this line a
            // wrong device name in a list would slip through — and a list of
            // targets is the obvious use.
            case Expr.ListLit list -> list.entries().forEach(entry ->
                    checkExprMoves(entry, view, known, problems));
            case null, default -> { }
        }
    }

    /**
     * A target has to be a connector, a group, a multiblock, or something
     * built-in.
     *
     * <p>A name pattern is skipped: {@code ofen_*} may match nothing, and that
     * is not an error but an empty group.
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

    /** The names a program assigns itself: groups and multiblocks. */
    public static Set<String> localNames(Program program) {
        Set<String> names = new java.util.HashSet<>();
        for (Decl declaration : program.declarations()) {
            // Everything the program names itself and that may stand in the
            // same place as a device. Without the last three, the check in a
            // move would warn about correct programs.
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
