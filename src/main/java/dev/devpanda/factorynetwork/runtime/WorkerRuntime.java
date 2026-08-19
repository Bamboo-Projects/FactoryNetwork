package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Führt die Worker eines Programms aus.
 *
 * <p>Ein Worker ist keine Schleife, sondern eine Zusage — deshalb steht hier
 * kein Programmzähler, sondern für jeden Worker ein Zustand. Die Laufzeit
 * entscheidet selbst, wann sie tätig wird.
 *
 * <p>Diese Fassung deckt den Weg ab, den die erste spielbare Version braucht:
 * {@code from} und {@code to} zwischen Connector und Speicher, mit
 * {@code filter} und {@code rate}. {@code maintain}, {@code when},
 * {@code strategy} und {@code overflow} sind vorgesehen, aber noch nicht
 * ausgeführt — was fehlt, meldet die Laufzeit, statt es stillschweigend zu
 * übergehen.
 */
public final class WorkerRuntime {

    /** Wie viel ein Worker ohne rate-Angabe je Durchgang bewegt. */
    private static final int DEFAULT_BATCH = 64;
    /** Abstand zwischen zwei Durchgängen ohne rate-Angabe, in Ticks. */
    private static final int DEFAULT_INTERVAL = 20;

    private final Map<String, WorkerState> states = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();

    /** Der Zustand eines Workers, wie er auch im Terminal steht. */
    public static final class WorkerState {
        public Status status = Status.IDLE;
        public long lastRun;
        public long moved;
        public String detail = "";
    }

    public enum Status {
        /** Läuft und bewegt. */
        RUNNING,
        /** Nichts zu tun — Quelle leer. */
        IDLE,
        /** Ziel nimmt nichts mehr auf. */
        WAITING_TARGET,
        /** Bedingung trifft nicht zu. */
        WAITING_CONDITION,
        /** Ein Gerät fehlt oder etwas ist gebrochen. */
        HALTED
    }

    public Map<String, WorkerState> states() {
        return states;
    }

    public List<String> notes() {
        return List.copyOf(notes);
    }

    public void reset() {
        states.clear();
        notes.clear();
    }

    /** Ein Tick: läuft über alle Worker und bewegt, was ansteht. */
    public void tick(Level level, Program program, FactoryGraph graph, NetworkStorage storage) {
        long now = level.getGameTime();
        currentStorage = storage;
        for (Decl.Worker worker : program.workers()) {
            WorkerState state = states.computeIfAbsent(worker.name(), name -> new WorkerState());
            int interval = intervalOf(worker);
            if (now - state.lastRun < interval) {
                continue;
            }
            state.lastRun = now;
            runWorker(worker, state, graph, storage);
        }
    }

    private void runWorker(Decl.Worker worker, WorkerState state,
                           FactoryGraph graph, NetworkStorage storage) {
        Decl.Worker.Entry from = worker.entry(Decl.Worker.Entry.Kind.FROM);
        Decl.Worker.Entry to = worker.entry(Decl.Worker.Entry.Kind.TO);
        if (from == null || to == null) {
            state.status = Status.HALTED;
            state.detail = "from oder to fehlt";
            return;
        }

        // Trifft die Bedingung nicht zu, schläft der Worker — und im Terminal
        // steht, warum. Das ist die Schwester von WAITING_TARGET.
        if (!conditionHolds(worker, state)) {
            state.status = Status.WAITING_CONDITION;
            return;
        }

        int batch = batchOf(worker);
        List<Item> filter = filterItems(worker);

        boolean fromStorage = isStorage(from.value());
        boolean toStorage = isStorage(to.value());

        if (fromStorage && toStorage) {
            state.status = Status.IDLE;
            state.detail = "Quelle und Ziel sind beide der Speicher";
            return;
        }

        // maintain begrenzt, wie viel überhaupt noch fehlt.
        int maintain = maintainOf(worker);
        if (maintain > 0) {
            long present = presentAtTarget(to.value(), graph, storage, filter, state);
            if (present >= maintain) {
                state.status = Status.IDLE;
                state.detail = "Vorrat steht (" + present + " von " + maintain + ")";
                return;
            }
            batch = (int) Math.min(batch, maintain - present);
        }

        long moved;
        if (fromStorage) {
            moved = storageToDevice(to.value(), graph, storage, filter, batch, state);
        } else if (toStorage) {
            moved = deviceToStorage(from.value(), graph, storage, filter, batch, state);
        } else {
            moved = deviceToDevice(from.value(), to.value(), graph, filter, batch, state);
        }

        state.moved += moved;
        if (state.status != Status.HALTED && state.status != Status.WAITING_TARGET) {
            state.status = moved > 0 ? Status.RUNNING : Status.IDLE;
            state.detail = moved > 0 ? moved + " bewegt" : "nichts zu tun";
        }
    }

