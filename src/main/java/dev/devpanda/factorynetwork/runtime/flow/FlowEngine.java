package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.ast.Stmt;
import dev.devpanda.factorynetwork.runtime.Interpreter;
import dev.devpanda.factorynetwork.runtime.ScriptError;
import dev.devpanda.factorynetwork.runtime.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes flows that can wait.
 *
 * <p>The only difference from the ordinary interpreter is the control flow:
 * instead of calling itself recursively, this engine pushes frames onto a
 * stack and works through it step by step. What a statement <b>does</b> —
 * moving items, setting redstone, computing — still lives in one place only,
 * in the interpreter.
 *
 * <p>Only this makes it possible to halt a flow in the middle of a function,
 * persist it, and resume it after a server restart.
 */
public final class FlowEngine {

    /** How many statements a flow may execute per tick. */
    /**
     * How many steps <b>a single</b> flow may take per tick.
     *
     * <p>This is not a capacity model but a brake against the infinite loop:
     * a badly written {@code while} loop must not stall the server. How many
     * flows may run side by side is a different question and is determined
     * by the processors.
     */
    private static final int STEPS_PER_TICK = 500;

    /**
     * How many flows may run at the same time.
     *
     * <p>Set by the controller from the processors in its server racks.
     * Without one — in a test of the engine on its own, say — no limit
     * applies.
     */
    private int threadLimit = Integer.MAX_VALUE;

    /**
     * Is the network at a standstill?
     *
     * <p>Without a server rack or without power no flow moves — and it is
     * <b>frozen, not aborted</b>: it carries on where it was as soon as both
     * are back.
     *
     * <p>A flag of its own rather than a limit of zero: a limit only holds
     * back new flows, the running ones would keep going. And because
     * {@code startFlow} and {@code fireEvent} drive the engine directly,
     * without going through the controller's tick, the lock has to sit in
     * the engine and not in front of it.
     */
    private boolean frozen;

    /**
     * How many flows may exist at all — running and waiting ones.
     *
     * <p>The servers' memory. Unlike the processor limit this is not a queue
     * but a wall: whatever no longer fits fails visibly. <b>A flow that is
     * sleeping or waiting for an event occupies memory just like a computing
     * one</b> — after all, it sits somewhere, with all of its variables.
     *
     * <p>Without a controller — in a test of the engine on its own, say — no
     * limit applies.
     */
    private int memoryLimit = Integer.MAX_VALUE;

    /**
     * At most this many may be queued.
     *
     * <p>An unbounded queue would be a plant that accumulates work it never
     * gets through. Anything beyond this fails visibly and shows up among the
     * recent failures.
     */
    private static final int MAX_QUEUE = 32;

    private final Program program;
    private final Interpreter interpreter;
    private final BlockIndex blocks;
    private final Map<Long, Flow> flows = new LinkedHashMap<>();
    private final List<Flow> failed = new ArrayList<>();
    private final java.util.Deque<PendingEvent> pending = new java.util.ArrayDeque<>();
    private long nextId = 1;

    /** This many failed flows are kept around for inspection. */
    private static final int KEPT_FAILURES = 10;

    public FlowEngine(Program program, Interpreter interpreter) {
        this.program = program;
        this.interpreter = interpreter;
        this.blocks = BlockIndex.of(program);
        // From now on every emit of this interpreter goes through this engine.
        interpreter.setEventSink(this::post);
    }

    /**
     * Accepts an event without delivering it immediately.
     *
     * <p>An {@code emit} can sit in the middle of a flow, and delivering it
     * would touch the very stack that is currently being worked on. So the
     * event waits until between two steps. For the player it is still the
     * same tick.
     */
    public void setThreadLimit(int limit) {
        this.threadLimit = Math.max(0, limit);
    }

    public void setMemoryLimit(int limit) {
        this.memoryLimit = Math.max(0, limit);
    }

    public int memoryLimit() {
        return memoryLimit;
    }

