package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
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
 * <p>Ausgeführt werden alle Angaben, die ein Worker kennt: {@code from},
 * {@code to}, {@code filter}, {@code rate}, {@code maintain}, {@code when},
 * {@code priority}, {@code strategy} und {@code overflow}.
 *
 * <p>Was die Laufzeit <b>nicht</b> kann, meldet sie, statt es stillschweigend
 * zu übergehen — bei {@code when} etwa eine Bedingung, die über
 * Zahlvergleiche hinausgeht.
 */
public final class WorkerRuntime {

    /** Wie viel ein Worker ohne rate-Angabe je Durchgang bewegt. */
    private static final int DEFAULT_BATCH = 64;
    /** Abstand zwischen zwei Durchgängen ohne rate-Angabe, in Ticks. */
    private static final int DEFAULT_INTERVAL = 20;

    private final Map<String, WorkerState> states = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();
    /** Die Gruppen des Programms, gegen das Netz aufgelöst. */
    private final Map<String, DeviceGroup> groups = new LinkedHashMap<>();
    private final java.util.random.RandomGenerator random = new java.util.Random();

    /** Der Zustand eines Workers, wie er auch im Terminal steht. */
    public static final class WorkerState {
        public Status status = Status.IDLE;
        /** Vom Worker vorgegebene Verteilung, sonst gilt die der Gruppe. */
        public String strategyOverride;
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
        groups.clear();
    }

    public Map<String, DeviceGroup> groups() {
        return groups;
    }