    // ---- Die drei Wege ----------------------------------------------------

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
            ItemStack taken = handler.extractItem(slot, wanted, false);
            if (!taken.isEmpty()) {
                storage.insert(taken);
                moved += taken.getCount();
            }
        }
        return moved;
    }

    private long storageToDevice(Expr target, FactoryGraph graph, NetworkStorage storage,
                                 List<Item> filter, int batch, WorkerState state) {
        IItemHandler handler = handlerOf(target, graph, state);
        if (handler == null) {
            return 0;
        }
        // Ohne Filter wird die erste Art genommen, die im Speicher liegt.
        Item item = filter.stream()
                .filter(candidate -> storage.count(candidate) > 0)
                .findFirst()
                .orElseGet(() -> filter.isEmpty()
                        ? storage.contents().keySet().stream().findFirst().orElse(null)
                        : null);
        if (item == null) {
            return 0;
        }
        long available = storage.count(item);
        if (available <= 0) {
            return 0;
        }
        int wanted = (int) Math.min(batch, available);
        ItemStack offered = new ItemStack(item, wanted);
        ItemStack rest = insertInto(handler, offered);
        int accepted = wanted - rest.getCount();
        if (accepted <= 0) {
            state.status = Status.WAITING_TARGET;
            state.detail = "Ziel ist voll";
            return 0;
        }
        storage.extract(item, accepted);
        return accepted;
    }

    private long deviceToDevice(Expr source, Expr target, FactoryGraph graph,
                                List<Item> filter, int batch, WorkerState state) {
        IItemHandler in = handlerOf(source, graph, state);
        IItemHandler out = handlerOf(target, graph, state);
        if (in == null || out == null) {
            return 0;
        }
        long moved = 0;
        for (int slot = 0; slot < in.getSlots() && moved < batch; slot++) {
            ItemStack stack = in.getStackInSlot(slot);
            if (stack.isEmpty() || (!filter.isEmpty() && !filter.contains(stack.getItem()))) {
                continue;
            }
            int wanted = (int) Math.min(batch - moved, stack.getCount());
            ItemStack simulated = in.extractItem(slot, wanted, true);
            ItemStack rest = insertInto(out, simulated);
            int accepted = simulated.getCount() - rest.getCount();
            if (accepted <= 0) {
                state.status = Status.WAITING_TARGET;
                state.detail = "Ziel ist voll";
                break;
            }
            in.extractItem(slot, accepted, false);
            moved += accepted;
        }
        return moved;
    }

    // ---- Hilfen -----------------------------------------------------------

    private static ItemStack insertInto(IItemHandler handler, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !rest.isEmpty(); slot++) {
            rest = handler.insertItem(slot, rest, false);
        }
        return rest;
    }

    private IItemHandler handlerOf(Expr target, FactoryGraph graph, WorkerState state) {
        if (!(target instanceof Expr.Name name)) {
            state.status = Status.HALTED;
            state.detail = "Gruppen und Muster kann diese Fassung noch nicht";
            return null;
        }
        Optional<BlockPos> position = graph.connector(name.value());
        if (position.isEmpty()) {
            state.status = Status.HALTED;
            state.detail = graph.closestName(name.value())
                    .map(suggestion -> "Unbekannter Connector " + name.value()
                            + " — meintest du " + suggestion + "?")
                    .orElse("Unbekannter Connector " + name.value());
            return null;
        }
        // Der Aufrufer hält die Welt; hier reicht der Connector selbst.
        return connectorHandler(position.get(), state);
    }

    private IItemHandler connectorHandler(BlockPos position, WorkerState state) {
        ConnectorBlockEntity connector = connectorAt(position);
        if (connector == null) {
            state.status = Status.WAITING_TARGET;
            state.detail = "Connector gerade nicht erreichbar";
            return null;
        }
        IItemHandler handler = connector.machineInventory();
        if (handler == null) {
            state.status = Status.HALTED;
            state.detail = "An diesem Connector hängt keine Maschine mit Inventar";
        }
        return handler;
    }

    /** Wird vom Controller vor jedem Tick gesetzt, damit hier keine Welt liegt. */
    private java.util.function.Function<BlockPos, ConnectorBlockEntity> connectorLookup = pos -> null;

    public void setConnectorLookup(java.util.function.Function<BlockPos, ConnectorBlockEntity> lookup) {
        this.connectorLookup = lookup;
    }

    private ConnectorBlockEntity connectorAt(BlockPos position) {
        return connectorLookup.apply(position);
    }

    private static boolean isStorage(Expr expr) {
        return expr instanceof Expr.Builtin builtin
                && builtin.kind() == Expr.Builtin.Kind.STORAGE;
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
     * Die Arten, auf die gefiltert wird — leer heißt alles.
     *
     * <p>Tags, Muster und {@code except} werden hier aufgelöst, einmal und
     * dann gemerkt. Das ist der Fall, für den die Auswahlregeln entworfen
     * wurden: In einem AllTheMods-Pack ist {@code tag:c/ores} der Normalfall
     * und die Aufzählung die Ausnahme.
     */
    private List<Item> filterItems(Decl.Worker worker) {
        Decl.Worker.Entry filter = worker.entry(Decl.Worker.Entry.Kind.FILTER);
        if (filter == null) {
            return List.of();
        }
        List<Item> resolved = ItemSelection.resolve(filter.value());
        if (resolved.isEmpty()) {
            note(worker.name() + ": die Auswahl trifft zurzeit nichts");
        }
        return resolved;
    }

    // ---- maintain und when ------------------------------------------------

    private static int maintainOf(Decl.Worker worker) {
        Decl.Worker.Entry maintain = worker.entry(Decl.Worker.Entry.Kind.MAINTAIN);
        if (maintain != null && maintain.value() instanceof Expr.IntLit count) {
            return (int) count.value();
        }
        return 0;
    }

    /**
     * Wie viel am Ziel schon liegt.
     *
     * <p>{@code maintain} gilt <b>pro Zielgerät</b>, nicht für die Gruppe, und
     * <b>pro Gegenstandsart</b>. Beim Speicher fallen beide Lesarten zusammen,
     * weil es ein Ziel ist. Nachzulesen in {@code sprache.md}, Abschnitt 7.
     */
    private long presentAtTarget(Expr target, FactoryGraph graph, NetworkStorage storage,
                                 List<Item> filter, WorkerState state) {
        if (isStorage(target)) {
            return filter.stream().mapToLong(storage::count).sum();
        }
        IItemHandler handler = handlerOf(target, graph, state);
        if (handler == null) {
            return 0;
        }
        long present = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || (!filter.isEmpty() && !filter.contains(stack.getItem()))) {
                continue;
            }
            present += stack.getCount();
        }
        return present;
    }

    /**
     * Wertet {@code when} aus.
     *
     * <p>Erlaubt sind nur beobachtbare Zustände. Diese Fassung versteht
     * Vergleiche über den Bestand im Speicher — mehr braucht der erste Schnitt
     * nicht, und alles Weitere ohne Beobachtbarkeit einzubauen hieße, genau
     * die Polling-Schleife zurückzuholen, gegen die Worker erfunden wurden.
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
        note(worker.name() + ": diese Bedingung kann die Laufzeit noch nicht auswerten");
        state.detail = "Bedingung noch nicht auswertbar";
        return true;
    }

    /** Liefert eine Zahl, wenn sie sich beobachten lässt — sonst nichts. */
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

    /** Der Speicher des laufenden Ticks, damit Bedingungen ihn lesen können. */
    private NetworkStorage currentStorage = new NetworkStorage();

    private void note(String message) {
        if (!notes.contains(message)) {
            notes.add(message);
        }
    }
}
