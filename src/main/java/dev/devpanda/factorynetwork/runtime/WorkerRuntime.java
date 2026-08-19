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

        int batch = batchOf(worker);
        Item filter = filterItem(worker);

        boolean fromStorage = isStorage(from.value());
        boolean toStorage = isStorage(to.value());

        if (fromStorage && toStorage) {
            state.status = Status.IDLE;
            state.detail = "Quelle und Ziel sind beide der Speicher";
            return;
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
                                 Item filter, int batch, WorkerState state) {
        IItemHandler handler = handlerOf(source, graph, state);
        if (handler == null) {
            return 0;
        }
        long moved = 0;
        for (int slot = 0; slot < handler.getSlots() && moved < batch; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || (filter != null && stack.getItem() != filter)) {
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
                                 Item filter, int batch, WorkerState state) {
        IItemHandler handler = handlerOf(target, graph, state);
        if (handler == null) {
            return 0;
        }
        // Ohne Filter wird die erste Art genommen, die im Speicher liegt.
        Item item = filter != null ? filter : storage.contents().keySet().stream()
                .findFirst().orElse(null);
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
                                Item filter, int batch, WorkerState state) {
        IItemHandler in = handlerOf(source, graph, state);
        IItemHandler out = handlerOf(target, graph, state);
        if (in == null || out == null) {
            return 0;
        }
        long moved = 0;
        for (int slot = 0; slot < in.getSlots() && moved < batch; slot++) {
            ItemStack stack = in.getStackInSlot(slot);
            if (stack.isEmpty() || (filter != null && stack.getItem() != filter)) {
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
     * Der Gegenstand, auf den gefiltert wird — oder {@code null} für alles.
     *
     * <p>Diese Fassung versteht nur einen einzelnen Gegenstand. Tags, Muster
     * und {@code except} sind spezifiziert und geparst, aber noch nicht
     * aufgelöst; dafür braucht es den Zugriff auf die Registry mit
     * Zwischenspeicher, und der gehört in denselben Schritt wie die Anzeige im
     * Editor, die zeigt, was ein Muster gerade trifft.
     */
    private Item filterItem(Decl.Worker worker) {
        Decl.Worker.Entry filter = worker.entry(Decl.Worker.Entry.Kind.FILTER);
        if (filter == null) {
            return null;
        }
        if (!(filter.value() instanceof Expr.Selector selector)) {
            note(worker.name() + ": nur einzelne Gegenstände werden zurzeit gefiltert");
            return null;
        }
        if (selector.kind() != Expr.Selector.Kind.ITEM || selector.hasPattern()) {
            note(worker.name() + ": Tags und Muster kann diese Fassung noch nicht auflösen");
            return null;
        }
        String namespace = selector.hasNamespace() ? selector.namespace() : "minecraft";
        ResourceLocation id = ResourceLocation.tryBuild(namespace, selector.path());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            note(worker.name() + ": unbekannter Gegenstand " + namespace + ":" + selector.path());
            return null;
        }
        return BuiltInRegistries.ITEM.get(id);
    }

    private void note(String message) {
        if (!notes.contains(message)) {
            notes.add(message);
        }
    }
}
