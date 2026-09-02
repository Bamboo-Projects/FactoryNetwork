package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.lang.BuiltinEvents;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.network.DevicePos;
import dev.devpanda.factorynetwork.network.DeviceState;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Root of a network: holds program, storage, graph and runtime.
 *
 * <p>The graph is not rebuilt on every tick, but at intervals and when someone
 * clicks the controller. A cable is rarely placed; a constant rebuild would be
 * the most expensive spot in the mod for the least benefit.
 */
public class ControllerBlockEntity extends BlockEntity {

    private static final String KEY_SOURCE = "Source";
    private static final String KEY_STORAGE = "Storage";
    private static final String KEY_FLOWS = "Flows";
    private static final String KEY_FLUIDS = "Fluids";
    private static final String KEY_POWER = "Power";
    private static final String KEY_GLOBALS = "Globals";
    private static final String KEY_LOG = "Log";
    private static final String KEY_HELD_BACK = "HeldBack";

    /**
     * This many lines the log holds.
     *
     * <p>It goes to disk with the world, so it must not grow. Two hundred
     * lines are enough to see what went wrong in the last hour, and small
     * enough that no one notices them.
     */
    private static final int LOG_LIMIT = 200;
    private static final int REBUILD_INTERVAL = 100;

    /**
     * The program as a project of several files.
     *
     * <p>A program of three hundred lines in one piece is no overview. All
     * files share one namespace — files are order for the human, not a
     * boundary for the language.
     */
    /**
     * Who is currently editing which file.
     *
     * <p>Only at runtime — a lock that outlasts a server restart would be a
     * file that belongs to no one any more and that no one opens.
     */
    private final dev.devpanda.factorynetwork.network.FileLocks locks =
            new dev.devpanda.factorynetwork.network.FileLocks();

    /**
     * What is in the editor.
     *
     * <p>Beside {@link #project}, which is running. A typo must not stop the
     * factory: the draft may be broken, the running state may not.
     */
    private dev.devpanda.factorynetwork.lang.Project draft =
            dev.devpanda.factorynetwork.lang.Project.of("");

    private dev.devpanda.factorynetwork.lang.Project project =
            dev.devpanda.factorynetwork.lang.Project.of("");
    private Program program = new Program(List.of());

    /**
     * The program's global values.
     *
     * <p>They belong to the network like the power reserve and the storage
     * contents, and they survive the server restart for the same reason: a
     * value that says which mode the factory runs in would be pointless after
     * a restart if it stood at its initial value again.
     */
    private final Map<String, dev.devpanda.factorynetwork.runtime.Value> globals =
            new java.util.LinkedHashMap<>();

    private List<Diagnostic> diagnostics = new ArrayList<>();
    private FactoryGraph graph = FactoryGraph.empty();
    /**
     * All of the network's contents, by resource kind.
     *
     * <p>The three named fields below point into it and spare the detour at
     * the many places that mean a particular kind. Ownership lies with
     * {@code stores}: a fourth store arrives there and nowhere else.
     */
    private final dev.devpanda.factorynetwork.network.NetworkStores stores =
            new dev.devpanda.factorynetwork.network.NetworkStores();

    private final NetworkStorage storage = stores.items();
    private final NetworkFluids fluidStorage = stores.fluids();

    /**
     * The chemical store.
     *
     * <p>An interface and not a class: chemicals belong to Mekanism, and a
     * field with a Mekanism type would keep this controller from loading in a
     * pack without the mod. Without Mekanism, the store here is the one that
     * can do nothing — and that is the truth about such a pack, not a stopgap.
     */
    private final dev.devpanda.factorynetwork.network.ResourceStore chemicalStorage =
            stores.chemicals();
    private final WorkerRuntime runtime = new WorkerRuntime();

    /**
     * The server racks in the network.
     *
     * <p>Without one of them the network does not compute — neither workers
     * nor flows. Just as a drive is the prerequisite for it to store.
     */
    private final List<dev.devpanda.factorynetwork.block.entity.RackBlockEntity> racks =
            new ArrayList<>();

    /** The drives in the network — for the power draw per cell. */
    private final List<DriveBlockEntity> drives = new ArrayList<>();

    /**
     * The network's power.
     *
     * <p>The buffer sits in the controller and accepts Forge Energy. Without
     * power everything stands still — and must spin up again afterwards.
     */
    private final dev.devpanda.factorynetwork.network.NetworkPower power =
            new dev.devpanda.factorynetwork.network.NetworkPower();
    /** Flows that can wait — they survive a server restart. */
    private FlowEngine flows;

    /**
     * Written-down flows that still wait until there is a world.
     *
     * <p>On load the world is not yet ready, but the flows need an interpreter
     * and it needs a world. So the tag is left lying until the first tick
     * comes.
     */
    private CompoundTag pendingFlows;

    /**
     * Whether the network has ever been built.
     *
     * <p>An empty graph and a never-yet-built one look the same, but are not:
     * the first time, nothing has come in.
     */
    private boolean networkKnown;
    /** Negative instead of Long.MIN_VALUE: the difference would otherwise overflow. */
    private long lastRebuild = -REBUILD_INTERVAL;

    /**
     * The project as a folder next to the world — the bridge to VS Code.
     *
     * <p>Created only at the first tick: before that there is no server and
     * therefore no world folder.
     */
    private dev.devpanda.factorynetwork.lang.ProgramFolder programFolder;
    private long lastFileCheck = -dev.devpanda.factorynetwork.lang.ProgramFolder.CHECK_INTERVAL;

    /**
     * Whether something has changed since the last write that belongs outside:
     * errors or device names.
     *
     * <p>A flag and not a write per tick. {@code ProgramStatus} does write only
     * when the content differs — but building and comparing the file every
     * time would be work for nothing, and that per controller and second.
     */
    private boolean statusStale = true;

    /** Last seen redstone strength per connector, for the event. */
    private final Map<String, Integer> lastRedstone = new HashMap<>();
    /**
     * What the network had to say.
     *
     * <p>Goes to disk with the world: whoever checks in the morning why the
     * installation stopped in the night still finds the line even if the
     * server restarted in between.
     */
    private final List<dev.devpanda.factorynetwork.runtime.LogEntry> log = new ArrayList<>();

    /**
     * What found no place anywhere — kept rather than thrown out.
     *
     * <p><b>The ground is not a store.</b> An item there vanishes after five
     * minutes, and the case strikes exactly when no one is watching. The
     * controller holds it instead and offers it again every tick — the reason
     * can fall away at any time: one more drive, a swapped cell, an emptied
     * chest.
     *
     * <p>Since a worker asks before reaching, nothing should ever land here.
     * This list is the net beneath, for machines that answer {@code simulate}
     * differently than the actual grab.
     */
    private final List<ItemStack> heldBack = new ArrayList<>();

    /**
     * Who currently has the storage view open.
     *
     * <p>Only to these players is the inventory sent, and only while they are
     * looking. Whoever reads the code tab needs no inventory changes — and the
     * inventory is the most expensive thing that goes over the wire.
     */
    private final Set<ServerPlayer> storageWatchers = new HashSet<>();

