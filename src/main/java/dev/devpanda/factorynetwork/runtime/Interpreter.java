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
 * Executes functions and event blocks.
 *
 * <p>A tree-walking interpreter, not bytecode. For the scale in question —
 * a few hundred statements per trigger — that is fast enough, and it is the
 * foundation the continuations will later build on: where a call returns
 * here, a state will later be saved.
 *
 * <p><b>What is still missing here:</b> waiting flows do not survive a server
 * restart. {@code await} and {@code sleep} halt the flow and give up instead
 * of saving their state. That is the mod's biggest open promise and
 * deliberately not half-built — persistence that only holds in simple cases
 * is worse than none at all, because nobody can rely on it.
 */
public final class Interpreter {

    /**
     * How many statements a flow may execute before it is aborted. Not a
     * tick budget with continuation, but a hard limit — as long as there are
     * no continuations, a flow cannot be paused and resumed later.
     *
     * <p><b>Read once per interpreter</b>, not at every step: the limit
     * should not shift in the middle of a loop when somebody reloads the
     * configuration.
     */
    private final int maxSteps = dev.devpanda.factorynetwork.FnConfig.stepBudget();

    /** How long a global list value may become. */
    private final int maxGlobalList =
            dev.devpanda.factorynetwork.FnConfig.globalListSize();

    private final Program program;
    private final Host host;
    private final Deque<Map<String, Value>> scopes = new ArrayDeque<>();
    private int steps;

    /**
     * The view onto the names of a flow.
     *
     * <p>The ordinary interpreter keeps its own stack; a flow holds its frames
     * as data. Both offer the same three accesses, and the statement logic
     * need not know the difference.
     */
    public interface Scope {

        Value find(String name);

        void declare(String name, Value value);

        boolean assign(String name, Value value);

        /**
         * Which plant the flow belongs to, or empty.
         *
         * <p>Only flows know that; the ordinary path never enters a template
         * function and therefore knows no instances.
         */
        default String devicePrefix() {
            return "";
        }
    }

    /**
     * What the interpreter needs from the world.
     *
     * <p>As an interface, so the language can be tested without Minecraft —
     * the tests plug in a simple version here.
     */
    public interface Host {

        /** Moves items and returns how many were moved. */
        long move(Value amount, Value from, Value to);

        /** How much of a kind lies in storage. */
        long count(Value what);

        /**
         * How much of a kind lies in a device.
         *
         * <p>Separate from {@link #count(Value)} because it is a different
         * question: {@code storage.count(…)} counts the network storage,
         * {@code brecher.count(…)} the crusher. Until 25 August both did the
         * same — the notation on the device measured the storage, and nobody
         * could see that.
         *
         * <p><b>The default speaks up instead of returning zero.</b> A host
         * without a world — the tests, the editor — cannot look into a
         * device, and an invented zero would be an answer to a question it
         * cannot answer.
         *
         * @param what the selection, or {@link Value.Nothing} for everything
         */
        default long countIn(String device, Value what) {
            throw new ScriptError("Ohne Welt lässt sich nicht in ein Gerät sehen.");
        }

        /**
         * Orders a crafting job and returns the job's identifier.
         *
         * <p>Zero means "no job" — and the only reason for that is a missing
         * recipe. Missing ingredients are not one: the job then waits and
         * says what is missing.
         *
         * <p>A host without a world knows no recipes and returns zero. That
         * is not an invented answer here, but the same as "does not exist".
         */
        default long craft(Value what) {
            return 0;
        }

        /**
         * How much energy a device holds, in FE.
         *
         * <p>A device without an energy store reports zero. <b>That is not an
         * invented answer</b>, unlike with {@code countIn}: a chest has no
         * energy, and zero is the truth about a chest. A host without a
         * world, by contrast, cannot look at all and says so.
         */
        default long energyIn(String device) {
            throw new ScriptError("Ohne Welt lässt sich kein Stromstand ablesen.");
        }

        /**
         * Touches the machine behind a device — a right-click.
         *
         * <p>For the machines that do nothing on their own: an altar waiting
         * for a hand, a lever, a button. Without this handle there would be
         * none for them.
         *
         * <p>A host without a world cannot touch anything and says so.
         *
         * @return whether the click went through
         */
        default boolean clickAt(String device) {
            throw new ScriptError("Ohne Welt lässt sich nichts anfassen.");
        }

        /**
         * How much energy the network holds, in FE.
         *
         * <p><b>Its own reserve, not a query into the world.</b> It lives in
         * the controller, which updates it every tick anyway — which is why
         * it is written without parentheses, unlike {@link #energyIn(String)}.
         *
         * <p>A host without a world has no network and says so instead of
         * inventing zero: zero would mean "empty", and empty is something
         * different from "does not exist here".
         */
        default long networkPower() {
            throw new ScriptError("Ohne Welt gibt es keinen Netzvorrat.");
        }

        /**
         * And how much fits in.
         *
         * <p>The reference value for it. A number alone cannot be displayed:
         * {@code progress(…)} wants a fraction, and "12,000 FE" means
         * something different in a network with one energy cell than in one
         * with thirty.
         */
        default long networkCapacity() {
            throw new ScriptError("Ohne Welt gibt es keinen Netzvorrat.");
        }

