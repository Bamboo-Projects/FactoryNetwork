package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.runtime.WorkerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

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

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.CONTROLLER.get(), pos, state);
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
        setChanged();
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
