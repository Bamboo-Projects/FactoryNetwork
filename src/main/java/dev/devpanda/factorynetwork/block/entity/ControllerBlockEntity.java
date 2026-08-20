package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.network.packet.StorageSnapshotPacket;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.runtime.Interpreter;
import dev.devpanda.factorynetwork.runtime.ScriptError;
import dev.devpanda.factorynetwork.runtime.Value;
import dev.devpanda.factorynetwork.runtime.WorkerRuntime;
import dev.devpanda.factorynetwork.runtime.WorldHost;
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
    private static final int REBUILD_INTERVAL = 100;

    private String source = "";
    private Program program = new Program(List.of());
    private List<Diagnostic> diagnostics = new ArrayList<>();
    private FactoryGraph graph = FactoryGraph.empty();
    private final NetworkStorage storage = new NetworkStorage();
    private final WorkerRuntime runtime = new WorkerRuntime();
    private long lastRebuild = Long.MIN_VALUE;

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
    private long lastStoragePush = Long.MIN_VALUE;
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
        setChanged();
        if (result.hasErrors()) {
            return false;
        }
        this.program = result.program();
        runtime.reset();
        return true;
    }

    // ---- Netzwerk ---------------------------------------------------------

    public FactoryGraph graph() {
        return graph;
    }

    public NetworkStorage storage() {
        return storage;
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
        if (level != null) {
            graph = FactoryGraph.build(level, worldPosition);
            lastRebuild = level.getGameTime();
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
        if (program.workers().isEmpty()) {
            return;
        }
        runtime.setConnectorLookup(position ->
                level.isLoaded(position)
                        && level.getBlockEntity(position) instanceof ConnectorBlockEntity connector
                        ? connector : null);
        runtime.tick(level, program, graph, storage);
        fireRedstoneEvents();
        pushStorageIfDue();
        setChanged();
    }

    // ---- Speicheransicht --------------------------------------------------

    /** So oft höchstens, in Ticks. Ein Worker bewegt sonst jeden Tick etwas. */
    private static final int STORAGE_PUSH_INTERVAL = 10;

    public void watchStorage(ServerPlayer player) {
        storageWatchers.add(player);
        pushStorageTo(player, true);
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
        PacketDistributor.sendToPlayer(player,
                new StorageSnapshotPacket(entries, replace, contents.size()));
    }

    /**
     * Sucht nach Änderungen der Redstone-Stärke und löst dafür Ereignisse aus.
     *
     * <p>Abgefragt statt gemeldet, und nur alle zehn Ticks: Das Konzept lässt
     * der Laufzeit ausdrücklich internes Abfragen, solange der Spieler dafür
     * keine Schleife schreiben muss. Bei einigen Dutzend Connectoren ist das
     * billiger als an jedem Block zu horchen.
     */
    private void fireRedstoneEvents() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        if (program.handlers().stream().noneMatch(h -> h.name().equals("redstone_changed"))) {
            return;
        }
        WorldHost host = new WorldHost(level, graph, storage);
        Interpreter interpreter = new Interpreter(program, host);
        for (Map.Entry<String, BlockPos> entry : graph.connectors().entrySet()) {
            if (!level.isLoaded(entry.getValue())) {
                continue;
            }
            int strength = level.getBestNeighborSignal(entry.getValue());
            Integer previous = lastRedstone.put(entry.getKey(), strength);
            if (previous != null && previous == strength) {
                continue;
            }
            try {
                interpreter.fire("redstone_changed",
                        List.of(new Value.Device(entry.getKey()), new Value.Int(strength)));
            } catch (ScriptError error) {
                note("redstone_changed: " + error);
            }
        }
        host.logs().forEach(this::note);
    }

    /** Ruft eine Funktion des Programms auf — für Tests und das Terminal. */
    public Value callFunction(String name, List<Value> arguments) {
        if (level == null) {
            throw new ScriptError("Keine Welt.");
        }
        WorldHost host = new WorldHost(level, graph, storage);
        try {
            return new Interpreter(program, host).call(name, arguments);
        } finally {
            host.logs().forEach(this::note);
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
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString(KEY_SOURCE, source);
        return tag;
    }
}
