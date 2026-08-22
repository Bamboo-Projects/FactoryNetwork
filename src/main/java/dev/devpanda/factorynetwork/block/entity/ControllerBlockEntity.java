package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.network.NetworkFluids;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.network.packet.DisplayStatePacket;
import dev.devpanda.factorynetwork.network.packet.FlowStatePacket;
import dev.devpanda.factorynetwork.network.packet.StorageSnapshotPacket;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.runtime.DisplayValues;
import dev.devpanda.factorynetwork.runtime.Interpreter;
import dev.devpanda.factorynetwork.runtime.MultiblockInstances;
import dev.devpanda.factorynetwork.runtime.ScriptError;
import dev.devpanda.factorynetwork.runtime.Value;
import dev.devpanda.factorynetwork.runtime.WorkerRuntime;
import dev.devpanda.factorynetwork.runtime.WorldHost;
import dev.devpanda.factorynetwork.runtime.flow.Flow;
import dev.devpanda.factorynetwork.runtime.flow.FlowCodec;
import dev.devpanda.factorynetwork.runtime.flow.FlowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wurzel eines Netzwerks: hält Programm, Speicher, Graph und Laufzeit.
 *
 * <p>Der Graph wird nicht in jedem Tick neu aufgebaut, sondern in Abständen
 * und wenn jemand den Controller anklickt. Ein Kabel wird selten gesetzt; ein
 * dauernder Neuaufbau wäre die teuerste Stelle der Mod für den geringsten
 * Nutzen.
 */
public class ControllerBlockEntity extends BlockEntity {

    private static final String KEY_SOURCE = "Source";
    private static final String KEY_STORAGE = "Storage";
    private static final String KEY_FLOWS = "Flows";
    private static final String KEY_FLUIDS = "Fluids";
    private static final String KEY_POWER = "Power";
    private static final int REBUILD_INTERVAL = 100;

    private String source = "";
    private Program program = new Program(List.of());
    private List<Diagnostic> diagnostics = new ArrayList<>();
    private FactoryGraph graph = FactoryGraph.empty();
    private final NetworkStorage storage = new NetworkStorage();
    private final NetworkFluids fluidStorage = new NetworkFluids();
    private final WorkerRuntime runtime = new WorkerRuntime();

    /**
     * Die Serverschränke im Netz.
     *
     * <p>Ohne einen davon rechnet das Netz nicht — weder Worker noch Abläufe.
     * So wie ein Laufwerk die Voraussetzung dafür ist, dass es lagert.
     */
    private final List<dev.devpanda.factorynetwork.block.entity.RackBlockEntity> racks =
            new ArrayList<>();

    /** Die Laufwerke im Netz — für den Stromverbrauch je Zelle. */
    private final List<DriveBlockEntity> drives = new ArrayList<>();

    /**
     * Der Strom des Netzes.
     *
     * <p>Der Puffer sitzt im Controller und nimmt Forge Energy an. Ohne
     * Strom steht alles still — und muss danach erst wieder hochfahren.
     */
    private final dev.devpanda.factorynetwork.network.NetworkPower power =
            new dev.devpanda.factorynetwork.network.NetworkPower();
    /** Abläufe, die warten können — sie überleben einen Serverneustart. */
    private FlowEngine flows;

    /**
     * Aufgeschriebene Abläufe, die noch warten, bis es eine Welt gibt.
     *
     * <p>Beim Laden steht die Welt noch nicht bereit, die Abläufe brauchen
     * aber einen Interpreter und der eine Welt. Also bleibt der Tag liegen,
     * bis der erste Tick kommt.
     */
    private CompoundTag pendingFlows;

    /**
     * Ob das Netz schon einmal aufgebaut wurde.
     *
     * <p>Ein leerer Graph und ein noch nie aufgebauter sehen gleich aus, sind
     * es aber nicht: Beim ersten Mal ist nichts dazugekommen.
     */
    private boolean networkKnown;
    /** Negativ statt Long.MIN_VALUE: Die Differenz liefe sonst über. */
    private long lastRebuild = -REBUILD_INTERVAL;

