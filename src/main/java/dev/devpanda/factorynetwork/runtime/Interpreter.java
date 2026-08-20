package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.ast.Stmt;
import dev.devpanda.factorynetwork.runtime.flow.Step;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Führt Funktionen und Ereignisblöcke aus.
 *
 * <p>Ein Baum-Interpreter, kein Bytecode. Für die Größenordnung, um die es
 * geht — einige hundert Anweisungen je Auslöser —, ist das schnell genug, und
 * er ist die Grundlage, auf der die Continuations später aufsetzen: Wo hier
 * ein Aufruf zurückkehrt, wird später ein Zustand gespeichert.
 *
 * <p><b>Was hier noch fehlt:</b> Wartende Abläufe überleben keinen
 * Serverneustart. {@code await} und {@code sleep} halten den Ablauf an und
 * geben auf, statt ihren Zustand zu sichern. Das ist die größte offene
 * Zusage der Mod und bewusst nicht halb gebaut — eine Persistenz, die nur in
 * einfachen Fällen trägt, ist schlimmer als gar keine, weil sich niemand
 * darauf verlassen kann.
 */
public final class Interpreter {

    /**
     * Wie viele Anweisungen ein Ablauf ausführen darf, bevor er abgebrochen
     * wird. Kein Tickbudget mit Fortsetzung, sondern eine harte Grenze —
     * solange es keine Continuations gibt, kann ein Ablauf nicht angehalten
     * und später fortgesetzt werden.
     */
    private static final int MAX_STEPS = 10_000;

    private final Program program;
    private final Host host;
    private final Deque<Map<String, Value>> scopes = new ArrayDeque<>();
    private int steps;

    /**
     * Der Blick auf die Namen eines Ablaufs.
     *
     * <p>Der gewöhnliche Interpreter führt seinen eigenen Stapel; ein Ablauf
     * hält seine Rahmen als Daten. Beide bieten dieselben drei Zugriffe an,
     * und die Anweisungslogik muss den Unterschied nicht kennen.
     */
    public interface Scope {

        Value find(String name);

        void declare(String name, Value value);

        boolean assign(String name, Value value);
    }

    /**
     * Was der Interpreter von der Welt braucht.
     *
     * <p>Als Schnittstelle, damit sich die Sprache ohne Minecraft prüfen
     * lässt — die Tests setzen hier eine einfache Fassung ein.
     */
    public interface Host {

        /** Bewegt Gegenstände und liefert, wie viele es wurden. */
        long move(Value amount, Value from, Value to);

        /** Wie viel von einer Art im Speicher liegt. */
        long count(Value what);

        /** Redstone-Stärke eines Geräts, 0 bis 15. */
        int redstone(String device);

        /** Setzt die Redstone-Stärke eines Geräts. */
        void setRedstone(String device, int strength);

        /** Schreibt eine Zeile ins Terminal. */
        void log(String message);

        /** Gibt es ein Gerät dieses Namens? */
        boolean hasDevice(String name);

        /** Vorschlag bei einem unbekannten Namen. */
        String suggestDevice(String name);
    }

    public Interpreter(Program program, Host host) {
        this.program = program;
        this.host = host;
    }

    // ---- Einstiegspunkte --------------------------------------------------