    /** Ein Tick: läuft über alle Worker und bewegt, was ansteht. */
    public void tick(Level level, Program program, FactoryGraph graph, NetworkStorage storage,
            NetworkFluids fluids) {
        this.fluids = fluids == null ? new NetworkFluids() : fluids;
        long now = level.getGameTime();
        currentStorage = storage;
        lastGraph = graph;
        resolveGroups(program, graph);
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

    /**
     * Löst die Gruppen gegen das Netz auf.
     *
     * <p>Bei jedem Tick, aber billig: Es sind wenige Gruppen mit wenigen
     * Mustern. Und es muss laufend geschehen — ein Ofen, der dazukommt, soll
     * ohne erneutes Übernehmen in seiner Gruppe landen.
     */
    private void resolveGroups(Program program, FactoryGraph graph) {
        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.Group group) {
                DeviceGroup previous = groups.get(group.name());
                DeviceGroup resolved = DeviceGroup.resolve(group, graph);
                // Den Zeiger von round_robin behalten, sonst fängt die Gruppe
                // bei jedem Tick wieder beim ersten Gerät an.
                if (previous != null && previous.members().equals(resolved.members())) {
                    continue;
                }
                groups.put(group.name(), resolved);
            }
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

        if (isFluidWorker(worker)) {
            tickFluidWorker(worker, graph, fluids, state, from, to);
            return;
        }

        int batch = batchOf(worker);
        List<Item> filter = filterItems(worker);
        state.strategyOverride = strategyOf(worker);

        boolean fromStorage = isStorage(from.value());
        boolean toStorage = isStorage(to.value());

        if (fromStorage && toStorage) {
            state.status = Status.IDLE;
            state.detail = "Quelle und Ziel sind beide der Speicher";
            return;
        }

        // maintain gilt je Gegenstandsart, nicht insgesamt: filter tag:c/coals
        // mit maintain 64 hält 64 von jeder Kohleart. Deshalb wird der Filter
        // auf die Arten verengt, die noch fehlen.
        int maintain = maintainOf(worker);
        if (maintain > 0) {
            if (filter.isEmpty()) {
                state.status = Status.HALTED;
                state.detail = "maintain braucht ein filter";
                note(worker.name() + ": maintain ohne filter — welche Art soll vorgehalten werden?");
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

        long moved;
        if (fromStorage) {
            moved = storageToDevice(to.value(), graph, storage, filter, batch, state);
        } else if (toStorage) {
            moved = deviceToStorage(from.value(), graph, storage, filter, batch, state);
        } else {
            moved = deviceToDevice(from.value(), to.value(), graph, filter, batch, state);
        }

        // Ist das Ziel voll und ein Ausweichziel angegeben, geht es dorthin.
        // Ohne das steht ein Worker bei vollem Lager still, statt den
        // Überschuss loszuwerden — und die Maschine davor läuft voll.
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
            if (taken.isEmpty()) {
                continue;
            }
            // Was der Speicher nicht nimmt, geht zurück ins Gerät. Seit es
            // Zellen gibt, kann er voll sein — und dann wären die Gegenstände
            // sonst weg, ohne dass es jemand merkt.
            long rest = storage.insert(taken.getItem(), taken.getCount());
            if (rest > 0) {
                ItemStack zurueck = insertInto(handler,
                        new ItemStack(taken.getItem(), (int) rest));
                if (!zurueck.isEmpty()) {
                    // Das Gerät nimmt nicht einmal zurück, was gerade drin
                    // lag. Dann fällt es lieber auf den Boden als zu
                    // verschwinden.
                    dropped.add(zurueck);
                }
                state.status = Status.WAITING_TARGET;
                state.detail = "Der Speicher ist voll";
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

    /**
     * Das Inventar hinter einem Ziel.
     *
     * <p>Ist das Ziel eine Gruppe, wird das erste Mitglied genommen, das nach
     * ihrer Verteilung an der Reihe ist und tatsächlich etwas annimmt. Deshalb
     * liefert {@link DeviceGroup#order} eine ganze Reihenfolge und nicht nur
     * eine Wahl: Ein volles Gerät darf den Transfer nicht beenden.
     */
    private IItemHandler handlerOf(Expr target, FactoryGraph graph, WorkerState state) {
        return resolve(target, graph, state, ConnectorBlockEntity::machineInventory,
                "An diesem Connector hängt keine Maschine mit Inventar", this::fillLevelOf);
    }

    private IFluidHandler tankOf(Expr target, FactoryGraph graph, WorkerState state) {
        return resolve(target, graph, state, ConnectorBlockEntity::machineTank,
                "An diesem Connector hängt nichts, das Flüssigkeit hält", this::tankLevelOf);
    }

    /**
     * Findet, womit ein Worker arbeiten soll.
     *
     * <p>Gruppen, Verteilung und alle Fehlermeldungen stehen hier ein einziges
     * Mal. Was am Ende geholt wird — ein Inventar oder ein Tank — entscheidet
     * der Aufrufer; sonst gäbe es diese ganze Suche zweimal, und die eine
     * Fassung bekäme Verbesserungen, die der anderen fehlen.
     */
    private <T> T resolve(Expr target, FactoryGraph graph, WorkerState state,
            java.util.function.Function<ConnectorBlockEntity, T> extract,
            String missingDetail, java.util.function.ToLongFunction<String> fillLevel) {
        if (!(target instanceof Expr.Name name)) {
            state.status = Status.HALTED;
            state.detail = "Als Ziel taugt nur ein Name";
            return null;
        }
        DeviceGroup group = groups.get(name.value());
        if (group != null) {
            // Eine Angabe am Worker geht der Gruppe vor: Dieselbe Gruppe kann
            // von zwei Workern verschieden bedient werden.
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
     * Wie voll ein Tank ist — für die Verteilung nach dem leersten.
     *
     * <p>Ohne diese Fassung würde eine Gruppe von Tanks nach Gegenstands-Slots
     * sortiert, die es dort nicht gibt: Jeder Tank sähe gleich voll aus, und
     * {@code strategy emptiest} verteilte in Wahrheit zufällig.
     */
    private long tankLevelOf(String device) {
        IFluidHandler tank = lastGraph == null ? null
                : resolveDevice(device, lastGraph, new WorkerState(),
                        ConnectorBlockEntity::machineTank, "");
        if (tank == null) {
            return Long.MAX_VALUE;
        }
        long used = 0;
        for (int slot = 0; slot < tank.getTanks(); slot++) {
            used += tank.getFluidInTank(slot).getAmount();
        }
        return used;
    }

    /** Wie voll ein Gerät ist — für die Verteilung nach dem leersten. */
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

    /** Der Graph des laufenden Ticks — nur für die Füllstandsabfrage. */
    private FactoryGraph lastGraph;

    /**
     * Was nirgends mehr hinpasste.
     *
     * <p>Der Controller wirft es in die Welt. Das ist hässlich, aber die
     * einzige Antwort, die keine Gegenstände verschwinden lässt: Wenn weder
     * Speicher noch Gerät etwas annehmen, muss es irgendwohin.
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

    /** Der Flüssigkeitsbestand des Netzes, für die Dauer eines Ticks. */
    private NetworkFluids fluids = new NetworkFluids();

    private IItemHandler handlerFor(String deviceName, FactoryGraph graph, WorkerState state) {
        return resolveDevice(deviceName, graph, state, ConnectorBlockEntity::machineInventory,
                "An diesem Connector hängt keine Maschine mit Inventar");
    }

    private <T> T resolveDevice(String deviceName, FactoryGraph graph, WorkerState state,
            java.util.function.Function<ConnectorBlockEntity, T> extract,
            String missingDetail) {
        Expr.Name name = new Expr.Name(deviceName, new dev.devpanda.factorynetwork.lang.Span(
                0, 0, 1, 1));
        Optional<BlockPos> position = graph.connector(name.value());
        if (position.isEmpty()) {
            state.status = Status.HALTED;
            // Zwischen "gibt es nicht" und "gibt es zweimal" unterscheiden:
            // Sonst sucht der Spieler an der falschen Stelle.
            if (graph.isAmbiguous(name.value())) {
                state.detail = "Der Name " + name.value() + " ist "
                        + graph.positionsOf(name.value()).size()
                        + "-mal vergeben und damit unbrauchbar";
            } else if (!graph.starvedConnectors().isEmpty()) {
                // Ein Gerät ohne Kanal hat einen Namen, ist aber nicht
                // erreichbar. Wer das als Tippfehler meldet, schickt den
                // Spieler auf die falsche Suche.
                state.detail = "Unbekannter Connector " + name.value()
                        + " — es hängen " + graph.starvedConnectors().size()
                        + " Geräte ohne freien Kanal im Netz";
            } else {
                state.detail = graph.closestName(name.value())
                        .map(suggestion -> "Unbekannter Connector " + name.value()
                                + " — meintest du " + suggestion + "?")
                        .orElse("Unbekannter Connector " + name.value());
            }
            return null;
        }
        // Der Aufrufer hält die Welt; hier reicht der Connector selbst.
        ConnectorBlockEntity connector = connectorAt(position.get());
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


    // ---- Flüssigkeiten ----------------------------------------------------

    /**
     * Ein Worker, der Flüssigkeit fördert.
     *
     * <p>Dieselbe Gestalt wie beim Gegenstands-Worker, dieselben Angaben:
     * {@code rate} zählt in Millibucket, {@code maintain} hält je Sorte einen
     * Vorrat, {@code overflow} nimmt, was das Ziel nicht mehr fasst. Was
     * anders ist, ist die Sache selbst — ein Tank hat keine Slots, und
     * gerechnet wird in Millibucket statt in Stück.
     */
    private void tickFluidWorker(Decl.Worker worker, FactoryGraph graph,
            NetworkFluids fluids, WorkerState state, Decl.Worker.Entry from,
            Decl.Worker.Entry to) {
        int batch = batchOf(worker);
        List<Fluid> filter = filterFluids(worker);
        state.strategyOverride = strategyOf(worker);

        boolean fromStorage = isStorage(from.value());
        boolean toStorage = isStorage(to.value());
        if (fromStorage && toStorage) {
            state.status = Status.IDLE;
            state.detail = "Quelle und Ziel sind beide der Speicher";
            return;
        }
        if (filter.isEmpty()) {
            // Ohne Angabe wäre nicht zu sagen, welche Sorte gemeint ist: Ein
            // Tank hält meist genau eine, und die falsche zu ziehen ist bei
            // Flüssigkeiten teurer als bei Gegenständen.
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
            // Erst fragen, wie viel der Speicher nimmt, dann erst ziehen.
            // Seit Flüssigkeiten in Zellen liegen, kann er voll sein — und
            // eine gezogene Flüssigkeit lässt sich nicht wie ein Gegenstand
            // zurücklegen, wenn der Tank sie nicht wieder annimmt.
            long asked = Math.min(batch - moved, inside.getAmount());
            long fits = fluids.room(inside.getFluid(), asked);
            if (fits <= 0) {
                state.status = Status.WAITING_TARGET;
                state.detail = "Der Speicher ist voll";
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
                // Nur melden, wenn wirklich nichts durchkam: Sonst stünde im
                // Terminal "nimmt nichts mehr" neben einer Zahl, die etwas
                // anderes sagt.
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
            FluidStack simulated = in.drain(wanted, IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty()) {
                continue;
            }
            int accepted = out.fill(simulated, IFluidHandler.FluidAction.EXECUTE);
            if (accepted <= 0) {
                if (moved == 0) {
                    state.status = Status.WAITING_TARGET;
                    state.detail = "Das Ziel nimmt nichts mehr";
                }
                break;
            }
            in.drain(new FluidStack(simulated.getFluid(), accepted),
                    IFluidHandler.FluidAction.EXECUTE);
            moved += accepted;
        }
        return moved;
    }

    /** Wie viel am Ziel schon steht, je Sorte — in Millibucket. */
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

    /** Die Sorten, auf die gefiltert wird. */
    private List<Fluid> filterFluids(Decl.Worker worker) {
        Decl.Worker.Entry filter = worker.entry(Decl.Worker.Entry.Kind.FILTER);
        if (filter == null) {
            return List.of();
        }
        List<Fluid> resolved = FluidSelection.resolve(filter.value());
        if (resolved.isEmpty()) {
            note(worker.name() + ": die Auswahl trifft zurzeit keine Flüssigkeit");
        }
        return resolved;
    }

    /**
     * Meint der Filter dieses Workers Flüssigkeiten?
     *
     * <p>Die Regel selbst steht in {@link WorkerKind}, weil die Prüfung im
     * Editor dieselbe braucht. Zwei Fassungen davon liefen auseinander.
     */
    private static boolean isFluidWorker(Decl.Worker worker) {
        return WorkerKind.of(worker) == Expr.Selector.Kind.FLUID;
    }

    // ---- maintain und when ------------------------------------------------

    /** Die Verteilung, die dieser Worker vorgibt — oder nichts. */
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
     * Wie viel am Ziel schon liegt, aufgeschlüsselt nach Art.
     *
     * <p>{@code maintain} gilt <b>pro Zielgerät</b>, nicht für die Gruppe, und
     * <b>pro Gegenstandsart</b>. Beim Speicher fallen beide Lesarten zusammen,
     * weil es ein Ziel ist. Nachzulesen in {@code sprache.md}, Abschnitt 7.
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
