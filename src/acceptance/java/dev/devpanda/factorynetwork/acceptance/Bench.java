package dev.devpanda.factorynetwork.acceptance;

import com.google.gson.Gson;
import dev.devpanda.factorynetwork.block.RackBlock;
import dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.item.ServerPart;
import dev.devpanda.factorynetwork.item.ServerPartItem;
import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.network.Power;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnItems;
import dev.devpanda.factorynetwork.storage.CellTier;
import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Load measurements on a dedicated server: {@code /fnbench run}.
 *
 * <p>Each scenario builds a network far from spawn, measures what one
 * operation costs in isolation, and then lets the server tick for a while
 * with the network standing there. Tick times are taken around the whole
 * server tick and compared against a baseline taken before anything was
 * built, so the difference is what Factory Network costs.
 *
 * <p>Every result goes to the log as one line starting with
 * {@code FN_BENCH }, followed by JSON.
 */
final class Bench {

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Gson JSON = new Gson();

    /** Ticks per tick-time window. Two hundred ticks are ten seconds. */
    private static final int WINDOW = 200;
    /** Ticks to let a freshly built network settle before measuring. */
    private static final int SETTLE = 40;

    private static final List<ControllerBlockEntity> CONTROLLERS = new ArrayList<>();
    private static final List<Step> STEPS = new ArrayList<>();
    /** Runs every tick, measured or not: load that has to be there all along. */
    private static final List<Runnable> BACKGROUND = new ArrayList<>();
    private static long tickStart;
    private static double[] window;
    private static double[] fnWindow;
    private static int windowFill;
    private static long fnBefore;
    private static int wait;

    private Bench() {
    }

    /** One stage of the run; returns once its work for this tick is done. */
    private interface Step {
        void run(MinecraftServer server);
    }

    static boolean running() {
        return !STEPS.isEmpty();
    }

    static void start(ServerLevel level, int types, int workers, int cables) {
        STEPS.clear();
        CONTROLLERS.clear();
        BACKGROUND.clear();
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("types", types);
        results.put("workers", workers);
        results.put("cables", cables);

        // Load every chunk the run will touch before anything is measured,
        // and put the player next to the terminal-to-be: chunk loading in a
        // measured window would be counted as ours.
        STEPS.add(server -> {
            int perDrive = DriveBlockEntity.SLOTS * CellTier.K64.types();
            forceArea(level, STORAGE, (types + perDrive - 1) / perDrive + 4, 4);
            forceArea(level, WORKERS, workers * 2 + 4, 6);
            forceArea(level, CABLES, cables + 4, 2);
            forceArea(level, ME_LINK, 12, 3);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.teleportTo(level, STORAGE.getX() + 0.5, STORAGE.getY(), STORAGE.getZ() + 2.5,
                        java.util.Set.of(), 180, 0, true);
            }
            wait = WINDOW;
        });
        // Baseline: the server as it is, nothing of ours built yet.
        measure("baseline", results);

        // Storage: many cells, many types.
        STEPS.add(server -> storage(level, types, results));
        settle();
        measure("storageIdle", results);
        STEPS.add(server -> terminal(level, results));
        settle();
        measure("storageWithTerminalOpen", results);
        STEPS.add(server -> closeTerminal(level));

        // Workers taking from the big store into chests.
        STEPS.add(server -> storageWorkers(level, results));
        settle();
        measure("storageWorkersTicking", results);
        STEPS.add(server -> stopStorageWorkers(level));

        // Workers moving items from chest to chest.
        STEPS.add(server -> workers(level, workers, results));
        settle();
        STEPS.add(server -> countMoved(level, workers, results, "movedBefore"));
        measure("workersTicking", results);
        STEPS.add(server -> countMoved(level, workers, results, "movedAfter"));

        // A long cable network, rebuilt from scratch.
        // An ME network behind a storage link, if AE2 is there.
        if (net.neoforged.fml.ModList.get().isLoaded("ae2")) {
            STEPS.add(server -> meBuild(level));
            STEPS.add(server -> wait = WINDOW);
            STEPS.add(server -> meMeasure(100, results));
            STEPS.add(server -> meMeasure(4_000, results));
        }

