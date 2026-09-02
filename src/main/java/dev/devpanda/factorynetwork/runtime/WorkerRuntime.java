package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.WorkerKind;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.network.NetworkFluids;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executes the workers of a program.
 *
 * <p>A worker is not a loop but a promise — which is why there is no program
 * counter here, but a state for each worker. The runtime decides for itself
 * when to act.
 *
 * <p>All entries a worker knows are executed: {@code from}, {@code to},
 * {@code filter}, {@code rate}, {@code maintain}, {@code when},
 * {@code priority}, {@code strategy} and {@code overflow}.
 *
 * <p>What the runtime <b>cannot</b> do, it reports instead of silently
 * skipping it — for {@code when}, say, a condition that goes beyond numeric
 * comparisons.
 */
public final class WorkerRuntime {

    /** How much a worker without a rate entry moves per pass. */
    private static final int DEFAULT_BATCH = 64;
    /** Interval between two passes without a rate entry, in ticks. */
    private static final int DEFAULT_INTERVAL = 20;

    private final Map<String, WorkerState> states = new LinkedHashMap<>();
    /** Notes nobody has collected yet. */
    private final List<LogEntry> notes = new ArrayList<>();

    /**
     * Whom to notify that a worker has written into a device.
     *
     * <p>The controller then advances the baseline for
     * {@code device_output}. <b>Without that, every delivery would report an
     * output that never existed</b> — a worker putting eight pieces into a
     * machine every twenty ticks would thereby wake, once a second, everyone
     * waiting for its output.
     */
    private java.util.function.Consumer<String> onDeviceFilled;

    /**
     * What has already been said once.
     *
     * <p>Separate from {@link #notes}, because those are collected and
     * cleared: without this memory the same note would reappear after every
     * collection, and a worker runs twenty times a second.
     */
    private final java.util.Set<String> saidBefore = new java.util.HashSet<>();
    /** The program's groups, resolved against the network. */
    private final Map<String, DeviceGroup> groups = new LinkedHashMap<>();

    /**
     * The program's filter templates, by name.
     *
     * <p>Re-read when a different program arrives — not every tick like the
     * groups. A template is not tied to any device: what it selects does not
     * change because a furnace is added.
     */
    private final Map<String, Decl.FilterTemplate> templates = new LinkedHashMap<>();

    /** The program the templates came from — for the comparison. */
    private Program templateSource;

    /**
     * The network's power reserve, or {@code null}.
     *
     * <p>Set by the controller. Without it — in tests without a world — a
     * power worker halts and says so, instead of pretending something flows.
     */
    private dev.devpanda.factorynetwork.network.NetworkPower power;
    private final java.util.random.RandomGenerator random = new java.util.Random();

    /** What a {@code when} condition is evaluated with, or {@code null}. */
    private Interpreter.Host conditionHost;
    private Program conditionProgram;

    /** The state of a worker, as it also appears in the terminal. */
    public static final class WorkerState {
        public Status status = Status.IDLE;
        /** Strategy dictated by the worker; otherwise the group's applies. */
        public String strategyOverride;
        public long lastRun;
        public long moved;
        public String detail = "";

        /**
         * How many ticks remain until the path is established.
         *
         * <p>Counts down and then stays at zero. A worker in continuous
         * operation never touches it again — otherwise latency would be a
         * throttle.
         */
        public int warmup;

        /**
         * The path is new and its latency not yet measured.
         *
         * <p>True for a fresh worker and after every network rebuild: whoever
         * reroutes a cable changes the path — and then the old number no
         * longer applies.
         */
        public boolean warmupPending = true;
    }

    public enum Status {
        /** Running and moving. */
        RUNNING,
        /** Nothing to do — source empty. */
        IDLE,
        /** Target accepts nothing more. */
        WAITING_TARGET,
        /** Condition does not hold. */
        WAITING_CONDITION,
        /** A device is missing or something is broken. */
        HALTED
    }

    public Map<String, WorkerState> states() {
        return states;
    }

    /**
     * After a network rebuild every path has to be measured anew.
     *
     * <p>A rerouted cable, one more router — and the latency from before no
     * longer applies. It is measured on the next transfer, not here: here
     * the path is not even known yet.
     */
    public void remeasureLatency() {
        states.values().forEach(state -> state.warmupPending = true);
    }

    /**
     * Collects the new notes and clears the list.
     *
     * <p>Collect rather than inspect: the controller writes them to the log,
     * and what is there need not sit here a second time.
     */
    public List<LogEntry> drainNotes() {
        List<LogEntry> out = List.copyOf(notes);
        notes.clear();
        return out;
    }

    public void reset() {
        states.clear();
        notes.clear();
        saidBefore.clear();
        groups.clear();
    }

    public Map<String, DeviceGroup> groups() {
        return groups;
    }

    /** One tick: runs over all workers and moves whatever is due. */
    public void tick(Level level, Program program, FactoryGraph graph,
            dev.devpanda.factorynetwork.network.NetworkStores stores) {
        tick(level, program, graph, stores, null);
    }

    /**
     * The same tick, but with a host for the conditions.
     *
     * <p><b>Without it, {@code when} can do almost nothing.</b> There used to
     * be a small evaluator of its own here that knew literals and
     * {@code storage.count(…)} — everything else counted as true. A worker
     * with {@code when modus == "tag"} therefore always ran, even at night,
     * and the odd thing about it: it even reported so ("condition not yet
     * evaluable"), just in a line nobody reads, while it kept working.
     */
    /**
     * What has already gone over the cables in this tick.
     *
     * <p>Here and not on the graph: the graph is rebuilt when the world
     * changes — the budget expires per tick, independently of that.
     */
    private final dev.devpanda.factorynetwork.network.TickBudget budget =
            dev.devpanda.factorynetwork.network.TickBudget.create();

    /** This tick's budget — for the display. */
    public dev.devpanda.factorynetwork.network.TickBudget budget() {
        return budget;
    }

    /**
     * What the network has moved over time.
     *
     * <p>Beside the budget and not inside it: the budget expires per tick,
     * the history keeps going.
     */
    private final dev.devpanda.factorynetwork.network.TrafficHistory history =
            new dev.devpanda.factorynetwork.network.TrafficHistory();

    public dev.devpanda.factorynetwork.network.TrafficHistory history() {
        return history;
    }

    public void tick(Level level, Program program, FactoryGraph graph,
            dev.devpanda.factorynetwork.network.NetworkStores stores, Interpreter.Host host) {
        // A new tick starts from zero: what went through yesterday limits
        // nothing today.
        budget.reset();
        history.tick();
        this.conditionHost = host;
        this.conditionProgram = program;
        // All stores in one go. Previously there were two parameters
        // here and a setter beside them, and the setter was forgotten once
        // on the chemicals path.
        var found = stores == null
                ? new dev.devpanda.factorynetwork.network.NetworkStores() : stores;
        this.fluids = found.fluids();
        this.chemicals = found.chemicals();
        NetworkStorage storage = found.items();
        long now = level.getGameTime();
        currentStorage = storage;
        lastGraph = graph;
        resolveGroups(program, graph);
        if (templateSource != program) {
            templates.clear();
            templates.putAll(FilterTemplates.of(program));
            templateSource = program;
        }
        for (Decl.Worker worker : byPriority(program.workers())) {
            WorkerState state = states.computeIfAbsent(worker.name(), name -> new WorkerState());
            // <b>Latency counts down here, before the rate.</b> If it came
            // after, a worker with "per 20t" would need twenty ticks for
            // every latency tick — two ticks of delay would become forty.
            if (state.warmup > 0) {
                state.warmup--;
                state.status = Status.RUNNING;
                state.detail = "Weg wird aufgebaut";
                continue;
            }
            int interval = intervalOf(worker);
            if (now - state.lastRun < interval) {
                continue;
            }
            state.lastRun = now;
            runWorker(level, worker, state, graph, storage);
        }
    }

