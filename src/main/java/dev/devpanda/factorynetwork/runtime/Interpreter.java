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

        /**
         * Zu welcher Anlage der Ablauf gehört, oder leer.
         *
         * <p>Nur Abläufe wissen das; der gewöhnliche Weg betritt keine
         * Vorlagenfunktion und kennt deshalb keine Instanzen.
         */
        default String devicePrefix() {
            return "";
        }
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

        /**
         * Wie viel von einer Art in einem Gerät liegt.
         *
         * <p>Getrennt von {@link #count(Value)}, weil es eine andere Frage
         * ist: {@code storage.count(…)} zählt den Netzspeicher,
         * {@code brecher.count(…)} den Brecher. Bis zum 25.08. taten beide
         * dasselbe — die Schreibweise am Gerät maß den Speicher, und niemand
         * konnte das sehen.
         *
         * <p><b>Die Vorgabe meldet sich, statt null zu liefern.</b> Ein Host
         * ohne Welt — die Prüfungen, der Editor — kann nicht in ein Gerät
         * sehen, und eine erfundene Null wäre eine Antwort auf eine Frage,
         * die er nicht beantworten kann.
         *
         * @param what die Auswahl, oder {@link Value.Nothing} für alles
         */
        default long countIn(String device, Value what) {
            throw new ScriptError("Ohne Welt lässt sich nicht in ein Gerät sehen.");
        }

        /** Redstone-Stärke eines Geräts, 0 bis 15. */
        int redstone(String device);

        /** Setzt die Redstone-Stärke eines Geräts. */
        void setRedstone(String device, int strength);

        /** Schreibt eine Zeile ins Protokoll. */
        void log(String message);

        /**
         * Dasselbe mit einer Stufe.
         *
         * <p>Als Voreinstellung auf die schlichte Fassung zurück, damit ein
         * Host, der nur Text sammelt — die Prüfungen tun das —, nichts
         * nachziehen muss.
         */
        default void log(LogLevel level, String message) {
            log(message);
        }

        /**
         * Wer gerade schreibt — ein Worker, ein Ablauf, eine Funktion.
         *
         * <p>Wird vor dem Ausführen gesetzt und gilt, bis jemand anderes sie
         * setzt. <b>Ohne sie ist eine Protokollzeile bei dreißig Workern
         * kaum etwas wert:</b> Man liest „Kohle wird knapp" und weiß nicht,
         * welcher von ihnen das meint.
         */
        default void setLogSource(String source) {
        }

        /** Gibt es ein Gerät dieses Namens? */
        boolean hasDevice(String name);

        /** Vorschlag bei einem unbekannten Namen. */
        String suggestDevice(String name);

        /**
         * Alle bekannten Gerätenamen.
         *
         * <p>Daraus werden die Anlagen erschlossen. Wer das nicht liefert,
         * bekommt keine Multiblocks — und braucht sie in einem Test meist
         * auch nicht.
         */
        default java.util.Collection<String> deviceNames() {
            return List.of();
        }

        /**
         * Ein globaler Wert, oder {@code null}.
         *
         * <p><b>Nicht im Stapel der Geltungsbereiche.</b> Der lebt für einen
         * Aufruf und wird danach weggeworfen; ein globaler Wert lebt für die
         * Fabrik und übersteht den Serverneustart. Er liegt deshalb dort, wo
         * auch Bestände und Redstone liegen — beim Host.
         *
         * <p>{@code null} heißt „gibt es nicht" und ist etwas anderes als
         * {@link Value.Nothing}: Ein globaler Wert, der nichts enthält, ist
         * erklärt; einer, der nicht erklärt wurde, ist ein Vertipper.
         */
        default Value global(String name) {
            return null;
        }

        /** Setzt einen globalen Wert. */
        default void setGlobal(String name, Value value) {
        }

        /**
         * Legt aus dem Netzspeicher etwas in ein Gerät.
         *
         * @return wie viel angekommen ist. <b>Weniger als gewünscht ist
         *         normal</b>, {@code 0} auch: Die Maschine kann voll sein und
         *         der Speicher leer. Ein Fehler ist es erst, wenn das Gerät
         *         nicht mehr da ist.
         */
        default long insertInto(String device, Value selection) {
            return 0;
        }

        /**
         * Was in einem Gerät liegt.
         *
         * <p>Eine leere Liste heißt „nichts drin" und ist kein Fehler — auch
         * dann nicht, wenn das Gerät gar kein Inventar hat. Wer wissen will,
         * ob es eines hat, sieht im Editor nach; das Profil sagt es.
         */
        default List<Value> itemsIn(String device) {
            return List.of();
        }

        /**
         * Was im Netzspeicher liegt.
         *
         * <p>Dieselbe Form wie {@link #itemsIn}, andere Quelle: Ein Bestand
         * ist ein Bestand, ob er in einer Zelle liegt oder in einer Kiste.
         */
        default List<Value> storedItems() {
            return List.of();
        }
    }

    /**
     * Die Filter-Vorlagen des Programms, nach Namen.
     *
     * <p>Aus dem Programm und nicht über den {@link Host}: Eine Vorlage steht
     * im Code und nicht in der Welt. Der Host ist die Naht zu dem, was
     * draußen liegt — Bestände, Geräte, Redstone.
     */
    private final Map<String, dev.devpanda.factorynetwork.lang.ast.Decl.FilterTemplate>
            templates;

    public Interpreter(Program program, Host host) {
        this.program = program;
        this.host = host;
        this.templates = FilterTemplates.of(program);
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
            case Stmt.Move move -> doMove(move.amount(), move.from(), move.to());
            case Stmt.Emit emit -> emit(emit.eventName(),
                    emit.arguments().stream().map(argument -> evaluate(argument.value())).toList());
            case Stmt.Sleep ignored -> throw new ScriptError(
                    "Hier lässt sich nicht warten.",
                    "sleep gibt es nur in Abläufen. Ein Worker prüft bei jedem Tick "
                            + "aufs Neue und braucht deshalb keine Wartezeit — was "
                            + "warten soll, gehört in eine Funktion.");
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

    /**
     * Woraus sich eine Runde je Eintrag machen lässt.
     *
     * <p>Ein Selektor wie {@code tag:c/ores} wird dabei aufgelöst: Über die
     * Gegenstände eines Tags zu laufen ist der häufigere Wunsch, als eine
     * Anfrage als Ganzes in der Hand zu halten.
     */
    private List<Value> entriesOf(Expr iterable) {
        Value value = evaluate(iterable);
        if (value instanceof Value.Request request) {
            // Nach der Art fragen und nicht raten: Ein Flüssigkeits-Selektor
            // träfe in der Gegenstandsauflösung nichts, und eine Schleife über
            // nichts sieht aus wie eine, die nichts zu tun hatte.
            if (request.kind() == Value.Request.Kind.FLUID) {
                return FluidSelection.resolve(iterable).stream()
                        .map(fluid -> (Value) new Value.FluidValue(fluid)).toList();
            }
            return ItemSelection.resolve(iterable).stream()
                    .map(item -> (Value) new Value.ItemValue(item)).toList();
        }
        return entriesOf(value);
    }

    private static List<Value> entriesOf(Value iterable) {
        return switch (iterable) {
            case Value.ValueList list -> list.entries();
            case Value.FluidSelection selection -> selection.fluids().stream()
                    .map(fluid -> (Value) new Value.FluidValue(fluid)).toList();
            case Value.Selection selection -> selection.items().stream()
                    .map(item -> (Value) new Value.ItemValue(item)).toList();
            default -> throw new ScriptError(
                    "Darüber lässt sich nicht laufen: " + iterable.describe() + ".",
                    "for braucht eine Liste, etwa storage.items() oder tag:c/ores.");
        };
    }

    private void executeFor(Stmt.For loop) {
        for (Value entry : entriesOf(loop.iterable())) {
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
                    Step invocation = invokeStep(let.value(), let.name());
                    if (invocation != null) {
                        yield invocation;
                    }
                    scope.declare(let.name(), evaluate(let.value()));
                    yield Step.Next.get();
                }
                case Stmt.Assign assign -> {
                    if (!(assign.target() instanceof Expr.Name name)) {
                        throw new ScriptError("Dorthin lässt sich nichts zuweisen.");
                    }
                    Value assigned = evaluate(assign.value());
                    if (!scope.assign(name.value(), assigned)) {
                        // Der Rahmen eines Ablaufs kennt nur seine eigenen
                        // Namen. Ohne diesen Zweig scheiterte jedes
                        // „modus = …" in einem Ablauf — und ein Ablauf ist
                        // alles, was über einen Knopf, ein Ereignis oder ein
                        // await läuft, also fast alles.
                        if (host.global(name.value()) == null) {
                            throw new ScriptError("Unbekannter Name " + name.value() + ".",
                                    "Neue Namen bekommen ein let davor. Ein Wert, den alle "
                                            + "Dateien sehen sollen, bekommt ein global auf "
                                            + "oberster Ebene.");
                        }
                        host.setGlobal(name.value(), assigned);
                    }
                    yield Step.Next.get();
                }
                case Stmt.ExprStmt expr -> {
                    if (expr.expr() instanceof Expr.Await await) {
                        yield awaitStep(await, gameTime);
                    }
                    Step invocation = invokeStep(expr.expr(), null);
                    if (invocation != null) {
                        yield invocation;
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
                    doMove(move.amount(), move.from(), move.to());
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
                case Stmt.For loop -> new Step.ForEach(loop.body(), loop.variable(),
                        entriesOf(loop.iterable()));
                case Stmt.Invalid ignored2 -> throw new ScriptError(
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

    /**
     * Macht aus dem Aufruf einer eigenen Funktion einen eigenen Rahmen.
     *
     * <p>Nur wenn der Aufruf allein dasteht — als Anweisung oder rechts von
     * einem {@code let}. Steckt er in einer Rechnung wie {@code let x = f() +
     * 2}, läuft er den gewöhnlichen Weg und kann dort nicht warten. Das ist
     * die ehrliche Grenze: Einen halb ausgewerteten Ausdruck aufzuschreiben
     * hieße, den Ausdrucksbaum selbst zur Zustandsmaschine zu machen.
     *
     * @return der Schritt, oder {@code null} für alles, was nicht so ein
     *         Aufruf ist
     */
    private Step invokeStep(Expr expr, String resultName) {
        if (!(expr instanceof Expr.Call call)) {
            return null;
        }
        if (call.callee() instanceof Expr.Member member
                && member.target() instanceof Expr.Name target) {
            Step onInstance = instanceStep(target.value(), member.name(), call, resultName);
            if (onInstance != null) {
                return onInstance;
            }
        }
        if (!(call.callee() instanceof Expr.Name name)) {
            return null;
        }
        Decl.Fn function = program.functions().stream()
                .filter(candidate -> candidate.name().equals(name.value()))
                .findFirst().orElse(null);
        if (function == null) {
            // Eingebautes wie log(), oder unbekannt — darum kümmert sich der
            // gewöhnliche Weg, samt seiner Fehlermeldung.
            return null;
        }
        return new Step.Invoke(
                function.parameters().stream().map(Decl.Param::name).toList(),
                call.arguments().stream().map(argument -> evaluate(argument.value())).toList(),
                function.body(), resultName, null);
    }

    /**
     * Der Aufruf einer Funktion an einer gebauten Anlage.
     *
     * <p>{@code ore_plant_1.process(…)} läuft im Rumpf der Vorlage, aber mit
     * den Geräten dieser einen Anlage. Der Rahmen merkt sich, zu welcher — und
     * schreibt es mit auf, sonst wüsste ein wartender Ablauf nach einem
     * Neustart nicht mehr, welche der drei Anlagen er bedient.
     *
     * @return {@code null}, wenn das gar keine Anlage ist
     */
    private Step instanceStep(String instanceName, String functionName, Expr.Call call,
            String resultName) {
        MultiblockInstances.Instance instance = instances().get(instanceName);
        if (instance == null) {
            return null;
        }
        if (instance.ambiguous()) {
            throw new ScriptError("Zu " + instanceName + " passen mehrere Vorlagen.",
                    "Die Anlage hat die Geräte von mehr als einer — benenne eines um.");
        }
        if (!instance.missing().isEmpty()) {
            throw new ScriptError("Der Anlage " + instanceName + " fehlt "
                    + String.join(", ", instance.missing()) + ".",
                    "Solange etwas fehlt, nimmt sie keine Aufrufe an.");
        }
        Decl.Fn function = instance.template().functions().stream()
                .filter(candidate -> candidate.name().equals(functionName))
                .findFirst().orElse(null);
        if (function == null) {
            throw new ScriptError(instance.template().name() + " kennt kein "
                    + functionName + ".");
        }
        return new Step.Invoke(
                function.parameters().stream().map(Decl.Param::name).toList(),
                call.arguments().stream().map(argument -> evaluate(argument.value())).toList(),
                function.body(), resultName, instanceName);
    }

    /** Ein bloßes await ohne Zuweisung — dasselbe, nur ohne Namen. */
    private Step awaitStep(Expr.Await await, long gameTime) {
        return awaitStep(await, null, gameTime);
    }

    /** Gesetzt, solange eine Anweisung für einen Ablauf ausgeführt wird. */
    private Scope externalScope;

    /** Zu welcher Anlage der laufende Ablauf gehört, oder leer. */
    private String devicePrefix() {
        return externalScope == null ? "" : externalScope.devicePrefix();
    }

    private Map<String, MultiblockInstances.Instance> instanceCache;
    private java.util.Collection<String> instanceCacheFor;

    /**
     * Die Anlagen des Netzes, gemerkt bis sich die Gerätenamen ändern.
     *
     * <p>Sie bei jedem Aufruf neu zu suchen wäre bei einigen Dutzend
     * Connectoren spürbar — und die Namen ändern sich nur, wenn jemand mit der
     * Beschriftungspistole daran war.
     */
    private Map<String, MultiblockInstances.Instance> instances() {
        java.util.Collection<String> names = host.deviceNames();
        if (instanceCache == null || !names.equals(instanceCacheFor)) {
            instanceCacheFor = List.copyOf(names);
            instanceCache = MultiblockInstances.resolve(program, instanceCacheFor);
        }
        return instanceCache;
    }

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
            // Eine Ausnahme lässt sich erst nach dem Auflösen anwenden:
            // Aus einem Tag etwas herauszunehmen heißt, die Menge zu kennen.
            // Solange hier evaluate(except.base()) stand, fielen die
            // Ausschlüsse weg — im Worker wirkte except, in move und count
            // nicht.
            case Expr.Except except -> resolvedSelection(except);
            case Expr.Move move -> new Value.Int(
                    doMove(move.amount(), move.from(), move.to()));
            case Expr.Unary unary -> unary(unary);
            case Expr.Binary binary -> binary(binary);
            case Expr.Member member -> member(member);
            case Expr.Call call -> call(call);
            case Expr.Await ignored -> throw new ScriptError(
                    "Hier lässt sich nicht warten.",
                    "await gibt es nur in Abläufen — in Funktionen und on-Blöcken. "
                            + "In einer Bedingung oder einem Worker wäre nicht zu "
                            + "sagen, worauf das Warten den Rest anhalten sollte.");
            case Expr.Lambda ignored -> throw new ScriptError(
                    "Eine Funktion als Wert kann diese Fassung noch nicht.");
            case Expr.Invalid ignored -> throw new ScriptError(
                    "Hier steht etwas, das nicht gelesen werden konnte.");
        };
    }

    /**
     * Setzt die vorangestellte Menge auf eine Auswahl.
     *
     * <p><b>Für jede Art von Auswahl, nicht nur für geschriebene.</b> Hier
     * stand einmal nur {@link Value.Request} — der Fall, in dem
     * {@code 8 item:iron_ore} wörtlich im Programm steht. Eine
     * Schleifenvariable ist aber ein aufgelöster Wert, und für die fiel die
     * Menge stillschweigend weg:
     *
     * <pre>
     * for sorte in storage.items() {
     *     move 8 sorte to ofen     // bewegte alles, nicht acht
     * }
     * </pre>
     *
     * <p>Das ist der schlimmste Fehler, den diese Sprache haben kann — er
     * räumt ein Lager leer und sieht dabei aus wie ein Programm, das tut, was
     * dasteht.
     */
    private static Value withAmount(Value selection, Long count) {
        if (count == null) {
            return selection;
        }
        return switch (selection) {
            case Value.Request request -> new Value.Request(request.selector(), count);
            case Value.ItemValue item -> new Value.Selection(List.of(item.item()), count);
            case Value.Selection choice -> new Value.Selection(choice.items(), count);
            case Value.FluidValue fluid -> new Value.FluidSelection(List.of(fluid.fluid()), count);
            case Value.FluidSelection choice ->
                    new Value.FluidSelection(choice.fluids(), count);
            default -> selection;
        };
    }

    private static String written(Expr.Selector selector) {
        String kind = selector.kind().name().toLowerCase(java.util.Locale.ROOT);
        return kind + ":" + (selector.hasNamespace() ? selector.namespace() + "/" : "")
                + selector.path();
    }

    /**
     * Eine Auswahl, die schon aufgelöst ist.
     *
     * <p>Gebraucht überall dort, wo der geschriebene Text allein nicht mehr
     * reicht — bei {@code except} und bei einer Filter-Vorlage. Aufgelöst
     * wird über dieselbe Stelle wie sonst auch, mitsamt Zwischenspeicher.
     */
    /**
     * Bewegt und liefert, wie viel es wurde.
     *
     * <p>An einer Stelle, weil es drei Aufrufer gibt: die Anweisung in einer
     * Funktion, dieselbe Anweisung als Schritt eines Ablaufs, und den
     * Ausdruck. Drei Fassungen liefen auseinander.
     */
    private long doMove(Expr amount, Expr from, Expr to) {
        return host.move(evaluate(amount),
                from == null ? null : evaluate(from),
                evaluate(to));
    }

    private Value resolvedSelection(Expr expr) {
        long amount = amountIn(expr);
        if (dev.devpanda.factorynetwork.lang.WorkerKind.resource(
                dev.devpanda.factorynetwork.lang.WorkerKind.selectorKind(expr))
                == Expr.Selector.Kind.FLUID) {
            List<net.minecraft.world.level.material.Fluid> fluids =
                    FluidSelection.resolve(expr);
            if (fluids.isEmpty()) {
                throw nothingSelected();
            }
            return new Value.FluidSelection(fluids, amount);
        }
        List<net.minecraft.world.item.Item> items = ItemSelection.resolve(expr);
        if (items.isEmpty()) {
            throw nothingSelected();
        }
        return new Value.Selection(items, amount);
    }

    /**
     * Nichts getroffen ist ein Fehler und keine leere Auswahl.
     *
     * <p><b>Sonst bewegte ein vertippter Tag alles.</b> Eine leere Liste
     * heißt für {@code move} „kein Filter", und kein Filter heißt „alles".
     * Solange die Ausnahme wegfiel, ging so ein Ausdruck über den Weg der
     * geschriebenen Auswahl und meldete sich dort; wer selbst auflöst, muss
     * selbst melden.
     */
    private static ScriptError nothingSelected() {
        return new ScriptError("Die Auswahl trifft nichts.",
                "Gibt es den Gegenstand oder den Tag in diesem Pack? Und nimmt das "
                        + "except vielleicht alles wieder heraus?");
    }

    /** Die Menge, die vor einer Auswahl steht, oder -1 ohne Angabe. */
    private static long amountIn(Expr expr) {
        return switch (expr) {
            case Expr.Amount amount -> amount.count() == null ? -1 : amount.count();
            case Expr.Except except -> amountIn(except.base());
            case null, default -> -1;
        };
    }

    /**
     * Die Auswahl hinter dem Namen einer Filter-Vorlage, oder {@code null}.
     *
     * <p>Nachgesehen wird <b>vor</b> den Geräten: Ein Vorlagenname steht im
     * Programm, ein Gerätename kommt aus der Beschriftungspistole. Hinge die
     * Bedeutung eines Programms daran, wie jemand später einen Connector
     * benennt, wäre es aus der Ferne nicht mehr zu lesen. Dass eine Vorlage
     * ein gleichnamiges Gerät verdeckt, meldet der Editor als Warnung.
     */
    private Value templateValue(String name) {
        var template = templates.get(name);
        if (template == null) {
            return null;
        }
        return dev.devpanda.factorynetwork.lang.FilterKind.of(template)
                        == dev.devpanda.factorynetwork.lang.FilterKind.FLUID
                ? new Value.FluidSelection(FilterTemplates.fluids(template), -1)
                : new Value.Selection(FilterTemplates.items(template), -1);
    }

    private Value resolveName(String name) {
        Value local = externalScope != null ? externalScope.find(name) : find(name);
        if (local != null) {
            return local;
        }
        // Ein Ablauf sucht in seinem eigenen Rahmen und sonst nirgends. Der
        // globale Wert kommt danach — dieselbe Reihenfolge wie beim
        // gewöhnlichen Aufruf, wo find() ihn zuletzt selbst holt. Ohne das
        // hier war ein globaler Wert in einem Ablauf schlicht unbekannt.
        if (externalScope != null) {
            Value shared = host.global(name);
            if (shared != null) {
                return shared;
            }
        }
        Value template = templateValue(name);
        if (template != null) {
            return template;
        }
        // In einer Vorlage meint ein Gerätename immer das eigene Gerät. Erst
        // wenn die Anlage keines dieses Namens hat, zählt der Rest des Netzes
        // — sonst wäre ein Netzspeicher aus einer Vorlage heraus unerreichbar.
        String prefix = devicePrefix();
        if (!prefix.isEmpty()) {
            String own = prefix + MultiblockInstances.SEPARATOR + name;
            if (host.hasDevice(own)) {
                return new Value.Device(own);
            }
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
                // Wer hierher kommt, hat meistens die Klammern vergessen —
                // „Bekannt sind online und name" verschwieg genau das und
                // ließ ihn glauben, es gäbe nichts weiter.
                default -> throw new ScriptError(
                        "Ein Gerät hat kein " + name + ".",
                        "Ohne Klammern gibt es nur online und name. Mit Klammern: "
                                + "redstone(), count(…), insert(…) und items().");
            };
        }
        Value entry = entryMember(target, name);
        if (entry != null) {
            return entry;
        }
        throw new ScriptError("Auf " + target.describe() + " gibt es kein " + name + ".");
    }

    /**
     * Was an einem Posten steht: {@code it.item} und {@code it.amount}.
     *
     * <p><b>Zwei Angaben, mehr nicht.</b> Ohne sie war eine Bestandsliste
     * nicht zu gebrauchen: {@code where} hatte nichts zu vergleichen,
     * {@code sort} sortierte nach lauter Nullen, und {@code sum} warf.
     *
     * <p>{@code it.item} gibt es nur, wenn der Posten genau eine Art meint.
     * Eine Auswahl über mehrere — etwa eine Filter-Vorlage — hat keine eine
     * Art, und sich für die erste zu entscheiden wäre geraten.
     *
     * @return {@code null}, wenn dieser Wert kein Posten ist
     */
    private static Value entryMember(Value target, String name) {
        if (target instanceof Value.Selection selection) {
            return switch (name) {
                case "amount" -> new Value.Int(selection.amount());
                case "item" -> selection.items().size() == 1
                        ? new Value.ItemValue(selection.items().get(0))
                        : throwNoSingleKind(selection.items().size());
                default -> null;
            };
        }
        if (target instanceof Value.FluidSelection selection) {
            return switch (name) {
                case "amount" -> new Value.Int(selection.amount());
                case "fluid" -> selection.fluids().size() == 1
                        ? new Value.FluidValue(selection.fluids().get(0))
                        : throwNoSingleKind(selection.fluids().size());
                default -> null;
            };
        }
        return null;
    }

    private static Value throwNoSingleKind(int kinds) {
        throw new ScriptError("Diese Auswahl meint " + (kinds == 0 ? "keine" : kinds)
                + " Art" + (kinds == 1 ? "" : "en") + ".",
                "it.item gibt es nur an einem Posten, der genau eine Art meint — "
                        + "etwa aus storage.items(). Bei einer Vorlage oder einem Tag "
                        + "wäre die eine Art geraten.");
    }

    private Value call(Expr.Call call) {
        // <b>Zwei Aufrufe werten ihr Argument nicht vorher aus.</b>
        // {@code where} und {@code sort} brauchen es für jeden Eintrag
        // einzeln, mit einem anderen {@code it} — würde es hier ausgerechnet,
        // stünde am Ende ein einziger Wert da, und die Liste hätte nichts
        // davon.
        if (call.callee() instanceof Expr.Member member
                && ("where".equals(member.name()) || "sort".equals(member.name()))
                && !call.arguments().isEmpty()) {
            Value target = evaluate(member.target());
            if (target instanceof Value.ValueList list) {
                return perEntry(list, member.name(), call.arguments().get(0).value());
            }
        }

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
            if ("items".equals(name)) {
                return new Value.ValueList(host.storedItems());
            }
        }
        if (target instanceof Value.ValueList list) {
            return listMember(list, name);
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
                // <b>Das Gerät und nicht der Speicher.</b> Hier stand
                // host.count(…), also dieselbe Zahl wie bei storage.count(…):
                // Die Schreibweise sagte „im Brecher", gemessen wurde das
                // Netz. Alle anderen Mitglieder am Gerät meinten schon immer
                // dieses Gerät.
                case "count" -> new Value.Int(host.countIn(device.name(),
                        arguments.isEmpty() ? Value.Nothing.get() : arguments.get(0)));
                // Legt aus dem Netzspeicher etwas ins Gerät und meldet, wie
                // viel ankam. Weniger als gewünscht ist normal: Die Maschine
                // kann voll sein, der Speicher leer.
                case "insert" -> new Value.Int(host.insertInto(device.name(),
                        arguments.isEmpty() ? Value.Nothing.get() : arguments.get(0)));
                // Was gerade drinliegt. Ohne die Listenoperationen aus
                // sprache.md §12 reicht das für log() und für eine Zählung —
                // mehr ist es heute nicht, und das ist ehrlicher als eine
                // Liste, mit der man nichts anfangen kann.
                case "items" -> new Value.ValueList(host.itemsIn(device.name()));
                default -> throw new ScriptError(
                        "Ein Gerät kann kein " + name + ".",
                        "Bekannt sind redstone, count, insert und items.");
            };
        }
        throw new ScriptError("Auf " + target.describe() + " gibt es kein " + name + ".");
    }

    /**
     * Was sich mit einer Liste anfangen lässt.
     *
     * <p>Drei von fünf, die {@code sprache.md} §12 nennt. {@code where} und
     * {@code sort} fehlen, weil sie ihren Ausdruck <b>je Element</b> auswerten
     * müssen — und Argumente werden hier vorher ausgewertet. Das ist ein
     * eigener Eingriff und kein Nachtrag.
     *
     * <p>Eine leere Liste ist nirgends ein Fehler: {@code first()} gibt
     * nichts, {@code sum()} gibt null. Wer über einen leeren Bestand rechnet,
     * hat einen leeren Bestand — keinen Programmfehler.
     */
    /** Die Menge eines Postens, oder {@code null} bei allem anderen. */
    private static Double amountOf(Value entry) {
        if (entry instanceof Value.Selection selection) {
            return (double) selection.amount();
        }
        if (entry instanceof Value.FluidSelection selection) {
            return (double) selection.amount();
        }
        return null;
    }

    private Value listMember(Value.ValueList list, String name) {
        return switch (name) {
            case "count" -> new Value.Int(list.entries().size());
            case "first" -> list.entries().isEmpty()
                    ? Value.Nothing.get() : list.entries().get(0);
            case "sum" -> {
                double total = 0;
                boolean whole = true;
                for (Value entry : list.entries()) {
                    // Ein Posten ist keine Zahl, trägt aber eine: seine
                    // Menge. Ohne diese Zeile warf sum genau an der Liste,
                    // für die es gedacht war — dem Bestand.
                    Double amount = amountOf(entry);
                    if (amount != null) {
                        total += amount;
                        continue;
                    }
                    total += number(entry, "sum");
                    whole = whole && entry instanceof Value.Int;
                }
                yield numberValue(total, whole);
            }
            // where und sort kommen hier nicht an: Sie werden in call()
            // abgefangen, weil sie ihren Ausdruck ungerechnet brauchen. Ohne
            // Argument landen sie doch hier, und dann fehlt genau das.
            case "where", "sort" -> throw new ScriptError(
                    name + " braucht einen Ausdruck.",
                    "Zum Beispiel: items().where(it) oder items().sort(it).");
            default -> throw new ScriptError(
                    "Eine Liste kann kein " + name + ".",
                    "Bekannt sind count, first, sum, where und sort.");
        };
    }

    /**
     * {@code where} und {@code sort} — der Ausdruck gilt je Eintrag.
     *
     * <p><b>{@code it} ist der Eintrag</b>, und er lebt in einem eigenen
     * Geltungsbereich, der nach jedem Eintrag wieder verschwindet. Damit
     * verdeckt er nichts, was außen steht, und zwei ineinandergeschachtelte
     * Aufrufe kommen sich nicht in die Quere — der innere legt sein eigenes
     * {@code it} darüber.
     *
     * <p>Ein Vergleich in {@code sort} braucht Zahlen. Was keine ist, zählt
     * als Null: Eine Liste zu sortieren, in der etwas Unvergleichbares steht,
     * ist kein Grund, das Programm anzuhalten — sie steht dann eben vorn.
     */
    private Value perEntry(Value.ValueList list, String operation, Expr expression) {
        if ("where".equals(operation)) {
            List<Value> kept = new java.util.ArrayList<>();
            for (Value entry : list.entries()) {
                if (truth(withIt(entry, expression))) {
                    kept.add(entry);
                }
            }
            return new Value.ValueList(kept);
        }
        List<Value> sorted = new java.util.ArrayList<>(list.entries());
        sorted.sort(java.util.Comparator.comparingDouble(entry -> {
            Value key = withIt(entry, expression);
            return key instanceof Value.Int number ? number.value()
                    : key instanceof Value.Decimal decimal ? decimal.value() : 0;
        }));
        return new Value.ValueList(sorted);
    }

    /** Wertet einen Ausdruck aus, in dem {@code it} dieser Eintrag ist. */
    private Value withIt(Value entry, Expr expression) {
        scopes.push(new HashMap<>(Map.of("it", entry)));
        try {
            return evaluate(expression);
        } finally {
            scopes.pop();
        }
    }

    /**
     * Die Stufe, unter der dieser Aufruf schreibt — oder {@code null}.
     *
     * <p>{@code log()} bleibt und schreibt als {@code info}: Es steht in
     * jeder Doku und in jedem Programm, das es schon gibt.
     */
    /** Reicht durch, wer gerade schreibt. */
    public void setLogSource(String source) {
        host.setLogSource(source);
    }

    private static LogLevel logLevel(String name) {
        if ("log".equals(name)) {
            return LogLevel.INFO;
        }
        return LogLevel.of(name);
    }

    private Value callFree(String name, List<Value> arguments) {
        LogLevel level = logLevel(name);
        if (level != null) {
            host.log(level, arguments.isEmpty() ? "" : arguments.get(0).describe());
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
        // Kein Name in Reichweite — aber vielleicht ein globaler. Dieselbe
        // Reihenfolge wie beim Lesen: Was in der Funktion steht, geht vor.
        if (host.global(name.value()) != null) {
            host.setGlobal(name.value(), value);
            return;
        }
        throw new ScriptError("Unbekannter Name " + name.value() + ".",
                "Neue Namen bekommen ein let davor. Ein Wert, den alle Dateien "
                        + "sehen sollen, bekommt ein global auf oberster Ebene.");
    }

    private Value find(String name) {
        for (Map<String, Value> scope : scopes) {
            Value value = scope.get(name);
            if (value != null) {
                return value;
            }
        }
        // Zuletzt die globalen Werte, und ausdrücklich nicht zuerst: Ein
        // gleichnamiges let in einer Funktion verdeckt sie. Andersherum
        // könnte man einen globalen Wert nie überdecken, und ein Name, den
        // jemand in seiner Funktion vergibt, hinge davon ab, was anderswo
        // im Projekt steht.
        return host.global(name);
    }

    private Value lookup(String name) {
        Value value = find(name);
        if (value == null) {
            throw new ScriptError(name + " gibt es hier nicht.");
        }
        return value;
    }

    /**
     * Wertet einen Ausdruck als Bedingung aus.
     *
     * <p>Für das {@code when} eines Workers, das je Durchgang geprüft wird.
     * Ein frischer Geltungsbereich, weil es dort keine Umgebung gibt — nur
     * globale Werte, Bestände und Gerätezustände, und die kommen alle vom
     * Host.
     *
     * <p><b>Ohne Seiteneffekte gemeint, aber nicht erzwungen.</b> Wer in ein
     * {@code when} einen Aufruf schreibt, der etwas bewegt, bewegt es je
     * Durchgang. Das ist die Folge davon, dass eine Bedingung ein gewöhnlicher
     * Ausdruck ist, und die Alternative — eine zweite, eingeschränkte
     * Ausdruckssprache nur für {@code when} — wäre schlimmer.
     */
    public boolean evaluateAsBoolean(Expr expr) {
        scopes.clear();
        scopes.push(new HashMap<>());
        steps = 0;
        try {
            return truth(evaluate(expr));
        } finally {
            scopes.clear();
        }
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