    /** How many flows currently occupy memory. */
    public int inMemory() {
        return occupied() + queued();
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public int threadLimit() {
        return threadLimit;
    }

    /**
     * How many slots are currently occupied.
     *
     * <p><b>Counted, not tracked.</b> A counter would have to be decremented
     * on every exit path — finished, failed, aborted, stale, deadline passed
     * —, and the one forgotten exit would be a network that starts nothing
     * any more after an hour. The list is small; counting costs nothing and
     * cannot leak.
     */
    public int occupied() {
        int count = 0;
        for (Flow flow : flows.values()) {
            switch (flow.status()) {
                case RUNNING, SLEEPING, AWAITING -> count++;
                default -> { }
            }
        }
        return count;
    }

    /** How many are currently queued. */
    public int queued() {
        int count = 0;
        for (Flow flow : flows.values()) {
            if (flow.status() == Flow.Status.QUEUED) {
                count++;
            }
        }
        return count;
    }

    /**
     * Queues a freshly built flow if no slot is free.
     *
     * <p>The flow is already fully set up — it is merely waiting. That way it
     * stays visible in the terminal instead of disappearing into a second
     * list nobody looks at.
     */
    private boolean admit(Flow flow) {
        // Memory first: a flow for which there is no room in memory does not
        // wait either — it simply does not fit.
        //
        // <b>Not while the network is down.</b> Then the limit is zero,
        // because there is no server, and every flow would be lost instead
        // of waiting until one is back. Delay is recoverable, loss is not —
        // the same answer as for overload.
        if (!frozen && inMemory() >= memoryLimit) {
            // "The storage is full" means something else elsewhere: the cells
            // in the drive. Anyone who knows both would go and install cells
            // on seeing that message.
            flow.fail("Kein Platz im Arbeitsspeicher — " + memoryLimit
                    + " Abläufe passen hinein. Bau größere Speicher in den Serverschrank.");
            remember(flow);
            return false;
        }
        // Asked before registering: otherwise the new flow would count
        // itself, and the second of two slots would already be taken before
        // it gets it.
        if (occupied() < threadLimit) {
            return true;
        }
        if (queued() >= MAX_QUEUE) {
            flow.fail("Zu viele Abläufe stehen an — " + MAX_QUEUE + " ist die Grenze.");
            remember(flow);
            return false;
        }
        flow.queue("wartet auf ein freies Rechenwerk");
        return true;
    }

    /**
     * Pulls in what is queued, as long as slots are free.
     *
     * <p>Oldest first, and only at this one point in the tick. A rule that
     * is fixed and can be explained — otherwise "why didn't mine run" has no
     * answer.
     */
    private void promote() {
        List<Flow> waiting = flows.values().stream()
                .filter(flow -> flow.status() == Flow.Status.QUEUED)
                .sorted(java.util.Comparator.comparingLong(Flow::id))
                .toList();
        for (Flow flow : waiting) {
            if (occupied() >= threadLimit) {
                return;
            }
            flow.dequeue();
        }
    }

    public void post(String event, List<Value> arguments) {
        pending.add(new PendingEvent(event, List.copyOf(arguments)));
    }

    /** An event waiting to be delivered. */
    private record PendingEvent(String event, List<Value> arguments) {}

    public Map<Long, Flow> flows() {
        return flows;
    }

    public BlockIndex blocks() {
        return blocks;
    }

    public Program program() {
        return program;
    }

    public long nextId() {
        return nextId;
    }

    public void setNextId(long nextId) {
        this.nextId = nextId;
    }

    /** Adopts a loaded flow without starting it over. */
    public void adopt(Flow flow) {
        flows.put(flow.id(), flow);
        if (flow.id() >= nextId) {
            nextId = flow.id() + 1;
        }
    }

    /** Starts a flow with one of the program's functions. */
    public Flow start(String functionName, List<Value> arguments) {
        Decl.Fn function = program.functions().stream()
                .filter(candidate -> candidate.name().equals(functionName))
                .findFirst()
                .orElseThrow(() -> new ScriptError("Unbekannte Funktion " + functionName + "."));

        Flow flow = new Flow(nextId++, functionName);
        flow.setStructureHash(blocks.structureHash());
        Frame frame = new Frame(function.body(), false);
        for (int i = 0; i < function.parameters().size(); i++) {
            frame.locals().put(function.parameters().get(i).name(),
                    i < arguments.size() ? arguments.get(i) : Value.Nothing.get());
        }
        flow.push(frame);
        if (admit(flow)) {
            flows.put(flow.id(), flow);
        }
        return flow;
    }

    /**
     * Is a flow currently standing by, waiting for this event?
     *
     * <p><b>An {@code await} is just as much a listener as an {@code on}.</b>
     * Counting only the blocks would treat an event somebody is waiting for
     * as unheard — and the waiter would never wake up again. For redstone and
     * for inventories this question decides whether anyone looks at all.
     */
    public boolean awaits(String event) {
        return flows.values().stream().anyMatch(flow -> flow.waitsFor(event));
    }

    /** Starts a flow for every block listening for this event. */
    public List<Flow> fire(String event, List<Value> arguments) {
        List<Flow> started = new ArrayList<>();
        for (Decl.On handler : program.handlers()) {
            if (!handler.name().equals(event)) {
                continue;
            }
            Flow flow = new Flow(nextId++, "on " + event);
            flow.setStructureHash(blocks.structureHash());
            Frame frame = new Frame(handler.body(), false);
            for (int i = 0; i < handler.parameters().size(); i++) {
                frame.locals().put(handler.parameters().get(i),
                        i < arguments.size() ? arguments.get(i) : Value.Nothing.get());
            }
            flow.push(frame);
            if (admit(flow)) {
                flows.put(flow.id(), flow);
                started.add(flow);
            }
        }
        return started;
    }

    /**
     * One tick: wakes whatever is due and lets the running flows do their
     * work.
     */
    public void tick(long gameTime) {
        if (frozen) {
            // Events stay put instead of getting lost: they arrive as soon as
            // the network is running again.
            return;
        }
        // An event can wake flows that in turn fire events. That may carry on
        // within the same tick, but not endlessly: two flows waking each
        // other up would otherwise stall the server. Whatever is left waits
        // for the next tick.
        for (int runde = 0; runde < EVENT_ROUNDS; runde++) {
            deliver();
            advanceAll(gameTime);
            if (pending.isEmpty()) {
                return;
            }
        }
    }

    /** This many event rounds may follow one another within a single tick. */
    private static final int EVENT_ROUNDS = 8;

    private void deliver() {
        if (pending.isEmpty()) {
            return;
        }
        List<PendingEvent> runde = List.copyOf(pending);
        pending.clear();
        for (PendingEvent event : runde) {
            wake(event.event(), event.arguments());
            fire(event.event(), event.arguments());
        }
    }

    private void advanceAll(long gameTime) {
        for (Flow flow : List.copyOf(flows.values())) {
            if (flow.isDue(gameTime)) {
                flow.resume();
            } else if (flow.hasTimedOut(gameTime)) {
                // The deadline has passed: the else branch takes over.
                timeOut(flow);
            }
            if (flow.status() == Flow.Status.RUNNING) {
                advance(flow, gameTime);
            }
        }
        flows.values().removeIf(flow -> {
            if (!flow.isFinished()) {
                return false;
            }
            if (flow.status() == Flow.Status.FAILED) {
                remember(flow);
            }
            return true;
        });
        // Clean up first, then promote: a slot that became free in this tick
        // should be filled again in this very tick.
        promote();
    }

    /**
     * Keeps the most recent failed flows.
     *
     * <p>A flow that died used to vanish from the list, and its reason with
     * it. Someone building a plant at night would otherwise see nothing the
     * next morning except that nothing happened.
     */
    private void remember(Flow flow) {
        failed.add(flow);
        while (failed.size() > KEPT_FAILURES) {
            failed.remove(0);
        }
    }

    /** The most recent failed flows, oldest first. */
    public List<Flow> failed() {
        return List.copyOf(failed);
    }

    /**
     * Wakes flows waiting for this event.
     *
     * <p>The {@code where} clause has a say: a flow waiting for <em>its</em>
     * job must not be woken by somebody else's events. If the clause does not
     * match, the flow stays put — and its deadline keeps running, because it
     * is absolute game time.
     *
     * <p>The result lands under the name the {@code await} specified — where
     * the program says {@code let ergebnis = await …}.
     */
    public int wake(String event, List<Value> arguments) {
        Decl.Event declaration = program.event(event);
        int woken = 0;
        for (Flow flow : List.copyOf(flows.values())) {
            if (!flow.waitsFor(event)) {
                continue;
            }
            Expr.Await await = awaitOf(flow);
            if (await != null && await.where() != null
                    && !matches(flow, await.where(), declaration, arguments)) {
                continue;
            }
            flow.bind(flow.awaitResultName(), resultOf(arguments));
            flow.resume();
            woken++;
        }
        return woken;
    }

    /**
     * Checks the {@code where} clause.
     *
     * <p>The event's parameters are visible here — {@code where
     * amount > 100} refers to the declaration's parameter, not to a variable
     * of the flow. Only what is not found there does the flow look up in
     * itself; that allows writing {@code where id == jobId}, with {@code id}
     * from the event and {@code jobId} from the flow.
     *
     * <p>An error in the condition must not spoil the event for everyone
     * else: it halts only this one flow.
     */
    private boolean matches(Flow flow, Expr where, Decl.Event declaration,
            List<Value> arguments) {
        Map<String, Value> bindings = new LinkedHashMap<>();
        if (declaration != null) {
            for (int i = 0; i < declaration.parameters().size(); i++) {
                bindings.put(declaration.parameters().get(i).name(),
                        i < arguments.size() ? arguments.get(i) : Value.Nothing.get());
            }
        }
        try {
            return interpreter.truthOf(interpreter.evaluateWith(where,
                    new EventScope(bindings, flow)));
        } catch (ScriptError error) {
            flow.fail(error.toString());
            return false;
        }
    }

    /** An event with one value yields that value, with several a list. */
    private static Value resultOf(List<Value> arguments) {
        return switch (arguments.size()) {
            case 0 -> Value.Nothing.get();
            case 1 -> arguments.get(0);
            default -> new Value.ValueList(List.copyOf(arguments));
        };
    }

    /**
     * Recovers the {@code await} statement a flow is standing on.
     *
     * <p>The counter was already advanced when the wait began, so that the
     * flow continues after the {@code await} once it wakes up — the statement
     * itself therefore sits one position earlier. Being able to find it again
     * this way saves persisting {@code where} and the {@code else} branch:
     * both live in the program, not in the flow.
     */
    private static Expr.Await awaitOf(Flow flow) {
        Frame frame = flow.top();
        if (frame == null || frame.index() == 0
                || frame.index() > frame.block().statements().size()) {
            return null;
        }
        Stmt statement = frame.block().statements().get(frame.index() - 1);
        return switch (statement) {
            case Stmt.Let let when let.value() instanceof Expr.Await await -> await;
            case Stmt.ExprStmt expr when expr.expr() instanceof Expr.Await await -> await;
            default -> null;
        };
    }

    /**
     * Aborts a flow.
     *
     * <p>One half of the choice for {@code STALE}, and at the same time the
     * way to get rid of a flow that is waiting for an event nobody fires any
     * more.
     */
    public boolean abort(long id) {
        Flow flow = flows.remove(id);
        if (flow == null) {
            return false;
        }
        flow.fail("abgebrochen");
        remember(flow);
        return true;
    }

    /** The other half: let the flow continue where it stopped. */
    public boolean unstale(long id) {
        Flow flow = flows.get(id);
        if (flow == null || flow.status() != Flow.Status.STALE) {
            return false;
        }
        flow.unstale(blocks.structureHash());
        return true;
    }

    /** Flows waiting for the player's decision. */
    public List<Flow> stale() {
        return flows.values().stream()
                .filter(flow -> flow.status() == Flow.Status.STALE)
                .toList();
    }

    /** Lets a flow work until it waits or uses up its budget. */
    private void advance(Flow flow, long gameTime) {
        int steps = 0;
        while (flow.status() == Flow.Status.RUNNING && steps++ < STEPS_PER_TICK) {
            if (flow.stack().isEmpty()) {
                flow.finish(Value.Nothing.get());
                return;
            }
            Frame frame = flow.top();
            if (frame.atEnd()) {
                leaveFrame(flow, frame);
                continue;
            }
            Stmt statement = frame.block().statements().get(frame.index());
            try {
                // Whoever is writing is noted next to the log line. Without
                // that, the log says "coal is running low" and nobody knows
                // which of the thirty flows means it.
                interpreter.setLogSource(flow.entryPoint());
                Step step = interpreter.perform(statement, new FlowScope(flow), gameTime);
                apply(flow, frame, step);
            } catch (ScriptError error) {
                flow.fail(error.toString());
                return;
            }
        }
    }

    /** A new frame belongs to the same multiblock as the one it comes from. */
    private static Frame inherit(Frame parent, Frame child) {
        child.setDevicePrefix(parent.devicePrefix());
        return child;
    }

    /**
     * Returns from a called function.
     *
     * <p>A {@code return} no longer necessarily ends the flow: if there is a
     * call above it, the value lands there under its name and execution
     * continues after the call. Only in the outermost frame is the flow
     * finished — and its return value is that of the entry point.
     */
    private void returnFrom(Flow flow, Value value) {
        while (!flow.stack().isEmpty()) {
            Frame frame = flow.pop();
            if (frame.exitOnLeave()) {
                // The else branch leaves the flow — with the value it
                // returns, not with nothing.
                flow.finish(value);
                return;
            }
            if (frame.isCall() && !flow.stack().isEmpty()) {
                if (frame.resultName() != null) {
                    flow.top().locals().put(frame.resultName(), value);
                }
                flow.top().advance();
                return;
            }
        }
        flow.finish(value);
    }

    /**
     * Leaves a frame.
     *
     * <p>A loop body starts over — the condition is checked on the next pass
     * of the {@code while} statement, which sits one level below.
     */
    private void leaveFrame(Flow flow, Frame frame) {
        // An iteration over a list keeps its frame: the position lives there
        // and nowhere else.
        if (frame.hasIteration() && frame.nextIteration()) {
            frame.restart();
            return;
        }
        flow.pop();
        if (frame.exitOnLeave() || flow.stack().isEmpty()) {
            flow.finish(Value.Nothing.get());
            return;
        }
        if (frame.isCall()) {
            // A function that ends without a return returns nothing.
            if (frame.resultName() != null) {
                flow.top().locals().put(frame.resultName(), Value.Nothing.get());
            }
            flow.top().advance();
            return;
        }
        if (!frame.isLoop() || frame.hasIteration()) {
            // The statement that opened the block is done.
            //
            // With while it is not: its condition has to be checked again.
            // With for it is — the list is exhausted. Without this
            // distinction the iteration would evaluate its list again and
            // start over, endlessly.
            flow.top().advance();
        }
    }

    /** Carries out what the statement requested as its next step. */
    private void apply(Flow flow, Frame frame, Step step) {
        switch (step) {
            case Step.Next ignored -> frame.advance();
            case Step.Enter enter -> flow.push(inherit(frame,
                    new Frame(enter.block(), enter.loop())));
            case Step.Invoke invoke -> {
                Frame body = inherit(frame, new Frame(invoke.body(), false));
                body.beginCall(invoke.resultName(),
                        invoke.devicePrefix() != null ? invoke.devicePrefix()
                                : frame.devicePrefix());
                for (int i = 0; i < invoke.parameters().size(); i++) {
                    body.locals().put(invoke.parameters().get(i),
                            i < invoke.arguments().size() ? invoke.arguments().get(i)
                                    : Value.Nothing.get());
                }
                flow.push(body);
            }
            case Step.ForEach each -> {
                if (each.values().isEmpty()) {
                    // Nothing to do — that settles the loop.
                    frame.advance();
                } else {
                    Frame body = inherit(frame, new Frame(each.body(), true));
                    body.beginIteration(each.variable(), each.values());
                    flow.push(body);
                }
            }
            case Step.Return ret -> returnFrom(flow, ret.value());
            case Step.Break ignored -> unwindLoop(flow, true);
            case Step.Continue ignored -> unwindLoop(flow, false);
            case Step.Sleep sleep -> {
                // Advance first, then sleep: after waking up the flow should
                // continue after the sleep, not before it.
                frame.advance();
                flow.sleepUntil(sleep.untilGameTime());
            }
            case Step.Await await -> {
                frame.advance();
                flow.awaitEvent(await.event(), await.deadline(), await.resultName());
            }
        }
    }

    /**
     * Leaves the innermost loop.
     *
     * <p>{@code break} discards the loop body <em>and</em> the {@code while}
     * statement beneath it; {@code continue} only the body, so that the
     * condition is checked again.
     */
    private void unwindLoop(Flow flow, boolean leaveLoop) {
        while (!flow.stack().isEmpty()) {
            Frame frame = flow.top();
            if (frame.isCall() && !frame.isLoop()) {
                // break and continue do not reach beyond a call — otherwise a
                // called function would leave its caller's loop, and the call
                // would depend on where it sits.
                flow.fail("break oder continue steht hier außerhalb einer Schleife.");
                return;
            }
            if (frame.isLoop() && !leaveLoop && frame.hasIteration()) {
                // continue in an iteration over a list: next entry. Popping
                // as with while would be wrong here — the list would be
                // re-evaluated and the iteration would start over.
                if (frame.nextIteration()) {
                    frame.restart();
                    return;
                }
                flow.pop();
                if (!flow.stack().isEmpty()) {
                    flow.top().advance();
                }
                return;
            }
            flow.pop();
            if (frame.exitOnLeave()) {
                // The else branch of an await leaves the flow, even if it
                // ends with break or continue.
                flow.finish(Value.Nothing.get());
                return;
            }
            if (frame.isLoop()) {
                if ((leaveLoop || frame.hasIteration()) && !flow.stack().isEmpty()) {
                    flow.top().advance();
                }
                return;
            }
        }
        flow.finish(Value.Nothing.get());
    }

    /**
     * The deadline of an {@code await} has passed.
     *
     * <p>If there is an {@code else} branch, it runs — it says what to do
     * when the event fails to arrive. The language requires it to leave the
     * flow; this code does not rely on that but marks it on the frame.
     */
    private void timeOut(Flow flow) {
        Expr.Await await = awaitOf(flow);
        Block elseBody = await == null ? null : await.elseBody();
        if (elseBody == null) {
            flow.fail("Zeit abgelaufen beim Warten auf " + flow.awaitedEvent());
            return;
        }
        flow.resume();
        Frame frame = new Frame(elseBody, false);
        frame.setExitOnLeave(true);
        flow.push(frame);
    }

    /**
     * An event's names, with the flow behind them.
     *
     * <p>Assignment always passes through to the flow: an event's parameters
     * exist only for the duration of the check, and assigning to them would
     * have no effect and would therefore be misleading.
     */
    private record EventScope(Map<String, Value> bindings, Flow flow)
            implements Interpreter.Scope {

        @Override
        public Value find(String name) {
            Value bound = bindings.get(name);
            return bound != null ? bound : flow.find(name);
        }

        @Override
        public void declare(String name, Value value) {
            flow.top().locals().put(name, value);
        }

        @Override
        public boolean assign(String name, Value value) {
            return flow.assign(name, value);
        }
    }

    /**
     * A flow's view of its names.
     *
     * <p>The interpreter knows only this interface and need not know whether
     * its own stack or the frames of a flow are behind it.
     */
    public static final class FlowScope implements Interpreter.Scope {

        private final Flow flow;

        public FlowScope(Flow flow) {
            this.flow = flow;
        }

        @Override
        public Value find(String name) {
            return flow.find(name);
        }

        @Override
        public void declare(String name, Value value) {
            flow.top().locals().put(name, value);
        }

        @Override
        public boolean assign(String name, Value value) {
            return flow.assign(name, value);
        }

        @Override
        public String devicePrefix() {
            return flow.devicePrefix();
        }
    }
}