    /** Letzte gesehene Redstone-Stärke je Connector, für das Ereignis. */
    private final Map<String, Integer> lastRedstone = new HashMap<>();
    private final List<String> log = new ArrayList<>();

    /**
     * Wer gerade die Speicheransicht offen hat.
     *
     * <p>Nur an diese Spieler wird der Bestand geschickt, und nur solange sie
     * hinsehen. Wer den Code-Reiter liest, braucht keine Bestandsänderungen.
     */
    private final Set<ServerPlayer> storageWatchers = new HashSet<>();
    private long lastStoragePush = -10;
    private boolean storageDirty;

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.CONTROLLER.get(), pos, state);
        storage.setChangeListener(this::markStorageDirty);
    }

    // ---- Programm ---------------------------------------------------------

    public String source() {
        return source;
    }

    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public Program program() {
        return program;
    }

    /**
     * Übernimmt neuen Code. Bei Fehlern bleibt das laufende Programm stehen —
     * eine Fabrik, die wegen eines Tippfehlers ausgeht, ist schlimmer als eine,
     * die noch mit der alten Fassung läuft.
     */
    public boolean deploy(String newSource) {
        Parser.ParseResult result = Parser.parse(newSource);
        this.source = newSource;
        this.diagnostics = new ArrayList<>(result.diagnostics());
        // Erst nachsehen, dann urteilen: Wer eben einen Schrank gesetzt hat,
        // bekäme sonst „kein Server" zu hören, obwohl einer danebensteht.
        if (level != null && !hasServer()) {
            rebuildNetwork();
        }
        if (!hasServer()) {
            // Ein Programm ohne Server ist kein Fehler im Code. Es wird
            // trotzdem nicht übernommen — sonst stünde es da und liefe nicht,
            // und niemand wüsste warum.
            this.diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                    new dev.devpanda.factorynetwork.lang.Span(0, 0, 1, 1),
                    "Kein Serverschrank im Netz.",
                    "Setze einen Serverschrank ans Kabel und stecke einen Prozessor hinein."));
            setChanged();
            return false;
        }
        setChanged();
        if (result.hasErrors()) {
            return false;
        }
        // Wartende Abläufe gehen denselben Weg wie über einen Serverneustart:
        // aufschreiben, neue Maschine bauen, zurücklesen. Passt die Gestalt
        // des Programms noch, laufen sie weiter; passt sie nicht, melden sie
        // sich als STALE. Ein Weg, ein Verhalten — statt zwei, die
        // auseinanderlaufen.
        CompoundTag carried = flows == null ? null : FlowCodec.write(flows);
        this.program = result.program();
        runtime.reset();
        this.flows = null;
        if (!FlowCodec.isEmpty(carried)) {
            this.pendingFlows = carried;
        }
        return true;
    }

    // ---- Strom -------------------------------------------------------------

    public dev.devpanda.factorynetwork.network.NetworkPower power() {
        return power;
    }

    /**
     * Was das Netz gerade zieht, in FE je Tick.
     *
     * <p>Gezählt statt mitgeführt, wie die belegten Prozessorplätze: Ein
     * Zähler müsste bei jedem gesetzten und abgerissenen Block nachgeführt
     * werden, und der eine vergessene Ort wäre ein Netz, das für Geräte
     * zahlt, die es nicht mehr gibt.
     */
    public int powerDraw() {
        int total = dev.devpanda.factorynetwork.network.Power.CONTROLLER;
        total += graph.connectorCount() * dev.devpanda.factorynetwork.network.Power.CONNECTOR;
        total += graph.displays().size() * dev.devpanda.factorynetwork.network.Power.DISPLAY;
        total += graph.routers().size() * dev.devpanda.factorynetwork.network.Power.ROUTER;
        for (DriveBlockEntity drive : drives) {
            total += dev.devpanda.factorynetwork.network.Power.DRIVE
                    + drive.usedSlots() * dev.devpanda.factorynetwork.network.Power.PER_CELL;
        }
        for (var rack : racks) {
            total += dev.devpanda.factorynetwork.network.Power.RACK
                    + rack.usedSlots() * dev.devpanda.factorynetwork.network.Power.PER_PROCESSOR;
        }
        return total;
    }

    /** Läuft das Netz — genug Strom und ein Serverschrank? */
    public boolean isOnline() {
        return hasServer() && power.isRunning();
    }

    // ---- Rechenleistung ---------------------------------------------------

    /**
     * Wie viele Abläufe gleichzeitig laufen dürfen.
     *
     * <p>Die Summe der Prozessoren in allen Serverschränken des Netzes. Null
     * heißt: Es gibt keinen Server, und dann läuft gar nichts.
     */
    public int threads() {
        int total = 0;
        for (var rack : racks) {
            total += rack.threads();
        }
        return total;
    }

    /** Steht im Netz überhaupt Rechenleistung? */
    public boolean hasServer() {
        return threads() > 0;
    }

    // ---- Netzwerk ---------------------------------------------------------

    public FactoryGraph graph() {
        return graph;
    }

    public NetworkStorage storage() {
        return storage;
    }

    public NetworkFluids fluids() {
        return fluidStorage;
    }

    public WorkerRuntime runtime() {
        return runtime;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            ControllerRegistry.add(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            ControllerRegistry.remove(level, worldPosition);
        }
        super.setRemoved();
    }

    public void rebuildNetwork() {
        if (level == null) {
            return;
        }
        Set<String> before = networkKnown ? Set.copyOf(graph.connectorNames()) : null;
        graph = FactoryGraph.build(level, worldPosition);
        lastRebuild = level.getGameTime();
        networkKnown = true;
        // Der Speicher ist die Summe der Laufwerke im Netz. Ohne diesen
        // Schritt lagert der Controller nichts — was auch stimmt: Ohne
        // Laufwerk gibt es keinen Platz.
        List<DriveBlockEntity> found = new ArrayList<>();
        for (BlockPos pos : graph.drives()) {
            if (level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof DriveBlockEntity drive) {
                found.add(drive);
            }
        }
        drives.clear();
        drives.addAll(found);
        storage.setDrives(found);
        // Dieselben Laufwerke tragen die Flüssigkeitszellen. Ein zweites
        // Laufwerk nur dafür wäre ein Block mehr für dieselbe Handlung.
        fluidStorage.setDrives(found);
        racks.clear();
        for (BlockPos pos : graph.racks()) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos)
                    instanceof dev.devpanda.factorynetwork.block.entity.RackBlockEntity rack) {
                racks.add(rack);
            }
        }
        announceDeviceChanges(before);
    }

    /**
     * Meldet, welche Geräte gekommen und gegangen sind.
     *
     * <p>Beim allerersten Aufbau bleibt es still: Da ist nichts
     * dazugekommen, es war nur vorher nichts bekannt. Ein Sturm von
     * {@code device_online} bei jedem Serverstart wäre für den Spieler
     * dasselbe wie Rauschen.
     */
    private void announceDeviceChanges(Set<String> before) {
        if (before == null || program.handlers().isEmpty()) {
            return;
        }
        Set<String> now = graph.connectorNames();
        for (String name : now) {
            if (!before.contains(name)) {
                fireEvent("device_online", List.of(new Value.Device(name)));
            }
        }
        for (String name : before) {
            if (!now.contains(name)) {
                // Kein Value.Device: Das Gerät gibt es nicht mehr, und ein
                // Verweis darauf ließe sich nicht mehr auflösen.
                fireEvent("device_offline", List.of(new Value.Text(name)));
            }
        }
    }

    // ---- Tick -------------------------------------------------------------

    public void serverTick() {
        if (level == null) {
            return;
        }
        if (level.getGameTime() - lastRebuild >= REBUILD_INTERVAL) {
            rebuildNetwork();
        }
        power.setDraw(powerDraw());
        power.tick();

        // Ohne Serverschrank oder ohne Strom steht das Netz still — und läuft
        // weiter, wo es war, sobald beides wieder da ist. Nichts wird
        // abgebrochen: Ein Ablauf hält zwischen zwei Schritten keine
        // Gegenstände, also kostet das Einfrieren nichts.
        if (!isOnline()) {
            pushStorageIfDue();
            pushFlowsIfDue();
            setChanged();
            return;
        }
        // Abläufe laufen auch ohne Worker weiter — ein Programm darf allein
        // aus Funktionen bestehen, die auf Ereignisse warten.
        if (!program.workers().isEmpty()) {
            runtime.setConnectorLookup(position ->
                    level.isLoaded(position)
                            && level.getBlockEntity(position) instanceof ConnectorBlockEntity connector
                            ? connector : null);
            runtime.tick(level, program, graph, storage, fluidStorage);
            // Was weder Speicher noch Gerät annahm, fällt auf den Boden.
            // Hässlich, aber die einzige Antwort, die nichts verschwinden
            // lässt.
            for (net.minecraft.world.item.ItemStack stack : runtime.takeDropped()) {
                net.minecraft.world.level.block.Block.popResource(level, worldPosition, stack);
            }
        }
        tickFlows();
        fireRedstoneEvents();
        fireInventoryEvents();
        pushStorageIfDue();
        pushFlowsIfDue();
        setChanged();
    }

    // ---- Speicheransicht --------------------------------------------------

    /**
     * So oft höchstens, in Ticks. Ein Worker bewegt sonst jeden Tick etwas.
     *
     * <p>Der Anfangswert von {@code lastStoragePush} ist derselbe Wert
     * negativ — als Konstante geschrieben liefe er der Feldreihenfolge
     * voraus.
     */
    private static final int STORAGE_PUSH_INTERVAL = 10;

    public void watchStorage(ServerPlayer player) {
        storageWatchers.add(player);
        pushStorageTo(player, true);
        pushFlowsTo(player);
        pushDisplaysTo(player);
    }

    public void unwatchStorage(ServerPlayer player) {
        storageWatchers.remove(player);
    }

    /** Merkt vor, dass sich etwas geändert hat — geschickt wird gebündelt. */
    public void markStorageDirty() {
        storageDirty = true;
    }

    private void pushStorageIfDue() {
        if (level == null || storageWatchers.isEmpty()) {
            return;
        }
        if (!storageDirty || level.getGameTime() - lastStoragePush < STORAGE_PUSH_INTERVAL) {
            return;
        }
        lastStoragePush = level.getGameTime();
        storageDirty = false;
        // Abgemeldete Spieler mitnehmen, damit die Menge nicht leckt.
        storageWatchers.removeIf(player -> player.isRemoved()
                || !(player.containerMenu instanceof TerminalMenu));
        storageWatchers.forEach(player -> pushStorageTo(player, true));
    }

    /** Schickt den Bestand an einen Spieler. */
    public void pushStorageTo(ServerPlayer player, boolean replace) {
        Map<Item, Long> contents = storage.contents();
        List<StorageSnapshotPacket.Entry> entries = contents.entrySet().stream()
                .sorted(Map.Entry.<Item, Long>comparingByValue().reversed())
                .limit(StorageSnapshotPacket.MAX_ENTRIES)
                .map(entry -> new StorageSnapshotPacket.Entry(entry.getKey(), entry.getValue()))
                .toList();
        List<StorageSnapshotPacket.FluidEntry> fluidEntries = fluidStorage.contents().entrySet()
                .stream()
                .map(entry -> new StorageSnapshotPacket.FluidEntry(entry.getKey(),
                        entry.getValue()))
                .toList();
        PacketDistributor.sendToPlayer(player,
                new StorageSnapshotPacket(entries, fluidEntries, replace, contents.size(),
                        storage.freeTypes(), fluidStorage.freeTypes()));
    }

    // ---- Abläufe im Terminal ----------------------------------------------

    /** So oft höchstens, in Ticks. */
    private static final int FLOW_PUSH_INTERVAL = 10;

    private long lastFlowPush = -FLOW_PUSH_INTERVAL;

    /**
     * Schickt die Liste der Abläufe an die Zuschauer.
     *
     * <p>Anders als der Bestand wird sie nicht vorgemerkt, sondern regelmäßig
     * geschickt: Ein Ablauf, der schläft, ändert nichts und wäre trotzdem eine
     * Zeile wert, deren Zustand sich jederzeit ändern kann.
     */
    private void pushFlowsIfDue() {
        if (level == null || storageWatchers.isEmpty()
                || level.getGameTime() - lastFlowPush < FLOW_PUSH_INTERVAL) {
            return;
        }
        lastFlowPush = level.getGameTime();
        storageWatchers.forEach(player -> {
            pushFlowsTo(player);
            pushDisplaysTo(player);
        });
    }

    /** Schickt die Abläufe an einen Spieler. */
    public void pushFlowsTo(ServerPlayer player) {
        FlowEngine engine = flowEngine();
        PacketDistributor.sendToPlayer(player, new FlowStatePacket(flowLines(), threads(),
                engine == null ? 0 : engine.occupied(),
                engine == null ? 0 : engine.queued(),
                new FlowStatePacket.Supply(power.state().ordinal(), power.stored(),
                        power.capacity(), powerDraw())));
    }

    /** Die Abläufe als Zeilen — getrennt vom Senden, damit prüfbar. */
    public List<FlowStatePacket.Line> flowLines() {
        List<FlowStatePacket.Line> lines = new ArrayList<>();
        if (flows != null) {
            flows.flows().values().forEach(flow -> lines.add(new FlowStatePacket.Line(
                    flow.id(), flow.entryPoint(), flow.status().name(), flow.detail())));
            // Die zuletzt gescheiterten hinten dran, damit ein Fehler von
            // heute Nacht morgen früh noch dasteht.
            flows.failed().forEach(flow -> lines.add(new FlowStatePacket.Line(
                    flow.id(), flow.entryPoint(), flow.status().name(), flow.detail())));
        }
        return lines;
    }

    // ---- Anzeigen im Terminal ---------------------------------------------

    /**
     * Schickt die Anzeigen an einen Spieler.
     *
     * <p>Ausgewertet wird hier, gezeichnet dort — dieselbe Aufteilung wie beim
     * Display an der Wand. Was über die Leitung geht, steht am Ende so da.
     */
    public void pushDisplaysTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new DisplayStatePacket(displayPanels()));
    }

    /** Die Anzeigen als fertige Zeilen — getrennt vom Senden, damit prüfbar. */
    public List<DisplayStatePacket.Panel> displayPanels() {
        List<DisplayStatePacket.Panel> panels = new ArrayList<>();
        DisplayValues values = new DisplayValues(graph, storage, runtime);
        for (Decl declaration : program.declarations()) {
            if (!(declaration instanceof Decl.Display display)) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            List<Integer> buttons = new ArrayList<>();
            List<DisplayValues.Line> evaluated = values.evaluate(display);
            for (int i = 0; i < evaluated.size(); i++) {
                lines.add(DisplayBlockEntity.format(evaluated.get(i)));
                if (evaluated.get(i).kind() == Decl.Display.Entry.Kind.BUTTON) {
                    buttons.add(i);
                }
            }
            panels.add(new DisplayStatePacket.Panel(display.name(), lines, buttons));
        }
        return panels;
    }

    /**
     * Der Flüssigkeitsbestand, als Zeilen für das Terminal.
     *
     * <p>Als Text und nicht als Symbolraster: Eine Flüssigkeit hat kein
     * Gegenstandsbild, und ein Netz hält selten mehr als eine Handvoll Sorten
     * — dafür lohnt kein zweites Raster.
     */
    public List<String> fluidLines() {
        return fluidStorage.contents().entrySet().stream()
                .sorted(Map.Entry.<net.minecraft.world.level.material.Fluid, Long>
                        comparingByValue().reversed())
                .map(entry -> net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .getKey(entry.getKey()).getPath() + ": " + entry.getValue() + " mB")
                .toList();
    }

    /**
     * Die gebauten Anlagen, als Zeilen für das Terminal.
     *
     * <p>Eine unvollständige Anlage bliebe sonst unsichtbar: Sie tut nichts
     * und sagt nichts, und der Spieler sucht den Fehler im Programm statt an
     * der Beschriftung.
     */
    public List<String> plants() {
        return MultiblockInstances.resolve(program, graph.connectorNames()).values().stream()
                .map(instance -> {
                    if (instance.ambiguous()) {
                        return instance.name() + ": mehrere Vorlagen passen";
                    }
                    if (!instance.missing().isEmpty()) {
                        return instance.name() + ": es fehlt "
                                + String.join(", ", instance.missing());
                    }
                    return instance.name() + ": " + instance.template().name();
                })
                .toList();
    }

    /**
     * Löst aus, was hinter einem Knopf steht.
     *
     * <p>Als Ablauf, nicht als gewöhnlicher Aufruf: Ein Knopf soll etwas
     * anstoßen dürfen, das wartet, und einen Rückgabewert braucht niemand.
     */
    public void pressDisplayButton(String displayName, int entryIndex) {
        Decl.Display display = program.declarations().stream()
                .filter(Decl.Display.class::isInstance).map(Decl.Display.class::cast)
                .filter(candidate -> candidate.name().equals(displayName))
                .findFirst().orElse(null);
        if (display == null || entryIndex < 0 || entryIndex >= display.entries().size()) {
            return;
        }
        Decl.Display.Entry entry = display.entries().get(entryIndex);
        if (entry.kind() != Decl.Display.Entry.Kind.BUTTON) {
            // Der Client hat auf eine Zeile gezeigt, die kein Knopf ist.
            return;
        }
        String function = functionNameOf(entry.value());
        if (function == null) {
            note("Der Knopf " + entry.label() + " nennt keine Funktion.");
            return;
        }
        try {
            startFlow(function, List.of());
        } catch (ScriptError error) {
            note(entry.label() + ": " + error);
        }
    }

    /** Welche Funktion hinter einem Knopf steht — Name oder Aufruf. */
    private static String functionNameOf(Expr value) {
        return switch (value) {
            case Expr.Name name -> name.value();
            case Expr.Call call when call.callee() instanceof Expr.Name name -> name.value();
            case null, default -> null;
        };
    }

    /**
     * Sucht nach Änderungen der Redstone-Stärke und löst dafür Ereignisse aus.
     *
     * <p>Abgefragt statt gemeldet, und nur alle zehn Ticks: Das Konzept lässt
     * der Laufzeit ausdrücklich internes Abfragen, solange der Spieler dafür
     * keine Schleife schreiben muss. Bei einigen Dutzend Connectoren ist das
     * billiger als an jedem Block zu horchen.
     */
    /** Der zuletzt gesehene Inhalt je Gerät, als Fingerabdruck. */
    private final Map<String, Integer> lastContents = new HashMap<>();

    /**
     * Meldet, wenn sich der Inhalt eines Geräts geändert hat.
     *
     * <p><b>Nicht {@code device_done}.</b> Ob eine Maschine <em>fertig</em>
     * ist, weiß niemand von außen: Der Ausgang kann von vorher gefüllt sein,
     * und jede Mod zählt anders. Was sich sagen lässt, ist, dass sich etwas
     * geändert hat — was das bedeutet, schreibt der Spieler selbst. Eine
     * Automatisierung, die einmal zu früh weiterschaltet, verliert Gegenstände
     * in einer Kiste, die niemand mehr findet.
     *
     * <p>Abgefragt statt gemeldet und nur alle zehn Ticks, wie beim Redstone —
     * und nur, wenn das Programm überhaupt darauf hört.
     */
    private void fireInventoryEvents() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        if (program.handlers().stream().noneMatch(h -> h.name().equals("device_changed"))) {
            lastContents.clear();
            return;
        }
        for (Map.Entry<String, BlockPos> entry : graph.connectors().entrySet()) {
            if (!level.isLoaded(entry.getValue())
                    || !(level.getBlockEntity(entry.getValue())
                            instanceof ConnectorBlockEntity connector)) {
                continue;
            }
            int fingerprint = contentsFingerprint(connector);
            Integer previous = lastContents.put(entry.getKey(), fingerprint);
            if (previous == null || previous == fingerprint) {
                // Beim ersten Sehen wird nicht gemeldet: Da hat sich nichts
                // geändert, es war nur nichts bekannt.
                continue;
            }
            fireEvent("device_changed", List.of(new Value.Device(entry.getKey())));
        }
    }

    /**
     * Eine Zahl, die sich ändert, wenn sich der Inhalt ändert.
     *
     * <p>Inventar und Tank zusammen — eine Maschine kann beides haben, und wer
     * auf sie wartet, will von beidem wissen.
     */
    private static int contentsFingerprint(ConnectorBlockEntity connector) {
        int hash = 17;
        var handler = connector.machineInventory();
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                var stack = handler.getStackInSlot(slot);
                hash = hash * 31 + (stack.isEmpty() ? 0
                        : stack.getItem().hashCode() * 31 + stack.getCount());
            }
        }
        var tank = connector.machineTank();
        if (tank != null) {
            for (int slot = 0; slot < tank.getTanks(); slot++) {
                var stack = tank.getFluidInTank(slot);
                hash = hash * 31 + (stack.isEmpty() ? 0
                        : stack.getFluid().hashCode() * 31 + stack.getAmount());
            }
        }
        return hash;
    }

    private void fireRedstoneEvents() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        if (program.handlers().stream().noneMatch(h -> h.name().equals("redstone_changed"))) {
            return;
        }
        for (Map.Entry<String, BlockPos> entry : graph.connectors().entrySet()) {
            if (!level.isLoaded(entry.getValue())) {
                continue;
            }
            int strength = level.getBestNeighborSignal(entry.getValue());
            Integer previous = lastRedstone.put(entry.getKey(), strength);
            if (previous != null && previous == strength) {
                continue;
            }
            // Über die Ablaufmaschine, damit ein on redstone_changed selbst
            // warten darf — auf eine Maschine, auf einen Zähler, auf Zeit.
            fireEvent("redstone_changed",
                    List.of(new Value.Device(entry.getKey()), new Value.Int(strength)));
        }
    }

    /**
     * Lässt die wartenden Abläufe arbeiten.
     *
     * <p>Die Maschine wird beim ersten Bedarf gebaut und beim Übernehmen
     * neuen Codes verworfen — mit der Einschränkung, die noch fehlt: Abläufe,
     * die gerade warten, müssten den Wechsel überstehen und den Spieler
     * fragen. Das kommt im nächsten Schritt.
     */
    private void tickFlows() {
        if (flows == null && pendingFlows == null) {
            return;
        }
        FlowEngine engine = flowEngine();
        if (engine != null) {
            engine.tick(level.getGameTime());
        }
    }

    /**
     * Die Ablaufmaschine, bei Bedarf gebaut und mit dem Gespeicherten gefüllt.
     *
     * <p>Das Auspacken steht hier und nicht im Tick, damit es nicht darauf
     * ankommt, wer zuerst fragt. Ein Ereignis, das im selben Tick eintrifft,
     * in dem der Chunk geladen wurde, würde sonst auf eine leere Maschine
     * treffen — und die wartenden Abläufe kämen einen Tick zu spät.
     */
    public FlowEngine flowEngine() {
        if (flows == null && level != null) {
            flows = new FlowEngine(program, new Interpreter(program,
                    new WorldHost(level, graph, storage, fluidStorage)));
        }
        if (flows != null && pendingFlows != null) {
            CompoundTag saved = pendingFlows;
            pendingFlows = null;
            FlowCodec.read(saved, flows);
        }
        // Ein frisch geladener Controller kennt sein Netz noch nicht. Das ist
        // etwas anderes als „kein Server, kein Strom" — ohne diesen Aufbau
        // stünde jedes Netz nach dem Laden still, bis der erste Tick kommt.
        if (flows != null && !networkKnown) {
            rebuildNetwork();
        }
        if (flows != null) {
            // Hier und nicht im Tick: Jeder Zugriff auf die Maschine geht
            // durch diese Stelle, und beides kann sich zwischen zwei Ticks
            // ändern — jemand steckt einen Prozessor dazu, reißt den Schrank
            // ab oder der Strom fällt aus. Ein Knopf am Display und ein
            // Ereignis treiben die Maschine unmittelbar an, ohne über den
            // Tick zu gehen; die Sperre muss deshalb hier sitzen.
            flows.setThreadLimit(threads());
            flows.setFrozen(!isOnline());
        }
        return flows;
    }

    /** Beginnt einen wartefähigen Ablauf. */
    public Flow startFlow(String functionName, List<Value> arguments) {
        FlowEngine engine = flowEngine();
        if (engine == null) {
            throw new ScriptError("Keine Welt.");
        }
        Flow flow = engine.start(functionName, arguments);
        engine.tick(level.getGameTime());
        return flow;
    }

    /** Weckt wartende Abläufe und löst Ereignisblöcke aus. */
    public void fireEvent(String event, List<Value> arguments) {
        FlowEngine engine = flowEngine();
        if (engine == null) {
            return;
        }
        engine.post(event, arguments);
        engine.tick(level.getGameTime());
    }

    /** Ruft eine Funktion des Programms auf — für Tests und das Terminal. */
    public Value callFunction(String name, List<Value> arguments) {
        if (level == null) {
            throw new ScriptError("Keine Welt.");
        }
        WorldHost host = new WorldHost(level, graph, storage, fluidStorage);
        Interpreter interpreter = new Interpreter(program, host);
        FlowEngine engine = flowEngine();
        if (engine != null) {
            // Auch ein Aufruf aus dem Terminal kann etwas auslösen, worauf
            // ein Ablauf wartet.
            interpreter.setEventSink(engine::post);
        }
        try {
            return interpreter.call(name, arguments);
        } finally {
            host.logs().forEach(this::note);
            if (engine != null) {
                engine.tick(level.getGameTime());
            }
        }
    }

    public List<String> log() {
        return List.copyOf(log);
    }

    private void note(String message) {
        log.add(message);
        if (log.size() > 100) {
            log.remove(0);
        }
    }

    // ---- Speichern --------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        source = tag.getString(KEY_SOURCE);
        storage.load(tag.getCompound(KEY_STORAGE), registries);
        fluidStorage.load(tag.getCompound(KEY_FLUIDS), registries);
        power.load(tag.getCompound(KEY_POWER), registries);
        // Die Abläufe warten auf den ersten Tick: Sie brauchen einen
        // Interpreter, der braucht eine Welt, und die gibt es hier noch nicht
        // verlässlich.
        pendingFlows = tag.contains(KEY_FLOWS) ? tag.getCompound(KEY_FLOWS) : null;
        // Nach dem Laden wird neu übersetzt: Der Baum selbst wird nicht
        // gespeichert, weil sich die Sprache ändern kann, der Quelltext aber
        // gültig bleibt.
        if (!source.isBlank()) {
            Parser.ParseResult result = Parser.parse(source);
            diagnostics = new ArrayList<>(result.diagnostics());
            if (!result.hasErrors()) {
                program = result.program();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(KEY_SOURCE, source);
        CompoundTag storageTag = new CompoundTag();
        storage.save(storageTag, registries);
        tag.put(KEY_STORAGE, storageTag);
        CompoundTag fluidTag = new CompoundTag();
        fluidStorage.save(fluidTag, registries);
        tag.put(KEY_FLUIDS, fluidTag);
        CompoundTag powerTag = new CompoundTag();
        power.save(powerTag, registries);
        tag.put(KEY_POWER, powerTag);
        if (flows != null) {
            tag.put(KEY_FLOWS, FlowCodec.write(flows));
        } else if (pendingFlows != null) {
            // Noch nicht ausgepackt — dann unverändert weiterreichen, statt
            // die Abläufe beim ersten Speichern zu verlieren.
            tag.put(KEY_FLOWS, pendingFlows);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString(KEY_SOURCE, source);
        return tag;
    }
}