    /**
     * Who has the terminal open at all, on whichever tab.
     *
     * <p><b>Separate from the inventory, because the status line is always
     * there.</b> Power, channels and servers belong to every tab; sending them
     * only with storage would mean the line freezes on the code tab and jumps
     * when switching back. That looks like a bug and is one.
     *
     * <p>It is not expensive: a few numbers and the list of flows, every ten
     * ticks.
     */
    private final Set<ServerPlayer> terminalWatchers = new HashSet<>();
    private long lastStoragePush = -10;
    private boolean storageDirty;

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.CONTROLLER.get(), pos, state);
        storage.setChangeListener(this::markStorageDirty);
        runtime.setDeviceFilled(this::noteFilled);
    }

    // ---- Program ------------------------------------------------------------

    /** The project with all of its files. */
    public dev.devpanda.factorynetwork.lang.Project project() {
        return project;
    }

    /**
     * The whole source code in one piece.
     *
     * <p>For everything that only wants to read. Whoever needs a single file
     * asks the project.
     */
    public String source() {
        return project.joined();
    }

    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public Program program() {
        return program;
    }

    /**
     * Adopts new code. On errors the running program stays put — a factory
     * that goes out because of a typo is worse than one that still runs on the
     * old version.
     */
    /**
     * Adopts a single text as a whole project.
     *
     * <p>The convenient way for everything that has only one text — tests, the
     * file bridge of an old world, a call from outside.
     */
    public boolean deploy(String newSource) {
        return deploy(dev.devpanda.factorynetwork.lang.Project.of(newSource));
    }

    public boolean deploy(dev.devpanda.factorynetwork.lang.Project newProject) {
        // With an eye on the network: even someone who writes through the
        // folder next to the world should learn that no display is called
        // "test".
        Parser.ParseResult result = newProject.parse(networkView());
        this.project = newProject;
        this.diagnostics = new ArrayList<>(result.diagnostics());
        // What the compiler says belongs outside too.
        statusStale = true;
        // Adopted means: the draft and the running state are the same.
        this.draft = newProject;
        writeProgramFile();
        pushProjectToWatchers();
        // Look first, then judge: whoever just placed a rack would otherwise
        // be told "no server", even though one stands right beside it.
        if (level != null && !hasServer()) {
            rebuildNetwork();
        }
        if (!hasServer()) {
            // A program without a server is not an error in the code. It is
            // still not adopted — otherwise it would stand there and not run,
            // and no one would know why.
            this.diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                    new dev.devpanda.factorynetwork.lang.Span(0, 0, 1, 1),
                    "Kein Serverschrank im Netz.",
                    "Setze einen Serverschrank ans Kabel und bestücke einen Einschub "
                            + "mit Rechenwerk, Speicher und Datenträger."));
            setChanged();
            return false;
        }
        setChanged();
        if (result.hasErrors()) {
            return false;
        }
        // The limit stands here and not first at run time: whoever presses
        // Adopt should learn at the same moment that it does not fit — not a
        // minute later at a factory that stands still.
        int size = dev.devpanda.factorynetwork.lang.ProgramSize.of(result.program());
        if (size > diskSpace()) {
            this.diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                    new dev.devpanda.factorynetwork.lang.Span(0, 0, 1, 1),
                    "Das Programm ist zu groß: " + size + " Anweisungen, "
                            + diskSpace() + " passen auf die Datenträger.",
                    "Bau größere Datenträger ein oder kürze das Programm. "
                            + "Kommentare und Leerzeilen zählen nicht mit."));
            return false;
        }
        // Waiting flows go the same way as over a server restart: write down,
        // build a new machine, read back. If the shape of the program still
        // fits, they run on; if it does not, they report themselves as STALE.
        // One way, one behaviour — instead of two that drift apart.
        CompoundTag carried = flows == null ? null : FlowCodec.write(flows);
        adoptGlobals(result.program());
        this.program = result.program();
        runtime.reset();
        // Resolve groups at once and not first on the next tick: a flow that
        // starts right after adoption would otherwise query an empty group.
        runtime.resolveGroups(this.program, graph);
        this.flows = null;
        if (!FlowCodec.isEmpty(carried)) {
            this.pendingFlows = carried;
        }
        return true;
    }

    // ---- Global values ------------------------------------------------------

    /** What global values the program holds. */
    public Map<String, dev.devpanda.factorynetwork.runtime.Value> globals() {
        return globals;
    }

    /**
     * Switches the global values over to a new program.
     *
     * <p>The rule in four cases:
     *
     * <ul>
     *   <li><b>Same name, same type:</b> the value stays. Whoever set the mode
     *       to "night" and then changes a worker does not want to start again
     *       at "day".
     *   <li><b>New name:</b> the initial value from the declaration.
     *   <li><b>Name gone:</b> forgotten.
     *   <li><b>Same name, different type:</b> the new initial value. A text
     *       suddenly read as a number is not a state worth keeping.
     * </ul>
     *
     * <p>The same stance as with the worker states: what still fits stays;
     * what no longer fits starts anew.
     */
    private void adoptGlobals(Program incoming) {
        Map<String, dev.devpanda.factorynetwork.runtime.Value> next =
                new java.util.LinkedHashMap<>();
        for (dev.devpanda.factorynetwork.lang.ast.Decl declaration : incoming.declarations()) {
            if (!(declaration instanceof dev.devpanda.factorynetwork.lang.ast.Decl.Global global)) {
                continue;
            }
            dev.devpanda.factorynetwork.runtime.Value fresh =
                    dev.devpanda.factorynetwork.runtime.Value.ofLiteral(global.value());
            dev.devpanda.factorynetwork.runtime.Value kept = globals.get(global.name());
            boolean sameKind = kept != null && kept.getClass() == fresh.getClass();
            next.put(global.name(), sameKind ? kept : fresh);
        }
        globals.clear();
        globals.putAll(next);
        setChanged();
    }

    // ---- Power --------------------------------------------------------------

    public dev.devpanda.factorynetwork.network.NetworkPower power() {
        return power;
    }

    /**
     * What the network currently draws, in FE per tick.
     *
     * <p>Counted rather than carried along, like the occupied compute slots: a
     * counter would have to be updated on every block placed and torn down,
     * and the one forgotten spot would be a network that pays for devices
     * that no longer exist.
     */
    public int powerDraw() {
        int total = dev.devpanda.factorynetwork.network.Power.CONTROLLER;
        total += graph.connectorCount() * dev.devpanda.factorynetwork.network.Power.CONNECTOR;
        total += graph.displays().size() * dev.devpanda.factorynetwork.network.Power.DISPLAY;
        total += graph.routers().size() * dev.devpanda.factorynetwork.network.Power.ROUTER;
        total += graph.extensions().size() * dev.devpanda.factorynetwork.network.Power.EXTENSION;
        // Both ends pay; both stand in the graph when both are loaded.
        total += graph.bridges().size() * dev.devpanda.factorynetwork.network.Power.BRIDGE;
        total += graph.fabricators().size()
                * dev.devpanda.factorynetwork.network.Power.FABRICATOR;
        // A mast costs according to its build-out: what it draws hangs on its
        // cards. That is why there is a loop here and not a multiplication.
        for (net.minecraft.core.BlockPos pos : graph.masts()) {
            if (level != null && level.getBlockEntity(pos)
                    instanceof MastBlockEntity mast) {
                total += dev.devpanda.factorynetwork.network.Power.mast(mast.loadout());
            }
        }
        for (DriveBlockEntity drive : drives) {
            total += dev.devpanda.factorynetwork.network.Power.DRIVE
                    + drive.usedSlots() * dev.devpanda.factorynetwork.network.Power.PER_CELL;
        }
        for (var rack : racks) {
            total += dev.devpanda.factorynetwork.network.Power.RACK
                    + rack.runningBays() * dev.devpanda.factorynetwork.network.Power.PER_SERVER;
        }
        return total;
    }

    /** Is the network running — enough power and a server rack? */
    public boolean isOnline() {
        return hasServer() && power.isRunning();
    }

    // ---- Compute power ------------------------------------------------------

    /**
     * What the network's servers carry together.
     *
     * <p>The sum over all <b>complete</b> bays in all racks. A bay without a
     * disk is not in it — it is not a server, but a heap of parts.
     */
    public dev.devpanda.factorynetwork.network.ServerBay capacity() {
        var total = dev.devpanda.factorynetwork.network.ServerBay.EMPTY;
        for (var rack : racks) {
            total = total.plus(rack.capacity());
        }
        return total;
    }

    /**
     * How many flows may run at once.
     *
     * <p>The CPUs. Zero means: there is no server, and then nothing runs at
     * all.
     */
    public int threads() {
        return capacity().cpu();
    }

    /** How many flows may exist at all — the RAM. */
    public int memory() {
        return capacity().ram();
    }

    /** How large the program may be — the disks. */
    public int diskSpace() {
        return capacity().disk();
    }

    /** How large the program currently is, in instructions. */
    public int programSize() {
        return dev.devpanda.factorynetwork.lang.ProgramSize.of(program);
    }

    /**
     * Does the program fit onto the disks?
     *
     * <p>Without a server the question is none: nothing runs anyway then, and
     * a second reason for that only confuses.
     */
    public boolean programFits() {
        return !hasServer() || programSize() <= diskSpace();
    }

    /** Is there any compute power in the network at all? */
    public boolean hasServer() {
        return threads() > 0;
    }

    // ---- Network ------------------------------------------------------------

    public FactoryGraph graph() {
        return graph;
    }

    public NetworkStorage storage() {
        return storage;
    }

    /**
     * Where a resource kind is stored.
     *
     * <p>The one place where the network is asked for contents without knowing
     * in advance which kind it is about. The named accessors beside it stay:
     * items have storage buses, fluids have variety slots, and one asks for
     * those only when one means the kind.
     */
    public dev.devpanda.factorynetwork.network.ResourceStore store(
            dev.devpanda.factorynetwork.runtime.ResourceKind kind) {
        return stores.of(kind);
    }

    public NetworkFluids fluids() {
        return fluidStorage;
    }

    /** The chemical store; without Mekanism, one that can do nothing. */
    public dev.devpanda.factorynetwork.network.ResourceStore chemicals() {
        return chemicalStorage;
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
        // The device names change here, and they are in no file — an editor
        // beside us learns them only from us.
        statusStale = true;
        graph = FactoryGraph.build(level, worldPosition);
        // Counted here and not on every query: Bandwidth.at hits the
        // controller per slot per worker per tick, and the attachments change
        // only when someone builds.
        extensionCount = graph.extensions().size();
        // The path may be a different one than before — and so its latency too.
        runtime.remeasureLatency();
        lastRebuild = level.getGameTime();
        networkKnown = true;
        stampDeviceStates();
        // Storage is the sum of the drives in the network. Without this step
        // the controller stores nothing — which is also true: without a drive
        // there is no space.
        List<DriveBlockEntity> found = new ArrayList<>();
        for (BlockPos pos : graph.drives()) {
            if (level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof DriveBlockEntity drive) {
                found.add(drive);
            }
        }
        drives.clear();
        drives.addAll(found);
        // The same drives carry the cells of all kinds: a second drive just
        // for fluids would be one more block for the same operation. Which
        // cells a store can read, it knows itself.
        stores.setDrives(found);
        // And the same ones carry the energy cells. Without this line the
        // reserve is the buffer in the controller, no matter how many
        // batteries sit in the shelf.
        power.setDrives(found);
        rebuildBuses();
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
     * Reports which devices have come and gone.
     *
     * <p>On the very first build it stays silent: nothing has come in, it was
     * only that nothing was known before. A storm of {@code device_online} on
     * every server start would be the same as noise to the player.
     */
    private void announceDeviceChanges(Set<String> before) {
        if (before == null || program.handlers().isEmpty()) {
            return;
        }
        Set<String> now = graph.connectorNames();
        for (String name : now) {
            if (!before.contains(name)) {
                fireEvent(BuiltinEvents.DEVICE_ONLINE, List.of(new Value.Device(name)));
            }
        }
        for (String name : before) {
            if (!now.contains(name)) {
                // No Value.Device: the device no longer exists, and a
                // reference to it could no longer be resolved.
                fireEvent(BuiltinEvents.DEVICE_OFFLINE, List.of(new Value.Text(name)));
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
        // A chest on the storage bus changes without telling anyone. Looking
        // once per tick is the price of its contents counting toward the
        // inventory — and cheaper than counting on every query.
        storage.refreshBuses();
        power.setDraw(powerDraw());
        power.tick();

        // Before the early return below: a network without power or without a
        // server is exactly the network whose program you are working on.
        checkProgramFile();

        // Without a server rack or without power the network stands still —
        // and runs on where it was, as soon as both are back. Nothing is
        // aborted: a flow holds no items between two steps, so freezing costs
        // nothing.
        if (!isOnline()) {
            pushStorageIfDue();
            pushFlowsIfDue();
            setChanged();
            return;
        }
        // Flows run on even without workers — a program may consist solely of
        // functions that wait on events.
        if (!program.workers().isEmpty()) {
            runtime.setConnectorLookup(where ->
                    where.side() != null && level.isLoaded(where.pos())
                            ? dev.devpanda.factorynetwork.block.entity.Connectors.at(level, where.pos(), where.side())
                            : null);
            runtime.setPower(power);
            runtime.setCrafting(craftingForWorkers);
            runtime.tick(level, program, graph, stores,
                    newHost());
            // What the workers had to report belongs in the log. Until now
            // the runtime collected these notes and no one read them.
            runtime.drainNotes().forEach(this::add);
            // What neither storage nor device accepted, the controller keeps.
            runtime.takeDropped().forEach(this::holdBack);
        }
        // Outside the worker block, and deliberately so: held-back items
        // belong back as soon as there is room — even in a network whose
        // program currently has no worker.
        retryHeldBack();
        tickFlows();
        tickCrafting();
        fireRedstoneEvents();
        fireInventoryEvents();
        pushStorageIfDue();
        pushFlowsIfDue();
        pushTraffic();
        setChanged();
    }

    // ---- Crafting -----------------------------------------------------------

    /** Every twenty ticks one step per fabricator. */
    private static final int CRAFT_INTERVAL = 20;

    /**
     * The network's crafting jobs.
     *
     * <p>They live here and not at the fabricator: a job that hung on the
     * device would be gone the moment someone breaks the device — and that is
     * exactly the moment you want to know what was still open.
     */
    private final List<dev.devpanda.factorynetwork.crafting.CraftingJob> jobs =
            new ArrayList<>();

    private long nextJobId = 1;

    public List<dev.devpanda.factorynetwork.crafting.CraftingJob> craftingJobs() {
        return List.copyOf(jobs);
    }

    /**
     * Accepts a job, or rejects it.
     *
     * <p><b>Only what has no recipe at all is rejected.</b> Missing ingredients
     * are no reason: the job then waits and says what is missing — whoever
     * orders 64 chests and afterwards stores planks should not have to order
     * again.
     *
     * @return the job, or {@code null} if there is no recipe
     */
    public dev.devpanda.factorynetwork.crafting.CraftingJob requestCraft(
            net.minecraft.world.item.Item target, int amount) {
        if (level == null || amount <= 0) {
            return null;
        }
        if (!recipeKnown(target)) {
            return null;
        }
        var job = new dev.devpanda.factorynetwork.crafting.CraftingJob(
                nextJobId++, target, amount);
        jobs.add(job);
        setChanged();
        return job;
    }

    /**
     * Crafting, as a {@code from crafting} worker sees it.
     *
     * <p>A field and not a new instance per tick: the worker queries it
     * several times per round, and an object created twenty times a second is
     * garbage without purpose.
     */
    private final dev.devpanda.factorynetwork.runtime.WorkerRuntime.Crafting craftingForWorkers =
            new dev.devpanda.factorynetwork.runtime.WorkerRuntime.Crafting() {

                @Override
                public long pending(net.minecraft.world.item.Item item) {
                    long open = 0;
                    for (var job : jobs) {
                        if (job.target() == item) {
                            open += job.remaining();
                        }
                    }
                    return open;
                }

                @Override
                public boolean order(net.minecraft.world.item.Item item, int amount) {
                    return requestCraft(item, amount) != null;
                }
            };

    /** Cancels a job. */
    public boolean cancelCraft(long id) {
        boolean removed = jobs.removeIf(job -> job.id() == id);
        if (removed) {
            setChanged();
        }
        return removed;
    }

    /**
     * One crafting cycle.
     *
     * <p><b>One step per fabricator</b>, and a step is one run of a recipe, as
     * many times as it fits into a stack. Whoever wants to craft faster sets
     * down a second fabricator — the same answer as with channels and drives:
     * more device, more throughput.
     */
    private void tickCrafting() {
        if (jobs.isEmpty()) {
            return;
        }
        // Finished ones report before they drop out of the list. An event
        // after they vanish would have no one left to speak about.
        jobs.removeIf(job -> {
            if (job.status() == dev.devpanda.factorynetwork.crafting.CraftingJob.Status.DONE) {
                fireEvent(dev.devpanda.factorynetwork.lang.BuiltinEvents.CRAFTING_FINISHED,
                        List.of(new dev.devpanda.factorynetwork.runtime.Value.Int(job.id())));
                return true;
            }
            if (job.status() == dev.devpanda.factorynetwork.crafting.CraftingJob.Status.FAILED) {
                fireEvent(dev.devpanda.factorynetwork.lang.BuiltinEvents.CRAFTING_FAILED,
                        List.of(new dev.devpanda.factorynetwork.runtime.Value.Int(job.id()),
                                new dev.devpanda.factorynetwork.runtime.Value.Text(
                                        job.detail())));
                return true;
            }
            return false;
        });
        if (jobs.isEmpty() || level.getGameTime() % CRAFT_INTERVAL != 0) {
            return;
        }
        int steps = graph.fabricators().size();
        if (steps == 0) {
            jobs.forEach(job -> job.note(
                    dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    "kein Fabricator im Netz"));
            return;
        }
        for (var job : jobs) {
            if (steps <= 0) {
                break;
            }
            if (craftOnce(job)) {
                steps--;
            }
        }
        setChanged();
    }

    /**
     * The recipe lookup for this tick.
     *
     * <p>Built once per tick and then used for all jobs and all nodes of their
     * recipe trees. Keeping it longer would mean relying on a {@code /reload}
     * swapping out the recipe manager — and that is not a promise, but
     * internals.
     */
    private dev.devpanda.factorynetwork.crafting.RecipeLookup handCache;
    private dev.devpanda.factorynetwork.crafting.MachineRecipes machineCache;
    private long recipeCacheTick = Long.MIN_VALUE;

    private dev.devpanda.factorynetwork.crafting.DeclaredRecipes declaredCache;
    private Program declaredFrom;

    private void refreshRecipes() {
        long now = level.getGameTime();
        if (handCache == null || recipeCacheTick != now) {
            handCache = dev.devpanda.factorynetwork.crafting.RecipeLookup.of(level);
            machineCache = dev.devpanda.factorynetwork.crafting.MachineRecipes.of(level);
            recipeCacheTick = now;
        }
        // The declared recipes hang on the program and not on the tick: they
        // change only on adoption. The running program is already compiled
        // anyway — reading it again would be the most expensive way to an
        // answer that is off the mark.
        if (declaredCache == null || declaredFrom != program()) {
            declaredFrom = program();
            declaredCache =
                    dev.devpanda.factorynetwork.crafting.DeclaredRecipes.of(declaredFrom);
        }
    }

    /**
     * All recipes the network can read.
     *
     * <p>Two sources: what works without a machine (crafting table, stonecutter)
     * and what needs a machine (the furnace family, the press). For one item
     * both can have something — nine ingots from a block, one ingot from raw
     * ore — and <b>the inventory decides</b>, as it does already within a
     * single source.
     */
    private dev.devpanda.factorynetwork.crafting.CraftingPlanner.Recipes<
            net.minecraft.world.item.Item> recipes() {
        refreshRecipes();
        return dev.devpanda.factorynetwork.crafting.CraftingPlanner.anyOf(
                handCache, machineCache, declaredCache);
    }

    /** Whether there is any recipe for this item at all — in one of the sources. */
    private boolean recipeKnown(net.minecraft.world.item.Item target) {
        return recipes().find(target, item -> 0L) != null;
    }

    /**
     * One run for a job.
     *
     * <p><b>Plan first, then one step.</b> The plan breaks the order down to
     * what is on hand — eight planks that do not exist become two logs that
     * do. Of that, the lowest step is carried out, the one that can run at
     * once; the next one is found by the next cycle.
     *
     * <p><b>And the plan is recomputed each time</b>, not remembered. A
     * remembered plan would be wrong from the moment a worker stores something
     * — and that is exactly what workers do all day. In return it costs
     * nothing in the save format and survives every restart on its own.
     *
     * <p>Check first, then extract, then store. It could not be otherwise: a
     * recipe that lacks something after half the extraction would have let the
     * other half vanish.
     *
     * @return whether anything was actually built
     */
    private boolean craftOnce(dev.devpanda.factorynetwork.crafting.CraftingJob job) {
        // If something already lies in a machine, nothing else happens. A
        // freshly computed plan would see the ingredient gone and the result
        // not yet there - and would insert a second time.
        if (job.running() != null) {
            return collectRunning(job);
        }
        var recipes = recipes();
        if (!recipeKnown(job.target())) {
            // A recipe that no longer exists: the job can no longer be
            // finished, and waiting on that would mean waiting forever.
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.FAILED,
                    "kein Rezept mehr");
            return false;
        }
        // At most one stack of the target per step. A job for ten thousand
        // should not hold the server for a whole tick.
        long want = Math.min(job.remaining(), job.target().getDefaultMaxStackSize());
        var plan = dev.devpanda.factorynetwork.crafting.CraftingPlanner.plan(
                recipes, storage::count, job.target(), want,
                dev.devpanda.factorynetwork.FnConfig.craftingDepth(),
                dev.devpanda.factorynetwork.FnConfig.craftingBudget());
        if (!plan.complete()) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    dev.devpanda.factorynetwork.crafting.RecipeLookup.missing(plan.missing()));
            return false;
        }
        var step = plan.steps().get(0);
        // The plan reckoned with the same inventory that is about to be drawn
        // from. The check costs nothing and keeps the promise that no half
        // extraction is ever left standing.
        for (var entry : step.consumed().entrySet()) {
            if (storage.count(entry.getKey()) < entry.getValue()) {
                job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                        "es fehlt: " + entry.getValue() + " "
                                + entry.getKey().getDescription().getString());
                return false;
            }
        }
        if (!step.station().isEmpty()) {
            return startAtMachine(job, step);
        }
        // <b>The check above is only an indication too.</b> A storage bus
        // counts foreign inventories toward the inventory, and those may keep
        // their contents — a furnace shows its fuel slot from below and does
        // not give it up. What actually came out decides whether the step took
        // place; otherwise the result would arise unpaid, and because the
        // inventory does not follow along, anew every round.
        Map<Item, Long> paid = new LinkedHashMap<>();
        for (var entry : step.consumed().entrySet()) {
            long got = storage.extract(entry.getKey(), entry.getValue());
            if (got > 0) {
                paid.put(entry.getKey(), got);
            }
            if (got < entry.getValue()) {
                refund(paid);
                job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                        "der Speicher gibt nicht her, was er zeigt");
                return false;
            }
        }
        long left = storage.insert(step.result(), step.yield());
        if (left > 0) {
            // <b>Then the step did not take place.</b> The result comes back
            // out, the ingredients go back, and the job waits. Holding back
            // would be wrong here: a crafting that runs on with a full store
            // fills the holdback without end.
            //
            // <b>Why not ask beforehand?</b> A recipe that presses nine stones
            // into one block makes room for its own result while extracting.
            // Whoever asks beforehand would hold it forever at a full store.
            storage.extract(step.result(), step.yield() - left);
            refund(step.consumed());
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    "der Speicher ist voll");
            return false;
        }
        if (step.result() == job.target()) {
            job.produced((int) step.yield());
        } else {
            // An intermediate stage does not count as progress on the order —
            // but it should be shown, otherwise a job over a machine looks for
            // minutes as if nothing were happening.
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.RUNNING,
                    "baut " + step.yield() + " " + step.result().getDescription().getString());
        }
        return true;
    }

    /**
     * Places the ingredient into a free machine and remembers what is waited on.
     *
     * <p><b>One kind per pass.</b> The plan may mix — five iron ores and three
     * raw iron for eight ingots — but a furnace slot cannot. So the first kind
     * is taken, and as much of it as fits into one slot; the rest comes up on
     * the next cycle.
     *
     * @return whether anything was actually started
     */
    /**
     * Puts back ingredients that came to nothing.
     *
     * <p>The space has only just become free, so everything ought to go back
     * in. If it does not — a storage bus with a filter does not take back
     * everything it gave out — then the controller holds it. Throwing it on
     * the ground would be wrong here: the player gave the order, they did not
     * set the ingredients down.
     */
    private void refund(Map<Item, Long> items) {
        items.forEach((item, amount) -> {
            long rest = storage.insert(item, amount);
            if (rest > 0) {
                holdBack(new ItemStack(item, (int) rest));
            }
        });
    }

    private boolean startAtMachine(dev.devpanda.factorynetwork.crafting.CraftingJob job,
            dev.devpanda.factorynetwork.crafting.CraftingPlanner.Step<
                    net.minecraft.world.item.Item> step) {
        String declared = dev.devpanda.factorynetwork.crafting.DeclaredRecipes
                .deviceOf(step.station());
        if (declared != null) {
            return startAtDeclared(job, step, declared);
        }
        var station = dev.devpanda.factorynetwork.crafting.MachineRecipes
                .stationOf(step.station());
        if (station == null) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.FAILED,
                    "unbekannte Station " + step.station());
            return false;
        }
        var first = step.consumed().entrySet().iterator().next();
        net.minecraft.world.item.Item ingredient = first.getKey();
        String free = freeMachine(step.station(), station, ingredient);
        if (free == null) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    "wartet auf " + machineName(step.station()) + " im Netz");
            return false;
        }
        var handler = machineInventory(free);
        int room = handler.getSlotLimit(station.inputSlot());
        long take = Math.min(first.getValue(), Math.min(room, step.runs()));
        if (take <= 0) {
            return false;
        }
        long got = storage.extract(ingredient, take);
        if (got <= 0) {
            return false;
        }
        net.minecraft.world.item.ItemStack rest = handler.insertItem(station.inputSlot(),
                new net.minecraft.world.item.ItemStack(ingredient, (int) got), false);
        long placed = got - rest.getCount();
        if (!rest.isEmpty()) {
            // What the machine did not take after all goes back — the same
            // pattern as everywhere this mod offers something.
            storage.insert(ingredient, rest.getCount());
        }
        if (placed <= 0) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    machineName(step.station()) + " nimmt gerade nichts an");
            return false;
        }
        job.setRunning(new dev.devpanda.factorynetwork.crafting.CraftingJob.Running(
                step.station(), free, step.result(), placed * step.perCraft(), 0));
        job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.RUNNING,
                placed + " " + ingredient.getDescription().getString() + " in " + free);
        return true;
    }

    /**
     * A recipe from the program: the ingredients into the named machine.
     *
     * <p><b>Without slot numbers.</b> Where something belongs in a foreign
     * machine, only it knows itself — and that is exactly what the side-based
     * access is for, the one {@code move} also uses. The furnace is the
     * special case where this mod knows the slots; a machine from a foreign
     * mod is not, and it should keep its own rules.
     *
     * <p>Unlike with the furnace family, <b>all</b> ingredients are inserted
     * at once: a declared recipe may have several, and a machine that gets
     * only half the reckoning does not even start.
     */
    private boolean startAtDeclared(dev.devpanda.factorynetwork.crafting.CraftingJob job,
            dev.devpanda.factorynetwork.crafting.CraftingPlanner.Step<
                    net.minecraft.world.item.Item> step, String device) {
        var handler = machineSide(device);
        if (handler == null) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    "an " + device + " hängt keine Maschine");
            return false;
        }
        // Test first, then extract: a machine that rejects the second
        // ingredient would otherwise have swallowed the first and left the job
        // standing with half a recipe.
        for (var entry : step.consumed().entrySet()) {
            net.minecraft.world.item.ItemStack probe = new net.minecraft.world.item.ItemStack(
                    entry.getKey(), (int) (long) entry.getValue());
            if (!insertAll(handler, probe, true).isEmpty()) {
                job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                        device + " nimmt gerade nicht alles an");
                return false;
            }
        }
        // And the same for what is not an item. The test stands before every
        // movement, otherwise the ore would already lie in the machine while
        // the water is missing.
        String missing = fillExtras(step, device, true);
        if (missing != null) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING, missing);
            return false;
        }
        // <b>The ingredients before the water.</b> A storage bus can show what
        // it does not give up; if that only comes out after filling, the water
        // stands in the machine and the job waits for a result that, without
        // the ingredient, never arises.
        Map<Item, Long> paid = new LinkedHashMap<>();
        for (var entry : step.consumed().entrySet()) {
            long got = storage.extract(entry.getKey(), entry.getValue());
            if (got > 0) {
                paid.put(entry.getKey(), got);
            }
            if (got < entry.getValue()) {
                refund(paid);
                job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                        "der Speicher gibt nicht her, was er zeigt");
                return false;
            }
        }
        String failed = fillExtras(step, device, false);
        if (failed != null) {
            // Something changed between the test and the move. Rare, but then
            // honestly reported instead of silently half-executed.
            refund(paid);
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING, failed);
            return false;
        }
        paid.forEach((item, amount) -> {
            net.minecraft.world.item.ItemStack rest = insertAll(handler,
                    new net.minecraft.world.item.ItemStack(item, (int) (long) amount), false);
            if (!rest.isEmpty()) {
                storage.insert(item, rest.getCount());
            }
        });
        job.setRunning(new dev.devpanda.factorynetwork.crafting.CraftingJob.Running(
                step.station(), device, step.result(), step.yield(), 0));
        job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.RUNNING,
                "läuft in " + device);
        return true;
    }

    /**
     * Fills in the ingredients that are not items.
     *
     * <p><b>From the network storage into the machine</b>, exactly like the
     * items beside them. The planner does not procure fluids — that would need
     * a resource kind that is open rather than fixed — but the job must move
     * them: otherwise the line {@code in 1000 fluid:water} claims something
     * that never happens, and whoever fills the tank by worker would get out
     * of step with the network inventory.
     *
     * <p>The amount grows with the passes: the items go in for all at once,
     * the water has to keep up.
     *
     * @param probe whether only a query is made and nothing is moved yet
     * @return what is missing, or {@code null} if everything is present
     */
    private String fillExtras(dev.devpanda.factorynetwork.crafting.CraftingPlanner.Step<
            net.minecraft.world.item.Item> step, String device, boolean probe) {
        if (declaredCache == null) {
            return null;
        }
        for (var extra : declaredCache.extrasOf(step.station())) {
            long need = extra.needFor(step.runs());
            if (need <= 0) {
                continue;
            }
            String missing = extra.fluid()
                    ? fillFluid(device, extra, need, probe)
                    : fillChemical(device, extra, need, probe);
            if (missing != null) {
                return missing;
            }
        }
        return null;
    }

    /**
     * A fluid from the network into the machine's tank.
     *
     * <p><b>One variety per ingredient.</b> If the selection matches several —
     * {@code fluidtag:c/water} — the one there is enough of is taken. A tank
     * usually holds exactly one, and mixing two varieties in would be not a
     * recipe but an accident.
     */
    private String fillFluid(String device,
            dev.devpanda.factorynetwork.crafting.DeclaredRecipes.Extra extra, long need,
            boolean probe) {
        var options = dev.devpanda.factorynetwork.runtime.FluidSelection
                .resolve(extra.selection());
        if (options.isEmpty()) {
            return "diese Auswahl trifft keine Flüssigkeit";
        }
        net.minecraft.world.level.material.Fluid chosen = null;
        for (var fluid : options) {
            if (fluidStorage.count(fluid) >= need) {
                chosen = fluid;
                break;
            }
        }
        if (chosen == null) {
            var first = options.get(0);
            return "es fehlen " + (need - fluidStorage.count(first)) + " mB "
                    + fluidName(first);
        }
        var tank = machineTank(device);
        if (tank == null) {
            return device + " hat keinen Tank für " + fluidName(chosen);
        }
        int wanted = (int) Math.min(need, Integer.MAX_VALUE);
        var stack = new net.neoforged.neoforge.fluids.FluidStack(chosen, wanted);
        if (tank.fill(stack, net.neoforged.neoforge.fluids.capability.IFluidHandler
                .FluidAction.SIMULATE) < wanted) {
            return device + " nimmt gerade nicht genug " + fluidName(chosen) + " an";
        }
        if (probe) {
            return null;
        }
        long got = fluidStorage.extract(chosen, wanted);
        int placed = tank.fill(new net.neoforged.neoforge.fluids.FluidStack(chosen, (int) got),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        if (placed < got) {
            // Back with what did not go in after all — the same pattern as
            // everywhere this mod offers something.
            fluidStorage.insert(chosen, got - placed);
        }
        return null;
    }

    /**
     * The same for a chemical.
     *
     * <p>Without Mekanism there are none, and then it is missing — the message
     * says so, instead of pretending the machine is to blame.
     *
     * <p><b>Untested in game.</b> A Mekanism tank put into a test run via
     * {@code setBlock} has no side configuration and accepts nothing —
     * measured, the same gap as with the chemical worker. What stands here is
     * the same way as with fluids, with the same ask-first-then-pull.
     */
    private String fillChemical(String device,
            dev.devpanda.factorynetwork.crafting.DeclaredRecipes.Extra extra, long need,
            boolean probe) {
        var ids = dev.devpanda.factorynetwork.compat.mekanism.Chemicals
                .resolve(extra.selection());
        if (ids.isEmpty()) {
            return dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.installed()
                    ? "diese Auswahl trifft keine Chemikalie"
                    : "dafür fehlt Mekanism";
        }
        String chosen = null;
        for (String id : ids) {
            if (chemicalStorage.count(id) >= need) {
                chosen = id;
                break;
            }
        }
        if (chosen == null) {
            String first = ids.get(0);
            return "es fehlen " + (need - chemicalStorage.count(first)) + " mB "
                    + dev.devpanda.factorynetwork.compat.mekanism.Chemicals.nameOf(first);
        }
        var connector = connectorNamed(device);
        if (connector == null) {
            return "an " + device + " hängt keine Maschine";
        }
        var facing = connector.facing();
        var target = connector.pos().relative(facing);
        String name = dev.devpanda.factorynetwork.compat.mekanism.Chemicals.nameOf(chosen);
        if (dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.roomFor(
                level, target, facing.getOpposite(), chosen, need) < need) {
            return device + " nimmt gerade nicht genug " + name + " an";
        }
        if (probe) {
            return null;
        }
        dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.fillFrom(chemicalStorage,
                level, target, facing.getOpposite(), java.util.List.of(chosen), need);
        return null;
    }

    /** What a fluid is called in plain text. */
    private static String fluidName(net.minecraft.world.level.material.Fluid fluid) {
        return fluid.getFluidType().getDescription().getString();
    }

    /** The tank of the machine behind a device name. */
    private net.neoforged.neoforge.fluids.capability.IFluidHandler machineTank(String device) {
        var connector = connectorNamed(device);
        return connector == null ? null : connector.machineTank();
    }

    /**
     * Places a stack into any slot that takes it.
     *
     * <p>Which one is decided by the machine through its side rules — the same
     * information {@code move} relies on.
     */
    private static net.minecraft.world.item.ItemStack insertAll(
            net.neoforged.neoforge.items.IItemHandler handler,
            net.minecraft.world.item.ItemStack stack, boolean simulate) {
        net.minecraft.world.item.ItemStack rest = stack;
        for (int slot = 0; slot < handler.getSlots() && !rest.isEmpty(); slot++) {
            rest = handler.insertItem(slot, rest, simulate);
        }
        return rest;
    }

    /**
     * Pulls a result out of any slot that gives it up.
     *
     * <p>An input slot gives nothing up on its own — the machine decides that,
     * and so this loop never accidentally fetches back the ingredient that is
     * currently being processed.
     */
    private static long extractAll(net.neoforged.neoforge.items.IItemHandler handler,
            net.minecraft.world.item.Item wanted, long limit) {
        long taken = 0;
        for (int slot = 0; slot < handler.getSlots() && taken < limit; slot++) {
            if (handler.getStackInSlot(slot).getItem() != wanted) {
                continue;
            }
            taken += handler.extractItem(slot, (int) (limit - taken), false).getCount();
        }
        return taken;
    }

    /**
     * Which foreign inventories count toward storage.
     *
     * <p>From the program's {@code store} lines, resolved against the network.
     * A name that does not exist falls away — the check on adoption already
     * reported it, and a job that aborted because of it would help no one.
     *
     * <p>Higher priority first: whoever writes {@code priority 10} wants
     * things stored there before the cells.
     */
    private void rebuildBuses() {
        var program = program();
        if (program == null) {
            storage.setBuses(java.util.List.of());
            return;
        }
        java.util.List<dev.devpanda.factorynetwork.network.StorageBus> found =
                new java.util.ArrayList<>();
        for (var declaration : program.declarations()) {
            if (!(declaration instanceof dev.devpanda.factorynetwork.lang.ast.Decl.Store store)) {
                continue;
            }
            if (graph.connector(store.device()).isEmpty()) {
                continue;
            }
            String device = store.device();
            // The filter is resolved here and not per store operation:
            // checking a selection against the registry is expensive, and it
            // changes only when the program changes.
            var erlaubt = store.filter() == null ? java.util.List
                    .<net.minecraft.world.item.Item>of()
                    : dev.devpanda.factorynetwork.runtime.ItemSelection.resolve(store.filter());
            found.add(new dev.devpanda.factorynetwork.network.StorageBus(
                    device, store.priority(),
                    // A filter names identifiers, and every variant of them
                    // is meant: whoever writes "eisenbarren" also means the
                    // ones renamed from another mod.
                    erlaubt.stream()
                            .map(dev.devpanda.factorynetwork.storage.ItemKey::bare)
                            .toList(),
                    () -> machineSide(device)));
        }
        found.sort(java.util.Comparator.comparingLong(
                dev.devpanda.factorynetwork.network.StorageBus::priority).reversed());
        storage.setBuses(found);
    }

    /** The side-based access to the machine behind a connector. */
    private net.neoforged.neoforge.items.IItemHandler machineSide(String device) {
        var connector = connectorNamed(device);
        return connector == null ? null : connector.machineInventory();
    }

    /**
     * Collects what the machine has finished.
     *
     * <p>The network collects itself and does not wait for someone to write a
     * worker for it. A job that stands still because a line is missing from
     * the program that no one asked for would be the most unpleasant kind of
     * standstill.
     */
    private boolean collectRunning(dev.devpanda.factorynetwork.crafting.CraftingJob job) {
        var running = job.running();
        boolean declared = dev.devpanda.factorynetwork.crafting.DeclaredRecipes
                .deviceOf(running.station()) != null;
        var station = dev.devpanda.factorynetwork.crafting.MachineRecipes
                .stationOf(running.station());
        var handler = declared ? machineSide(running.device())
                : machineInventory(running.device());
        if (handler == null || (!declared && station == null)) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    running.device() + " ist nicht erreichbar");
            return false;
        }
        // Ask first, then collect. What does not fit into storage stays in the
        // machine — that is the natural backlog: it stalls instead of a heap
        // piling up in front of the controller.
        long fits = storage.room(running.result(), running.left());
        if (fits <= 0) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    "der Speicher ist voll");
            return false;
        }
        long wanted = Math.min(running.left(), fits);
        long got = declared
                ? extractAll(handler, running.result(), wanted)
                : handler.extractItem(station.outputSlot(), (int) wanted, false)
                        .getCount();
        if (got <= 0) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    "wartet auf " + running.device()
                            + (declared ? "" : " — hat er " + station.supply() + "?"));
            return false;
        }
        long rest = storage.insert(running.result(), got);
        if (rest > 0) {
            // The net beneath: it was asked beforehand, but a chest behind a
            // storage bus may have changed in between.
            holdBack(new ItemStack(running.result(), (int) rest));
        }
        long done = running.done() + got;
        if (done < running.expected()) {
            job.setRunning(running.withDone(done));
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.RUNNING,
                    done + " von " + running.expected() + " aus " + running.device());
            return true;
        }
        job.setRunning(null);
        if (running.result() == job.target()) {
            job.produced((int) done);
        } else {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.RUNNING,
                    done + " " + running.result().getDescription().getString() + " fertig");
        }
        return true;
    }

    /**
     * A connector to which a matching and free machine is attached.
     *
     * <p><b>Free means: the output slot is empty</b> and the ingredient fits
     * into the input slot. Whatever is already smelting something else is not
     * touched — otherwise a job would mix its ore into someone else's work.
     */
    private String freeMachine(String station,
            dev.devpanda.factorynetwork.crafting.MachineRecipes.Station shape,
            net.minecraft.world.item.Item ingredient) {
        for (var entry : graph.connectors().entrySet()) {
            var connector = connectorNamed(entry.getKey());
            if (connector == null) {
                continue;
            }
            if (!dev.devpanda.factorynetwork.crafting.MachineRecipes.fits(
                    station, connector.machineBlockEntity())) {
                continue;
            }
            var handler = connector.machineInventoryAll();
            if (handler == null || handler.getSlots() <= shape.outputSlot()) {
                continue;
            }
            if (!handler.getStackInSlot(shape.outputSlot()).isEmpty()) {
                continue;
            }
            net.minecraft.world.item.ItemStack probe =
                    new net.minecraft.world.item.ItemStack(ingredient);
            if (handler.insertItem(shape.inputSlot(), probe, true).getCount()
                    < probe.getCount()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private net.neoforged.neoforge.items.IItemHandler machineInventory(String device) {
        var connector = connectorNamed(device);
        return connector == null ? null : connector.machineInventoryAll();
    }

    /** What the machine of a station is called in plain text. */
    private static String machineName(String station) {
        return dev.devpanda.factorynetwork.crafting.MachineRecipes.machineName(station);
    }

    private static final String KEY_JOBS = "Jobs";
    private static final String KEY_NEXT_JOB = "NextJob";

    /** Under this name stand the files of the project. */
    private static final String KEY_FILES = "Files";

    /** And under this the draft that has not yet been adopted. */
    private static final String KEY_DRAFT = "Draft";
    private static final String KEY_OWNER = "Owner";

    /**
     * Reads the project from the save format.
     *
     * <p><b>Old worlds read along too:</b> there a single text stands under
     * {@code Source}. It becomes the {@code main.mf} — a project of one file
     * is exactly what a controller had before.
     */
    private static dev.devpanda.factorynetwork.lang.Project readProject(CompoundTag tag) {
        if (tag.contains(KEY_FILES)) {
            CompoundTag files = tag.getCompound(KEY_FILES);
            Map<String, String> sources = new HashMap<>();
            files.getAllKeys().forEach(name -> sources.put(name, files.getString(name)));
            return new dev.devpanda.factorynetwork.lang.Project(sources);
        }
        return dev.devpanda.factorynetwork.lang.Project.of(tag.getString(KEY_SOURCE));
    }

    private void writeProject(CompoundTag tag) {
        CompoundTag files = new CompoundTag();
        project.files().forEach(files::putString);
        tag.put(KEY_FILES, files);
        // The draft only if it differs from the running one: otherwise every
        // program would stand in the world twice.
        if (!draft.files().equals(project.files())) {
            CompoundTag entwurf = new CompoundTag();
            draft.files().forEach(entwurf::putString);
            tag.put(KEY_DRAFT, entwurf);
        }
        // The old key stays written: whoever opens the world with an older
        // version of the mod at least finds their text again, instead of an
        // empty terminal.
        tag.putString(KEY_SOURCE, project.joined());
    }

    // ---- The file next to the world ---------------------------------------

    /**
     * Checks whether someone has changed the program from outside.
     *
     * <p>On the very first look, what decides is whether the file already
     * exists: if there is none, it receives the running program. If there is
     * one, it holds — then someone created it or edited it while the server
     * was off, and both are intentional.
     *
     * <p><b>A controller that returns to the same spot finds its program
     * again.</b> The file stays when the block is broken; whoever misclicked
     * places the block back and has everything.
     */
    private void checkProgramFile() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        if (level.getGameTime() - lastFileCheck
                < dev.devpanda.factorynetwork.lang.ProgramFolder.CHECK_INTERVAL) {
            return;
        }
        lastFileCheck = level.getGameTime();
        writeStatus();
        if (programFolder == null) {
            programFolder = dev.devpanda.factorynetwork.lang.ProgramFolder.of(
                    server, worldPosition);
            if (programFolder == null) {
                return;
            }
            // On the first look: if the folder is empty, it receives the
            // running project. If something stands in it, that holds — then
            // someone created it or edited it while the server was off, and
            // both are intentional.
            programFolder.write(project);
            statusStale = true;
        }
        dev.devpanda.factorynetwork.lang.Project incoming = programFolder.poll(project);
        if (incoming == null) {
            return;
        }
        String name = programFolder.path().getFileName().toString();
        if (deploy(incoming)) {
            note("Projekt aus " + name + " übernommen: "
                    + String.join(", ", incoming.names()));
        } else {
            // The errors stand in the code tab as always. Here only the note
            // that it was the files and not the terminal.
            note(dev.devpanda.factorynetwork.runtime.LogLevel.ERROR, name,
                    "Fehler beim Übernehmen — das laufende Programm läuft weiter.");
        }
        // In both cases, and in the second all the more: whoever works in VS
        // Code sees nothing otherwise — their program silently does not run.
        statusStale = true;
    }

    /**
     * Writes, next to the program files, what the game knows about them.
     *
     * <p>Errors with file and line, plus the names from the world. With that,
     * an editor beside them gets both things it cannot have on its own — and
     * from the compiler, which runs anyway, instead of from a second version
     * of the same rules.
     *
     * <p>Called when something changes: after an adoption, after a failed
     * adoption, and after a rebuild of the network — the device names change
     * there. Not per tick: {@code ProgramStatus} does write only on change,
     * but building and comparing the file every time would be work for
     * nothing.
     */
    private void writeStatus() {
        if (programFolder == null || !statusStale) {
            return;
        }
        statusStale = false;
        dev.devpanda.factorynetwork.lang.ProgramStatus.write(programFolder.path(),
                diagnostics, List.copyOf(graph.connectorNames()), displayNames(),
                // What counts as a resource kind in this pack, only the
                // running game knows — since foreign mods may register their
                // own.
                List.copyOf(new java.util.TreeSet<>(
                        dev.devpanda.factorynetwork.runtime.ResourceKinds
                                .selectorPrefixes())));
    }


    /**
     * Where the project lies as a folder, or {@code null} as long as no one
     * has yet looked for it.
     */
    public java.nio.file.Path programFilePath() {
        return programFolder == null ? null : programFolder.path();
    }

    /**
     * Writes the program into the file.
     *
     * <p>After <b>every</b> adoption, even one with errors: folder and
     * terminal must show the same text. If the files still held the last
     * error-free version, the next look would fetch it back and overwrite what
     * had just been typed.
     */
    private void writeProgramFile() {
        if (programFolder != null) {
            programFolder.write(project);
        }
    }

    // ---- Storage view -------------------------------------------------------

    /**
     * At most this often, in ticks. Otherwise a worker moves something every
     * tick.
     *
     * <p>The initial value of {@code lastStoragePush} is the same value
     * negated — written as a constant it would run ahead of the field order.
     */
    private static final int STORAGE_PUSH_INTERVAL = 10;

    /**
     * Sends the network state to a player who is just opening a screen.
     *
     * <p><b>Lives here and not on the terminal block</b>, since there are two
     * ways into the screen. The second leads over a transmission mast, and
     * there is no terminal there to answer the question — without this move
     * the network tab would stay empty from afar.
     */
    public void sendNetworkStateTo(ServerPlayer player) {
        rebuildNetwork();
        java.util.List<String> workers = runtime().states().entrySet().stream()
                .map(state -> state.getKey() + ": " + state.getValue().status)
                .toList();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new dev.devpanda.factorynetwork.network.packet.NetworkStatePacket(
                        connectorPlaces(), displayPlaces(), workers, plants(),
                        fluidLines(), connectorProfiles()));
    }

    /** The terminal is open, no matter which tab. */
    public void watchTerminal(ServerPlayer player) {
        terminalWatchers.add(player);
        pushProjectTo(player);
        pushFlowsTo(player);
        pushDisplaysTo(player);
        // Otherwise the tab would stay empty until the next window of the
        // regular sending opens up — and whoever opens it on purpose would
        // have the impression that nothing had happened.
        pushLogTo(player);
        pushTrafficTo(player);
    }

    /**
     * Sends the traffic history to a watcher.
     *
     * <p>Only to those who have the terminal open: the history changes every
     * second, and a network with ten players would otherwise send it ten
     * times to no one.
     */
    private void pushTrafficTo(ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                dev.devpanda.factorynetwork.network.packet.TrafficPacket.of(
                        runtime.history(), bandwidth()));
    }

    /**
     * And once per second to all watchers.
     *
     * <p>Per second, because the history has one point per second — sending
     * more often would mean transmitting the same curve several times.
     */
    private void pushTraffic() {
        if (level == null || terminalWatchers.isEmpty()
                || level.getGameTime() % dev.devpanda.factorynetwork.network.Bandwidth
                        .TICKS_PER_SECOND != 0) {
            return;
        }
        var paket = dev.devpanda.factorynetwork.network.packet.TrafficPacket.of(
                runtime.history(), bandwidth());
        terminalWatchers.forEach(watcher ->
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(watcher, paket));
    }

    /**
     * Sends the whole project.
     *
     * <p>On opening and after every adoption — not continuously. A project
     * changes when someone changes it, and then the server knows.
     */
    public void pushProjectTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                dev.devpanda.factorynetwork.network.packet.ProjectStatePacket
                        .of(worldPosition, project, draft, locksFor(player.getUUID())));
    }

    /**
     * What names the network yields — for the compiler.
     *
     * <p>With this, adoption too reports what the editor already says while
     * typing. Important for the way over the folder next to the world:
     * whoever writes there has no editor to warn them.
     */
    public dev.devpanda.factorynetwork.lang.NetworkView networkView() {
        List<String> connectorNames = new ArrayList<>(graph.connectorNames());
        List<String> displayNames = displayNames();
        return new dev.devpanda.factorynetwork.lang.NetworkView() {
            @Override
            public boolean knowsNetwork() {
                return !connectorNames.isEmpty() || !displayNames.isEmpty();
            }

            @Override
            public List<String> connectors() {
                return connectorNames;
            }

            @Override
            public List<String> displays() {
                return displayNames;
            }
        };
    }

    /**
     * The display walls in the network, each once, with its location.
     *
     * <p>The names from the blocks and not from the graph: the graph remembers
     * locations, not names. A wall consists of many panels with the same name
     * — the first is taken, and that is enough to find it again.
     */
    public List<dev.devpanda.factorynetwork.network.packet.NamedPlace> displayPlaces() {
        if (level == null) {
            return List.of();
        }
        Map<String, BlockPos> byName = new java.util.TreeMap<>();
        for (BlockPos pos : graph.displays()) {
            if (level.getBlockEntity(pos) instanceof DisplayBlockEntity panel) {
                String name = panel.displayName();
                if (name != null && !name.isBlank()) {
                    byName.putIfAbsent(name, pos.immutable());
                }
            }
        }
        return byName.entrySet().stream()
                .map(entry -> new dev.devpanda.factorynetwork.network.packet.NamedPlace(
                        entry.getKey(), entry.getValue()))
                .toList();
    }

    /** Only the names — for the compiler, which needs no locations. */
    public List<String> displayNames() {
        return displayPlaces().stream()
                .map(dev.devpanda.factorynetwork.network.packet.NamedPlace::name)
                .toList();
    }

    /**
     * The connectors in the network with their location.
     *
     * <p>The position and not the face: the name is written above the block
     * on which the connector sits.
     */
    public List<dev.devpanda.factorynetwork.network.packet.NamedPlace> connectorPlaces() {
        return graph.connectors().entrySet().stream()
                .map(entry -> new dev.devpanda.factorynetwork.network.packet.NamedPlace(
                        entry.getKey(), entry.getValue().pos()))
                .toList();
    }

    /**
     * The profiles of all connectors, for the editor.
     *
     * <p>Once on opening the terminal. Whatever is not loaded yields a profile
     * that says nothing about itself — and that is something other than one
     * that can do nothing.
     */
    public List<dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec.Flat>
            connectorProfiles() {
        List<dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec.Flat> profiles =
                new java.util.ArrayList<>();
        if (level == null) {
            return profiles;
        }
        for (String name : graph.connectors().keySet()) {
            var connector = connectorNamed(name);
            if (connector == null) {
                continue;
            }
            profiles.add(dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec.toFlat(
                    name, DeviceScan.of(connector)));
        }
        return profiles;
    }


    // ---- The network state at the connector --------------------------------

    /**
     * Which connectors this controller last stamped.
     *
     * <p>Needed for those that drop out of the network: whoever no longer
     * stands in the graph gets no one to tell them anything — and would
     * otherwise stay forever on their last state. A green light on a cut-off
     * device would be worse than none at all.
     */
    private java.util.Set<DevicePos> stamped = java.util.Set.of();

    /**
     * Writes down for each connector how it stands.
     *
     * <p><b>The state is a shadow of the graph.</b> It is computed here, on
     * rebuild — every hundred ticks and on every change to the network. The
     * connector only stores it, so that a look at the block can answer it
     * without asking the controller.
     *
     * <p><b>What that costs:</b> nothing the graph does not compute anyway,
     * and a packet only where something has really changed — {@code
     * ConnectorPart.setState} takes care of that.
     *
     * <p>If a connector hangs on two networks, the controller that stamped
     * last wins. That is the same case the <i>Network</i> tab reports as
     * contested, and it is rare enough not to resolve specially here.
     */
    private void stampDeviceStates() {
        Map<DevicePos, DeviceState> wanted = statesFromGraph();
        for (DevicePos where : stamped) {
            if (!wanted.containsKey(where)) {
                stamp(where, DeviceState.OFFLINE);
            }
        }
        wanted.forEach(this::stamp);
        stamped = java.util.Set.copyOf(wanted.keySet());
    }

    /** What the graph says about each connector. */
    private Map<DevicePos, DeviceState> statesFromGraph() {
        Map<DevicePos, DeviceState> found = new LinkedHashMap<>();
        graph.connectors().forEach((name, where) ->
                found.put(where, DeviceState.ONLINE));
        // Names given more than once are not in connectors() — precisely for
        // that reason, and precisely for that reason they must be added here
        // explicitly.
        for (String name : graph.ambiguousNames()) {
            for (DevicePos where : graph.positionsOf(name)) {
                found.put(where, DeviceState.DUPLICATE);
            }
        }
        for (DevicePos where : graph.unnamedConnectors()) {
            found.put(where, DeviceState.UNNAMED);
        }
        return found;
    }

    private void stamp(DevicePos where, DeviceState state) {
        if (level == null || where.side() == null || !level.isLoaded(where.pos())) {
            return;
        }
        ConnectorPart part = Connectors.at(level, where.pos(), where.side());
        if (part != null) {
            part.setState(state);
        }
    }

    /** What is in the editor, even if it is not running yet. */
    public dev.devpanda.factorynetwork.lang.Project draft() {
        return draft;
    }

    /**
     * Receives a draft.
     *
     * <p><b>The reason it exists:</b> before, the text lived only in the
     * client. A crash, a kick, an accidental leaving of the world — and half
     * an hour of work was gone, because only adoption would have saved it. And
     * adoption works only when the program is error-free; right in the middle
     * of a change it never is.
     *
     * <p>The whole draft is sent and not a change list. A program here is a
     * few dozen lines, capped at 64 KB per file, and a change list with
     * positions is the kind of code where a bug only shows once the text is
     * already broken.
     *
     * <p>The sender gets nothing back: they just sent the text, and an echo
     * would hit them mid-typing.
     */
    public void acceptDraft(dev.devpanda.factorynetwork.lang.Project incoming,
                            java.util.UUID author, String authorName) {
        if (incoming.files().equals(draft.files())) {
            return;
        }
        this.draft = author == null ? incoming : merge(incoming, author, authorName);
        setChanged();
        // The sender normally gets nothing back — they just sent the text,
        // and an echo would hit them mid-typing.
        //
        // <b>Except when something was rejected.</b> Then the echo is the only
        // word about it, and without it they keep typing into a file no one
        // accepts any more.
        boolean rejected = !draft.files().equals(incoming.files());
        for (ServerPlayer watcher : terminalWatchers) {
            if (!watcher.getUUID().equals(author) || rejected) {
                pushProjectTo(watcher);
            }
        }
    }

    /**
     * Adopts from the incoming draft only what the sender may write.
     *
     * <p><b>Without this, two players overwrite each other wordlessly.</b> Both
     * send the whole draft; whoever types last wins — even over a file they
     * did not have open at all. The other notices when their work is gone.
     *
     * <p>A file someone else holds therefore stays as it is. The sender gets it
     * sent back with the next state and sees it as locked in the editor.
     */
    private dev.devpanda.factorynetwork.lang.Project merge(
            dev.devpanda.factorynetwork.lang.Project incoming, java.util.UUID author,
            String authorName) {
        long now = level == null ? 0L : level.getGameTime();
        Map<String, String> merged = new HashMap<>(draft.files());
        for (Map.Entry<String, String> entry : incoming.files().entrySet()) {
            if (draft.files().containsKey(entry.getKey())
                    && entry.getValue().equals(draft.source(entry.getKey()))) {
                // Sent along unchanged: no change, so no claim on the file
                // either.
                //
                // The check for "already exists" belongs to it: a freshly
                // created file is empty, and source() likewise returns the
                // empty text for an unknown one. Without it, every new file
                // would fall through here and never reach the server.
                continue;
            }
            if (locks.claim(entry.getKey(), author, authorName, now)) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        // Only what the sender may also hold is deleted.
        merged.keySet().removeIf(name -> !incoming.files().containsKey(name)
                && locks.claim(name, author, authorName, now));
        return new dev.devpanda.factorynetwork.lang.Project(merged);
    }

    /** Who holds which file, from this player's point of view. */
    /** Who currently holds this file, or {@code null}. */
    public java.util.UUID holderOf(String file) {
        return level == null ? null : locks.holderOf(file, level.getGameTime());
    }

    public Map<String, String> locksFor(java.util.UUID player) {
        return locks.othersFor(player, level == null ? 0L : level.getGameTime());
    }

    /** And to all who are watching — after a change from outside. */
    private void pushProjectToWatchers() {
        terminalWatchers.forEach(this::pushProjectTo);
    }

    public void unwatchTerminal(ServerPlayer player) {
        // Release first, then unregister: waiting for the timeout would mean
        // someone else standing for a minute in front of a file no one has
        // open any more.
        locks.release(player.getUUID());
        terminalWatchers.stream().filter(other -> other != player)
                .forEach(this::pushProjectTo);
        terminalWatchers.remove(player);
        storageWatchers.remove(player);
    }

    public void watchStorage(ServerPlayer player) {
        storageWatchers.add(player);
        pushStorageTo(player, true);
    }

    public void unwatchStorage(ServerPlayer player) {
        storageWatchers.remove(player);
    }

    /** Notes that something changed — the send is bundled. */
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
        // Take along logged-off players, so the set does not leak.
        storageWatchers.removeIf(player -> player.isRemoved()
                || !(player.containerMenu instanceof TerminalMenu));
        storageWatchers.forEach(player -> pushStorageTo(player, true));
    }

    /** Sends the inventory to a player. */
    public void pushStorageTo(ServerPlayer player, boolean replace) {
        // The items themselves and not their identifiers: otherwise an
        // enchanted book and an empty one would stand in the same line.
        var contents = storage.contents();
        List<StorageSnapshotPacket.Entry> entries = contents.entrySet().stream()
                .sorted(Map.Entry.<dev.devpanda.factorynetwork.storage.ItemKey, Long>
                        comparingByValue().reversed())
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

    // ---- Flows in the terminal --------------------------------------------

    /** At most this often, in ticks. */
    private static final int FLOW_PUSH_INTERVAL = 10;

    private long lastFlowPush = -FLOW_PUSH_INTERVAL;

    /**
     * Sends the list of flows to the watchers.
     *
     * <p>Unlike the inventory, it is not flagged but sent regularly: a flow
     * that sleeps changes nothing and would still be worth a line whose state
     * can change at any time.
     */
    private void pushFlowsIfDue() {
        if (level == null || terminalWatchers.isEmpty()
                || level.getGameTime() - lastFlowPush < FLOW_PUSH_INTERVAL) {
            return;
        }
        lastFlowPush = level.getGameTime();
        terminalWatchers.removeIf(player -> player.isRemoved()
                || player.containerMenu
                        instanceof net.minecraft.world.inventory.InventoryMenu);
        terminalWatchers.forEach(player -> {
            pushFlowsTo(player);
            pushDisplaysTo(player);
            pushLogTo(player);
            pushCraftingTo(player);
        });
    }

    /** Sends the flows to a player. */
    public void pushFlowsTo(ServerPlayer player) {
        FlowEngine engine = flowEngine();
        PacketDistributor.sendToPlayer(player, new FlowStatePacket(flowLines(),
                new FlowStatePacket.Compute(threads(),
                        engine == null ? 0 : engine.occupied(),
                        engine == null ? 0 : engine.queued(),
                        memory(), programSize(), diskSpace()),
                new FlowStatePacket.Supply(power.state().ordinal(), power.stored(),
                        power.capacity(), powerDraw(), power.supplied()),
                globalLines()));
    }

    /**
     * Sends the crafting jobs to a player.
     *
     * <p>The name of the target as finished text: the client should show it and
     * not have to resolve it.
     */
    public void pushCraftingTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new dev.devpanda.factorynetwork.network.packet.CraftingStatePacket(
                        craftingLines()));
    }

    /** The jobs as finished lines — separate from sending, so it is testable. */
    public List<dev.devpanda.factorynetwork.network.packet.CraftingStatePacket.Line>
            craftingLines() {
        List<dev.devpanda.factorynetwork.network.packet.CraftingStatePacket.Line> lines =
                new ArrayList<>(jobs.size());
        for (var job : jobs) {
            lines.add(new dev.devpanda.factorynetwork.network.packet
                    .CraftingStatePacket.Line(job.id(),
                    job.target().getDescription().getString(), job.wanted(), job.done(),
                    job.status().name(), job.detail()));
        }
        return lines;
    }

    /** Sends the log to a player. */
    public void pushLogTo(ServerPlayer player) {
        List<dev.devpanda.factorynetwork.network.packet.LogStatePacket.Line> lines =
                new ArrayList<>(log.size());
        for (dev.devpanda.factorynetwork.runtime.LogEntry entry : log) {
            lines.add(new dev.devpanda.factorynetwork.network.packet.LogStatePacket.Line(
                    entry.level().key(), entry.time(), entry.source(), entry.text()));
        }
        PacketDistributor.sendToPlayer(player,
                new dev.devpanda.factorynetwork.network.packet.LogStatePacket(lines));
    }

    /**
     * The global values as lines for the terminal.
     *
     * <p><b>A value you cannot see is worthless when debugging.</b> The detour
     * would be a {@code log()} in a loop — and that is the kind of stopgap you
     * then forget to take out again.
     */
    public List<String> globalLines() {
        List<String> lines = new ArrayList<>();
        globals.forEach((name, value) -> lines.add(name + " = " + value.describe()));
        return lines;
    }

    /** The flows as lines — separate from sending, so it is testable. */
    public List<FlowStatePacket.Line> flowLines() {
        List<FlowStatePacket.Line> lines = new ArrayList<>();
        if (flows != null) {
            flows.flows().values().forEach(flow -> lines.add(new FlowStatePacket.Line(
                    flow.id(), flow.entryPoint(), flow.status().name(), flow.detail())));
            // The most recently failed ones appended, so that a failure from
            // last night is still there in the morning.
            flows.failed().forEach(flow -> lines.add(new FlowStatePacket.Line(
                    flow.id(), flow.entryPoint(), flow.status().name(), flow.detail())));
        }
        return lines;
    }

    // ---- Displays in the terminal -----------------------------------------

    /**
     * Sends the displays to a player.
     *
     * <p>Evaluated here, drawn there — the same split as with the display on
     * the wall. What goes over the wire is how it finally stands.
     */
    public void pushDisplaysTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new DisplayStatePacket(displayPanels()));
    }

    /** The displays as finished lines — separate from sending, so it is testable. */
    public List<DisplayStatePacket.Panel> displayPanels() {
        List<DisplayStatePacket.Panel> panels = new ArrayList<>();
        DisplayValues values = new DisplayValues(graph, storage, runtime, globals, level);
        for (Decl declaration : program.declarations()) {
            if (!(declaration instanceof Decl.Display display)) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            List<DisplayStatePacket.Button> buttons = new ArrayList<>();
            List<DisplayValues.Line> evaluated = values.evaluate(display);
            // The entry counts on its own: a list brings several lines with
            // it, and the line number runs ahead afterwards.
            int entryIndex = -1;
            Decl.Display.Entry.Kind previous = null;
            for (int i = 0; i < evaluated.size(); i++) {
                DisplayValues.Line line = evaluated.get(i);
                if (belongsToPreviousEntry(line, previous)) {
                    lines.add(DisplayBlockEntity.format(line));
                    continue;
                }
                entryIndex++;
                previous = line.kind();
                lines.add(DisplayBlockEntity.format(line));
                if (line.kind() == Decl.Display.Entry.Kind.BUTTON) {
                    buttons.add(new DisplayStatePacket.Button(i, entryIndex));
                }
            }
            panels.add(new DisplayStatePacket.Panel(display.name(), lines, buttons));
        }
        addUnknownPanels(panels);
        return panels;
    }

    /**
     * Does this line still belong to the entry before it?
     *
     * <p>Only a list brings more than one line with it: its heading is
     * followed by the items as lines, until the next entry begins. Everything
     * else is one to one.
     */
    private static boolean belongsToPreviousEntry(DisplayValues.Line line,
                                                  Decl.Display.Entry.Kind previous) {
        if (previous != Decl.Display.Entry.Kind.LIST) {
            return false;
        }
        return line.kind() == Decl.Display.Entry.Kind.ROW
                || line.kind() == Decl.Display.Entry.Kind.TEXT;
    }

    /**
     * Adds what hangs in the world and is missing from the program.
     *
     * <p>Until now the tab listed only the {@code display} declarations. A
     * named panel without a matching declaration was thus visible
     * <b>nowhere</b> — it only said so itself on its front, and that hangs
     * perhaps three rooms away. The same holds for a panel without a name.
     *
     * <p>That is the same kind of information as "unnamed connector" or
     * "device without a channel": something hangs in the network and does
     * nothing, and the reason belongs where you look.
     */
    private void addUnknownPanels(List<DisplayStatePacket.Panel> panels) {
        if (level == null) {
            return;
        }
        Set<String> declared = new HashSet<>();
        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.Display display) {
                declared.add(display.name());
            }
        }
        Set<BlockPos> walls = new HashSet<>();
        Set<String> unknown = new java.util.LinkedHashSet<>();
        int nameless = 0;
        for (BlockPos pos : graph.displays()) {
            if (!level.isLoaded(pos)
                    || !(level.getBlockEntity(pos) instanceof DisplayBlockEntity panel)) {
                continue;
            }
            // A wall once, not once per panel.
            var wall = panel.wall();
            if (!walls.add(wall.anchor())) {
                continue;
            }
            String name = panel.wallName(wall);
            if (name.isBlank()) {
                nameless++;
            } else if (!declared.contains(name)) {
                unknown.add(name);
            }
        }
        for (String name : unknown) {
            panels.add(new DisplayStatePacket.Panel(name,
                    List.of("§eim Netz, aber das Programm kennt kein display " + name),
                    List.of()));
        }
        if (nameless > 0) {
            panels.add(new DisplayStatePacket.Panel("—",
                    List.of("§8" + nameless + " Tafel(n) ohne Namen"), List.of()));
        }
    }

    /**
     * The fluid contents, as lines for the terminal.
     *
     * <p>As text and not as a grid of symbols: a fluid has no item icon, and a
     * network rarely holds more than a handful of varieties — no second grid
     * is worth it for that.
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
     * The built installations, as lines for the terminal.
     *
     * <p>An incomplete installation would otherwise stay invisible: it does
     * nothing and says nothing, and the player hunts for the error in the
     * program instead of in the labeling.
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
     * Triggers what stands behind a button.
     *
     * <p>As a flow, not as an ordinary call: a button should be allowed to set
     * something going that waits, and no one needs a return value.
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
            // The client pointed at a line that is not a button.
            return;
        }
        String function = functionNameOf(entry.value());
        if (function == null) {
            note(dev.devpanda.factorynetwork.runtime.LogLevel.ERROR, display.name(),
                    "Der Knopf " + entry.label() + " nennt keine Funktion.");
            return;
        }
        try {
            startFlow(function, List.of());
        } catch (ScriptError error) {
            note(entry.label() + ": " + error);
        }
    }

    /** Which function stands behind a button — a name or a call. */
    private static String functionNameOf(Expr value) {
        return switch (value) {
            case Expr.Name name -> name.value();
            case Expr.Call call when call.callee() instanceof Expr.Name name -> name.value();
            case null, default -> null;
        };
    }

    /**
     * Looks for changes in redstone strength and fires events for them.
     *
     * <p>Polled rather than reported, and only every ten ticks: the concept
     * expressly allows the runtime internal polling, as long as the player
     * need not write a loop for it. With a few dozen connectors that is
     * cheaper than listening at every block.
     */
    /** The last seen contents per device, as a fingerprint. */
    private final Map<String, Integer> lastContents = new HashMap<>();

    /** The last seen amounts per device — the baseline for device_output. */
    private final Map<String, DeviceAmounts> lastAmounts = new HashMap<>();

    /**
     * Reports what happens at the devices.
     *
     * <p><b>{@code device_changed}: something stirred.</b> It says no more, and
     * can say no more — burning coal is a change too. What that means the
     * player writes themselves.
     *
     * <p><b>{@code device_output}: something has come in.</b> The amounts per
     * kind are compared with those from the last look; only more counts, so
     * consumption and extraction never trigger anything. What the network
     * inserts itself pulls the baseline along at once (see {@link
     * #noteFilled}) and so never passes as an output. Every increase is
     * reported and not only the first: a machine that puts out a batch piece
     * by piece reports every piece — otherwise the rest would stay in it.
     *
     * <p><b>Not "done".</b> Whether a machine has finished its work, no one
     * knows from outside: the output can be filled from before, and every mod
     * counts differently. An automation that once switches on too early loses
     * items in a chest no one finds again — so the name says what is measured,
     * and not what the player would like to conclude from it.
     *
     * <p>Polled rather than reported and only every ten ticks, as with the
     * redstone — and per event only if the program listens for it at all.
     */
    private void fireInventoryEvents() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        boolean watchChanges = listensTo(BuiltinEvents.DEVICE_CHANGED);
        boolean watchOutput = listensTo(BuiltinEvents.DEVICE_OUTPUT);
        if (!watchChanges) {
            lastContents.clear();
        }
        if (!watchOutput) {
            lastAmounts.clear();
        }
        if (!watchChanges && !watchOutput) {
            return;
        }
        for (Map.Entry<String, dev.devpanda.factorynetwork.network.DevicePos> entry
                : graph.connectors().entrySet()) {
            var connector = connectorNamed(entry.getKey());
            if (connector == null) {
                continue;
            }
            // On first sight nothing is reported: nothing has changed there,
            // it was only that nothing was known.
            if (watchChanges) {
                int fingerprint = contentsFingerprint(connector);
                Integer previous = lastContents.put(entry.getKey(), fingerprint);
                if (previous != null && previous != fingerprint) {
                    fireEvent(BuiltinEvents.DEVICE_CHANGED,
                            List.of(new Value.Device(entry.getKey())));
                }
            }
            if (watchOutput) {
                DeviceAmounts amounts = DeviceAmounts.of(connector);
                DeviceAmounts previous = lastAmounts.put(entry.getKey(), amounts);
                if (previous != null && amounts.hasMoreThan(previous)) {
                    fireEvent(BuiltinEvents.DEVICE_OUTPUT,
                            List.of(new Value.Device(entry.getKey())));
                }
            }
        }
    }

    /**
     * The network has just put something into a device itself.
     *
     * <p>The baseline is pulled along at once, so that its own delivery does
     * not pass as an output of the device on the next look. Without it, a flow
     * that inserts and then waits would wake itself.
     *
     * <p>Only for devices that are already watched. Whatever has no baseline
     * yet gets one on the next look — and the first look never reports.
     */
    public void noteFilled(String device) {
        lastAmounts.computeIfPresent(device, (name, previous) -> {
            var connector = connectorNamed(name);
            // A device that is currently unreachable keeps its old baseline.
            // An empty one would mean: at the next look everything in it is
            // new.
            return connector == null ? previous : DeviceAmounts.of(connector);
        });
    }

    /**
     * The connector with this name, or {@code null}.
     *
     * <p>By position <b>and</b> face: if six connectors sit on one cable
     * block, the position alone is no answer.
     */
    private ConnectorPart connectorNamed(String device) {
        var where = graph.connector(device).orElse(null);
        if (where == null || where.side() == null || level == null
                || !level.isLoaded(where.pos())) {
            return null;
        }
        return Connectors.at(level, where.pos(), where.side());
    }

    /**
     * A number that changes when the contents change.
     *
     * <p>Inventory and tank together — a machine can have both, and whoever
     * waits on it wants to know about both.
     */
    private static int contentsFingerprint(ConnectorPart connector) {
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

    /**
     * Does anyone listen for this event?
     *
     * <p><b>A waiting flow counts too.</b> For a long time only the {@code on}
     * blocks were counted, and so a
     *
     * <pre>let gerät = await device_changed</pre>
     *
     * stood forever: without a block nothing was even looked at, the event
     * never fired, and the flow waited on something no one triggered any more.
     * The query is expensive enough to spare — but only when truly no one is
     * listening.
     */
    private boolean listensTo(String event) {
        if (program.handlers().stream().anyMatch(handler -> handler.name().equals(event))) {
            return true;
        }
        return flows != null && flows.awaits(event);
    }

    private void fireRedstoneEvents() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        if (!listensTo(BuiltinEvents.REDSTONE_CHANGED)) {
            return;
        }
        for (Map.Entry<String, dev.devpanda.factorynetwork.network.DevicePos> entry
                : graph.connectors().entrySet()) {
            if (!level.isLoaded(entry.getValue().pos())) {
                continue;
            }
            // Read at the block and not at the face — the same rule as in
            // WorldHost.redstone: what reaches the block reaches every
            // connector on it.
            int strength = level.getBestNeighborSignal(entry.getValue().pos());
            Integer previous = lastRedstone.put(entry.getKey(), strength);
            if (previous != null && previous == strength) {
                continue;
            }
            // Through the flow engine, so that an on redstone_changed may
            // itself wait — on a machine, on a counter, on time.
            fireEvent(BuiltinEvents.REDSTONE_CHANGED,
                    List.of(new Value.Device(entry.getKey()), new Value.Int(strength)));
        }
    }

    /**
     * Lets the waiting flows work.
     *
     * <p>The engine is built on first need and discarded on adopting new code
     * — with the limitation that is still missing: flows that are currently
     * waiting would have to survive the change and ask the player. That comes
     * in the next step.
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
     * The flow engine, built on demand and filled with what was saved.
     *
     * <p>The unpacking stands here and not in the tick, so that it does not
     * matter who asks first. An event that arrives in the same tick the chunk
     * was loaded would otherwise meet an empty engine — and the waiting flows
     * would come a tick too late.
     */
    public FlowEngine flowEngine() {
        if (flows == null && level != null) {
            flows = new FlowEngine(program, new Interpreter(program,
                    newHost()));
        }
        if (flows != null && pendingFlows != null) {
            CompoundTag saved = pendingFlows;
            pendingFlows = null;
            FlowCodec.read(saved, flows);
        }
        // A freshly loaded controller does not yet know its network. That is
        // something other than "no server, no power" — without this build
        // every network would stand still after loading until the first tick
        // comes.
        if (flows != null && !networkKnown) {
            rebuildNetwork();
        }
        if (flows != null) {
            // Here and not in the tick: every access to the engine goes
            // through this spot, and both can change between two ticks —
            // someone adds a part, tears the rack down, or the power fails. A
            // button on the display and an event drive the engine directly,
            // without going through the tick; the lock must therefore sit
            // here.
            flows.setThreadLimit(threads());
            flows.setMemoryLimit(memory());
            // If someone pulls out a disk, the program suddenly no longer
            // fits. Then the network freezes, as with a power failure — not
            // aborting, not truncating.
            flows.setFrozen(!isOnline() || !programFits());
        }
        return flows;
    }

    /** Begins a flow that can wait. */
    public Flow startFlow(String functionName, List<Value> arguments) {
        FlowEngine engine = flowEngine();
        if (engine == null) {
            throw new ScriptError("Keine Welt.");
        }
        Flow flow = engine.start(functionName, arguments);
        engine.tick(level.getGameTime());
        return flow;
    }

    /** Wakes waiting flows and fires event blocks. */
    public void fireEvent(String event, List<Value> arguments) {
        FlowEngine engine = flowEngine();
        if (engine == null) {
            return;
        }
        engine.post(event, arguments);
        engine.tick(level.getGameTime());
    }

    /** Calls a function of the program — for tests and the terminal. */
    public Value callFunction(String name, List<Value> arguments) {
        if (level == null) {
            throw new ScriptError("Keine Welt.");
        }
        WorldHost host = newHost();
        Interpreter interpreter = new Interpreter(program, host);
        FlowEngine engine = flowEngine();
        if (engine != null) {
            // A call from the terminal too can trigger something a flow is
            // waiting on.
            interpreter.setEventSink(engine::post);
        }
        interpreter.setLogSource(name);
        try {
            return interpreter.call(name, arguments);
        } finally {
            if (engine != null) {
                engine.tick(level.getGameTime());
            }
        }
    }

    public List<dev.devpanda.factorynetwork.runtime.LogEntry> log() {
        return List.copyOf(log);
    }

    /** Clears the log — only on explicit request in the terminal. */
    public void clearLog() {
        log.clear();
        setChanged();
    }

    /**
     * A host that writes to the log.
     *
     * <p>Built in one place, because otherwise there are three — for the
     * workers, for the flow engine, and for the call from the terminal. One of
     * them was forgotten, and the flows' messages arrived nowhere.
     */
    private WorldHost newHost() {
        WorldHost host = new WorldHost(level, graph, stores, globals,
                this::setChanged);
        host.setLogSink(this::add);
        host.setPower(power);
        host.setDeviceFilled(this::noteFilled);
        // So that a move which fails at a stubborn machine does not take the
        // goods along with the error.
        host.setHoldBack(this::holdBack);
        // The same groups as the workers, including their pointer for round_robin.
        host.setGroups(runtime.groups());
        // Orders go to the same list the tab also shows.
        host.setCrafting((item, amount) -> {
            var job = requestCraft(item, amount);
            return job == null ? 0 : job.id();
        });
        return host;
    }

    private void note(String message) {
        note(dev.devpanda.factorynetwork.runtime.LogLevel.INFO, "", message);
    }

    private void note(dev.devpanda.factorynetwork.runtime.LogLevel level,
                      String source, String message) {
        add(new dev.devpanda.factorynetwork.runtime.LogEntry(
                level, System.currentTimeMillis(), source, message));
    }

    /**
     * Takes into holding what found no place anywhere.
     *
     * <p>And says so in the log. A silent buffer would only be the friendlier
     * way to vanish — whoever checks why a chest stays empty should read it
     * here.
     */
    public void holdBack(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        heldBack.add(stack);
        add(new dev.devpanda.factorynetwork.runtime.LogEntry(
                dev.devpanda.factorynetwork.runtime.LogLevel.WARN,
                System.currentTimeMillis(), "Speicher",
                stack.getCount() + "x " + stack.getHoverName().getString()
                        + " wird zurückgehalten: nichts nimmt es gerade an."));
    }

    /**
     * What the controller lets through per tick.
     *
     * <p><b>It is the weakest link, and deliberately so.</b> Everything in the
     * network goes through it — six solid cables on six sides together carried
     * 153 MB/s until 30.08., and it passed all of it through. Now it carries
     * as much as one solid cable, and each attachment adds half again.
     *
     * <p>At the limit everything gets slower, not individual things dead — the
     * same rule as at the cable. Two different answers to the same question
     * would be one too many.
     */
    public int bandwidth() {
        return dev.devpanda.factorynetwork.network.Bandwidth.ofController(extensionCount);
    }

    /** How many attachments hang on the controller — counted on rebuild. */
    private int extensionCount;

    /**
     * What has already gone through the controller in this tick.
     *
     * <p>It is its own node in the budget, and because every path runs through
     * it, the sum of the whole network stands here.
     */
    public int bandwidthUsed() {
        return runtime.budget().usedAt(
                new dev.devpanda.factorynetwork.network.FactoryGraph.Node(
                        worldPosition,
                        dev.devpanda.factorynetwork.block.CableColour.NONE));
    }

    /** How many attachments count — for the analyzer's readout. */
    public int extensionCount() {
        return extensionCount;
    }

    /** What currently lies in holding. */
    public List<ItemStack> held() {
        return List.copyOf(heldBack);
    }

    /**
     * Offers held-back items again.
     *
     * <p>Every tick, because the reason can fall away at any time. What goes
     * in goes in; the rest stays and is asked again next time.
     */
    private void retryHeldBack() {
        if (heldBack.isEmpty()) {
            return;
        }
        heldBack.removeIf(stack -> {
            long rest = storage.insert(stack);
            if (rest <= 0) {
                return true;
            }
            // Partially accommodated: only the rest stays.
            stack.setCount((int) rest);
            return false;
        });
        setChanged();
    }

    private void add(dev.devpanda.factorynetwork.runtime.LogEntry entry) {
        log.add(entry);
        while (log.size() > LOG_LIMIT) {
            log.remove(0);
        }
        setChanged();
    }

    /**
     * Who placed the controller, or {@code null}.
     *
     * <p>Needed only when the server demands it (see {@code FnConfig}, section
     * {@code protection}). It is remembered always regardless: whoever turns
     * the protection on only later would otherwise have nothing but ownerless
     * installations.
     */
    private java.util.UUID owner;

    public java.util.UUID owner() {
        return owner;
    }

    /** Set by the block on placement. */
    public void setOwner(java.util.UUID who) {
        this.owner = who;
        setChanged();
    }

    // ---- Saving -------------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID(KEY_OWNER) ? tag.getUUID(KEY_OWNER) : null;
        jobs.clear();
        nextJobId = Math.max(1, tag.getLong(KEY_NEXT_JOB));
        net.minecraft.nbt.ListTag jobList = tag.getList(KEY_JOBS,
                net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < jobList.size(); i++) {
            var job = dev.devpanda.factorynetwork.crafting.CraftingJob
                    .load(jobList.getCompound(i));
            if (job != null) {
                jobs.add(job);
            }
        }
        project = readProject(tag);
        // Without a draft of its own, the running state is the draft.
        if (tag.contains(KEY_DRAFT)) {
            CompoundTag entwurf = tag.getCompound(KEY_DRAFT);
            Map<String, String> sources = new HashMap<>();
            entwurf.getAllKeys().forEach(name -> sources.put(name, entwurf.getString(name)));
            draft = new dev.devpanda.factorynetwork.lang.Project(sources);
        } else {
            draft = project;
        }
        heldBack.clear();
        net.minecraft.nbt.ListTag heldTag = tag.getList(KEY_HELD_BACK,
                net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < heldTag.size(); i++) {
            ItemStack.parse(registries, heldTag.getCompound(i)).ifPresent(heldBack::add);
        }
        storage.load(tag.getCompound(KEY_STORAGE), registries);
        fluidStorage.load(tag.getCompound(KEY_FLUIDS), registries);
        power.load(tag.getCompound(KEY_POWER), registries);
        globals.clear();
        CompoundTag globalsTag = tag.getCompound(KEY_GLOBALS);
        for (String name : globalsTag.getAllKeys()) {
            globals.put(name, dev.devpanda.factorynetwork.runtime.flow.ValueCodec
                    .read(globalsTag.getCompound(name)));
        }
        log.clear();
        net.minecraft.nbt.ListTag logTag =
                tag.getList(KEY_LOG, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < logTag.size(); i++) {
            log.add(dev.devpanda.factorynetwork.runtime.LogEntry
                    .read(logTag.getCompound(i)));
        }
        // The flows wait for the first tick: they need an interpreter, which
        // needs a world, and that is not yet reliably available here.
        pendingFlows = tag.contains(KEY_FLOWS) ? tag.getCompound(KEY_FLOWS) : null;
        // After loading it is recompiled: the tree itself is not saved,
        // because the language can change while the source text stays valid.
        if (!source().isBlank()) {
            Parser.ParseResult result = project.parse();
            diagnostics = new ArrayList<>(result.diagnostics());
            if (!result.hasErrors()) {
                program = result.program();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID(KEY_OWNER, owner);
        }
        net.minecraft.nbt.ListTag jobList = new net.minecraft.nbt.ListTag();
        jobs.forEach(job -> jobList.add(job.save()));
        tag.put(KEY_JOBS, jobList);
        tag.putLong(KEY_NEXT_JOB, nextJobId);
        writeProject(tag);
        CompoundTag storageTag = new CompoundTag();
        storage.save(storageTag, registries);
        tag.put(KEY_STORAGE, storageTag);
        CompoundTag fluidTag = new CompoundTag();
        fluidStorage.save(fluidTag, registries);
        tag.put(KEY_FLUIDS, fluidTag);
        CompoundTag powerTag = new CompoundTag();
        power.save(powerTag, registries);
        tag.put(KEY_POWER, powerTag);
        CompoundTag globalsTag = new CompoundTag();
        globals.forEach((name, value) -> globalsTag.put(name,
                dev.devpanda.factorynetwork.runtime.flow.ValueCodec.write(value)));
        tag.put(KEY_GLOBALS, globalsTag);
        net.minecraft.nbt.ListTag logTag = new net.minecraft.nbt.ListTag();
        log.forEach(entry -> logTag.add(entry.write()));
        tag.put(KEY_LOG, logTag);
        if (!heldBack.isEmpty()) {
            // Without this the loss would come back through the back door: a
            // chunk that unloads would take the held-back items with it.
            net.minecraft.nbt.ListTag heldTag = new net.minecraft.nbt.ListTag();
            heldBack.forEach(stack -> heldTag.add(stack.save(registries)));
            tag.put(KEY_HELD_BACK, heldTag);
        }
        if (flows != null) {
            tag.put(KEY_FLOWS, FlowCodec.write(flows));
        } else if (pendingFlows != null) {
            // Not yet unpacked — then pass it on unchanged, instead of losing
            // the flows on the first save.
            tag.put(KEY_FLOWS, pendingFlows);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeProject(tag);
        return tag;
    }
}