        STEPS.add(server -> cables(level, cables, results));
        settle();
        measure("cablesIdle", results);

        STEPS.add(server -> {
            LOGGER.info("FN_BENCH {}", JSON.toJson(results));
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("FN_BENCH_DONE"), false);
        });
    }

    // ---- Tick loop ----------------------------------------------------------

    static void preTick(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
        fnBefore = dev.devpanda.factorynetwork.network.TickCost.nanos();
    }

    static void postTick(ServerTickEvent.Post event) {
        double millis = (System.nanoTime() - tickStart) / 1_000_000.0;
        for (ControllerBlockEntity controller : CONTROLLERS) {
            if (!controller.isRemoved()) {
                controller.power().fill(Power.CAPACITY);
            }
        }
        BACKGROUND.forEach(Runnable::run);
        if (window != null && windowFill < window.length) {
            fnWindow[windowFill] = (dev.devpanda.factorynetwork.network.TickCost.nanos() - fnBefore) / 1_000_000.0;
            window[windowFill++] = millis;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (!STEPS.isEmpty()) {
            STEPS.removeFirst().run(event.getServer());
        }
    }

    private static void settle() {
        STEPS.add(server -> wait = SETTLE);
    }

    private static void measure(String name, Map<String, Object> results) {
        STEPS.add(server -> {
            window = new double[WINDOW];
            fnWindow = new double[WINDOW];
            windowFill = 0;
            dev.devpanda.factorynetwork.network.TickCost.reset();
        });
        STEPS.add(server -> {
            double[] sorted = window.clone();
            Arrays.sort(sorted);
            double sum = 0;
            for (double value : sorted) {
                sum += value;
            }
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("avgMs", round(sum / sorted.length));
            stats.put("p95Ms", round(sorted[(int) (sorted.length * 0.95)]));
            stats.put("maxMs", round(sorted[sorted.length - 1]));
            // The controllers' own share, measured around their ticks.
            double[] fn = fnWindow.clone();
            Arrays.sort(fn);
            double fnSum = 0;
            for (double value : fn) {
                fnSum += value;
            }
            stats.put("fnAvgMs", round(fnSum / fn.length));
            stats.put("fnP95Ms", round(fn[(int) (fn.length * 0.95)]));
            stats.put("fnMaxMs", round(fn[fn.length - 1]));
            stats.put("slowestControllerMs", round(dev.devpanda.factorynetwork.network.TickCost.slowest() / 1e6));
            stats.put("slowestControllerAt", String.valueOf(dev.devpanda.factorynetwork.network.TickCost.slowestAt()));
            // Which phase and which worker made the slowest ticks.
            Map<String, Object> phases = new LinkedHashMap<>();
            dev.devpanda.factorynetwork.network.TickCost.slowestPhases()
                    .forEach((phase, nanos) -> phases.put(phase, round(nanos / 1e6)));
            stats.put("slowestPhaseMs", phases);
            stats.put("slowestWorkerMs", round(dev.devpanda.factorynetwork.network.TickCost.slowestWorker() / 1e6));
            stats.put("slowestWorker", String.valueOf(dev.devpanda.factorynetwork.network.TickCost.slowestWorkerName()));
            results.put(name, stats);
            window = null;
            LOGGER.info("FN_BENCH_STEP {} {}", name, JSON.toJson(stats));
        });
    }

    // ---- Scenarios ----------------------------------------------------------

    // On top of a superflat world, whose grass lies at -61.
    private static final BlockPos STORAGE = new BlockPos(100, -60, 0);
    private static final BlockPos WORKERS = new BlockPos(100, -60, 40);
    private static final BlockPos CABLES = new BlockPos(100, -60, 80);
    private static final BlockPos ME_LINK = new BlockPos(100, -60, 120);
    /** Seven drives: an ad-hoc ME network has eight channels, the link takes one. */
    private static final int ME_DRIVES = 7;
    private static dev.devpanda.factorynetwork.block.entity.StorageLinkBlockEntity meLink;
    private static int meFilled;

    private static void storage(ServerLevel level, int types, Map<String, Object> results) {
        int perDrive = DriveBlockEntity.SLOTS * CellTier.K64.types();
        int drives = (types + perDrive - 1) / perDrive;
        ControllerBlockEntity controller = core(level, STORAGE);
        ItemStack cell = new ItemStack(FnItems.CELLS.get(CellTier.K64).get());
        for (int i = 1; i <= drives; i++) {
            BlockPos cable = STORAGE.east(i);
            level.setBlockAndUpdate(cable, FnBlocks.CABLE.get().defaultBlockState());
            BlockPos drivePos = cable.above();
            level.setBlockAndUpdate(drivePos, FnBlocks.DRIVE.get().defaultBlockState());
            DriveBlockEntity drive = (DriveBlockEntity) level.getBlockEntity(drivePos);
            for (int slot = 0; slot < DriveBlockEntity.SLOTS; slot++) {
                drive.setCell(slot, cell.copy());
            }
        }
        controller.rebuildNetwork();

        List<ItemKey> keys = new ArrayList<>(types);
        for (int i = 0; i < types; i++) {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("bench " + i));
            keys.add(ItemKey.of(stack));
        }
        long start = System.nanoTime();
        long left = 0;
        for (ItemKey key : keys) {
            left += controller.storage().insert(key, 1_000);
        }
        results.put("fillMs", round((System.nanoTime() - start) / 1e6));
        results.put("fillRejected", left);
        // One plain sort behind all the named papers: what a worker filtering
        // on iron has to find.
        results.put("ironRejected", controller.storage().insert(ItemKey.bare(Items.IRON_INGOT), 100));
        results.put("storedTypes", controller.storage().contents().size());

        Random random = new Random(1);
        int rounds = 10_000;
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            controller.storage().insert(keys.get(random.nextInt(types)), 1);
        }
        results.put("insertUs", round((System.nanoTime() - start) / 1e3 / rounds));
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            controller.storage().extract(keys.get(random.nextInt(types)), 1);
        }
        results.put("extractUs", round((System.nanoTime() - start) / 1e3 / rounds));
        start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            controller.storage().contents();
        }
        results.put("contentsMs", round((System.nanoTime() - start) / 1e6 / 20));
        // How well the keys spread in a hash map: few distinct hashes would
        // make every map of the stock slow.
        results.put("distinctKeyHashes", keys.stream().mapToInt(ItemKey::hashCode).distinct().count());
        start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            new java.util.ArrayList<>(controller.storage().contents().keySet());
        }
        long copyAndList = System.nanoTime() - start;
        var once = controller.storage().contents();
        start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            new java.util.ArrayList<>(once.keySet());
        }
        results.put("listOnlyMs", round((System.nanoTime() - start) / 1e6 / 20));
        results.put("contentsAgainMs", round(copyAndList / 1e6 / 20));
        start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            controller.storage().distinctTypes();
        }
        results.put("indexOnlyMs", round((System.nanoTime() - start) / 1e6 / 20));
        // A network rebuild drops the stock index; the next question builds
        // it again from all cells.
        start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            controller.rebuildNetwork();
            controller.storage().distinctTypes();
        }
        results.put("rebuildWithIndexMs", round((System.nanoTime() - start) / 1e6 / 5));
        start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            controller.rebuildNetwork();
        }
        results.put("rebuildAloneMs", round((System.nanoTime() - start) / 1e6 / 5));
        // And the first question by item after it, which groups the index.
        start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            controller.rebuildNetwork();
            controller.storage().count(Items.PAPER);
        }
        results.put("rebuildWithCountMs", round((System.nanoTime() - start) / 1e6 / 5));
        // The two parts on their own: building the index, then the first
        // questions by item after it.
        long indexNanos = 0;
        long countNanos = 0;
        long firstNanos = 0;
        for (int i = 0; i < 10; i++) {
            controller.rebuildNetwork();
            long t0 = System.nanoTime();
            controller.storage().distinctTypes();
            long t1 = System.nanoTime();
            controller.storage().count(Items.PAPER);
            long t2 = System.nanoTime();
            controller.storage().firstPresent(List.of(Items.IRON_INGOT));
            long t3 = System.nanoTime();
            indexNanos += t1 - t0;
            countNanos += t2 - t1;
            firstNanos += t3 - t2;
        }
        results.put("indexBuildMs", round(indexNanos / 1e6 / 10));
        results.put("firstCountAfterRebuildMs", round(countNanos / 1e6 / 10));
        results.put("firstIronAfterRebuildMs", round(firstNanos / 1e6 / 10));
        results.put("firstIronFound", String.valueOf(controller.storage().firstPresent(List.of(Items.IRON_INGOT))));
        results.put("typesAfterRebuild", controller.storage().distinctTypes());
        results.put("drivesBuilt", drives);
        results.put("drivesInGraph", controller.graph().drives().size());
        results.put("graphTruncated", controller.graph().isTruncated());
        controller.storage().distinctTypes();
        start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            new java.util.LinkedHashMap<>(once);
        }
        results.put("plainCopyMs", round((System.nanoTime() - start) / 1e6 / 20));
        // By item across all variants, as a condition or display asks: one
        // with every sort a variant of it, one with a single bare sort.
        start = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            controller.storage().count(Items.PAPER);
        }
        results.put("countAllPaperUs", round((System.nanoTime() - start) / 1e3 / 1_000));
        start = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            controller.storage().count(Items.IRON_INGOT);
        }
        results.put("countIronUs", round((System.nanoTime() - start) / 1e3 / 1_000));
        // The first present sort for a worker from storage, filtered on iron:
        // as a copy of the whole stock (the way it was), and from the store.
        List<net.minecraft.world.item.Item> iron = List.of(Items.IRON_INGOT);
        start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            controller.storage().contents().keySet().stream()
                    .filter(key -> iron.contains(key.item()))
                    .filter(key -> controller.storage().count(key) > 0)
                    .findFirst();
        }
        results.put("firstIronByCopyUs", round((System.nanoTime() - start) / 1e3 / 100));
        start = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            controller.storage().firstPresent(iron);
        }
        results.put("firstIronUs", round((System.nanoTime() - start) / 1e3 / 1_000));
        controller.setChanged();
    }

    private static void terminal(ServerLevel level, Map<String, Object> results) {
        ControllerBlockEntity controller = (ControllerBlockEntity) level.getBlockEntity(STORAGE);
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        BlockPos terminal = STORAGE.south();
        level.setBlockAndUpdate(terminal, FnBlocks.TERMINAL.get().defaultBlockState());
        controller.rebuildNetwork();
        ServerPlayer player;
        if (players.isEmpty()) {
            // Headless: a stand-in whose connection drops every packet. That
            // times what the send costs the server thread; encoding runs on
            // the network thread for a real player anyway.
            player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.fromString(
                            "6b3c1d0e-5f1a-4c55-9d1e-0b8f3a2c7e11"), "BenchViewer"));
            player.setPos(terminal.getX() + 0.5, terminal.getY(), terminal.getZ() + 1.5);
            player.containerMenu = new TerminalMenu(1, player.getInventory(), terminal);
            results.put("terminalViewer", "fake player");
        } else {
            player = players.getFirst();
            player.openMenu((net.minecraft.world.MenuProvider) level.getBlockEntity(terminal),
                    buffer -> TerminalMenu.writeBlock(buffer, terminal));
            results.put("terminalViewer", "client");
        }
        terminalViewer = player;
        // Placing the terminal rebuilt the network and with it the stock
        // index; opening a terminal in the game does not. Rebuild it first so
        // the first send is timed on its own.
        controller.storage().distinctTypes();
        long start = System.nanoTime();
        controller.watchStorage(player);
        results.put("firstPushMs", round((System.nanoTime() - start) / 1e6));
        start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            controller.pushStorageTo(player, true);
        }
        results.put("snapshotPushMs", round((System.nanoTime() - start) / 1e6 / 20));
        // The usual case while a terminal is open: one sort changed.
        ItemKey changed = ItemKey.bare(Items.STONE);
        start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            controller.storage().insert(changed, 1);
            controller.pushStorageTo(player, false);
        }
        results.put("deltaPushMs", round((System.nanoTime() - start) / 1e6 / 20));
        // Split up: the store change alone, and the stock list after it.
        start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            controller.storage().insert(changed, 1);
        }
        results.put("changeOnlyMs", round((System.nanoTime() - start) / 1e6 / 20));
        start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            controller.storage().insert(changed, 1);
            controller.storage().contents();
        }
        results.put("changeAndContentsMs", round((System.nanoTime() - start) / 1e6 / 20));
        start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            controller.storage().freeTypes();
        }
        results.put("freeTypesMs", round((System.nanoTime() - start) / 1e6 / 20));
        // Keep the network changing while the terminal is open: that is when
        // the server resends the stock.
        // Report if the menu closes on its own while the terminal phase runs.
        boolean[] reported = {false};
        BACKGROUND.add(() -> {
            if (!reported[0] && !(player.containerMenu instanceof TerminalMenu)) {
                reported[0] = true;
                LOGGER.info("FN_BENCH_TERMINAL_CLOSED pos={} distance={} menu={}", player.blockPosition(),
                        Math.sqrt(player.distanceToSqr(terminal.getCenter())),
                        player.containerMenu.getClass().getSimpleName());
            }
        });
        ItemKey churn = ItemKey.bare(Items.COBBLESTONE);
        BACKGROUND.add(() -> {
            controller.storage().insert(churn, 1);
            controller.markStorageDirty();
        });
    }

    private static ServerPlayer terminalViewer;

    private static void closeTerminal(ServerLevel level) {
        BACKGROUND.clear();
        if (terminalViewer instanceof net.neoforged.neoforge.common.util.FakePlayer fake) {
            fake.containerMenu = fake.inventoryMenu;
            ((ControllerBlockEntity) level.getBlockEntity(STORAGE)).unwatchStorage(fake);
        }
        terminalViewer = null;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.closeContainer();
        }
    }

    private static void workers(ServerLevel level, int count, Map<String, Object> results) {
        ControllerBlockEntity controller = core(level, WORKERS);
        StringBuilder program = new StringBuilder();
        for (int i = 0; i < count; i++) {
            BlockPos cable = WORKERS.east(1 + i * 2);
            level.setBlockAndUpdate(cable, FnBlocks.CABLE.get().defaultBlockState());
            level.setBlockAndUpdate(cable.east(), FnBlocks.CABLE.get().defaultBlockState());
            BlockPos source = cable.north();
            BlockPos target = cable.south();
            level.setBlockAndUpdate(source, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(target, Blocks.CHEST.defaultBlockState());
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(source);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                chest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            CableBusBlockEntity bus = (CableBusBlockEntity) level.getBlockEntity(cable);
            bus.addPart(Direction.NORTH).setLabel("s" + i);
            bus.addPart(Direction.SOUTH).setLabel("t" + i);
            program.append("worker w").append(i).append(" {\n    from s").append(i)
                    .append("\n    to t").append(i).append("\n}\n");
        }
        controller.rebuildNetwork();
        boolean deployed = controller.deploy(Project.of(program.toString()));
        results.put("workersDeployed", deployed);
        if (!deployed) {
            results.put("workersDiagnostics", String.valueOf(controller.diagnostics()));
        }
    }

    /** Eight workers from the store into chests: four by a sort with thousands of variants, four by a plain one. */
    private static void storageWorkers(ServerLevel level, Map<String, Object> results) {
        ControllerBlockEntity controller = (ControllerBlockEntity) level.getBlockEntity(STORAGE);
        StringBuilder program = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            BlockPos cable = STORAGE.east(1 + i).south();
            level.setBlockAndUpdate(cable, FnBlocks.CABLE.get().defaultBlockState());
            level.setBlockAndUpdate(cable.south(), Blocks.CHEST.defaultBlockState());
            CableBusBlockEntity bus = (CableBusBlockEntity) level.getBlockEntity(cable);
            bus.addPart(Direction.SOUTH).setLabel("k" + i);
            program.append("worker k").append(i).append(" {\n    from storage\n    to k").append(i)
                    .append("\n    filter item:").append(i < 4 ? "paper" : "iron_ingot").append("\n}\n");
        }
        controller.rebuildNetwork();
        controller.storage().insert(ItemKey.bare(Items.IRON_INGOT), 1_000_000);
        boolean deployed = controller.deploy(Project.of(program.toString()));
        results.put("storageWorkersDeployed", deployed);
        if (!deployed) {
            results.put("storageWorkersDiagnostics", String.valueOf(controller.diagnostics()));
        }
        // Empty the chests every tick, so the workers keep moving.
        BACKGROUND.add(() -> {
            for (int i = 0; i < 8; i++) {
                if (level.getBlockEntity(STORAGE.east(1 + i).south(2)) instanceof ChestBlockEntity chest) {
                    chest.clearContent();
                }
            }
        });
    }

    private static void stopStorageWorkers(ServerLevel level) {
        BACKGROUND.clear();
        ((ControllerBlockEntity) level.getBlockEntity(STORAGE)).deploy(Project.of(""));
    }

    private static void countMoved(ServerLevel level, int count, Map<String, Object> results, String key) {
        long moved = 0;
        for (int i = 0; i < count; i++) {
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(
                    WORKERS.east(1 + i * 2).south());
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                moved += chest.getItem(slot).getCount();
            }
        }
        results.put(key, moved);
    }

    private static void meBuild(ServerLevel level) {
        var registry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        java.util.function.Function<String, net.minecraft.world.level.block.Block> ae2 =
                path -> registry.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("ae2", path));
        level.setBlockAndUpdate(ME_LINK, FnBlocks.STORAGE_LINK.get().defaultBlockState());
        level.setBlockAndUpdate(ME_LINK.north(), ae2.apply("creative_energy_cell").defaultBlockState());
        ItemStack cell = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("ae2", "item_storage_cell_256k")));
        for (int i = 1; i <= ME_DRIVES; i++) {
            BlockPos pos = ME_LINK.east(i);
            level.setBlockAndUpdate(pos, ae2.apply("drive").defaultBlockState());
            var handler = dev.devpanda.factorynetwork.platform.Caps.items(level, pos, null);
            for (int slot = 0; slot < 10 && handler != null; slot++) {
                handler.insertItem(slot, cell.copy(), false);
            }
        }
        meLink = (dev.devpanda.factorynetwork.block.entity.StorageLinkBlockEntity) level.getBlockEntity(ME_LINK);
        meFilled = 0;
    }

    /** Fills the ME network up to {@code types} sorts, then times access by sort. */
    private static void meMeasure(int types, Map<String, Object> results) {
        var adapter = meLink == null ? null : meLink.adapter();
        if (adapter == null || adapter.items() == null) {
            results.put("me" + types, "link not attached");
            return;
        }
        var stock = adapter.items();
        for (int i = meFilled; i < types; i++) {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("me " + i));
            stock.insert(ItemKey.of(stack), 100, false);
        }
        meFilled = Math.max(meFilled, types);
        long[] sorts = {0};
        stock.find(List.of(), (key, amount) -> sorts[0]++);
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("sortsInMe", sorts[0]);
        // One worker step as the runtime does it: narrow the view to the
        // filter, take a stack, put it back.
        var view = meLink.itemView();
        int rounds = 2_000;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            var narrowed = dev.devpanda.factorynetwork.network.link.KeyedItems.narrow(view, List.of(Items.PAPER));
            ItemStack taken = narrowed.extractItem(0, 1, false);
            narrowed.insertItem(0, taken, false);
        }
        me.put("narrowFindAllPaperUs", round((System.nanoTime() - start) / 1e3 / rounds));
        var bare = ItemKey.bare(Items.IRON_INGOT);
        stock.insert(bare, 10_000, false);
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            var narrowed = dev.devpanda.factorynetwork.network.link.KeyedItems.narrow(view, List.of(Items.IRON_INGOT));
            ItemStack taken = narrowed.extractItem(0, 64, false);
            narrowed.insertItem(0, taken, false);
        }
        me.put("narrowOneSortUs", round((System.nanoTime() - start) / 1e3 / rounds));
        results.put("me" + types, me);
        LOGGER.info("FN_BENCH_STEP me{} {}", types, JSON.toJson(me));
    }

    private static void cables(ServerLevel level, int count, Map<String, Object> results) {
        ControllerBlockEntity controller = core(level, CABLES);
        for (int i = 1; i <= count; i++) {
            level.setBlock(CABLES.east(i), FnBlocks.CABLE.get().defaultBlockState(), 2);
        }
        long start = System.nanoTime();
        int rounds = 10;
        for (int i = 0; i < rounds; i++) {
            controller.rebuildNetwork();
        }
        results.put("rebuildMs", round((System.nanoTime() - start) / 1e6 / rounds));
        results.put("cableEdges", controller.graph().edges().size());
        // What every placed or broken network block costs: each controller in
        // the level is asked whether the spot touches its network. A spot
        // next to none of them is the case that looks at everything.
        BlockPos nowhere = CABLES.above(10);
        start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            dev.devpanda.factorynetwork.network.ControllerRegistry.markChanged(level, nowhere);
        }
        results.put("markChangedUs", round((System.nanoTime() - start) / 1e3 / 10_000));
        ControllerBlockEntity workers = (ControllerBlockEntity) level.getBlockEntity(WORKERS);
        start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            workers.graph().contains(nowhere);
        }
        results.put("containsWorkersNetUs", round((System.nanoTime() - start) / 1e3 / 10_000));
    }

    // ---- Building blocks ----------------------------------------------------

    /** Controller with a fully equipped rack, powered and booted. */
    private static ControllerBlockEntity core(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, FnBlocks.CONTROLLER.get().defaultBlockState());
        BlockPos rackPos = pos.west();
        level.setBlock(rackPos, FnBlocks.RACK.get().defaultBlockState(), 2);
        level.setBlockAndUpdate(rackPos.above(), FnBlocks.RACK.get().defaultBlockState()
                .setValue(RackBlock.HALF, DoubleBlockHalf.UPPER));
        RackBlockEntity rack = (RackBlockEntity) level.getBlockEntity(rackPos);
        for (int bay = 0; bay < RackBlockEntity.BAYS; bay++) {
            rack.setItem(RackBlockEntity.chassisSlot(bay), new ItemStack(FnItems.SERVER_CHASSIS.get()));
            for (ServerPart kind : ServerPart.values()) {
                var tiers = FnItems.SERVER_PARTS.get(kind);
                rack.setItem(RackBlockEntity.slotOf(bay, kind), new ItemStack(tiers.getLast().get()));
            }
        }
        ControllerBlockEntity controller = (ControllerBlockEntity) level.getBlockEntity(pos);
        controller.rebuildNetwork();
        controller.power().fill(Power.CAPACITY);
        controller.power().setDraw(controller.powerDraw());
        for (int tick = 0; tick <= Power.BOOT_TICKS; tick++) {
            controller.power().tick();
        }
        CONTROLLERS.add(controller);
        return controller;
    }

    private static void forceArea(ServerLevel level, BlockPos origin, int lengthX, int widthZ) {
        ChunkPos from = ChunkPos.containing(origin.offset(-2, 0, -widthZ));
        ChunkPos to = ChunkPos.containing(origin.offset(lengthX, 0, widthZ));
        for (int x = from.x(); x <= to.x(); x++) {
            for (int z = from.z(); z <= to.z(); z++) {
                level.setChunkForced(x, z, true);
            }
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
