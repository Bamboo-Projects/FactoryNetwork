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
    /** Hinweise, die noch niemand abgeholt hat. */
    private final List<LogEntry> notes = new ArrayList<>();

    /**
     * Wem zu melden ist, dass ein Worker in ein Gerät geschrieben hat.
     *
     * <p>Der Controller zieht daraufhin die Grundlinie für
     * {@code device_output} nach. <b>Ohne das meldete jede Lieferung eine
     * Ausgabe, die es nie gab</b> — ein Worker, der alle zwanzig Ticks acht
     * Stück in eine Maschine legt, weckte damit im Sekundentakt jeden, der
     * auf ihre Ausgabe wartet.
     */
    private java.util.function.Consumer<String> onDeviceFilled;

    /**
     * Was schon einmal gesagt wurde.
     *
     * <p>Getrennt von {@link #notes}, weil die abgeholt und geleert werden:
     * Ohne dieses Gedächtnis stünde derselbe Hinweis nach jedem Abholen
     * wieder da, und ein Worker läuft zwanzigmal je Sekunde.
     */
    private final java.util.Set<String> saidBefore = new java.util.HashSet<>();
    /** Die Gruppen des Programms, gegen das Netz aufgelöst. */
    private final Map<String, DeviceGroup> groups = new LinkedHashMap<>();

    /**
     * Die Filter-Vorlagen des Programms, nach Namen.
     *
     * <p>Neu eingelesen, wenn ein anderes Programm kommt — nicht bei jedem
     * Tick wie die Gruppen. Eine Vorlage hängt an keinem Gerät: Was sie
     * auswählt, ändert sich nicht dadurch, dass ein Ofen dazukommt.
     */
    private final Map<String, Decl.FilterTemplate> templates = new LinkedHashMap<>();

    /** Das Programm, aus dem die Vorlagen stammen — für den Vergleich. */
    private Program templateSource;

    /**
     * Der Stromvorrat des Netzes, oder {@code null}.
     *
     * <p>Setzt der Controller. Ohne ihn — in Prüfungen ohne Welt — hält ein
     * Strom-Worker an und sagt es, statt so zu tun, als flösse etwas.
     */
    private dev.devpanda.factorynetwork.network.NetworkPower power;
    private final java.util.random.RandomGenerator random = new java.util.Random();

    /** Womit eine {@code when}-Bedingung ausgewertet wird, oder {@code null}. */
    private Interpreter.Host conditionHost;
    private Program conditionProgram;

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

    /**
     * Holt die neuen Hinweise ab und leert die Liste.
     *
     * <p>Abholen statt Ansehen: Der Controller schreibt sie ins Protokoll,
     * und was dort steht, muss hier nicht noch einmal liegen.
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

    /** Ein Tick: läuft über alle Worker und bewegt, was ansteht. */
    public void tick(Level level, Program program, FactoryGraph graph,
            dev.devpanda.factorynetwork.network.NetworkStores stores) {
        tick(level, program, graph, stores, null);
    }

    /**
     * Derselbe Tick, aber mit einem Host für die Bedingungen.
     *
     * <p><b>Ohne ihn kann {@code when} fast nichts.</b> Es gab hier einen
     * eigenen kleinen Auswerter, der Literale und {@code storage.count(…)}
     * kannte — alles andere galt als wahr. Ein Worker mit
     * {@code when modus == "tag"} lief damit immer, auch nachts, und das
     * Merkwürdige daran: Er meldete es sogar („Bedingung noch nicht
     * auswertbar"), nur eben in einer Zeile, die niemand liest, während er
     * weiterarbeitete.
     */
    /**
     * Was in diesem Tick schon über die Kabel ging.
     *
     * <p>Hier und nicht am Graphen: Der Graph wird neu gebaut, wenn sich die
     * Welt ändert — das Budget läuft je Tick ab, unabhängig davon.
     */
    private final dev.devpanda.factorynetwork.network.TickBudget budget =
            dev.devpanda.factorynetwork.network.TickBudget.create();

    /** Das Budget dieses Ticks — für die Anzeige. */
    public dev.devpanda.factorynetwork.network.TickBudget budget() {
        return budget;
    }

    /**
     * Was das Netz über die Zeit bewegt hat.
     *
     * <p>Neben dem Budget und nicht darin: Das Budget läuft je Tick ab, der
     * Verlauf läuft weiter.
     */
    private final dev.devpanda.factorynetwork.network.TrafficHistory history =
            new dev.devpanda.factorynetwork.network.TrafficHistory();

    public dev.devpanda.factorynetwork.network.TrafficHistory history() {
        return history;
    }

    public void tick(Level level, Program program, FactoryGraph graph,
            dev.devpanda.factorynetwork.network.NetworkStores stores, Interpreter.Host host) {
        // Ein neuer Tick fängt bei null an: Was gestern durchging, begrenzt
        // heute nichts.
        budget.reset();
        history.tick();
        this.conditionHost = host;
        this.conditionProgram = program;
        // Alle Bestände in einem Griff. Vorher standen hier zwei Parameter
        // und ein Setter daneben, und der Setter wurde beim Chemikalienpfad
        // einmal vergessen.
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
            int interval = intervalOf(worker);
            if (now - state.lastRun < interval) {
                continue;
            }
            state.lastRun = now;
            runWorker(level, worker, state, graph, storage);
        }
    }

    /**
     * Die Worker in der Reihenfolge, in der sie drankommen.
     *
     * <p><b>Kleine Zahl zuerst</b>, wie überall in der Sprache, und bei
     * gleicher Zahl in der Reihenfolge, in der sie dastehen. Das war bisher
     * eine Zusage ohne Wirkung: {@code priority} stand in der Doku und wurde
     * nirgends gelesen.
     *
     * <p>Beim Strom entscheidet die Reihenfolge, wer bei Knappheit leer
     * ausgeht — und bei Gegenständen, wer den letzten Stapel aus einem
     * knappen Lager bekommt.
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
     * Löst die Gruppen gegen das Netz auf.
     *
     * <p>Bei jedem Tick, aber billig: Es sind wenige Gruppen mit wenigen
     * Mustern. Und es muss laufend geschehen — ein Ofen, der dazukommt, soll
     * ohne erneutes Übernehmen in seiner Gruppe landen.
     *
     * <p><b>Und einmal beim Übernehmen</b>, weil eine Gruppe sonst einen Tick
     * lang leer wäre: Ein Ablauf, der sofort nach dem Übernehmen startet,
     * fragte {@code kisten.members()} und bekäme nichts.
     */
    public void resolveGroups(Program program, FactoryGraph graph) {
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

    private void runWorker(Level level, Decl.Worker worker, WorkerState state,
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

        // maintain gilt je Gegenstandsart, nicht insgesamt: filter tag:c/coals
        // mit maintain 64 hält 64 von jeder Kohleart. Deshalb wird der Filter
        // auf die Arten verengt, die noch fehlen.
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

        // <b>Hier wirkt der Durchsatz.</b> Der Weg zum Gerät sagt, wie
        // viel in diesem Tick noch hindurchgeht; der Worker nimmt das
        // Kleinere von beidem.
        //
        // Und er fällt nicht aus, wenn nichts mehr frei ist — er bewegt
        // nichts und versucht es im nächsten Tick wieder. Das ist der
        // Unterschied zu den Kanälen: eng, nicht tot.
        List<FactoryGraph.Node> path = pathFor(from.value(), to.value(),
                fromStorage, toStorage, graph);
        int free = path.isEmpty()
                ? dev.devpanda.factorynetwork.network.Bandwidth.UNLIMITED
                : budget.free(level, path);
        if (free <= 0) {
            // Dieselbe Art zu warten wie bei einem vollen Ziel: Es liegt
            // nicht am Programm, sondern an der Leitung davor.
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
        // Mit der wirklichen Menge, nicht der geplanten: Wer 64 wollte und
        // 3 bekam, hat das Kabel nicht mit 64 belegt.
        budget.spend(path, (int) moved);
        // Und derselbe Betrag in den Verlauf, unter dem Namen des Workers.
        // Nicht unter dem des Geräts: Ein Worker kann von mehreren nehmen,
        // und die Frage lautet „welches Programm frisst am meisten".
        history.record(worker.name(), (int) moved);

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
            long rest = storage.insert(taken);
            if (rest > 0) {
                ItemStack zurueck = insertInto(handler,
                        taken.copyWithCount((int) rest));
                if (!zurueck.isEmpty()) {
                    // Das Gerät nimmt nicht einmal zurück, was gerade drin
                    // lag. Dann fällt es lieber auf den Boden als zu
                    // verschwinden.
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
        // Ohne Filter wird der erste Posten genommen, der im Speicher
        // liegt. <b>Ein Posten und keine Art:</b> Was hier gewählt wird,
        // geht danach als Stapel in die Maschine — und eine benannte Hacke
        // käme als nackte an, wenn hier nur die Kennung stünde.
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

    /**
     * Der Weg, über den dieser Griff läuft.
     *
     * <p><b>Das belastete Gerät ist das, das nicht der Speicher ist.</b>
     * Zwischen zwei Geräten zählt der längere der beiden Wege — die Ware muss
     * über beide, und ein Kabel, das nur auf einer Seite eng ist, ist trotzdem
     * eng.
     *
     * <p>Ein leerer Weg heißt: Wir wissen es nicht. Dann begrenzt nichts —
     * lieber zu großzügig als ein Worker, der aus einem Missverständnis
     * stillsteht.
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

    /** Der Weg eines benannten Geräts, oder leer. */
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

    /** Sagt Bescheid, sobald ein Worker in ein Gerät geschrieben hat. */
    public void setDeviceFilled(java.util.function.Consumer<String> listener) {
        this.onDeviceFilled = listener;
    }

    /** Die Meldung für dieses Gerät, oder {@code null} ohne Empfänger. */
    private Runnable noticeFor(dev.devpanda.factorynetwork.block.entity.ConnectorPart connector) {
        if (onDeviceFilled == null) {
            return null;
        }
        String device = connector.label();
        return () -> onDeviceFilled.accept(device);
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
            java.util.function.Function<dev.devpanda.factorynetwork.block.entity.ConnectorPart, T> extract,
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
     * {@code strategy least_filled} verteilte in Wahrheit zufällig.
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
            // Zwischen "gibt es nicht" und "gibt es zweimal" unterscheiden:
            // Sonst sucht der Spieler an der falschen Stelle.
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
        // Der Aufrufer hält die Welt; hier reicht der Anschluss selbst.
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

    /** Wird vom Controller vor jedem Tick gesetzt, damit hier keine Welt liegt. */
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

    // ---- Nachschub ---------------------------------------------------------

    /**
     * Die Fertigung des Netzes; setzt der Controller.
     *
     * <p>Zwei Fragen, und die zweite ist die, an der es sonst schiefgeht: Ein
     * Worker, der nur den Bestand ansieht, bestellt jede Runde neu, solange
     * der Auftrag läuft — aus „halte 256 vor" werden Tausende.
     */
    public interface Crafting {

        /** Wie viel von dieser Art bestellt und noch nicht geliefert ist. */
        long pending(Item item);

        /** Bestellt; {@code false}, wenn es dafür kein Rezept gibt. */
        boolean order(Item item, int amount);
    }

    private Crafting crafting;

    public void setCrafting(Crafting crafting) {
        this.crafting = crafting;
    }

    /**
     * Ein Worker, der bestellt statt zu schieben.
     *
     * <p>{@code from crafting} ist eine Quelle wie jede andere — das ist der
     * Grund, warum {@code from} eine Quelle nennt und keine Betriebsart.
     * Vorratshaltung braucht damit keine eigene Form:
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
     * <p>Drei Formentscheidungen stecken darin. <b>Das Ziel ist der
     * Speicher</b>, und nur der: Gefertigt wird ins Lager, und von dort holt
     * es ein zweiter Worker ab — dem Fabricator ein Ziel beizubringen, das er
     * nicht hat, wäre der teurere Weg. <b>{@code maintain} ist Pflicht</b>,
     * denn ohne eine Zahl hieße die Anweisung „bestelle endlos". Und
     * <b>{@code rate} begrenzt die Bestellung nur, wenn es dasteht</b>: Wer
     * es schreibt, meint „höchstens so viel je Runde"; wer es weglässt, will
     * die Lücke geschlossen haben und nicht in Häppchen von vierundsechzig.
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
            // Der Bestand allein reicht nicht: Er steigt erst, wenn der
            // Auftrag fertig ist.
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
     * Die Arten, auf die gefiltert wird — leer heißt alles.
     *
     * <p>Tags, Muster und {@code except} werden hier aufgelöst, einmal und
     * dann gemerkt. Das ist der Fall, für den die Auswahlregeln entworfen
     * wurden: In einem AllTheMods-Pack ist {@code tag:c/ores} der Normalfall
     * und die Aufzählung die Ausnahme.
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
     * Die Arten hinter einem Vorlagennamen.
     *
     * <p><b>Halten statt weitermachen.</b> Eine leere Liste heißt für den
     * Worker „kein Filter", und kein Filter heißt „alles" — wegen eines
     * Tippfehlers im Vorlagennamen zöge er dann das ganze Lager um.
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


    // ---- Strom -------------------------------------------------------------

    /** Der Stromvorrat des Netzes; setzt der Controller. */
    public void setPower(dev.devpanda.factorynetwork.network.NetworkPower supply) {
        this.power = supply;
    }

    /**
     * Ein Worker, der Strom bewegt.
     *
     * <p><b>Eine Seite ist immer das Netz.</b> {@code from network to
     * crusher_1} versorgt, {@code from akku_1 to network} speist ein. Strom
     * von einer Maschine direkt in die andere zu schieben wäre eine Leitung
     * ohne Kabel — dafür gibt es Kabel.
     *
     * <p>Es gibt <b>keine Kabelgrenze</b> (entschieden am 25.08.): Was
     * fließt, begrenzen die Rate des Workers, der Vorrat des Netzes und was
     * die Maschine annimmt. Eine zweite Knappheit neben {@code priority}
     * hätte zwei Ursachen für dasselbe Symptom bedeutet.
     *
     * <p>Bei Knappheit gilt die Reihenfolge der {@code priority}: Wer zuerst
     * drankommt, bekommt bis zu seiner Rate, und wer leer ausgeht, geht leer
     * aus. Die Reihenfolge stellt {@link #tick} her.
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

    /** Aus dem Netz in eine Maschine. */
    private int supply(Expr target, FactoryGraph graph, WorkerState state, int batch) {
        IEnergyStorage machine = energyOf(target, graph, state);
        if (machine == null) {
            return 0;
        }
        // Erst fragen, wie viel hineinpasst, dann erst aus dem Vorrat nehmen:
        // Was die Maschine nicht annimmt, wäre sonst für einen Tick weg.
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
            // Zwischen Frage und Antwort kann sich etwas geändert haben.
            power.fill(taken - accepted);
        }
        // Damit die Abgabe im Netz-Reiter steht: Sie zählt nicht zum Bedarf,
        // und ohne diese Meldung sähe ein Netz, das kräftig liefert, aus wie
        // eines, das nichts tut.
        power.noteSupplied(accepted);
        return accepted;
    }

    /** Aus einer Maschine ins Netz. */
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

    /** Der Stromspeicher hinter einem Ziel. */
    private IEnergyStorage energyOf(Expr target, FactoryGraph graph, WorkerState state) {
        return resolve(target, graph, state, dev.devpanda.factorynetwork.block.entity.ConnectorPart::machineEnergy,
                "An diesem Connector hängt nichts, das Strom annimmt", this::energyLevelOf);
    }

    /**
     * Wie voll der Stromspeicher eines Geräts ist — für {@code least_filled}.
     *
     * <p>Ohne diese Fassung sortierte eine Gruppe von Maschinen nach
     * Gegenstands-Fächern, die für Strom nichts aussagen: Alle sähen gleich
     * voll aus, und die Verteilung wäre in Wahrheit reihum.
     */
    private long energyLevelOf(String device) {
        IEnergyStorage machine = lastGraph == null ? null
                : resolveDevice(device, lastGraph, new WorkerState(),
                        dev.devpanda.factorynetwork.block.entity.ConnectorPart::machineEnergy, "");
        return machine == null ? Long.MAX_VALUE : machine.getEnergyStored();
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
     * Meint der Filter dieses Workers Flüssigkeiten?
     *
     * <p>Die Regel selbst steht in {@link WorkerKind}, weil die Prüfung im
     * Editor dieselbe braucht. Zwei Fassungen davon liefen auseinander.
     */
    // ---- Chemikalien -------------------------------------------------------

    /** Der Chemikalienspeicher des Netzes; kommt mit dem Tick. */
    private dev.devpanda.factorynetwork.network.ResourceStore chemicals =
            dev.devpanda.factorynetwork.network.ResourceStore.NONE;

    /**
     * Ein Worker, der Chemikalien bewegt.
     *
     * <p>Derselbe Aufbau wie bei Flüssigkeiten, und aus denselben Gründen:
     * <b>Ein {@code filter} ist Pflicht</b>, denn ein Behälter hält meist
     * genau eine Sorte, und die falsche zu ziehen ist teurer als bei
     * Gegenständen. <b>Nur zwischen Gerät und Speicher</b>, in beide
     * Richtungen — von Gerät zu Gerät läuft es über den Speicher, und dafür
     * schreibt man zwei Worker.
     *
     * <p>Ohne Mekanism hält er an und sagt es. Ein Worker, der still nichts
     * tut, weil eine Mod fehlt, ist der Fehler, den man am längsten sucht.
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
            // Wie überall: je Sorte, nicht insgesamt.
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

    /** Die Kennungen hinter dem Filter eines Chemikalien-Workers. */
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
     * Der Connector hinter einem Gerätewert, oder {@code null}.
     *
     * <p>Über denselben Weg wie bei Gegenständen und Flüssigkeiten: Der
     * Helfer kennt die Fälle, an denen es sonst schiefgeht — ein Name, den es
     * nicht gibt, einer, den es zweimal gibt, und einer ohne freien Kanal.
     * Drei verschiedene Meldungen für drei verschiedene Suchen.
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
     * <p><b>Mit dem echten Interpreter</b>, sobald ein Host da ist: Damit gilt
     * hier dasselbe wie überall sonst in der Sprache — Texte, globale Werte,
     * Gerätezustände. Ohne Host bleibt der alte Weg, Zahl gegen Zahl; das
     * betrifft Prüfungen ohne Welt.
     *
     * <p>Erlaubt sind trotzdem nur beobachtbare Zustände: Eine Bedingung, für
     * die jemand eine Schleife schreiben müsste, holte genau das Polling
     * zurück, gegen das Worker erfunden wurden.
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

        // Mit dem echten Interpreter, damit hier dasselbe gilt wie überall
        // sonst in der Sprache: Texte, globale Werte, Gerätezustände.
        if (conditionHost != null && conditionProgram != null) {
            try {
                return new Interpreter(conditionProgram, conditionHost).evaluateAsBoolean(condition);
            } catch (ScriptError error) {
                // Eine kaputte Bedingung hält den Worker an, statt ihn
                // stillschweigend laufen zu lassen. Andersherum wäre es die
                // gefährlichere Wahl: Ein Worker, der bei jedem Fehler
                // arbeitet, bewegt Dinge, die niemand bewegt haben wollte.
                state.status = Status.HALTED;
                state.detail = error.getMessage();
                note(worker, error.getMessage());
                return false;
            }
        }

        // Ohne Host bleibt der alte Weg: Zahlen gegen Zahlen.
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

    /**
     * Ein Hinweis zu einem Worker.
     *
     * <p>Der Name steht als Herkunft daneben und nicht mehr im Text: So
     * lässt er sich im Protokoll filtern, und die Zeile wiederholt ihn nicht.
     * Warnung und nicht Fehler — der Worker läuft weiter, er tut nur nichts.
     */
    private void note(Decl.Worker worker, String message) {
        note(LogLevel.WARN, worker.name(), message);
    }

    private void note(LogLevel level, String source, String message) {
        // <b>Einmal und nicht öfter.</b> Ein Worker läuft zwanzigmal je
        // Sekunde; ohne diese Prüfung stünde derselbe Hinweis ebenso oft im
        // Protokoll und verdeckte alles andere. Zurückgesetzt wird das
        // Gedächtnis beim Übernehmen eines Programms — dann darf der Hinweis
        // wiederkommen, denn dann ist er eine neue Auskunft.
        if (!saidBefore.add(source + "\n" + message)) {
            return;
        }
        notes.add(new LogEntry(level, System.currentTimeMillis(), source, message));
        if (notes.size() > 100) {
            notes.remove(0);
        }
    }
}