        /**
         * The devices of a group, in the order of their distribution.
         *
         * <p>Resolved against the network and not against the program: a
         * pattern picks up whatever is present right now. A host without a
         * world knows no groups and returns nothing.
         */
        default java.util.List<String> membersOf(String group) {
            return java.util.List.of();
        }

        /** Redstone strength of a device, 0 to 15. */
        int redstone(String device);

        /** Sets the redstone strength of a device. */
        void setRedstone(String device, int strength);

        /** Writes a line to the log. */
        void log(String message);

        /**
         * The same with a level.
         *
         * <p>Falls back to the plain version by default, so that a host that
         * only collects text — the tests do that — need not catch up.
         */
        default void log(LogLevel level, String message) {
            log(message);
        }

        /**
         * Who is currently writing — a worker, a flow, a function.
         *
         * <p>Set before execution and valid until somebody else sets it.
         * <b>Without it, a log line is worth little with thirty workers:</b>
         * one reads "coal is running low" and does not know which of them
         * means it.
         */
        default void setLogSource(String source) {
        }

        /** Is there a device of this name? */
        boolean hasDevice(String name);

        /** A suggestion for an unknown name. */
        String suggestDevice(String name);

        /**
         * All known device names.
         *
         * <p>The plants are derived from these. Whoever does not supply them
         * gets no multiblocks — and usually does not need them in a test
         * either.
         */
        default java.util.Collection<String> deviceNames() {
            return List.of();
        }

        /**
         * A global value, or {@code null}.
         *
         * <p><b>Not in the scope stack.</b> That lives for one call and is
         * thrown away afterwards; a global value lives for the factory and
         * survives the server restart. It therefore lives where stocks and
         * redstone live too — with the host.
         *
         * <p>{@code null} means "does not exist" and is something different
         * from {@link Value.Nothing}: a global value that contains nothing is
         * declared; one that was not declared is a typo.
         */
        default Value global(String name) {
            return null;
        }

        /** Sets a global value. */
        default void setGlobal(String name, Value value) {
        }

        /**
         * Puts something from the network storage into a device.
         *
         * @return how much arrived. <b>Less than requested is normal</b>,
         *         {@code 0} too: the machine may be full and the storage
         *         empty. It is only an error once the device is no longer
         *         there.
         */
        default long insertInto(String device, Value selection) {
            return 0;
        }

        /**
         * What lies in a device.
         *
         * <p>An empty list means "nothing inside" and is not an error — not
         * even when the device has no inventory at all. Whoever wants to know
         * whether it has one looks in the editor; the profile says so.
         */
        default List<Value> itemsIn(String device) {
            return List.of();
        }

        /**
         * What lies in specific slots of a device.
         *
         * <p><b>Over the whole inventory</b> and not over the side the
         * connector is attached to: only somebody who knows the machine
         * writes a slot number, and one connector per machine should be
         * enough.
         */
        default java.util.List<Value> itemsInSlots(String device,
                java.util.List<Integer> slots) {
            return java.util.List.of();
        }

        /**
         * What lies in the network storage.
         *
         * <p>The same shape as {@link #itemsIn}, a different source: a stock
         * is a stock, whether it lies in a cell or in a chest.
         */
        default List<Value> storedItems() {
            return List.of();
        }
    }

    /**
     * The program's filter templates, by name.
     *
     * <p>From the program and not via the {@link Host}: a template lives in
     * the code and not in the world. The host is the seam to what lies
     * outside — stocks, devices, redstone.
     */
    private final Map<String, dev.devpanda.factorynetwork.lang.ast.Decl.FilterTemplate>
            templates;

    public Interpreter(Program program, Host host) {
        this.program = program;
        this.host = host;
        this.templates = FilterTemplates.of(program);
    }

    // ---- Entry points -----------------------------------------------------