    /** Ruft eine Funktion des Programms auf. */
    public Value call(String name, List<Value> arguments) {
        Decl.Fn function = program.functions().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ScriptError("Unbekannte Funktion " + name + "."));
        return invoke(function.parameters().stream().map(Decl.Param::name).toList(),
                arguments, function.body());
    }

    /**
     * Wohin ein {@code emit} geht.
     *
     * <p>Ohne Empfänger löst der Interpreter die {@code on}-Blöcke selbst aus
     * und ist damit fertig. Das reicht nicht, sobald es wartende Abläufe gibt:
     * Ein Ereignis muss auch die wecken, und ein {@code on}-Block muss selbst
     * warten dürfen. Beides kann nur die Ablaufmaschine, also nimmt sie das
     * Ereignis entgegen, wenn es sie gibt.
     */
    @FunctionalInterface
    public interface EventSink {
        void emit(String event, List<Value> arguments);
    }

    private EventSink eventSink;

    public void setEventSink(EventSink eventSink) {
        this.eventSink = eventSink;
    }

    /** Gibt ein Ereignis weiter — an die Ablaufmaschine oder an sich selbst. */
    private void emit(String event, List<Value> arguments) {
        if (eventSink != null) {
            eventSink.emit(event, arguments);
            return;
        }
        fire(event, arguments);
    }

    /** Löst alle Blöcke aus, die auf dieses Ereignis hören. */
    public void fire(String event, List<Value> arguments) {
        for (Decl.On handler : program.handlers()) {
            if (handler.name().equals(event)) {
                invoke(handler.parameters(), arguments, handler.body());
            }
        }
    }

    private Value invoke(List<String> parameters, List<Value> arguments, Block body) {
        steps = 0;
        scopes.clear();
        Map<String, Value> frame = new HashMap<>();
        for (int i = 0; i < parameters.size(); i++) {
            frame.put(parameters.get(i),
                    i < arguments.size() ? arguments.get(i) : Value.Nothing.get());
        }
        scopes.push(frame);
        try {
            execute(body);
            return Value.Nothing.get();
        } catch (Return signal) {
            return signal.value;
        } finally {
            scopes.clear();
        }
    }

    // ---- Anweisungen ------------------------------------------------------

    private void execute(Block block) {
        scopes.push(new HashMap<>());
        try {
            for (Stmt statement : block.statements()) {
                execute(statement);
            }
        } finally {
            scopes.pop();
        }
    }

    private void execute(Stmt statement) {
        if (++steps > MAX_STEPS) {
            throw new ScriptError("Der Ablauf ist zu lang geworden und wurde angehalten.",
                    "Mehr als " + MAX_STEPS + " Schritte. Gehört das in einen Worker?");
        }
        switch (statement) {
            case Stmt.Let let -> declare(let.name(), evaluate(let.value()));
            case Stmt.Assign assign -> assign(assign);
            case Stmt.ExprStmt expr -> evaluate(expr.expr());
            case Stmt.Return ret -> throw new Return(
                    ret.value() == null ? Value.Nothing.get() : evaluate(ret.value()));
            case Stmt.Break ignored -> throw new BreakLoop();
            case Stmt.Continue ignored -> throw new ContinueLoop();
            case Stmt.If branch -> executeIf(branch);
            case Stmt.While loop -> executeWhile(loop);
            case Stmt.For loop -> executeFor(loop);
            case Stmt.Move move -> host.move(evaluate(move.amount()),
                    move.from() == null ? null : evaluate(move.from()),
                    evaluate(move.to()));
            case Stmt.Emit emit -> emit(emit.eventName(),
                    emit.arguments().stream().map(argument -> evaluate(argument.value())).toList());
            case Stmt.Sleep ignored -> throw new ScriptError(
                    "sleep kann diese Fassung noch nicht.",
                    "Wartender Code braucht Continuations, und die sind noch nicht gebaut.");
            case Stmt.Invalid ignored -> throw new ScriptError(
                    "Hier steht etwas, das nicht gelesen werden konnte.");
        }
    }

    private void executeIf(Stmt.If branch) {
        if (truth(evaluate(branch.condition()))) {
            execute(branch.thenBody());
        } else if (branch.elseIf() != null) {
            execute(branch.elseIf());
        } else if (branch.elseBlock() != null) {
            execute(branch.elseBlock());
        }
    }

    private void executeWhile(Stmt.While loop) {
        while (truth(evaluate(loop.condition()))) {
            if (++steps > MAX_STEPS) {
                throw new ScriptError("Die Schleife läuft zu lange.",
                        "Dauerhafte Aufgaben gehören in einen Worker, nicht in eine Schleife.");
            }
            try {
                execute(loop.body());
            } catch (BreakLoop stop) {
                return;
            } catch (ContinueLoop ignored) {
                // weiter mit der nächsten Runde
            }
        }
    }

    private void executeFor(Stmt.For loop) {
        Value iterable = evaluate(loop.iterable());
        List<Value> entries = switch (iterable) {
            case Value.ValueList list -> list.entries();
            case Value.Selection selection -> selection.items().stream()
                    .map(item -> (Value) new Value.ItemValue(item)).toList();
            default -> throw new ScriptError(
                    "Darüber lässt sich nicht laufen: " + iterable.describe() + ".",
                    "for braucht eine Liste, etwa crushers.members().");
        };
        for (Value entry : entries) {
            scopes.push(new HashMap<>(Map.of(loop.variable(), entry)));
            try {
                execute(loop.body());
            } catch (BreakLoop stop) {
                return;
            } catch (ContinueLoop ignored) {
                // weiter
            } finally {
                scopes.pop();
            }
        }
    }

    /**
     * Führt eine einzelne Anweisung aus und meldet, wie es weitergeht.
     *
     * <p>Hier steht, was eine Anweisung <b>tut</b>. Wie es danach weitergeht,
     * entscheidet der Aufrufer: Der gewöhnliche Weg ruft sich selbst auf, ein
     * Ablauf legt einen Rahmen auf seinen Stapel. Ohne diese Trennung gäbe es
     * jede Anweisung zweimal — und eine der beiden Fassungen liefe
     * irgendwann auseinander.
     */
    public Step perform(Stmt statement, Scope scope, long gameTime) {
        Scope previous = externalScope;
        externalScope = scope;
        try {
            return switch (statement) {
                case Stmt.Let let -> {
                    // Der Fall, um den es geht: let ergebnis = await …
                    // Hier wird nicht ausgewertet, sondern gewartet — und der
                    // Name gemerkt, unter dem das Ergebnis später landet.
                    if (let.value() instanceof Expr.Await await) {
                        yield awaitStep(await, let.name(), gameTime);
                    }
                    scope.declare(let.name(), evaluate(let.value()));
                    yield Step.Next.get();
                }
                case Stmt.Assign assign -> {
                    if (!(assign.target() instanceof Expr.Name name)) {
                        throw new ScriptError("Dorthin lässt sich nichts zuweisen.");
                    }
                    if (!scope.assign(name.value(), evaluate(assign.value()))) {
                        throw new ScriptError("Unbekannter Name " + name.value() + ".",
                                "Neue Namen bekommen ein let davor.");
                    }
                    yield Step.Next.get();
                }
                case Stmt.ExprStmt expr -> {
                    if (expr.expr() instanceof Expr.Await await) {
                        yield awaitStep(await, gameTime);
                    }
                    evaluate(expr.expr());
                    yield Step.Next.get();
                }
                case Stmt.If branch -> {
                    if (truth(evaluate(branch.condition()))) {
                        yield new Step.Enter(branch.thenBody(), false);
                    }
                    if (branch.elseBlock() != null) {
                        yield new Step.Enter(branch.elseBlock(), false);
                    }
                    if (branch.elseIf() != null) {
                        yield perform(branch.elseIf(), scope, gameTime);
                    }
                    yield Step.Next.get();
                }
                case Stmt.While loop -> truth(evaluate(loop.condition()))
                        ? new Step.Enter(loop.body(), true)
                        : Step.Next.get();
                case Stmt.Return ret -> new Step.Return(
                        ret.value() == null ? Value.Nothing.get() : evaluate(ret.value()));
                case Stmt.Break ignored -> new Step.Break();
                case Stmt.Continue ignored -> new Step.Continue();
                case Stmt.Move move -> {
                    host.move(evaluate(move.amount()),
                            move.from() == null ? null : evaluate(move.from()),
                            evaluate(move.to()));
                    yield Step.Next.get();
                }
                case Stmt.Emit emit -> {
                    emit(emit.eventName(), emit.arguments().stream()
                            .map(argument -> evaluate(argument.value())).toList());
                    yield Step.Next.get();
                }
                case Stmt.Sleep sleep -> {
                    Value duration = evaluate(sleep.duration());
                    if (!(duration instanceof Value.Duration ticks)) {
                        throw new ScriptError("sleep braucht eine Zeitangabe, etwa 5s.");
                    }
                    yield new Step.Sleep(gameTime + ticks.ticks());
                }
                case Stmt.For ignored -> throw new ScriptError(
                        "for kann ein wartender Ablauf noch nicht.",
                        "Schreibe die Schleife als while, oder ohne await darin.");
                case Stmt.Invalid ignored -> throw new ScriptError(
                        "Hier steht etwas, das nicht gelesen werden konnte.");
            };
        } finally {
            externalScope = previous;
        }
    }

    /**
     * Baut den Warteschritt aus einem {@code await}.
     *
     * <p>Die Frist ist absolute Spielzeit. Solange der Server steht, vergeht
     * keine — eine Frist von dreißig Sekunden läuft also nicht ab, während
     * niemand spielt. Für Minecraft ist das die richtige Bedeutung, auch wenn
     * es für den, der an eine Uhr denkt, nach einem Fehler aussieht.
     */
    private Step awaitStep(Expr.Await await, String resultName, long gameTime) {
        long deadline = -1;
        if (await.timeout() != null) {
            Value timeout = evaluate(await.timeout());
            if (!(timeout instanceof Value.Duration ticks)) {
                throw new ScriptError("timeout braucht eine Zeitangabe, etwa 30s.");
            }
            deadline = gameTime + ticks.ticks();
        }
        return new Step.Await(await.eventName(), await.where(), deadline,
                await.elseBody(), resultName);
    }

    /** Ein bloßes await ohne Zuweisung — dasselbe, nur ohne Namen. */
    private Step awaitStep(Expr.Await await, long gameTime) {
        return awaitStep(await, null, gameTime);
    }

    /** Gesetzt, solange eine Anweisung für einen Ablauf ausgeführt wird. */
    private Scope externalScope;

    // ---- Ausdrücke --------------------------------------------------------

    /**
     * Wertet einen Ausdruck mit fremden Namen aus.
     *
     * <p>Gebraucht für die {@code where}-Klausel eines {@code await}: Dort
     * sind die Parameter des Ereignisses sichtbar, die es im Ablauf selbst
     * nicht gibt.
     */
    public Value evaluateWith(Expr expr, Scope scope) {
        Scope previous = externalScope;
        externalScope = scope;
        try {
            return evaluate(expr);
        } finally {
            externalScope = previous;
        }
    }

    /** Wahrheitswert eines Ausdrucks, für Aufrufer außerhalb. */
    public boolean truthOf(Value value) {
        return truth(value);
    }

    private Value evaluate(Expr expr) {
        return switch (expr) {
            case Expr.IntLit literal -> new Value.Int(literal.value());
            case Expr.FloatLit literal -> new Value.Decimal(literal.value());
            case Expr.StringLit literal -> new Value.Text(literal.value());
            case Expr.BoolLit literal -> new Value.Bool(literal.value());
            case Expr.DurationLit literal -> new Value.Duration(literal.ticks());
            case Expr.It ignored -> lookup("it");
            case Expr.Name name -> resolveName(name.value());
            case Expr.NamePattern pattern -> new Value.Text(pattern.pattern());
            case Expr.Builtin builtin -> new Value.Builtin(
                    builtin.kind().name().toLowerCase(java.util.Locale.ROOT));
            case Expr.Selector selector -> new Value.Request(written(selector), -1);
            case Expr.Amount amount -> withAmount(evaluate(amount.selection()), amount.count());
            case Expr.Except except -> evaluate(except.base());
            case Expr.Unary unary -> unary(unary);
            case Expr.Binary binary -> binary(binary);
            case Expr.Member member -> member(member);
            case Expr.Call call -> call(call);
            case Expr.Await ignored -> throw new ScriptError(
                    "await kann diese Fassung noch nicht.",
                    "Wartender Code braucht Continuations, und die sind noch nicht gebaut.");
            case Expr.Lambda ignored -> throw new ScriptError(
                    "Eine Funktion als Wert kann diese Fassung noch nicht.");
            case Expr.Invalid ignored -> throw new ScriptError(
                    "Hier steht etwas, das nicht gelesen werden konnte.");
        };
    }

    /** Setzt die vorangestellte Menge auf eine Auswahl. */
    private static Value withAmount(Value selection, Long count) {
        if (selection instanceof Value.Request request && count != null) {
            return new Value.Request(request.selector(), count);
        }
        return selection;
    }

    private static String written(Expr.Selector selector) {
        String kind = selector.kind().name().toLowerCase(java.util.Locale.ROOT);
        return kind + ":" + (selector.hasNamespace() ? selector.namespace() + "/" : "")
                + selector.path();
    }

    private Value resolveName(String name) {
        Value local = externalScope != null ? externalScope.find(name) : find(name);
        if (local != null) {
            return local;
        }
        if (host.hasDevice(name)) {
            return new Value.Device(name);
        }
        String suggestion = host.suggestDevice(name);
        throw new ScriptError("Unbekannter Name " + name + ".",
                suggestion == null ? null : "Meintest du " + suggestion + "?");
    }

    private Value unary(Expr.Unary unary) {
        Value operand = evaluate(unary.operand());
        return switch (unary.op()) {
            case NOT -> new Value.Bool(!truth(operand));
            case NEGATE -> switch (operand) {
                case Value.Int value -> new Value.Int(-value.value());
                case Value.Decimal value -> new Value.Decimal(-value.value());
                default -> throw new ScriptError(
                        "Das lässt sich nicht negieren: " + operand.describe() + ".");
            };
        };
    }

    private Value binary(Expr.Binary binary) {
        // Und und Oder werten die rechte Seite nur aus, wenn es darauf ankommt.
        if (binary.op() == Expr.Binary.Op.AND) {
            return new Value.Bool(truth(evaluate(binary.left()))
                    && truth(evaluate(binary.right())));
        }
        if (binary.op() == Expr.Binary.Op.OR) {
            return new Value.Bool(truth(evaluate(binary.left()))
                    || truth(evaluate(binary.right())));
        }

        Value left = evaluate(binary.left());
        Value right = evaluate(binary.right());

        if (binary.op() == Expr.Binary.Op.EQ) {
            return new Value.Bool(equal(left, right));
        }
        if (binary.op() == Expr.Binary.Op.NEQ) {
            return new Value.Bool(!equal(left, right));
        }
        // Text plus Text hängt aneinander — praktisch für log().
        if (binary.op() == Expr.Binary.Op.ADD
                && (left instanceof Value.Text || right instanceof Value.Text)) {
            return new Value.Text(left.describe() + right.describe());
        }

        double a = number(left, binary.op().written());
        double b = number(right, binary.op().written());
        boolean whole = left instanceof Value.Int && right instanceof Value.Int;

        return switch (binary.op()) {
            case ADD -> numberValue(a + b, whole);
            case SUB -> numberValue(a - b, whole);
            case MUL -> numberValue(a * b, whole);
            case DIV -> {
                if (b == 0) {
                    throw new ScriptError("Durch null lässt sich nicht teilen.");
                }
                yield numberValue(a / b, whole);
            }
            case MOD -> {
                if (b == 0) {
                    throw new ScriptError("Durch null lässt sich nicht teilen.");
                }
                yield numberValue(a % b, whole);
            }
            case LT -> new Value.Bool(a < b);
            case LTE -> new Value.Bool(a <= b);
            case GT -> new Value.Bool(a > b);
            case GTE -> new Value.Bool(a >= b);
            default -> throw new ScriptError("Unbekannte Rechnung.");
        };
    }

    private Value member(Expr.Member member) {
        Value target = evaluate(member.target());
        String name = member.name();
        if (target instanceof Value.Device device) {
            return switch (name) {
                case "online" -> new Value.Bool(host.hasDevice(device.name()));
                case "name" -> new Value.Text(device.name());
                default -> throw new ScriptError(
                        "Ein Gerät hat kein " + name + ".",
                        "Bekannt sind online und name.");
            };
        }
        throw new ScriptError("Auf " + target.describe() + " gibt es kein " + name + ".");
    }

    private Value call(Expr.Call call) {
        List<Value> arguments = call.arguments().stream()
                .map(argument -> evaluate(argument.value()))
                .toList();

        // Aufruf einer Methode: crusher_1.insert(...), storage.count(...)
        if (call.callee() instanceof Expr.Member member) {
            return callMember(member, arguments);
        }
        if (call.callee() instanceof Expr.Name name) {
            return callFree(name.value(), arguments);
        }
        throw new ScriptError("Das lässt sich nicht aufrufen.");
    }

    private Value callMember(Expr.Member member, List<Value> arguments) {
        Value target = evaluate(member.target());
        String name = member.name();

        if (target instanceof Value.Builtin builtin && "storage".equals(builtin.name())) {
            if ("count".equals(name)) {
                return new Value.Int(host.count(arguments.isEmpty()
                        ? Value.Nothing.get() : arguments.get(0)));
            }
        }
        if (target instanceof Value.Device device) {
            return switch (name) {
                case "redstone" -> {
                    if (arguments.isEmpty()) {
                        yield new Value.Int(host.redstone(device.name()));
                    }
                    host.setRedstone(device.name(), (int) number(arguments.get(0), "redstone"));
                    yield Value.Nothing.get();
                }
                case "count" -> new Value.Int(host.count(arguments.isEmpty()
                        ? Value.Nothing.get() : arguments.get(0)));
                default -> throw new ScriptError(
                        "Ein Gerät kann kein " + name + ".",
                        "Bekannt sind redstone und count.");
            };
        }
        throw new ScriptError("Auf " + target.describe() + " gibt es kein " + name + ".");
    }

    private Value callFree(String name, List<Value> arguments) {
        if ("log".equals(name)) {
            host.log(arguments.isEmpty() ? "" : arguments.get(0).describe());
            return Value.Nothing.get();
        }
        boolean known = program.functions().stream()
                .anyMatch(function -> function.name().equals(name));
        if (!known) {
            throw new ScriptError("Unbekannte Funktion " + name + ".");
        }
        // Eigene Aufrufe bekommen einen frischen Rahmen, aber denselben Zähler.
        int outerSteps = steps;
        Deque<Map<String, Value>> outerScopes = new ArrayDeque<>(scopes);
        try {
            return call(name, arguments);
        } finally {
            steps = outerSteps;
            scopes.clear();
            scopes.addAll(outerScopes);
        }
    }

    // ---- Umgebung ---------------------------------------------------------

    private void declare(String name, Value value) {
        scopes.peek().put(name, value);
    }

    private void assign(Stmt.Assign assign) {
        if (!(assign.target() instanceof Expr.Name name)) {
            throw new ScriptError("Dorthin lässt sich nichts zuweisen.");
        }
        Value value = evaluate(assign.value());
        for (Map<String, Value> scope : scopes) {
            if (scope.containsKey(name.value())) {
                scope.put(name.value(), value);
                return;
            }
        }
        throw new ScriptError("Unbekannter Name " + name.value() + ".",
                "Neue Namen bekommen ein let davor.");
    }

    private Value find(String name) {
        for (Map<String, Value> scope : scopes) {
            Value value = scope.get(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Value lookup(String name) {
        Value value = find(name);
        if (value == null) {
            throw new ScriptError(name + " gibt es hier nicht.");
        }
        return value;
    }

    // ---- Umwandlungen -----------------------------------------------------

    private static boolean truth(Value value) {
        return switch (value) {
            case Value.Bool bool -> bool.value();
            case Value.Int number -> number.value() != 0;
            case Value.Nothing ignored -> false;
            default -> throw new ScriptError(
                    "Hier wird wahr oder falsch erwartet, gefunden wurde "
                            + value.describe() + ".");
        };
    }

    private static double number(Value value, String operation) {
        return switch (value) {
            case Value.Int number -> number.value();
            case Value.Decimal number -> number.value();
            case Value.Duration duration -> duration.ticks();
            default -> throw new ScriptError(
                    "Mit " + value.describe() + " lässt sich nicht rechnen (" + operation + ").");
        };
    }

    private static Value numberValue(double result, boolean whole) {
        return whole ? new Value.Int((long) result) : new Value.Decimal(result);
    }

    private static boolean equal(Value left, Value right) {
        if (left instanceof Value.Int a && right instanceof Value.Int b) {
            return a.value() == b.value();
        }
        if (left instanceof Value.Text a && right instanceof Value.Text b) {
            return a.value().equals(b.value());
        }
        if (left instanceof Value.Bool a && right instanceof Value.Bool b) {
            return a.value() == b.value();
        }
        if (left instanceof Value.Device a && right instanceof Value.Device b) {
            return a.name().equals(b.name());
        }
        return left.equals(right);
    }

    // ---- Ablaufsteuerung --------------------------------------------------

    /** Kein Fehler, sondern ein Sprung — deshalb ohne Stapel und ohne Meldung. */
    private static final class Return extends RuntimeException {
        private final transient Value value;

        Return(Value value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    private static final class BreakLoop extends RuntimeException {
        BreakLoop() {
            super(null, null, false, false);
        }
    }

    private static final class ContinueLoop extends RuntimeException {
        ContinueLoop() {
            super(null, null, false, false);
        }
    }
}
