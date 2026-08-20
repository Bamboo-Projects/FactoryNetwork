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
 * Führt Abläufe aus, die warten können.
 *
 * <p>Der Unterschied zum gewöhnlichen Interpreter ist allein die
 * Ablaufsteuerung: Statt sich rekursiv aufzurufen, legt diese Maschine Rahmen
 * auf einen Stapel und arbeitet ihn Schritt für Schritt ab. Was eine
 * Anweisung <b>tut</b> — Gegenstände bewegen, Redstone setzen, rechnen —
 * steht weiterhin nur an einer Stelle, im Interpreter.
 *
 * <p>Nur dadurch lässt sich ein Ablauf mitten in einer Funktion anhalten,
 * aufschreiben und nach einem Serverneustart fortsetzen.
 */
public final class FlowEngine {

    /** So viele Anweisungen darf ein Ablauf je Tick machen. */
    private static final int STEPS_PER_TICK = 500;

    private final Program program;
    private final Interpreter interpreter;
    private final BlockIndex blocks;
    private final Map<Long, Flow> flows = new LinkedHashMap<>();
    private long nextId = 1;

    public FlowEngine(Program program, Interpreter interpreter) {
        this.program = program;
        this.interpreter = interpreter;
        this.blocks = BlockIndex.of(program);
    }

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

    /** Nimmt einen geladenen Ablauf auf, ohne ihn neu zu beginnen. */
    public void adopt(Flow flow) {
        flows.put(flow.id(), flow);
        if (flow.id() >= nextId) {
            nextId = flow.id() + 1;
        }
    }

    /** Beginnt einen Ablauf mit einer Funktion des Programms. */
    public Flow start(String functionName, List<Value> arguments) {
        Decl.Fn function = program.functions().stream()
                .filter(candidate -> candidate.name().equals(functionName))
                .findFirst()
                .orElseThrow(() -> new ScriptError("Unbekannte Funktion " + functionName + "."));

        Flow flow = new Flow(nextId++, functionName);
        Frame frame = new Frame(function.body(), false);
        for (int i = 0; i < function.parameters().size(); i++) {
            frame.locals().put(function.parameters().get(i).name(),
                    i < arguments.size() ? arguments.get(i) : Value.Nothing.get());
        }
        flow.push(frame);
        flows.put(flow.id(), flow);
        return flow;
    }

    /** Beginnt einen Ablauf für jeden Block, der auf dieses Ereignis hört. */
    public List<Flow> fire(String event, List<Value> arguments) {
        List<Flow> started = new ArrayList<>();
        for (Decl.On handler : program.handlers()) {
            if (!handler.name().equals(event)) {
                continue;
            }
            Flow flow = new Flow(nextId++, "on " + event);
            Frame frame = new Frame(handler.body(), false);
            for (int i = 0; i < handler.parameters().size(); i++) {
                frame.locals().put(handler.parameters().get(i),
                        i < arguments.size() ? arguments.get(i) : Value.Nothing.get());
            }
            flow.push(frame);
            flows.put(flow.id(), flow);
            started.add(flow);
        }
        return started;
    }

    /**
     * Ein Tick: Weckt, was fällig ist, und lässt die laufenden Abläufe
     * arbeiten.
     */
    public void tick(long gameTime) {
        for (Flow flow : List.copyOf(flows.values())) {
            if (flow.isDue(gameTime)) {
                flow.resume();
            } else if (flow.hasTimedOut(gameTime)) {
                // Die Frist ist um: Der else-Zweig übernimmt.
                timeOut(flow);
            }
            if (flow.status() == Flow.Status.RUNNING) {
                advance(flow, gameTime);
            }
        }
        flows.values().removeIf(Flow::isFinished);
    }

    /**
     * Weckt Abläufe, die auf dieses Ereignis warten.
     *
     * <p>Die {@code where}-Klausel entscheidet mit: Ein Ablauf, der auf
     * <em>seinen</em> Auftrag wartet, darf nicht von fremden Ereignissen
     * geweckt werden. Passt sie nicht, bleibt der Ablauf liegen — und seine
     * Frist läuft weiter, denn sie ist absolute Spielzeit.
     *
     * <p>Das Ergebnis landet unter dem Namen, den das {@code await} angegeben
     * hat — dort, wo im Programm {@code let ergebnis = await …} steht.
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
     * Prüft die {@code where}-Klausel.
     *
     * <p>Sichtbar sind dabei die Parameter des Ereignisses — {@code where
     * amount > 100} meint den Parameter der Deklaration, nicht eine Variable
     * des Ablaufs. Nur was dort nicht gefunden wird, sucht der Ablauf bei
     * sich; so lässt sich {@code where id == jobId} schreiben, mit {@code id}
     * aus dem Ereignis und {@code jobId} aus dem Ablauf.
     *
     * <p>Ein Fehler in der Bedingung darf nicht das Ereignis für alle anderen
     * verderben: Er hält nur diesen einen Ablauf an.
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

    /** Ein Ereignis mit einem Wert liefert diesen, mit mehreren eine Liste. */
    private static Value resultOf(List<Value> arguments) {
        return switch (arguments.size()) {
            case 0 -> Value.Nothing.get();
            case 1 -> arguments.get(0);
            default -> new Value.ValueList(List.copyOf(arguments));
        };
    }