    /** Calls a function of the program. */
    public Value call(String name, List<Value> arguments) {
        Decl.Fn function = program.functions().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ScriptError("Unbekannte Funktion " + name + "."));
        return invoke(function.parameters().stream().map(Decl.Param::name).toList(),
                arguments, function.body());
    }

    /**
     * Where an {@code emit} goes.
     *
     * <p>Without a receiver, the interpreter fires the {@code on} blocks
     * itself and is done. That is not enough once there are waiting flows: an
     * event must wake those too, and an {@code on} block must be allowed to
     * wait itself. Only the flow engine can do both, so it receives the event
     * when it exists.
     */
    @FunctionalInterface
    public interface EventSink {
        void emit(String event, List<Value> arguments);
    }

    private EventSink eventSink;

    public void setEventSink(EventSink eventSink) {
        this.eventSink = eventSink;
    }

    /** Passes an event on — to the flow engine or to itself. */
    private void emit(String event, List<Value> arguments) {
        if (eventSink != null) {
            eventSink.emit(event, arguments);
            return;
        }
        fire(event, arguments);
    }

    /** Fires all blocks that listen for this event. */
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

    // ---- Statements -------------------------------------------------------

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
        if (++steps > maxSteps) {
            throw new ScriptError("Der Ablauf ist zu lang geworden und wurde angehalten.",
                    "Mehr als " + maxSteps + " Schritte. Gehört das in einen Worker?");
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
            if (++steps > maxSteps) {
                throw new ScriptError("Die Schleife läuft zu lange.",
                        "Dauerhafte Aufgaben gehören in einen Worker, nicht in eine Schleife.");
            }
            try {
                execute(loop.body());
            } catch (BreakLoop stop) {
                return;
            } catch (ContinueLoop ignored) {
                // on to the next round
            }
        }
    }

    /**
     * What one round per entry can be made from.
     *
     * <p>A selector like {@code tag:c/ores} is resolved in the process:
     * iterating over the items of a tag is the more common wish than holding
     * a request as a whole.
     */
    private List<Value> entriesOf(Expr iterable) {
        Value value = evaluate(iterable);
        if (value instanceof Value.Request request) {
            // Ask for the kind rather than guess: a fluid selector would
            // match nothing in the item resolution, and a loop over nothing
            // looks like one that had nothing to do.
            ResourceKind kind = ResourceKind.orItems(request.kind());
            return kind.resolve(iterable).stream()
                    .map(key -> (Value) new Value.Resource(kind, key)).toList();
        }
        return entriesOf(value);
    }

    private static List<Value> entriesOf(Value iterable) {
        return switch (iterable) {
            case Value.ValueList list -> list.entries();
            case Value.Selection selection -> selection.keys().stream()
                    .map(key -> (Value) new Value.Resource(selection.kind(), key)).toList();
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
                // continue
            } finally {
                scopes.pop();
            }
        }
    }

    /**
     * Executes a single statement and reports how to proceed.
     *
     * <p>This is where what a statement <b>does</b> lives. How to proceed
     * afterwards is decided by the caller: the ordinary path calls itself, a
     * flow pushes a frame onto its stack. Without this separation every
     * statement would exist twice — and one of the two versions would drift
     * apart eventually.
     */
    public Step perform(Stmt statement, Scope scope, long gameTime) {
        Scope previous = externalScope;
        externalScope = scope;
        try {
            return switch (statement) {
                case Stmt.Let let -> {
                    // The case this is all about: let ergebnis = await …
                    // Nothing is evaluated here; instead we wait — and
                    // remember the name under which the result lands later.
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
                        // A flow's frame knows only its own names. Without
                        // this branch every "modus = …" in a flow failed —
                        // and a flow is everything that runs via a button,
                        // an event or an await, so almost everything.
                        if (host.global(name.value()) == null) {
                            throw new ScriptError("Unbekannter Name " + name.value() + ".",
                                    "Neue Namen bekommen ein let davor. Ein Wert, den alle "
                                            + "Dateien sehen sollen, bekommt ein global auf "
                                            + "oberster Ebene.");
                        }
                        writeGlobal(name.value(), assigned);
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
     * Builds the wait step from an {@code await}.
     *
     * <p>The deadline is absolute game time. While the server is stopped,
     * none passes — so a thirty-second deadline does not expire while nobody
     * is playing. For Minecraft that is the right meaning, even if it looks
     * like a bug to somebody thinking of a clock.
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
     * Turns the call of a user-defined function into its own frame.
     *
     * <p>Only when the call stands alone — as a statement or on the right of
     * a {@code let}. If it sits inside a calculation like {@code let x = f() +
     * 2}, it takes the ordinary path and cannot wait there. That is the
     * honest limit: writing down a half-evaluated expression would mean
     * turning the expression tree itself into a state machine.
     *
     * @return the step, or {@code null} for anything that is not such a call
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
            // Something built-in like log(), or unknown — the ordinary path
            // takes care of that, including its error message.
            return null;
        }
        return new Step.Invoke(
                function.parameters().stream().map(Decl.Param::name).toList(),
                call.arguments().stream().map(argument -> evaluate(argument.value())).toList(),
                function.body(), resultName, null);
    }

    /**
     * The call of a function on a built plant.
     *
     * <p>{@code ore_plant_1.process(…)} runs in the body of the template, but
     * with the devices of this one plant. The frame remembers which one — and
     * writes it down too, otherwise a waiting flow would no longer know after
     * a restart which of the three plants it serves.
     *
     * @return {@code null} if this is not a plant at all
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

    /** A bare await without assignment — the same, just without a name. */
    private Step awaitStep(Expr.Await await, long gameTime) {
        return awaitStep(await, null, gameTime);
    }

    /** Set while a statement is being executed for a flow. */
    private Scope externalScope;

    /** Which plant the running flow belongs to, or empty. */
    private String devicePrefix() {
        return externalScope == null ? "" : externalScope.devicePrefix();
    }

    private Map<String, MultiblockInstances.Instance> instanceCache;
    private java.util.Collection<String> instanceCacheFor;

    /**
     * The plants of the network, remembered until the device names change.
     *
     * <p>Searching for them anew on every call would be noticeable with a
     * few dozen connectors — and the names only change when somebody has
     * been at them with the label gun.
     */
    private Map<String, MultiblockInstances.Instance> instances() {
        java.util.Collection<String> names = host.deviceNames();
        if (instanceCache == null || !names.equals(instanceCacheFor)) {
            instanceCacheFor = List.copyOf(names);
            instanceCache = MultiblockInstances.resolve(program, instanceCacheFor);
        }
        return instanceCache;
    }

    // ---- Expressions ------------------------------------------------------

    /**
     * Evaluates an expression with foreign names.
     *
     * <p>Needed for the {@code where} clause of an {@code await}: there the
     * parameters of the event are visible, which do not exist in the flow
     * itself.
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

    /** Truth value of an expression, for callers outside. */
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
            case Expr.ListLit literal -> new Value.ValueList(
                    literal.entries().stream().map(this::evaluate).toList());
            case Expr.It ignored -> lookup("it");
            case Expr.Name name -> resolveName(name.value());
            case Expr.NamePattern pattern -> new Value.Text(pattern.pattern());
            case Expr.Builtin builtin -> new Value.Builtin(
                    builtin.kind().name().toLowerCase(java.util.Locale.ROOT));
            case Expr.Selector selector -> new Value.Request(written(selector), -1);
            case Expr.Amount amount -> withAmount(evaluate(amount.selection()), amount.count());
            // An exclusion can only be applied after resolving: taking
            // something out of a tag means knowing the set. As long as
            // evaluate(except.base()) stood here, the exclusions were
            // dropped — except worked in the worker, but not in move and
            // count.
            case Expr.Except except -> resolvedSelection(except);
            case Expr.Move move -> new Value.Int(
                    doMove(move.amount(), move.from(), move.to()));
            case Expr.Range range -> rangeList(range);
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
     * Applies the leading amount to a selection.
     *
     * <p><b>For every kind of selection, not just written ones.</b> This once
     * handled only {@link Value.Request} — the case in which
     * {@code 8 item:iron_ore} appears literally in the program. A loop
     * variable, however, is a resolved value, and for that the amount was
     * silently dropped:
     *
     * <pre>
     * for sorte in storage.items() {
     *     move 8 sorte to ofen     // moved everything, not eight
     * }
     * </pre>
     *
     * <p>That is the worst bug this language can have — it empties a
     * warehouse while looking like a program that does what it says.
     */
    private static Value withAmount(Value selection, Long count) {
        if (count == null) {
            return selection;
        }
        return switch (selection) {
            case Value.Request request -> new Value.Request(request.selector(), count);
            case Value.Resource resource ->
                    new Value.Selection(resource.kind(), List.of(resource.key()), count);
            case Value.Selection choice ->
                    new Value.Selection(choice.kind(), choice.keys(), count);
            default -> selection;
        };
    }

    private static String written(Expr.Selector selector) {
        // The two without a type are written without a colon — the way they
        // are written in the program. An "all:" with an empty remainder would
        // be a lie about the form, and resolution would then look for an
        // item named nothing.
        if (selector.kind() == Expr.Selector.Kind.ALL
                || selector.kind() == Expr.Selector.Kind.POWER) {
            return selector.kind().name().toLowerCase(java.util.Locale.ROOT);
        }
        String kind = selector.kind().name().toLowerCase(java.util.Locale.ROOT);
        return kind + ":" + (selector.hasNamespace() ? selector.namespace() + "/" : "")
                + selector.path();
    }

    /**
     * A selection that is already resolved.
     *
     * <p>Needed wherever the written text alone no longer suffices — with
     * {@code except} and with a filter template. Resolution goes through the
     * same place as everywhere else, including the cache.
     */
    /**
     * Moves and returns how much was moved.
     *
     * <p>In one place, because there are three callers: the statement in a
     * function, the same statement as a step of a flow, and the expression.
     * Three versions drifted apart.
     */
    private long doMove(Expr amount, Expr from, Expr to) {
        return host.move(evaluate(amount),
                from == null ? null : evaluate(from),
                evaluate(to));
    }

    private Value resolvedSelection(Expr expr) {
        // Via the expression and not via its written form: a third-party
        // kind is merely CUSTOM in the enum, and which one it is stands in
        // the prefix.
        ResourceKind written = ResourceKind.of(
                dev.devpanda.factorynetwork.lang.WorkerKind.selectorOf(expr));
        ResourceKind kind = written == null ? ResourceKinds.ITEM : written;
        List<?> keys = kind.resolve(expr);
        if (keys.isEmpty()) {
            // Without Mekanism a chemical selection matches nothing, and the
            // message must say what the cause is — not pretend the pack is
            // to blame.
            throw kind == ResourceKinds.CHEMICAL
                    && !dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.installed()
                    ? new ScriptError("Dafür fehlt Mekanism.",
                            dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.hint())
                    : nothingSelected();
        }
        return new Value.Selection(kind, keys, amountIn(expr));
    }

    /**
     * Matching nothing is an error, not an empty selection.
     *
     * <p><b>Otherwise a mistyped tag would move everything.</b> To
     * {@code move}, an empty list means "no filter", and no filter means
     * "everything". As long as the exclusion was dropped, such an expression
     * took the path of the written selection and reported there; whoever
     * resolves on their own must report on their own.
     */
    private static ScriptError nothingSelected() {
        return new ScriptError("Die Auswahl trifft nichts.",
                "Gibt es den Gegenstand oder den Tag in diesem Pack? Und nimmt das "
                        + "except vielleicht alles wieder heraus?");
    }

    /**
     * {@code 1..5} as a list, both ends inclusive.
     *
     * <p>Backwards yields nothing: {@code 5..1} is empty and not an error.
     * Otherwise, whoever computes a bound would get an abort for a case they
     * did not foresee — and an empty list is exactly what is meant there.
     */
    private Value rangeList(Expr.Range range) {
        long from = Math.round(number(evaluate(range.from()), "Bereich"));
        long to = Math.round(number(evaluate(range.to()), "Bereich"));
        List<Value> entries = new java.util.ArrayList<>();
        for (long i = from; i <= to; i++) {
            entries.add(new Value.Int(i));
        }
        return new Value.ValueList(List.copyOf(entries));
    }

    /** The amount written before a selection, or -1 when none is given. */
    private static long amountIn(Expr expr) {
        return switch (expr) {
            case Expr.Amount amount -> amount.count() == null ? -1 : amount.count();
            case Expr.Except except -> amountIn(except.base());
            case null, default -> -1;
        };
    }

    /**
     * The selection behind the name of a filter template, or {@code null}.
     *
     * <p>Looked up <b>before</b> the devices: a template name lives in the
     * program, a device name comes from the label gun. If the meaning of a
     * program depended on how somebody later names a connector, it could no
     * longer be read from a distance. That a template shadows a device of
     * the same name is reported by the editor as a warning.
     */
    private Value templateValue(String name) {
        var template = templates.get(name);
        if (template == null) {
            return null;
        }
        return dev.devpanda.factorynetwork.lang.FilterKind.of(template)
                        == dev.devpanda.factorynetwork.lang.FilterKind.FLUID
                ? Value.Selection.ofFluids(FilterTemplates.fluids(template), -1)
                : Value.Selection.ofItems(FilterTemplates.items(template), -1);
    }

    private Value resolveName(String name) {
        Value local = externalScope != null ? externalScope.find(name) : find(name);
        if (local != null) {
            return local;
        }
        // A flow searches in its own frame and nowhere else. The global
        // value comes afterwards — the same order as in the ordinary call,
        // where find() fetches it last itself. Without this, a global value
        // was simply unknown in a flow.
        if (externalScope != null) {
            Value shared = host.global(name);
            if (shared != null) {
                return shared;
            }
        }
        // A constant lives in the program and not in the world: it comes
        // from the same source as a filter template and needs no detour via
        // the host.
        for (dev.devpanda.factorynetwork.lang.ast.Decl declaration : program.declarations()) {
            if (declaration instanceof dev.devpanda.factorynetwork.lang.ast.Decl.Const constant
                    && constant.name().equals(name)) {
                return Value.ofLiteral(constant.value());
            }
        }
        Value template = templateValue(name);
        if (template != null) {
            return template;
        }
        // A group lives in the program, its members live in the network.
        // That is why the value carries only the name — the lookup happens
        // only when somebody asks.
        for (dev.devpanda.factorynetwork.lang.ast.Decl declaration : program.declarations()) {
            if (declaration instanceof dev.devpanda.factorynetwork.lang.ast.Decl.Group group
                    && group.name().equals(name)) {
                return new Value.Group(name);
            }
        }
        // In a template a device name always means the plant's own device.
        // Only when the plant has none of that name does the rest of the
        // network count — otherwise a network storage would be unreachable
        // from within a template.
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
        // And and Or evaluate the right-hand side only when it matters.
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
        // Text plus text concatenates — handy for log().
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
        // The network itself. Without parentheses, like online: what stands
        // here the controller knows anyway — it is not a look into a foreign
        // machine.
        if (target instanceof Value.Builtin builtin && "network".equals(builtin.name())) {
            return switch (name) {
                case "power" -> new Value.Int(host.networkPower());
                case "capacity" -> new Value.Int(host.networkCapacity());
                default -> throw new ScriptError(
                        "Am Netz gibt es kein " + name + ".",
                        "Bekannt sind power und capacity, beide in FE. "
                                + "Der Stand einer einzelnen Maschine ist "
                                + "geraet.energy().");
            };
        }
        if (target instanceof Value.Device device) {
            return switch (name) {
                case "online" -> new Value.Bool(host.hasDevice(device.name()));
                case "name" -> new Value.Text(device.name());
                // Whoever ends up here has usually forgotten the parentheses
                // — "known are online and name" concealed exactly that and
                // let them believe there was nothing more.
                default -> throw new ScriptError(
                        "Ein Gerät hat kein " + name + ".",
                        "Ohne Klammern gibt es nur online und name. Mit Klammern: "
                                + "redstone(), count(…), insert(…), items() und click().");
            };
        }
        Value entry = entryMember(target, name);
        if (entry != null) {
            return entry;
        }
        if (target instanceof Value.Group group) {
            throw new ScriptError("Eine Gruppe hat kein " + name + ".",
                    "Mit Klammern: " + group.name() + ".members() und "
                            + group.name() + ".send(…).");
        }
        throw new ScriptError("Auf " + target.describe() + " gibt es kein " + name + ".");
    }

    /**
     * What a stock entry exposes: {@code it.item} and {@code it.amount}.
     *
     * <p><b>Two pieces of information, no more.</b> Without them a stock list
     * was unusable: {@code where} had nothing to compare, {@code sort} sorted
     * by nothing but zeros, and {@code sum} threw.
     *
     * <p>{@code it.item} exists only when the entry denotes exactly one kind.
     * A selection over several — a filter template, say — has no single
     * kind, and settling on the first would be a guess.
     *
     * @return {@code null} if this value is not a stock entry
     */
    private static Value entryMember(Value target, String name) {
        if (!(target instanceof Value.Selection selection)) {
            return null;
        }
        if ("amount".equals(name)) {
            return new Value.Int(selection.amount());
        }
        // The resource member is named after the kind: it.item, it.fluid,
        // it.chemical. Asking for a different one is not an exception but
        // simply not a member — the message for that lives in member().
        if (!selection.kind().prefix().equals(name)) {
            return null;
        }
        return selection.keys().size() == 1
                ? selection.single()
                : throwNoSingleKind(selection.kind(), selection.keys().size());
    }

    private static Value throwNoSingleKind(ResourceKind kind, int kinds) {
        throw new ScriptError("Diese Auswahl meint " + (kinds == 0 ? "keine" : kinds)
                + " Art" + (kinds == 1 ? "" : "en") + ".",
                "it." + kind.prefix() + " gibt es nur an einem Posten, der genau eine "
                        + "Art meint — etwa aus storage.items(). Bei einer Vorlage oder "
                        + "einem Tag wäre die eine Art geraten.");
    }

    private Value call(Expr.Call call) {
        // <b>Two calls do not evaluate their argument beforehand.</b>
        // {@code where} and {@code sort} need it for each entry
        // individually, with a different {@code it} — if it were computed
        // here, a single value would remain at the end, and the list would
        // gain nothing from it.
        if (call.callee() instanceof Expr.Member member
                && ("where".equals(member.name()) || "sort".equals(member.name()))
                && !call.arguments().isEmpty()) {
            Value target = evaluate(member.target());
            if (target instanceof Value.DeviceSlots view) {
                target = new Value.ValueList(host.itemsInSlots(view.device(), view.slots()));
            }
            if (target instanceof Value.ValueList list) {
                return perEntry(list, member.name(), call.arguments().get(0).value());
            }
        }

        List<Value> arguments = call.arguments().stream()
                .map(argument -> evaluate(argument.value()))
                .toList();

        // Call of a method: crusher_1.insert(...), storage.count(...)
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
            return listMember(list, name, arguments);
        }
        // When read, slots behave like a list of their entries.
        if (target instanceof Value.DeviceSlots view) {
            return listMember(new Value.ValueList(
                    host.itemsInSlots(view.device(), view.slots())), name, arguments);
        }
        if (target instanceof Value.Group group) {
            return switch (name) {
                case "members" -> new Value.ValueList(host.membersOf(group.name()).stream()
                        .map(each -> (Value) new Value.Device(each))
                        .toList());
                // send is move in short: from the storage to the group, and
                // how it is distributed is decided by the group itself.
                case "send" -> new Value.Int(host.move(
                        arguments.isEmpty() ? Value.Nothing.get() : arguments.get(0),
                        new Value.Builtin("storage"), group));
                default -> throw new ScriptError(
                        "Eine Gruppe kann kein " + name + ".",
                        "Bekannt sind members und send. Was ein einzelnes Gerät kann, "
                                + "fragt man an einem Mitglied: "
                                + group.name() + ".members().first()." + name + "()");
            };
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
                // <b>The device and not the storage.</b> This used to be
                // host.count(…), i.e. the same number as storage.count(…):
                // the notation said "in the crusher", but the network was
                // measured. All other members on the device have always
                // meant this device.
                case "count" -> new Value.Int(host.countIn(device.name(),
                        arguments.isEmpty() ? Value.Nothing.get() : arguments.get(0)));
                // Puts something from the network storage into the device and
                // reports how much arrived. Less than requested is normal:
                // the machine may be full, the storage empty.
                case "insert" -> new Value.Int(host.insertInto(device.name(),
                        arguments.isEmpty() ? Value.Nothing.get() : arguments.get(0)));
                // What is currently inside. Without the list operations from
                // sprache.md §12 this suffices for log() and for a count —
                // it is no more than that today, and that is more honest
                // than a list one can do nothing with.
                case "items" -> new Value.ValueList(host.itemsIn(device.name()));
                // <b>Slots see the whole inventory</b>, not just the side the
                // connector is attached to. Whoever writes a number knows
                // what they are doing — and one connector per machine should
                // be enough.
                case "slots" -> new Value.DeviceSlots(device.name(),
                        slotNumbers(arguments));
                // With parentheses like redstone() and count(): it is a look
                // into the world and not a name the program knows anyway.
                case "energy" -> new Value.Int(host.energyIn(device.name()));
                // With parentheses like everything that reaches into the
                // world — and here it reaches furthest: a click is the same
                // operation as a player's right-click, with everything that
                // entails.
                case "click" -> new Value.Bool(host.clickAt(device.name()));
                default -> throw new ScriptError(
                        "Ein Gerät kann kein " + name + ".",
                        "Bekannt sind redstone, count, insert, items, slots, energy "
                                + "und click.");
            };
        }
        throw new ScriptError("Auf " + target.describe() + " gibt es kein " + name + ".");
    }

    /**
     * The slot numbers from the argument of {@code slots(…)}.
     *
     * <p>A number or a range — {@code slots(2)} and {@code slots(1..5)}.
     * Counting starts at zero, as everywhere else one gets to see slot
     * numbers.
     */
    private static List<Integer> slotNumbers(List<Value> arguments) {
        if (arguments.isEmpty()) {
            throw new ScriptError("slots braucht eine Fachnummer.",
                    "Zum Beispiel: brecher_1.slots(2) oder brecher_1.slots(1..5)");
        }
        Value first = arguments.get(0);
        if (first instanceof Value.Int single) {
            return List.of((int) single.value());
        }
        if (first instanceof Value.ValueList list) {
            List<Integer> found = new java.util.ArrayList<>();
            for (Value entry : list.entries()) {
                if (entry instanceof Value.Int number) {
                    found.add((int) number.value());
                }
            }
            return List.copyOf(found);
        }
        throw new ScriptError("slots erwartet eine Zahl oder einen Bereich.",
                "Zum Beispiel: brecher_1.slots(2) oder brecher_1.slots(1..5)");
    }

    /**
     * What can be done with a list.
     *
     * <p>Three of the five that {@code sprache.md} §12 names. {@code where}
     * and {@code sort} are missing because they must evaluate their
     * expression <b>per element</b> — and arguments are evaluated beforehand
     * here. That is a separate intervention, not an afterthought.
     *
     * <p>An empty list is never an error: {@code first()} yields nothing,
     * {@code sum()} yields zero. Whoever computes over an empty stock has an
     * empty stock — not a program error.
     */
    /** The amount of a stock entry, or {@code null} for anything else. */
    private static Double amountOf(Value entry) {
        return entry instanceof Value.Selection selection
                ? (double) selection.amount()
                : null;
    }

    /**
     * What a list can do.
     *
     * <p><b>Everything returns something new, nothing mutates.</b> That is
     * not a preference but follows from two things already in place: a
     * mutating {@code add} would not be an assignment and would thus bypass
     * the guard for {@code const} and the protection in multiplayer — both
     * hang off the write path. And a waiting flow survives the restart via
     * the {@code ValueCodec}, which separates references to the same value:
     * before the restart a change would be visible through two names,
     * afterwards only through one. With immutable values that difference
     * does not exist.
     *
     * <p>One therefore writes {@code liste = liste.plus(x)} — wordier than
     * {@code add}, but the language writes state through an assignment
     * anyway.
     */
    private Value listMember(Value.ValueList list, String name, List<Value> arguments) {
        return switch (name) {
            case "count" -> new Value.Int(list.entries().size());
            case "plus" -> {
                if (arguments.isEmpty()) {
                    throw new ScriptError("plus braucht den Wert, der dazukommt.",
                            "Zum Beispiel: warteschlange = warteschlange.plus(\"eisen\")");
                }
                List<Value> longer = new java.util.ArrayList<>(list.entries());
                longer.add(arguments.get(0));
                yield new Value.ValueList(List.copyOf(longer));
            }
            // Compared as with == — every occurrence and not just the first:
            // "without iron" means without iron. Whoever wants to remove
            // only one has a queue, and for that there are first and rest.
            case "without" -> {
                if (arguments.isEmpty()) {
                    throw new ScriptError("without braucht den Wert, der wegfällt.",
                            "Zum Beispiel: warteschlange.without(\"eisen\")");
                }
                List<Value> kept = new java.util.ArrayList<>();
                for (Value entry : list.entries()) {
                    if (!equal(entry, arguments.get(0))) {
                        kept.add(entry);
                    }
                }
                yield new Value.ValueList(List.copyOf(kept));
            }
            // Everything except the first. Together with first this is the
            // queue — and the reason there is no access by index: a list one
            // reaches into at an arbitrary position also wants to be changed
            // at an arbitrary position.
            case "rest" -> new Value.ValueList(list.entries().isEmpty()
                    ? List.of()
                    : List.copyOf(list.entries().subList(1, list.entries().size())));
            case "first" -> list.entries().isEmpty()
                    ? Value.Nothing.get() : list.entries().get(0);
            case "sum" -> {
                double total = 0;
                boolean whole = true;
                for (Value entry : list.entries()) {
                    // A stock entry is not a number, but carries one: its
                    // amount. Without this line, sum threw on exactly the
                    // list it was meant for — the stock.
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
            // where and sort do not arrive here: they are intercepted in
            // call() because they need their expression unevaluated. Without
            // an argument they do end up here, and then exactly that is
            // missing.
            case "where", "sort" -> throw new ScriptError(
                    name + " braucht einen Ausdruck.",
                    "Zum Beispiel: items().where(it) oder items().sort(it).");
            default -> throw new ScriptError(
                    "Eine Liste kann kein " + name + ".",
                    "Bekannt sind count, first, sum, where, sort, plus, without "
                            + "und rest.");
        };
    }

    /**
     * {@code where} and {@code sort} — the expression applies per entry.
     *
     * <p><b>{@code it} is the entry</b>, and it lives in its own scope that
     * disappears again after each entry. That way it shadows nothing that
     * stands outside, and two nested calls do not get in each other's way —
     * the inner one lays its own {@code it} on top.
     *
     * <p>A comparison in {@code sort} needs numbers. Whatever is not one
     * counts as zero: sorting a list containing something incomparable is no
     * reason to halt the program — it simply ends up at the front.
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

    /** Evaluates an expression in which {@code it} is this entry. */
    private Value withIt(Value entry, Expr expression) {
        scopes.push(new HashMap<>(Map.of("it", entry)));
        try {
            return evaluate(expression);
        } finally {
            scopes.pop();
        }
    }

    /**
     * The level under which this call writes — or {@code null}.
     *
     * <p>{@code log()} stays and writes as {@code info}: it appears in every
     * piece of documentation and in every program that already exists.
     */
    /** Passes through who is currently writing. */
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
        Value calculated = math(name, arguments);
        if (calculated != null) {
            return calculated;
        }
        // Ordering is an instruction to the network like log() — no dot in
        // front, because it belongs not to a device but to the network.
        if ("craft".equals(name)) {
            return new Value.Int(host.craft(arguments.isEmpty()
                    ? Value.Nothing.get() : arguments.get(0)));
        }
        boolean known = program.functions().stream()
                .anyMatch(function -> function.name().equals(name));
        if (!known) {
            throw new ScriptError("Unbekannte Funktion " + name + ".");
        }
        // User-defined calls get a fresh frame, but the same counter.
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

    /**
     * The math functions, or {@code null} for any other name.
     *
     * <p><b>Whole stays whole.</b> {@code min(64, 20)} is an integer,
     * {@code min(64, 19.5)} a decimal — the same rule as when calculating
     * with {@code +} and {@code *}. Whoever compares piece counts gets a
     * piece count back and not 20.0.
     *
     * <p>{@code round}, {@code floor} and {@code ceil} always return an
     * integer: they exist to get rid of a decimal.
     */
    private Value math(String name, List<Value> arguments) {
        return switch (name) {
            case "min" -> fold(name, arguments, Math::min);
            case "max" -> fold(name, arguments, Math::max);
            case "abs" -> {
                double value = single(name, arguments);
                yield numberValue(Math.abs(value), allWhole(arguments));
            }
            case "round" -> new Value.Int(Math.round(single(name, arguments)));
            case "floor" -> new Value.Int((long) Math.floor(single(name, arguments)));
            case "ceil" -> new Value.Int((long) Math.ceil(single(name, arguments)));
            // <b>Both ends inclusive</b>, as with a range: random(1, 6) is a
            // die and not five sixths of one.
            case "random" -> {
                if (arguments.size() < 2) {
                    throw new ScriptError("random braucht zwei Zahlen.",
                            "Zum Beispiel: random(1, 6) — beide Enden zählen mit.");
                }
                long from = Math.round(number(arguments.get(0), "random"));
                long to = Math.round(number(arguments.get(1), "random"));
                if (to < from) {
                    throw new ScriptError("Bei random ist die zweite Zahl die größere.",
                            "random(1, 6), nicht random(6, 1).");
                }
                yield new Value.Int(from + (long) (Math.random() * (to - from + 1)));
            }
            case null, default -> null;
        };
    }

    private double single(String name, List<Value> arguments) {
        if (arguments.isEmpty()) {
            throw new ScriptError(name + " braucht eine Zahl.",
                    "Zum Beispiel: " + name + "(3.6)");
        }
        return number(arguments.get(0), name);
    }

    /** {@code min(3, 7, 5)} works too — in order, from the left. */
    private Value fold(String name, List<Value> arguments,
                       java.util.function.DoubleBinaryOperator step) {
        if (arguments.isEmpty()) {
            throw new ScriptError(name + " braucht mindestens zwei Zahlen.",
                    "Zum Beispiel: " + name + "(64, vorhanden)");
        }
        double result = number(arguments.get(0), name);
        for (int i = 1; i < arguments.size(); i++) {
            result = step.applyAsDouble(result, number(arguments.get(i), name));
        }
        return numberValue(result, allWhole(arguments));
    }

    private static boolean allWhole(List<Value> arguments) {
        return arguments.stream().allMatch(value -> value instanceof Value.Int);
    }

    // ---- Environment ------------------------------------------------------

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
        // No name in reach — but perhaps a global one. The same order as
        // when reading: what stands in the function takes precedence.
        if (host.global(name.value()) != null) {
            writeGlobal(name.value(), value);
            return;
        }
        throw new ScriptError("Unbekannter Name " + name.value() + ".",
                "Neue Namen bekommen ein let davor. Ein Wert, den alle Dateien "
                        + "sehen sollen, bekommt ein global auf oberster Ebene.");
    }

    /**
     * Writes a global value — the only place where that happens.
     *
     * <p>There are two ways to get here, the straight-line interpreter and
     * the flow engine, and both pass through here. That is the reason
     * {@code plus} has no mutating version: as long as every change is an
     * assignment, the cap only needs to stand in one place — and the same
     * goes for the guard over {@code const} and the protection in
     * multiplayer.
     */
    private void writeGlobal(String name, Value value) {
        if (value instanceof Value.ValueList list
                && list.entries().size() > maxGlobalList) {
            throw new ScriptError(
                    "Die Liste " + name + " wird zu lang (" + list.entries().size()
                            + " von " + maxGlobalList + ").",
                    "Ein globaler Wert übersteht den Neustart und liegt in der "
                            + "Weltdatei. Nimm heraus, was erledigt ist — "
                            + name + " = " + name + ".rest()");
        }
        host.setGlobal(name, value);
    }

    private Value find(String name) {
        for (Map<String, Value> scope : scopes) {
            Value value = scope.get(name);
            if (value != null) {
                return value;
            }
        }
        // The global values last, and explicitly not first: a let of the
        // same name in a function shadows them. The other way round, a
        // global value could never be shadowed, and a name somebody assigns
        // in their function would depend on what stands elsewhere in the
        // project.
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
     * Evaluates an expression as a condition.
     *
     * <p>For the {@code when} of a worker, which is checked every pass. A
     * fresh scope, because there is no environment there — only global
     * values, stocks and device states, and those all come from the host.
     *
     * <p><b>Meant to be side-effect free, but not enforced.</b> Whoever
     * writes a call into a {@code when} that moves something moves it every
     * pass. That is the consequence of a condition being an ordinary
     * expression, and the alternative — a second, restricted expression
     * language just for {@code when} — would be worse.
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

    // ---- Conversions ------------------------------------------------------

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

    // ---- Control flow -----------------------------------------------------

    /** Not an error but a jump — hence without a stack trace and without a message. */
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