    /**
     * The workers in the order in which they get their turn.
     *
     * <p><b>Small number first</b>, as everywhere in the language, and for
     * equal numbers in the order in which they are written. Until now this
     * was a promise without effect: {@code priority} was in the docs and was
     * read nowhere.
     *
     * <p>For power, the order decides who goes empty-handed when supply is
     * scarce — and for items, who gets the last stack from a scarce store.
     */
    private static List<Decl.Worker> byPriority(List<Decl.Worker> workers) {
        List<Decl.Worker> sorted = new ArrayList<>(workers);
        sorted.sort(java.util.Comparator.comparingInt(WorkerRuntime::priorityOf));
        return sorted;
    }

    private static int priorityOf(Decl.Worker worker) {
        Decl.Worker.Entry entry = worker.entry(Decl.Worker.Entry.Kind.PRIORITY);
        if (entry == null || !(entry.value() instanceof Expr.IntLit number)) {
            return 0;
        }
        return (int) number.value();
    }

    /**
     * Resolves the groups against the network.
     *
     * <p>Every tick, but cheap: there are few groups with few patterns. And
     * it has to happen continuously — a furnace that is added should land in
     * its group without the program being applied again.
     *
     * <p><b>And once on apply</b>, because otherwise a group would be empty
     * for one tick: a flow that starts right after apply would ask
     * {@code kisten.members()} and get nothing.
     */
    public void resolveGroups(Program program, FactoryGraph graph) {
        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.Group group) {
                DeviceGroup previous = groups.get(group.name());
                DeviceGroup resolved = DeviceGroup.resolve(group, graph);
                // Keep the round_robin cursor, otherwise the group starts at
                // the first device again every tick.
                if (previous != null && previous.members().equals(resolved.members())) {
                    continue;
                }
                groups.put(group.name(), resolved);
            }
        }
    }

    private void runWorker(Level level, Decl.Worker worker, WorkerState state,
                           FactoryGraph graph, NetworkStorage storage) {
        Decl.Worker.Entry from = worker.entry(Decl.Worker.Entry.Kind.FROM);
        Decl.Worker.Entry to = worker.entry(Decl.Worker.Entry.Kind.TO);
        if (from == null || to == null) {
            state.status = Status.HALTED;
            state.detail = "from oder to fehlt";
            return;
        }

        // If the condition does not hold, the worker sleeps — and the
        // terminal says why. This is the sibling of WAITING_TARGET.
        if (!conditionHolds(worker, state)) {
            state.status = Status.WAITING_CONDITION;
            return;
        }

        if (isFluidWorker(worker)) {
            tickFluidWorker(worker, graph, fluids, state, from, to);
            return;
        }

        if (WorkerKind.of(worker, templates) == Expr.Selector.Kind.POWER) {
            tickPowerWorker(worker, state, graph, from, to);
            return;
        }

        if (isCrafting(from.value())) {
            tickCraftingWorker(worker, state, to);
            return;
        }

        if (WorkerKind.of(worker, templates) == Expr.Selector.Kind.CHEMICAL) {
            tickChemicalWorker(worker, state, from, to);
            return;
        }

        int batch = batchOf(worker);
        List<Item> filter = filterItems(worker, state);
        if (state.status == Status.HALTED) {
            return;
        }
        state.strategyOverride = strategyOf(worker);

        boolean fromStorage = isStorage(from.value());
        boolean toStorage = isStorage(to.value());

        if (fromStorage && toStorage) {
            state.status = Status.IDLE;
            state.detail = "Quelle und Ziel sind beide der Speicher";
            return;
        }

        // maintain applies per item type, not in total: filter tag:c/coals
        // with maintain 64 keeps 64 of every kind of coal. That is why the
        // filter is narrowed to the types still missing.
        int maintain = maintainOf(worker);
        if (maintain > 0) {
            if (filter.isEmpty()) {
                state.status = Status.HALTED;
                state.detail = "maintain braucht ein filter";
                note(worker, "maintain ohne filter — welche Art soll vorgehalten werden?");
                return;
            }
            Map<Item, Long> present = presentPerType(to.value(), graph, storage, filter, state);
            List<Item> missing = filter.stream()
                    .filter(item -> present.getOrDefault(item, 0L) < maintain)
                    .toList();
            if (missing.isEmpty()) {
                state.status = Status.IDLE;
                state.detail = "Vorrat steht (" + maintain + " je Art)";
                return;
            }
            long largestGap = missing.stream()
                    .mapToLong(item -> maintain - present.getOrDefault(item, 0L))
                    .max().orElse(0);
            filter = missing;
            batch = (int) Math.min(batch, largestGap);
        }

        // <b>This is where throughput takes effect.</b> The path to the
        // device says how much still fits through in this tick; the worker
        // takes the smaller of the two.
        //
        // And it does not fail when nothing is free any more — it moves
        // nothing and tries again next tick. That is the difference from
        // channels: narrow, not dead.
        List<FactoryGraph.Node> path = pathFor(from.value(), to.value(),
                fromStorage, toStorage, graph);
        // The budget counts bytes, the worker items — one item weighs one
        // kilobyte.
        int freieBytes = path.isEmpty()
                ? dev.devpanda.factorynetwork.network.Bandwidth.UNLIMITED
                : budget.free(level, path);
        // <b>The first transfer waits for the path, the following ones do
        // not.</b> What is once under way arrives in quick succession — like
        // real packets. A latency that delayed every transfer would be a
        // bandwidth penalty in disguise.
        if (state.warmupPending) {
            state.warmupPending = false;
            state.warmup = dev.devpanda.factorynetwork.network.Latency.of(level, path);
            if (state.warmup > 0) {
                state.status = Status.RUNNING;
                state.detail = "Weg wird aufgebaut";
                return;
            }
        }
        int free = freieBytes / dev.devpanda.factorynetwork.network.Bandwidth.PER_ITEM;
        if (free <= 0) {
            // The same kind of waiting as with a full target: it is not the
            // program's fault but the line in front of it.
            state.status = Status.WAITING_TARGET;
            state.detail = "Das Kabel ist in diesem Tick voll";
            return;
        }
        batch = Math.min(batch, free);

        long moved;
        if (fromStorage) {
            moved = storageToDevice(to.value(), graph, storage, filter, batch, state);
        } else if (toStorage) {
            moved = deviceToStorage(from.value(), graph, storage, filter, batch, state);
        } else {
            moved = deviceToDevice(from.value(), to.value(), graph, filter, batch, state);
        }
        // With the actual amount, not the planned one: whoever wanted 64 and
        // got 3 did not occupy the cable with 64.
        budget.spend(path,
                (int) moved * dev.devpanda.factorynetwork.network.Bandwidth.PER_ITEM);
        // And the same amount into the history, under the worker's name.
        // Not under the device's: a worker can take from several, and the
        // question is "which program eats the most".
        history.record(worker.name(),
                (int) moved * dev.devpanda.factorynetwork.network.Bandwidth.PER_ITEM);

        // If the target is full and an overflow target is given, it goes
        // there. Without that, a worker stands still on a full store instead
        // of getting rid of the surplus — and the machine in front fills up.
        if (state.status == Status.WAITING_TARGET && moved == 0) {
            Decl.Worker.Entry overflow = worker.entry(Decl.Worker.Entry.Kind.OVERFLOW);
            if (overflow != null) {
                state.status = Status.RUNNING;
                long spilled = fromStorage
                        ? storageToDevice(overflow.value(), graph, storage, filter, batch, state)
                        : deviceToDevice(from.value(), overflow.value(), graph,
                                filter, batch, state);
                if (spilled > 0) {
                    state.moved += spilled;
                    state.detail = spilled + " ins Ausweichziel";
                    return;
                }
            }
        }

        state.moved += moved;
        if (state.status != Status.HALTED && state.status != Status.WAITING_TARGET) {
            state.status = moved > 0 ? Status.RUNNING : Status.IDLE;
            state.detail = moved > 0 ? moved + " bewegt" : "nichts zu tun";
        }
    }

    // ---- The three paths --------------------------------------------------

    private long deviceToStorage(Expr source, FactoryGraph graph, NetworkStorage storage,
                                 List<Item> filter, int batch, WorkerState state) {
        IItemHandler handler = handlerOf(source, graph, state);
        if (handler == null) {
            return 0;
        }
        long moved = 0;
        for (int slot = 0; slot < handler.getSlots() && moved < batch; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || (!filter.isEmpty() && !filter.contains(stack.getItem()))) {
                continue;
            }
            int wanted = (int) Math.min(batch - moved, stack.getCount());
            // <b>Ask first, then take.</b> The other way round, everything
            // hinges on the way back — and that does not always exist: a
            // furnace hands over its fuel slot and does not accept it again.
            // Whatever lies in between is lost.
            long fits = storage.room(
                    dev.devpanda.factorynetwork.storage.ItemKey.of(stack), wanted);
            if (fits <= 0) {
                // The next type may still find room: a cell that already
                // holds it needs no new type slot.
                state.status = Status.WAITING_TARGET;
                state.detail = "Der Netzspeicher ist voll";
                continue;
            }
            ItemStack taken = handler.extractItem(slot, (int) Math.min(wanted, fits), false);
            if (taken.isEmpty()) {
                continue;
            }
            // The way back stays as a safety net underneath: machines that
            // answer simulate differently from the real transfer do exist.
            long rest = storage.insert(taken);
            if (rest > 0) {
                ItemStack zurueck = Handoffs.insertInto(handler,
                        taken.copyWithCount((int) rest));
                if (!zurueck.isEmpty()) {
                    // The device will not even take back what was just
                    // inside it. Then it had better fall on the ground than
                    // vanish.
                    dropped.add(zurueck);
                }
                state.status = Status.WAITING_TARGET;
                state.detail = "Der Netzspeicher ist voll";
            }
            moved += taken.getCount() - rest;
        }
        return moved;
    }

    private long storageToDevice(Expr target, FactoryGraph graph, NetworkStorage storage,
                                 List<Item> filter, int batch, WorkerState state) {
        IItemHandler handler = handlerOf(target, graph, state);
        if (handler == null) {
            return 0;
        }
        // Without a filter, the first entry found in storage is taken.
        // <b>An entry and not a type:</b> what is chosen here goes into the
        // machine as a stack afterwards — and a named pickaxe would arrive
        // as a plain one if only the id stood here.
        dev.devpanda.factorynetwork.storage.ItemKey item = storage.contents().keySet()
                .stream()
                .filter(key -> filter.isEmpty() || filter.contains(key.item()))
                .filter(key -> storage.count(key) > 0)
                .findFirst()
                .orElse(null);
        if (item == null) {
            return 0;
        }
        long available = storage.count(item);
        if (available <= 0) {
            return 0;
        }
        int wanted = (int) Math.min(batch, Math.min(available, item.maxStackSize()));
        ItemStack offered = item.toStack(wanted);
        ItemStack rest = Handoffs.insertInto(handler, offered);
        int accepted = wanted - rest.getCount();
        if (accepted <= 0) {
            state.status = Status.WAITING_TARGET;
            state.detail = "Ziel ist voll";
            return 0;
        }
        // What storage actually yields, not what it shows. A storage bus
        // counts foreign inventories too, and those may keep their contents
        // — a furnace does that with its fuel. Without this line it would
        // end up there twice, and because the stock does not follow, once
        // more every tick.
        long taken = storage.extract(item, accepted);
        if (taken < accepted) {
            long stuck = Handoffs.pullBack(handler, item, accepted - taken);
            // HALTED and not IDLE: anything else gets overwritten by the
            // setter at the end of the tick with "nothing to do", and that
            // is exactly what it is not. A worker that inserts and fetches
            // back every tick while the terminal says "nothing to do" is a
            // riddle nobody can solve.
            state.status = Status.HALTED;
            state.detail = stuck > 0
                    ? "Der Speicher gibt nicht her, was er zeigt — "
                            + stuck + " blieben im Ziel"
                    : "Der Speicher gibt nicht her, was er zeigt";
        }
        return taken;
    }

    /**
     * The path this transfer runs over.
     *
     * <p><b>The loaded device is the one that is not storage.</b> Between two
     * devices the longer of the two paths counts — the goods must cross both,
     * and a cable that is narrow on only one side is narrow all the same.
     *
     * <p>An empty path means: we do not know. Then nothing limits — better
     * too generous than a worker standing still over a misunderstanding.
     */
    private List<FactoryGraph.Node> pathFor(Expr from, Expr to, boolean fromStorage,
                                            boolean toStorage, FactoryGraph graph) {
        if (fromStorage) {
            return pathOfDevice(to, graph);
        }
        if (toStorage) {
            return pathOfDevice(from, graph);
        }
        List<FactoryGraph.Node> quelle = pathOfDevice(from, graph);
        List<FactoryGraph.Node> ziel = pathOfDevice(to, graph);
        List<FactoryGraph.Node> beide = new ArrayList<>(quelle);
        beide.addAll(ziel);
        return beide;
    }

    /** The path of a named device, or empty. */
    private List<FactoryGraph.Node> pathOfDevice(Expr device, FactoryGraph graph) {
        if (!(device instanceof Expr.Name name)) {
            return List.of();
        }
        var stellen = graph.positionsOf(name.value());
        return stellen.isEmpty() ? List.of() : graph.pathTo(stellen.get(0));
    }

    private long deviceToDevice(Expr source, Expr target, FactoryGraph graph,
                                List<Item> filter, int batch, WorkerState state) {
        IItemHandler in = handlerOf(source, graph, state);
        IItemHandler out = handlerOf(target, graph, state);
        if (in == null || out == null) {
            return 0;
        }
        Handoffs.Handoff done = Handoffs.items(in, out, filter, batch);
        if (done.targetFull()) {
            state.status = Status.WAITING_TARGET;
            state.detail = "Ziel ist voll";
        }
        if (done.stranded() > 0) {
            // The source gave up less than promised, and the target will not
            // take it back. Silently, an amount nobody can explain would
            // remain in the device.
            state.status = Status.HALTED;
            state.detail = "Die Quelle gibt weniger her als sie zeigt — "
                    + done.stranded() + " blieben im Ziel";
        }
        return done.moved();
    }

    // ---- Helpers ----------------------------------------------------------

    /**
     * The inventory behind a target.
     *
     * <p>If the target is a group, the first member is taken that is next
     * according to its strategy and actually accepts something. That is why
     * {@link DeviceGroup#order} returns a whole order and not just one
     * choice: a full device must not end the transfer.
     */
    private IItemHandler handlerOf(Expr target, FactoryGraph graph, WorkerState state) {
        return resolve(target, graph, state,
                connector -> NotifyingHandlers.items(
                        connector.machineInventory(), noticeFor(connector)),
                "An diesem Connector hängt keine Maschine mit Inventar", this::fillLevelOf);
    }

    private IFluidHandler tankOf(Expr target, FactoryGraph graph, WorkerState state) {
        return resolve(target, graph, state,
                connector -> NotifyingHandlers.fluids(
                        connector.machineTank(), noticeFor(connector)),
                "An diesem Connector hängt nichts, das Flüssigkeit hält", this::tankLevelOf);
    }

    /** Notifies as soon as a worker has written into a device. */
    public void setDeviceFilled(java.util.function.Consumer<String> listener) {
        this.onDeviceFilled = listener;
    }

    /** The notification for this device, or {@code null} without a receiver. */
    private Runnable noticeFor(dev.devpanda.factorynetwork.block.entity.ConnectorPart connector) {
        if (onDeviceFilled == null) {
            return null;
        }
        String device = connector.label();
        return () -> onDeviceFilled.accept(device);
    }

    /**
     * Finds what a worker is supposed to work with.
     *
     * <p>Groups, strategy and all error messages live here exactly once.
     * What is fetched in the end — an inventory or a tank — the caller
     * decides; otherwise this whole search would exist twice, and one
     * version would get improvements the other lacks.
     */
    private <T> T resolve(Expr target, FactoryGraph graph, WorkerState state,
            java.util.function.Function<dev.devpanda.factorynetwork.block.entity.ConnectorPart, T> extract,
            String missingDetail, java.util.function.ToLongFunction<String> fillLevel) {
        if (!(target instanceof Expr.Name name)) {
            state.status = Status.HALTED;
            state.detail = "Als Ziel taugt nur ein Name";
            return null;
        }
        DeviceGroup group = groups.get(name.value());
        if (group != null) {
            // An entry on the worker takes precedence over the group: the
            // same group can be served differently by two workers.
            if (state.strategyOverride != null) {
                group = new DeviceGroup(group.name(), group.members(),
                        DeviceGroup.Strategy.of(state.strategyOverride));
            }
            if (group.isEmpty()) {
                state.status = Status.WAITING_TARGET;
                state.detail = "Die Gruppe " + name.value() + " hat kein Mitglied im Netz";
                return null;
            }
            for (String member : group.order(fillLevel::applyAsLong, random)) {
                T found = resolveDevice(member, graph, state, extract, missingDetail);
                if (found != null) {
                    return found;
                }
            }
            state.status = Status.WAITING_TARGET;
            state.detail = "Kein Mitglied von " + name.value() + " ist erreichbar";
            return null;
        }
        return resolveDevice(name.value(), graph, state, extract, missingDetail);
    }

    /**
     * How full a tank is — for distribution to the emptiest.
     *
     * <p>Without this version a group of tanks would be sorted by item slots
     * that do not exist there: every tank would look equally full, and
     * {@code strategy least_filled} would in truth distribute at random.
     */
    private long tankLevelOf(String device) {
        IFluidHandler tank = lastGraph == null ? null
                : resolveDevice(device, lastGraph, new WorkerState(),
                        dev.devpanda.factorynetwork.block.entity.ConnectorPart::machineTank, "");
        if (tank == null) {
            return Long.MAX_VALUE;
        }
        long used = 0;
        for (int slot = 0; slot < tank.getTanks(); slot++) {
            used += tank.getFluidInTank(slot).getAmount();
        }
        return used;
    }

    /** How full a device is — for distribution to the emptiest. */
    private long fillLevelOf(String device) {
        IItemHandler handler = lastGraph == null ? null
                : handlerFor(device, lastGraph, new WorkerState());
        if (handler == null) {
            return Long.MAX_VALUE;
        }
        long used = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            used += handler.getStackInSlot(slot).getCount();
        }
        return used;
    }

    /** The graph of the current tick — only for the fill-level query. */
    private FactoryGraph lastGraph;

    /**
     * What no longer fit anywhere.
     *
     * <p>The controller throws it into the world. That is ugly, but the only
     * answer that makes no items vanish: if neither storage nor device
     * accepts anything, it has to go somewhere.
     */
    private final List<ItemStack> dropped = new ArrayList<>();

    public List<ItemStack> takeDropped() {
        if (dropped.isEmpty()) {
            return List.of();
        }
        List<ItemStack> out = List.copyOf(dropped);
        dropped.clear();
        return out;
    }

    /** The network's fluid stock, for the duration of one tick. */
    private NetworkFluids fluids = new NetworkFluids();

    private IItemHandler handlerFor(String deviceName, FactoryGraph graph, WorkerState state) {
        return resolveDevice(deviceName, graph, state, dev.devpanda.factorynetwork.block.entity.ConnectorPart::machineInventory,
                "An diesem Connector hängt keine Maschine mit Inventar");
    }

    private <T> T resolveDevice(String deviceName, FactoryGraph graph, WorkerState state,
            java.util.function.Function<dev.devpanda.factorynetwork.block.entity.ConnectorPart, T> extract,
            String missingDetail) {
        Expr.Name name = new Expr.Name(deviceName, new dev.devpanda.factorynetwork.lang.Span(
                0, 0, 1, 1));
        Optional<dev.devpanda.factorynetwork.network.DevicePos> position =
                graph.connector(name.value());
        if (position.isEmpty()) {
            state.status = Status.HALTED;
            // Distinguish between "does not exist" and "exists twice":
            // otherwise the player looks in the wrong place.
            if (graph.isAmbiguous(name.value())) {
                state.detail = "Der Name " + name.value() + " ist "
                        + graph.positionsOf(name.value()).size()
                        + "-mal vergeben und damit unbrauchbar";
            } else {
                state.detail = graph.closestName(name.value())
                        .map(suggestion -> "Unbekannter Connector " + name.value()
                                + " — meintest du " + suggestion + "?")
                        .orElse("Unbekannter Connector " + name.value());
            }
            return null;
        }
        // The caller holds the world; here the connector itself suffices.
        dev.devpanda.factorynetwork.block.entity.ConnectorPart connector = connectorAt(position.get());
        if (connector == null) {
            state.status = Status.WAITING_TARGET;
            state.detail = "Connector gerade nicht erreichbar";
            return null;
        }
        T found = extract.apply(connector);
        if (found == null) {
            state.status = Status.HALTED;
            state.detail = missingDetail;
        }
        return found;
    }

    /** Set by the controller before every tick, so that no world is held here. */
    private java.util.function.Function<
            dev.devpanda.factorynetwork.network.DevicePos, dev.devpanda.factorynetwork.block.entity.ConnectorPart>
            connectorLookup = where -> null;

    public void setConnectorLookup(java.util.function.Function<
            dev.devpanda.factorynetwork.network.DevicePos, dev.devpanda.factorynetwork.block.entity.ConnectorPart> lookup) {
        this.connectorLookup = lookup;
    }

    private dev.devpanda.factorynetwork.block.entity.ConnectorPart connectorAt(
            dev.devpanda.factorynetwork.network.DevicePos where) {
        return connectorLookup.apply(where);
    }

    private static boolean isStorage(Expr expr) {
        return expr instanceof Expr.Builtin builtin
                && builtin.kind() == Expr.Builtin.Kind.STORAGE;
    }

    private static boolean isCrafting(Expr expr) {
        return expr instanceof Expr.Builtin builtin
                && builtin.kind() == Expr.Builtin.Kind.CRAFTING;
    }

    // ---- Restocking --------------------------------------------------------

    /**
     * The network's crafting; set by the controller.
     *
     * <p>Two questions, and the second is the one where it otherwise goes
     * wrong: a worker that only looks at the stock reorders every round as
     * long as the job is running — "keep 256 in stock" turns into thousands.
     */
    public interface Crafting {

        /** How much of this type is ordered and not yet delivered. */
        long pending(Item item);

        /** Orders; {@code false} if there is no recipe for it. */
        boolean order(Item item, int amount);
    }

    private Crafting crafting;

    public void setCrafting(Crafting crafting) {
        this.crafting = crafting;
    }

    /**
     * A worker that orders instead of pushing.
     *
     * <p>{@code from crafting} is a source like any other — that is the
     * reason {@code from} names a source and not a mode of operation.
     * Stockkeeping thus needs no form of its own:
     *
     * <pre>
     * worker keep_ingots {
     *     from crafting
     *     to storage
     *     filter item:iron_ingot
     *     maintain 256
     * }
     * </pre>
     *
     * <p>Three design decisions are in there. <b>The target is storage</b>,
     * and only storage: crafting goes into the store, and a second worker
     * fetches it from there — teaching the fabricator a target it does not
     * have would be the more expensive route. <b>{@code maintain} is
     * mandatory</b>, because without a number the instruction would mean
     * "order endlessly". And <b>{@code rate} limits the order only when
     * written</b>: whoever writes it means "at most this much per round";
     * whoever leaves it out wants the gap closed and not in morsels of
     * sixty-four.
     */
    private void tickCraftingWorker(Decl.Worker worker, WorkerState state,
                                    Decl.Worker.Entry to) {
        if (crafting == null) {
            state.status = Status.HALTED;
            state.detail = "an diesem Netz hängt keine Fertigung";
            return;
        }
        if (!isStorage(to.value())) {
            state.status = Status.HALTED;
            state.detail = "from crafting liefert nur to storage";
            note(worker, "from crafting braucht to storage — gefertigt wird ins Lager, "
                    + "und von dort holt es ein zweiter Worker ab.");
            return;
        }
        int maintain = maintainOf(worker);
        if (maintain <= 0) {
            state.status = Status.HALTED;
            state.detail = "from crafting braucht ein maintain";
            note(worker, "from crafting ohne maintain — wie viel soll vorgehalten werden? "
                    + "Ohne eine Zahl hieße die Anweisung „bestelle endlos\".");
            return;
        }
        List<Item> filter = filterItems(worker, state);
        if (state.status == Status.HALTED) {
            return;
        }
        if (filter.isEmpty()) {
            state.status = Status.HALTED;
            state.detail = "from crafting braucht ein filter";
            note(worker, "from crafting ohne filter — was soll bestellt werden?");
            return;
        }
        long cap = worker.entry(Decl.Worker.Entry.Kind.RATE) != null
                ? batchOf(worker) : Long.MAX_VALUE;
        List<String> ordered = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (Item item : filter) {
            // The stock alone is not enough: it only rises once the job is
            // finished.
            long have = currentStorage.count(item) + crafting.pending(item);
            long gap = Math.min(maintain - have, cap);
            if (gap <= 0) {
                continue;
            }
            String name = item.getDescription().getString();
            if (crafting.order(item, (int) gap)) {
                ordered.add(gap + " " + name);
            } else {
                unknown.add(name);
            }
        }
        if (!ordered.isEmpty()) {
            state.status = Status.RUNNING;
            state.detail = "bestellt: " + String.join(", ", ordered);
            return;
        }
        if (!unknown.isEmpty()) {
            state.status = Status.HALTED;
            state.detail = "kein Rezept: " + String.join(", ", unknown);
            note(worker, "kein Rezept für " + String.join(", ", unknown)
                    + " — das Netz kann nur bestellen, was eine Werkbank baut.");
            return;
        }
        state.status = Status.IDLE;
        state.detail = "Vorrat steht (" + maintain + " je Art)";
    }

    private static int batchOf(Decl.Worker worker) {
        Decl.Worker.Entry rate = worker.entry(Decl.Worker.Entry.Kind.RATE);
        if (rate != null && rate.value() instanceof Expr.IntLit count) {
            return (int) count.value();
        }
        return DEFAULT_BATCH;
    }

    private static int intervalOf(Decl.Worker worker) {
        Decl.Worker.Entry rate = worker.entry(Decl.Worker.Entry.Kind.RATE);
        if (rate != null && rate.second() instanceof Expr.DurationLit duration) {
            return (int) Math.max(1, duration.ticks());
        }
        return DEFAULT_INTERVAL;
    }

    /**
     * The types being filtered for — empty means everything.
     *
     * <p>Tags, patterns and {@code except} are resolved here, once and then
     * remembered. This is the case the selection rules were designed for: in
     * an AllTheMods pack, {@code tag:c/ores} is the normal case and the
     * enumeration the exception.
     */
    private List<Item> filterItems(Decl.Worker worker, WorkerState state) {
        Decl.Worker.Entry filter = worker.entry(Decl.Worker.Entry.Kind.FILTER);
        if (filter == null) {
            return List.of();
        }
        if (filter.value() instanceof Expr.Name name) {
            return fromTemplate(worker, state, name, FilterTemplates::items);
        }
        List<Item> resolved = ItemSelection.resolve(filter.value());
        if (resolved.isEmpty()) {
            note(worker, "die Auswahl trifft zurzeit nichts");
        }
        return resolved;
    }

    /**
     * The types behind a template name.
     *
     * <p><b>Halt instead of carrying on.</b> An empty list means "no filter"
     * to the worker, and no filter means "everything" — over a typo in the
     * template name it would then move the entire store.
     */
    private <T> List<T> fromTemplate(Decl.Worker worker, WorkerState state, Expr.Name name,
            java.util.function.Function<Decl.FilterTemplate, List<T>> resolve) {
        Decl.FilterTemplate template = templates.get(name.value());
        if (template == null) {
            state.status = Status.HALTED;
            state.detail = "Die Vorlage " + name.value() + " gibt es nicht";
            note(worker, "filter " + name.value() + " — eine Vorlage dieses Namens steht "
                    + "in keiner Datei des Projekts.");
            return List.of();
        }
        try {
            return resolve.apply(template);
        } catch (ScriptError error) {
            state.status = Status.HALTED;
            state.detail = error.getMessage();
            note(worker, error.getMessage());
            return List.of();
        }
    }


    // ---- Power -------------------------------------------------------------

    /** The network's power reserve; set by the controller. */
    public void setPower(dev.devpanda.factorynetwork.network.NetworkPower supply) {
        this.power = supply;
    }

    /**
     * A worker that moves power.
     *
     * <p><b>One side is always the network.</b> {@code from network to
     * crusher_1} supplies, {@code from akku_1 to network} feeds in. Pushing
     * power from one machine straight into another would be a line without a
     * cable — that is what cables are for.
     *
     * <p>There is <b>no cable limit</b> (decided on 25 Aug.): what flows is
     * limited by the worker's rate, the network's reserve and what the
     * machine accepts. A second scarcity beside {@code priority} would have
     * meant two causes for the same symptom.
     *
     * <p>When supply is scarce, the {@code priority} order applies: whoever
     * comes first gets up to their rate, and whoever goes empty-handed goes
     * empty-handed. The order is established by {@link #tick}.
     */
    private void tickPowerWorker(Decl.Worker worker, WorkerState state, FactoryGraph graph,
                                 Decl.Worker.Entry from, Decl.Worker.Entry to) {
        if (power == null) {
            state.status = Status.HALTED;
            state.detail = "Kein Stromvorrat";
            return;
        }
        boolean fromNetwork = isNetwork(from.value());
        boolean toNetwork = isNetwork(to.value());
        if (fromNetwork == toNetwork) {
            state.status = Status.HALTED;
            state.detail = "Bei Strom ist eine Seite network";
            note(worker, "Strom fließt aus dem Netz in eine Maschine oder aus einer "
                    + "Maschine ins Netz — from network to … oder from … to network.");
            return;
        }
        int batch = batchOf(worker);
        int moved = fromNetwork
                ? supply(to.value(), graph, state, batch)
                : drawIn(from.value(), graph, state, batch);
        state.moved += moved;
        if (state.status != Status.HALTED && state.status != Status.WAITING_TARGET) {
            state.status = moved > 0 ? Status.RUNNING : Status.IDLE;
            state.detail = moved > 0 ? moved + " FE" : "nichts zu tun";
        }
    }

    /** From the network into a machine. */
    private int supply(Expr target, FactoryGraph graph, WorkerState state, int batch) {
        IEnergyStorage machine = energyOf(target, graph, state);
        if (machine == null) {
            return 0;
        }
        // First ask how much fits in, only then take from the reserve: what
        // the machine does not accept would otherwise be gone for a tick.
        int room = machine.receiveEnergy(batch, true);
        if (room <= 0) {
            state.status = Status.WAITING_TARGET;
            state.detail = "Die Maschine ist voll";
            return 0;
        }
        int taken = power.take(room);
        if (taken <= 0) {
            state.status = Status.WAITING_TARGET;
            state.detail = "Kein Strom übrig";
            return 0;
        }
        int accepted = machine.receiveEnergy(taken, false);
        if (accepted < taken) {
            // Something may have changed between question and answer.
            power.fill(taken - accepted);
        }
        // So that the output shows in the network tab: it does not count as
        // demand, and without this report a network that delivers heavily
        // would look like one that does nothing.
        power.noteSupplied(accepted);
        return accepted;
    }

    /** From a machine into the network. */
    private int drawIn(Expr source, FactoryGraph graph, WorkerState state, int batch) {
        IEnergyStorage machine = energyOf(source, graph, state);
        if (machine == null) {
            return 0;
        }
        int room = Math.min(batch, power.room());
        if (room <= 0) {
            state.status = Status.WAITING_TARGET;
            state.detail = "Der Vorrat ist voll";
            return 0;
        }
        int taken = machine.extractEnergy(room, false);
        if (taken <= 0) {
            state.status = Status.WAITING_TARGET;
            state.detail = "Die Quelle gibt nichts";
            return 0;
        }
        power.fill(taken);
        return taken;
    }

    private static boolean isNetwork(Expr expr) {
        return expr instanceof Expr.Builtin builtin
                && builtin.kind() == Expr.Builtin.Kind.NETWORK;
    }

    /** The energy storage behind a target. */
    private IEnergyStorage energyOf(Expr target, FactoryGraph graph, WorkerState state) {
        return resolve(target, graph, state, dev.devpanda.factorynetwork.block.entity.ConnectorPart::machineEnergy,
                "An diesem Connector hängt nichts, das Strom annimmt", this::energyLevelOf);
    }

    /**
     * How full a device's energy storage is — for {@code least_filled}.
     *
     * <p>Without this version a group of machines would be sorted by item
     * slots, which say nothing about power: all would look equally full, and
     * the distribution would in truth be round robin.
     */
    private long energyLevelOf(String device) {
        IEnergyStorage machine = lastGraph == null ? null
                : resolveDevice(device, lastGraph, new WorkerState(),
                        dev.devpanda.factorynetwork.block.entity.ConnectorPart::machineEnergy, "");
        return machine == null ? Long.MAX_VALUE : machine.getEnergyStored();
    }

    // ---- Fluids -----------------------------------------------------------

    /**
     * A worker that pumps fluid.
     *
     * <p>The same shape as the item worker, the same entries: {@code rate}
     * counts in millibuckets, {@code maintain} keeps a reserve per kind,
     * {@code overflow} takes what the target no longer holds. What differs is
     * the matter itself — a tank has no slots, and the arithmetic is in
     * millibuckets instead of pieces.
     */
    private void tickFluidWorker(Decl.Worker worker, FactoryGraph graph,
            NetworkFluids fluids, WorkerState state, Decl.Worker.Entry from,
            Decl.Worker.Entry to) {
        int batch = batchOf(worker);
        List<Fluid> filter = filterFluids(worker, state);
        if (state.status == Status.HALTED) {
            return;
        }
        state.strategyOverride = strategyOf(worker);

        boolean fromStorage = isStorage(from.value());
        boolean toStorage = isStorage(to.value());
        if (fromStorage && toStorage) {
            state.status = Status.IDLE;
            state.detail = "Quelle und Ziel sind beide der Speicher";
            return;
        }
        if (filter.isEmpty()) {
            // Without an entry there would be no telling which kind is
            // meant: a tank usually holds exactly one, and draining the
            // wrong one is costlier with fluids than with items.
            state.status = Status.HALTED;
            state.detail = "Ein Flüssigkeits-Worker braucht ein filter";
            return;
        }

        int maintain = maintainOf(worker);
        if (maintain > 0) {
            Map<Fluid, Long> present = presentFluids(to.value(), graph, fluids, filter, state);
            List<Fluid> missing = filter.stream()
                    .filter(fluid -> present.getOrDefault(fluid, 0L) < maintain)
                    .toList();
            if (missing.isEmpty()) {
                state.status = Status.IDLE;
                state.detail = "Vorrat steht (" + maintain + " mB je Sorte)";
                return;
            }
            long largestGap = missing.stream()
                    .mapToLong(fluid -> maintain - present.getOrDefault(fluid, 0L))
                    .max().orElse(0);
            filter = missing;
            batch = (int) Math.min(batch, largestGap);
        }

        long moved;
        if (fromStorage) {
            moved = storageToTank(to.value(), graph, fluids, filter, batch, state);
        } else if (toStorage) {
            moved = tankToStorage(from.value(), graph, fluids, filter, batch, state);
        } else {
            moved = tankToTank(from.value(), to.value(), graph, filter, batch, state);
        }

        if (state.status == Status.WAITING_TARGET && moved == 0) {
            Decl.Worker.Entry overflow = worker.entry(Decl.Worker.Entry.Kind.OVERFLOW);
            if (overflow != null) {
                state.status = Status.RUNNING;
                long spilled = fromStorage
                        ? storageToTank(overflow.value(), graph, fluids, filter, batch, state)
                        : tankToTank(from.value(), overflow.value(), graph, filter, batch, state);
                if (spilled > 0) {
                    state.moved += spilled;
                    state.detail = spilled + " mB ins Ausweichziel";
                    return;
                }
            }
        }

        state.moved += moved;
        if (state.status != Status.HALTED && state.status != Status.WAITING_TARGET) {
            state.status = moved > 0 ? Status.RUNNING : Status.IDLE;
            state.detail = moved > 0 ? moved + " mB bewegt" : "nichts zu tun";
        }
    }

    private long tankToStorage(Expr source, FactoryGraph graph, NetworkFluids fluids,
            List<Fluid> filter, int batch, WorkerState state) {
        IFluidHandler tank = tankOf(source, graph, state);
        if (tank == null) {
            return 0;
        }
        long moved = 0;
        for (int slot = 0; slot < tank.getTanks() && moved < batch; slot++) {
            FluidStack inside = tank.getFluidInTank(slot);
            if (inside.isEmpty() || !filter.contains(inside.getFluid())) {
                continue;
            }
            // First ask how much storage takes, only then drain. Since
            // fluids live in cells, it can be full — and a drained fluid
            // cannot be put back like an item if the tank will not accept
            // it again.
            long asked = Math.min(batch - moved, inside.getAmount());
            long fits = fluids.room(inside.getFluid(), asked);
            if (fits <= 0) {
                state.status = Status.WAITING_TARGET;
                state.detail = "Der Netzspeicher ist voll";
                continue;
            }
            FluidStack wanted = new FluidStack(inside.getFluid(), (int) fits);
            FluidStack taken = tank.drain(wanted, IFluidHandler.FluidAction.EXECUTE);
            if (taken.isEmpty()) {
                continue;
            }
            fluids.insert(taken.getFluid(), taken.getAmount());
            moved += taken.getAmount();
        }
        return moved;
    }

    private long storageToTank(Expr target, FactoryGraph graph, NetworkFluids fluids,
            List<Fluid> filter, int batch, WorkerState state) {
        IFluidHandler tank = tankOf(target, graph, state);
        if (tank == null) {
            return 0;
        }
        long moved = 0;
        for (Fluid fluid : filter) {
            if (moved >= batch) {
                break;
            }
            long available = Math.min(batch - moved, fluids.count(fluid));
            if (available <= 0) {
                continue;
            }
            int accepted = tank.fill(new FluidStack(fluid, (int) available),
                    IFluidHandler.FluidAction.EXECUTE);
            if (accepted <= 0) {
                // Only report if really nothing got through: otherwise the
                // terminal would show "accepts nothing more" next to a
                // number that says something else.
                if (moved == 0) {
                    state.status = Status.WAITING_TARGET;
                    state.detail = "Das Ziel nimmt nichts mehr";
                }
                continue;
            }
            fluids.extract(fluid, accepted);
            moved += accepted;
        }
        return moved;
    }

    private long tankToTank(Expr source, Expr target, FactoryGraph graph,
            List<Fluid> filter, int batch, WorkerState state) {
        IFluidHandler in = tankOf(source, graph, state);
        IFluidHandler out = tankOf(target, graph, state);
        if (in == null || out == null) {
            return 0;
        }
        long moved = 0;
        for (int slot = 0; slot < in.getTanks() && moved < batch; slot++) {
            FluidStack inside = in.getFluidInTank(slot);
            if (inside.isEmpty() || !filter.contains(inside.getFluid())) {
                continue;
            }
            FluidStack wanted = new FluidStack(inside.getFluid(),
                    (int) Math.min(batch - moved, inside.getAmount()));
            Handoffs.Handoff done = Handoffs.fluid(in, out, wanted);
            if (done.targetFull()) {
                if (moved == 0) {
                    state.status = Status.WAITING_TARGET;
                    state.detail = "Das Ziel nimmt nichts mehr";
                }
                break;
            }
            if (done.stranded() > 0) {
                state.status = Status.HALTED;
                state.detail = "Die Quelle gibt weniger her als sie zeigt — "
                        + done.stranded() + " mB blieben im Ziel";
            }
            moved += done.moved();
        }
        return moved;
    }

    /** How much is already at the target, per kind — in millibuckets. */
    private Map<Fluid, Long> presentFluids(Expr target, FactoryGraph graph,
            NetworkFluids fluids, List<Fluid> filter, WorkerState state) {
        Map<Fluid, Long> present = new LinkedHashMap<>();
        if (isStorage(target)) {
            for (Fluid fluid : filter) {
                present.put(fluid, fluids.count(fluid));
            }
            return present;
        }
        IFluidHandler tank = tankOf(target, graph, state);
        if (tank == null) {
            return present;
        }
        for (int slot = 0; slot < tank.getTanks(); slot++) {
            FluidStack inside = tank.getFluidInTank(slot);
            if (!inside.isEmpty()) {
                present.merge(inside.getFluid(), (long) inside.getAmount(), Long::sum);
            }
        }
        return present;
    }

    /** The kinds being filtered for. */
    private List<Fluid> filterFluids(Decl.Worker worker, WorkerState state) {
        Decl.Worker.Entry filter = worker.entry(Decl.Worker.Entry.Kind.FILTER);
        if (filter == null) {
            return List.of();
        }
        if (filter.value() instanceof Expr.Name name) {
            return fromTemplate(worker, state, name, FilterTemplates::fluids);
        }
        List<Fluid> resolved = FluidSelection.resolve(filter.value());
        if (resolved.isEmpty()) {
            note(worker, "die Auswahl trifft zurzeit keine Flüssigkeit");
        }
        return resolved;
    }

    /**
     * Does this worker's filter mean fluids?
     *
     * <p>The rule itself lives in {@link WorkerKind}, because the check in
     * the editor needs the same one. Two versions of it drifted apart.
     */
    // ---- Chemicals ---------------------------------------------------------

    /** The network's chemical store; arrives with the tick. */
    private dev.devpanda.factorynetwork.network.ResourceStore chemicals =
            dev.devpanda.factorynetwork.network.ResourceStore.NONE;

    /**
     * A worker that moves chemicals.
     *
     * <p>The same structure as for fluids, and for the same reasons: <b>a
     * {@code filter} is mandatory</b>, because a container usually holds
     * exactly one kind, and draining the wrong one is costlier than with
     * items. <b>Only between device and storage</b>, in both directions —
     * device to device goes via storage, and for that one writes two
     * workers.
     *
     * <p>Without Mekanism it halts and says so. A worker that silently does
     * nothing because a mod is missing is the bug one searches for longest.
     */
    private void tickChemicalWorker(Decl.Worker worker, WorkerState state,
                                    Decl.Worker.Entry from, Decl.Worker.Entry to) {
        if (!dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.installed()) {
            state.status = Status.HALTED;
            state.detail = dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.reason();
            note(worker, dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.reason()
                    + " " + dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.hint());
            return;
        }
        List<String> filter = filterChemicals(worker, state);
        if (state.status == Status.HALTED) {
            return;
        }
        if (filter.isEmpty()) {
            state.status = Status.HALTED;
            state.detail = "Ein Chemikalien-Worker braucht ein filter";
            note(worker, "filter fehlt — ein Behälter hält meist genau eine Sorte, "
                    + "und welche gemeint ist, muss dastehen.");
            return;
        }
        boolean fromStorage = isStorage(from.value());
        boolean toStorage = isStorage(to.value());
        if (fromStorage == toStorage) {
            state.status = Status.HALTED;
            state.detail = fromStorage
                    ? "Quelle und Ziel sind beide der Speicher"
                    : "Chemikalien gehen zwischen Gerät und Speicher";
            return;
        }
        long limit = batchOf(worker);
        int maintain = maintainOf(worker);
        if (maintain > 0) {
            // As everywhere: per kind, not in total.
            long lacking = 0;
            for (String id : filter) {
                long have = toStorage ? chemicals.count(id) : amountAt(to.value(), id, state);
                lacking = Math.max(lacking, maintain - have);
            }
            if (lacking <= 0) {
                state.status = Status.IDLE;
                state.detail = "Vorrat steht (" + maintain + " mB je Sorte)";
                return;
            }
            limit = Math.min(limit, lacking);
        }
        long moved = fromStorage
                ? fillDevice(to.value(), filter, limit, state)
                : drainDevice(from.value(), filter, limit, state);
        if (state.status == Status.HALTED) {
            return;
        }
        state.moved += moved;
        state.status = moved > 0 ? Status.RUNNING : Status.IDLE;
        state.detail = moved > 0 ? moved + " mB" : "nichts zu holen";
    }

    /** The ids behind a chemical worker's filter. */
    private List<String> filterChemicals(Decl.Worker worker, WorkerState state) {
        Decl.Worker.Entry filter = worker.entry(Decl.Worker.Entry.Kind.FILTER);
        if (filter == null) {
            return List.of();
        }
        if (filter.value() instanceof Expr.Name name) {
            return fromTemplate(worker, state, name,
                    template -> dev.devpanda.factorynetwork.compat.mekanism.Chemicals
                            .resolve(template.includes().isEmpty() ? null
                                    : template.includes().get(0)));
        }
        List<String> resolved = dev.devpanda.factorynetwork.compat.mekanism.Chemicals
                .resolve(filter.value());
        if (resolved.isEmpty()) {
            note(worker, "die Auswahl trifft zurzeit keine Chemikalie");
        }
        return resolved;
    }

    /**
     * The connector behind a device value, or {@code null}.
     *
     * <p>Via the same route as for items and fluids: the helper knows the
     * cases where it otherwise goes wrong — a name that does not exist, one
     * that exists twice, and one without a free channel. Three different
     * messages for three different searches.
     */
    private dev.devpanda.factorynetwork.block.entity.ConnectorPart connectorOf(Expr target, WorkerState state) {
        if (!(target instanceof Expr.Name name) || lastGraph == null) {
            state.status = Status.HALTED;
            state.detail = "Ein Chemikalien-Worker braucht ein Gerät";
            return null;
        }
        return resolveDevice(name.value(), lastGraph, state,
                connector -> connector, "An diesem Connector hängt keine Maschine");
    }

    private long amountAt(Expr target, String id, WorkerState state) {
        dev.devpanda.factorynetwork.block.entity.ConnectorPart connector = connectorOf(target, state);
        if (connector == null) {
            state.status = Status.RUNNING;
            return 0;
        }
        var facing = connector.facing();
        return dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.amountAt(
                connector.level(), connector.pos().relative(facing),
                facing.getOpposite(), List.of(id));
    }

    private long fillDevice(Expr target, List<String> filter, long limit, WorkerState state) {
        dev.devpanda.factorynetwork.block.entity.ConnectorPart connector = connectorOf(target, state);
        if (connector == null) {
            return 0;
        }
        var facing = connector.facing();
        return dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.fillFrom(
                chemicals, connector.level(),
                connector.pos().relative(facing), facing.getOpposite(), filter, limit);
    }

    private long drainDevice(Expr source, List<String> filter, long limit, WorkerState state) {
        dev.devpanda.factorynetwork.block.entity.ConnectorPart connector = connectorOf(source, state);
        if (connector == null) {
            return 0;
        }
        var facing = connector.facing();
        return dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.drainInto(
                connector.level(), connector.pos().relative(facing),
                facing.getOpposite(), filter, chemicals, limit);
    }

    private boolean isFluidWorker(Decl.Worker worker) {
        return WorkerKind.of(worker, templates) == Expr.Selector.Kind.FLUID;
    }

    // ---- maintain and when ------------------------------------------------

    /** The strategy this worker dictates — or nothing. */
    private static String strategyOf(Decl.Worker worker) {
        Decl.Worker.Entry entry = worker.entry(Decl.Worker.Entry.Kind.STRATEGY);
        if (entry != null && entry.value() instanceof Expr.Name name) {
            return name.value();
        }
        return null;
    }

    private static int maintainOf(Decl.Worker worker) {
        Decl.Worker.Entry maintain = worker.entry(Decl.Worker.Entry.Kind.MAINTAIN);
        if (maintain != null && maintain.value() instanceof Expr.IntLit count) {
            return (int) count.value();
        }
        return 0;
    }

    /**
     * How much is already at the target, broken down by type.
     *
     * <p>{@code maintain} applies <b>per target device</b>, not to the group,
     * and <b>per item type</b>. For storage both readings coincide, because
     * it is a single target. See {@code sprache.md}, section 7.
     */
    private Map<Item, Long> presentPerType(Expr target, FactoryGraph graph,
                                           NetworkStorage storage, List<Item> filter,
                                           WorkerState state) {
        Map<Item, Long> present = new LinkedHashMap<>();
        if (isStorage(target)) {
            for (Item item : filter) {
                present.put(item, storage.count(item));
            }
            return present;
        }
        IItemHandler handler = handlerOf(target, graph, state);
        if (handler == null) {
            return present;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !filter.contains(stack.getItem())) {
                continue;
            }
            present.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
        }
        return present;
    }

    /**
     * Evaluates {@code when}.
     *
     * <p><b>With the real interpreter</b>, as soon as a host is present: then
     * the same applies here as everywhere else in the language — texts,
     * global values, device states. Without a host the old route remains,
     * number against number; that concerns tests without a world.
     *
     * <p>Still, only observable states are allowed: a condition for which
     * someone would have to write a loop would bring back exactly the
     * polling that workers were invented against.
     */
    private boolean conditionHolds(Decl.Worker worker, WorkerState state) {
        Decl.Worker.Entry when = worker.entry(Decl.Worker.Entry.Kind.WHEN);
        if (when == null) {
            return true;
        }
        Expr condition = when.value();
        if (condition instanceof Expr.BoolLit literal) {
            return literal.value();
        }

        // With the real interpreter, so that the same applies here as
        // everywhere else in the language: texts, global values, device
        // states.
        if (conditionHost != null && conditionProgram != null) {
            try {
                return new Interpreter(conditionProgram, conditionHost).evaluateAsBoolean(condition);
            } catch (ScriptError error) {
                // A broken condition halts the worker instead of silently
                // letting it run. The other way round would be the more
                // dangerous choice: a worker that works on every error moves
                // things nobody wanted moved.
                state.status = Status.HALTED;
                state.detail = error.getMessage();
                note(worker, error.getMessage());
                return false;
            }
        }

        // Without a host the old route remains: numbers against numbers.
        if (condition instanceof Expr.Binary binary) {
            Double left = observableNumber(binary.left());
            Double right = observableNumber(binary.right());
            if (left != null && right != null) {
                return switch (binary.op()) {
                    case LT -> left < right;
                    case LTE -> left <= right;
                    case GT -> left > right;
                    case GTE -> left >= right;
                    case EQ -> left.doubleValue() == right.doubleValue();
                    case NEQ -> left.doubleValue() != right.doubleValue();
                    default -> true;
                };
            }
        }
        note(worker, "diese Bedingung kann die Laufzeit noch nicht auswerten");
        state.detail = "Bedingung noch nicht auswertbar";
        return true;
    }

    /** Returns a number if it can be observed — otherwise nothing. */
    private Double observableNumber(Expr expr) {
        if (expr instanceof Expr.IntLit literal) {
            return (double) literal.value();
        }
        if (expr instanceof Expr.FloatLit literal) {
            return literal.value();
        }
        if (expr instanceof Expr.Call call
                && call.callee() instanceof Expr.Member member
                && "count".equals(member.name())
                && member.target() instanceof Expr.Builtin builtin
                && builtin.kind() == Expr.Builtin.Kind.STORAGE
                && call.arguments().size() == 1) {
            List<Item> items = ItemSelection.resolve(call.arguments().get(0).value());
            return (double) items.stream().mapToLong(currentStorage::count).sum();
        }
        return null;
    }

    /** The storage of the current tick, so that conditions can read it. */
    private NetworkStorage currentStorage = new NetworkStorage();

    /**
     * A note about a worker.
     *
     * <p>The name goes beside it as the source and no longer into the text:
     * that way it can be filtered in the log, and the line does not repeat
     * it. Warning and not error — the worker keeps running, it just does
     * nothing.
     */
    private void note(Decl.Worker worker, String message) {
        note(LogLevel.WARN, worker.name(), message);
    }

    private void note(LogLevel level, String source, String message) {
        // <b>Once and not more often.</b> A worker runs twenty times a
        // second; without this check the same note would appear just as
        // often in the log and obscure everything else. The memory is reset
        // when a program is applied — then the note may return, because
        // then it is new information.
        if (!saidBefore.add(source + "\n" + message)) {
            return;
        }
        notes.add(new LogEntry(level, System.currentTimeMillis(), source, message));
        if (notes.size() > 100) {
            notes.remove(0);
        }
    }
}