    /**
     * Holt die {@code await}-Anweisung zurück, auf der ein Ablauf steht.
     *
     * <p>Der Zähler wurde beim Warten schon weitergeschaltet, damit der
     * Ablauf nach dem Aufwachen hinter dem {@code await} weitermacht — die
     * Anweisung selbst liegt also eine Stelle davor. Dass sie sich so
     * wiederfinden lässt, spart {@code where} und den {@code else}-Zweig beim
     * Aufschreiben: Beide stehen im Programm und nicht im Ablauf.
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

    /** Lässt einen Ablauf arbeiten, bis er wartet oder sein Budget aufbraucht. */
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
                Step step = interpreter.perform(statement, new FlowScope(flow), gameTime);
                apply(flow, frame, step);
            } catch (ScriptError error) {
                flow.fail(error.toString());
                return;
            }
        }
    }

    /**
     * Verlässt einen Rahmen.
     *
     * <p>Ein Schleifenrumpf beginnt von vorn — die Bedingung wird beim
     * nächsten Durchlauf der {@code while}-Anweisung geprüft, die eine Ebene
     * darunter liegt.
     */
    private void leaveFrame(Flow flow, Frame frame) {
        flow.pop();
        if (frame.exitOnLeave() || flow.stack().isEmpty()) {
            flow.finish(Value.Nothing.get());
            return;
        }
        if (!frame.isLoop()) {
            // Die Anweisung, die den Block geöffnet hat, ist erledigt.
            flow.top().advance();
        }
    }

    /** Setzt um, was die Anweisung als nächsten Schritt verlangt hat. */
    private void apply(Flow flow, Frame frame, Step step) {
        switch (step) {
            case Step.Next ignored -> frame.advance();
            case Step.Enter enter -> flow.push(new Frame(enter.block(), enter.loop()));
            case Step.Return ret -> flow.finish(ret.value());
            case Step.Break ignored -> unwindLoop(flow, true);
            case Step.Continue ignored -> unwindLoop(flow, false);
            case Step.Sleep sleep -> {
                // Erst weiterschalten, dann schlafen: Nach dem Aufwachen soll
                // der Ablauf hinter dem sleep weitermachen, nicht davor.
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
     * Verlässt die innerste Schleife.
     *
     * <p>{@code break} wirft den Schleifenrumpf <em>und</em> die
     * {@code while}-Anweisung darunter weg; {@code continue} nur den Rumpf,
     * damit die Bedingung erneut geprüft wird.
     */
    private void unwindLoop(Flow flow, boolean leaveLoop) {
        while (!flow.stack().isEmpty()) {
            Frame frame = flow.pop();
            if (frame.exitOnLeave()) {
                // Der else-Zweig eines await verlässt den Ablauf, auch wenn
                // er mit break oder continue endet.
                flow.finish(Value.Nothing.get());
                return;
            }
            if (frame.isLoop()) {
                if (leaveLoop && !flow.stack().isEmpty()) {
                    flow.top().advance();
                }
                return;
            }
        }
        flow.finish(Value.Nothing.get());
    }

    /**
     * Die Frist eines {@code await} ist abgelaufen.
     *
     * <p>Gibt es einen {@code else}-Zweig, läuft er — dort steht, was beim
     * Ausbleiben zu tun ist. Die Sprache verlangt, dass er den Ablauf
     * verlässt; darauf verlässt sich diese Stelle nicht, sondern merkt es am
     * Rahmen vor.
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
     * Die Namen eines Ereignisses, mit dem Ablauf dahinter.
     *
     * <p>Zuweisen greift immer auf den Ablauf durch: Die Parameter eines
     * Ereignisses gibt es nur für die Dauer der Prüfung, ihnen etwas
     * zuzuweisen wäre folgenlos und damit irreführend.
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
     * Der Blick eines Ablaufs auf seine Namen.
     *
     * <p>Der Interpreter kennt nur diese Schnittstelle und muss nicht wissen,
     * ob dahinter sein eigener Stapel steht oder die Rahmen eines Ablaufs.
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
    }
}
