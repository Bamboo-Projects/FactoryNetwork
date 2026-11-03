package dev.devpanda.factorynetwork.acceptance;

import com.google.gson.Gson;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.RackBlock;
import dev.devpanda.factorynetwork.block.entity.*;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.item.ServerPart;
import dev.devpanda.factorynetwork.item.ServerPartItem;
import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.network.Power;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnItems;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;

/** Test companion only: real blocks, real menus, real packets; never in the product jar. */
@Mod(value = "factorynetwork_acceptance", dist = net.neoforged.api.distmarker.Dist.DEDICATED_SERVER)
public final class AcceptanceMod {
    private static final BlockPos CONTROLLER = new BlockPos(8, 64, 8);
    private static final BlockPos TERMINAL = CONTROLLER.south();
    private static final BlockPos SOURCE = new BlockPos(9, 64, 6);
    private static final BlockPos TARGET = new BlockPos(9, 64, 10);
    private static final Gson JSON = new Gson();

    public AcceptanceMod() {
        NeoForge.EVENT_BUS.addListener(AcceptanceMod::commands);
        NeoForge.EVENT_BUS.addListener(AcceptanceMod::power);
        if (Boolean.getBoolean("fn.acceptance.hall")) {
            NeoForge.EVENT_BUS.addListener(AcceptanceMod::hallForHall);
        }
        if (Boolean.getBoolean("fn.acceptance.slots")) {
            NeoForge.EVENT_BUS.addListener(AcceptanceMod::furnaceForSlots);
        }
        NeoForge.EVENT_BUS.addListener(Bench::preTick);
        NeoForge.EVENT_BUS.addListener(Bench::postTick);
    }

    private static void commands(RegisterCommandsEvent event) {
        // /fnprobe: which blocks with a block entity offer what to a connector.
        event.getDispatcher().register(Commands.literal("fnprobe")
                .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .executes(ctx -> {
                    Probe.run(ctx.getSource().getLevel());
                    ctx.getSource().sendSuccess(() -> Component.literal("FN_PROBE_DONE"), false);
                    return 1;
                }));
        // /fnbench run [types] [workers] [cables]
        event.getDispatcher().register(Commands.literal("fnbench")
                .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .then(Commands.literal("run")
                        .executes(ctx -> bench(ctx.getSource(), 5_000, 64, 4_000))
                        .then(Commands.argument("types", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .then(Commands.argument("workers", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                        .then(Commands.argument("cables", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                .executes(ctx -> bench(ctx.getSource(),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "types"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "workers"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "cables"))))))));
        // /fnhall: a stand-in player walks into a hall and out again.
        event.getDispatcher().register(Commands.literal("fnhall")
                .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(hallRoundTrip(ctx.getSource().getLevel())), false);
                    return 1;
                })
                .then(Commands.literal("link").then(Commands.argument("pos",
                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(ctx -> {
                    BlockPos at = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                    var link = (dev.devpanda.factorynetwork.block.entity.StorageLinkBlockEntity) ctx.getSource().getLevel().getBlockEntity(at);
                    var adapter = link.adapter();
                    var items = adapter == null ? null : adapter.items();
                    var iron = dev.devpanda.factorynetwork.storage.ItemKey.bare(net.minecraft.world.item.Items.IRON_INGOT);
                    String text = "FN_LINK adapter=" + (adapter == null ? "none" : adapter.systemName())
                            + " items=" + (items == null ? "none" : "room " + items.insert(iron, 64, true))
                            + " view=" + link.itemView();
                    ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                    return 1;
                })))
                .then(Commands.literal("couple").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(hallCouple(ctx.getSource().getLevel())), false);
                    return 1;
                }))
                .then(Commands.literal("loaded").then(Commands.argument("id",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(ctx -> {
                    // Asks without touching the door's chunk: what the server keeps loaded by itself.
                    var server = ctx.getSource().getServer();
                    var hall = server.getLevel(dev.devpanda.factorynetwork.hall.Halls.DIMENSION);
                    int id = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id");
                    BlockPos low = dev.devpanda.factorynetwork.hall.Halls.origin(id);
                    var entry = dev.devpanda.factorynetwork.hall.Halls.of(server).get(id);
                    ctx.getSource().sendSuccess(() -> Component.literal("FN_HALL_LOADED " + hall.isLoaded(low)
                            + " ticking=" + hall.shouldTickBlocksAt(low)
                            + " door=" + (entry == null ? "none" : entry.door().map(Object::toString).orElse("empty"))
                            + " doorChunk=" + server.overworld().isLoaded(HALL_DOOR)), false);
                    return 1;
                }))));
        event.getDispatcher().register(Commands.literal("fnacceptance")
                .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .then(Commands.literal("setup").executes(ctx -> {
                    setup(ctx.getSource().getLevel());
                    ctx.getSource().sendSuccess(() -> Component.literal("FN_ACCEPTANCE_SETUP_OK"), false);
                    return 1;
                }))
                // /fnacceptance deploy x y z: the controller there takes the
                // program from deploy.fn in the server's folder.
                .then(Commands.literal("deploy").then(Commands.argument("pos",
                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(ctx -> {
                    BlockPos at = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                    if (!(ctx.getSource().getLevel().getBlockEntity(at) instanceof ControllerBlockEntity controller)) {
                        ctx.getSource().sendFailure(Component.literal("FN_DEPLOY no controller at " + at));
                        return 0;
                    }
                    try {
                        String code = java.nio.file.Files.readString(java.nio.file.Path.of("deploy.fn"));
                        boolean ok = controller.deploy(Project.of(code));
                        ctx.getSource().sendSuccess(() -> Component.literal("FN_DEPLOY " + (ok ? "ok" : "failed: " + controller.diagnostics())), false);
                        return ok ? 1 : 0;
                    } catch (java.io.IOException e) {
                        ctx.getSource().sendFailure(Component.literal("FN_DEPLOY " + e));
                        return 0;
                    }
                })))
                .then(Commands.literal("snapshot").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(snapshot(ctx.getSource().getLevel())), false);
                    return 1;
                }))
                .then(Commands.literal("saveBrokenDraft").executes(ctx -> {
                    var level = ctx.getSource().getLevel();
                    var controller = (ControllerBlockEntity) level.getBlockEntity(CONTROLLER);
                    Project running = controller.project();
                    Project broken = Project.of("// acceptance: keep this draft after restart\nworker unfinished {\n    from source\n");
                    var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                            new com.mojang.authlib.GameProfile(java.util.UUID.fromString(
                                    "a5416312-270c-40bf-a1e3-1711d6aac483"), "AcceptanceWriter"));
                    player.setPos(8.5, 64, 12.5);
                    player.containerMenu = new TerminalMenu(1, player.getInventory(), TERMINAL);
                    try {
                        dev.devpanda.factorynetwork.network.packet.SaveDraftPacket.handle(
                                dev.devpanda.factorynetwork.network.packet.SaveDraftPacket.of(CONTROLLER, broken, 1),
                                new net.neoforged.neoforge.network.handling.ServerPayloadContext(player.connection,
                                        dev.devpanda.factorynetwork.network.packet.SaveDraftPacket.TYPE.id()));
                        dev.devpanda.factorynetwork.network.packet.DeployProgramPacket.handle(
                                dev.devpanda.factorynetwork.network.packet.DeployProgramPacket.of(TERMINAL, broken),
                                new net.neoforged.neoforge.network.handling.ServerPayloadContext(player.connection,
                                        dev.devpanda.factorynetwork.network.packet.DeployProgramPacket.TYPE.id()));
                        if (!running.equals(controller.project()) || !broken.equals(controller.draft()))
                            throw new IllegalStateException("Rejected deployment damaged running project or draft");
                    } finally {
                        player.containerMenu.removed(player);
                        player.containerMenu = player.inventoryMenu;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("FN_ACCEPTANCE_BROKEN_DRAFT_SAVED_RUNNING_UNCHANGED"), false);
                    return 1;
                }))
                .then(Commands.literal("refill").executes(ctx -> {
                    ((ChestBlockEntity) ctx.getSource().getLevel().getBlockEntity(SOURCE))
                            .setItem(0, new ItemStack(Items.IRON_INGOT, 64));
                    return 1;
                }))
                .then(Commands.literal("open").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            var player = EntityArgument.getPlayer(ctx, "player");
                            var level = ctx.getSource().getLevel();
                            player.teleportTo(level, 8.5, 64, 12.5, java.util.Set.of(), 180, 10, true);
                            var terminal = (TerminalBlockEntity) level.getBlockEntity(TERMINAL);
                            terminal.sendStateTo(player);
                            player.openMenu(terminal, buffer -> TerminalMenu.writeBlock(buffer, TERMINAL));
                            return 1;
                        }))));
    }

    private static int bench(net.minecraft.commands.CommandSourceStack source, int types, int workers, int cables) {
        if (Bench.running()) {
            source.sendFailure(Component.literal("FN_BENCH_BUSY"));
            return 0;
        }
        Bench.start(source.getLevel(), types, workers, cables);
        source.sendSuccess(() -> Component.literal("FN_BENCH_STARTED"), false);
        return 1;
    }

    private static void setup(ServerLevel level) {
        // Refuse to overwrite an earlier acceptance world: restart checks must retain it.
        if (level.getBlockEntity(CONTROLLER) instanceof ControllerBlockEntity)
            throw new IllegalStateException("Fixture already exists; use snapshot/open/refill.");
        level.setChunkForced(0, 0, true);
        for (int x = 5; x <= 12; x++) for (int z = 4; z <= 13; z++)
            level.setBlockAndUpdate(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(CONTROLLER, FnBlocks.CONTROLLER.get().defaultBlockState());
        var rackPos = CONTROLLER.west();
        level.setBlock(rackPos, FnBlocks.RACK.get().defaultBlockState(), 2);
        level.setBlockAndUpdate(rackPos.above(), FnBlocks.RACK.get().defaultBlockState()
                .setValue(RackBlock.HALF, DoubleBlockHalf.UPPER));
        var rack = (RackBlockEntity) level.getBlockEntity(rackPos);
        rack.setItem(RackBlockEntity.chassisSlot(0), new ItemStack(FnItems.SERVER_CHASSIS.get()));
        for (var kind : ServerPart.values()) {
            int value = switch (kind) { case CPU -> 32; case RAM -> 128; case DISK -> 4096; };
            var item = FnItems.SERVER_PARTS.get(kind).stream()
                    .filter(part -> ((ServerPartItem) part.get()).value() == value).findFirst().orElseThrow();
            rack.setItem(RackBlockEntity.slotOf(0, kind), new ItemStack(item.get()));
        }
        level.setBlockAndUpdate(CONTROLLER.east(), FnBlocks.CABLE.get().defaultBlockState());
        connector(level, CONTROLLER.east().north(), Direction.NORTH, "source");
        connector(level, CONTROLLER.east().south(), Direction.SOUTH, "target");
        level.setBlockAndUpdate(SOURCE, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(TARGET, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(TERMINAL, FnBlocks.TERMINAL.get().defaultBlockState());
        ((ChestBlockEntity) level.getBlockEntity(SOURCE)).setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        var controller = (ControllerBlockEntity) level.getBlockEntity(CONTROLLER);
        controller.rebuildNetwork();
        controller.power().fill(Power.CAPACITY);
        controller.power().setDraw(controller.powerDraw());
        for (int tick = 0; tick <= Power.BOOT_TICKS; tick++) controller.power().tick();
        if (!controller.deploy(Project.of("worker transfer {\n    from source\n    to target\n}\n")))
            throw new IllegalStateException("Fixture did not compile: " + controller.diagnostics());
        level.setRespawnData(new net.minecraft.world.level.storage.LevelData.RespawnData(
                net.minecraft.core.GlobalPos.of(level.dimension(), new BlockPos(8, 64, 12)), 180, 0));
    }

    private static void connector(ServerLevel level, BlockPos pos, Direction facing, String name) {
        level.setBlockAndUpdate(pos, FnBlocks.CABLE.get().defaultBlockState());
        ((CableBusBlockEntity) level.getBlockEntity(pos)).addPart(facing).setLabel(name);
    }

    private static void power(ServerTickEvent.Post event) {
        var level = event.getServer().overworld();
        if (level.getBlockEntity(CONTROLLER) instanceof ControllerBlockEntity controller)
            controller.power().fill(Power.CAPACITY);
    }

    private static String snapshot(ServerLevel level) {
        var controller = (ControllerBlockEntity) level.getBlockEntity(CONTROLLER);
        var result = new LinkedHashMap<String, Object>();
        result.put("project", controller.project().files());
        result.put("draft", controller.draft().files());
        result.put("online", controller.isOnline());
        result.put("source", count(level, SOURCE));
        result.put("target", count(level, TARGET));
        result.put("workers", controller.runtime().states());
        result.put("bamboocefLoaded", net.neoforged.fml.ModList.get().isLoaded("bamboocef"));
        return "FN_ACCEPTANCE_SNAPSHOT " + JSON.toJson(result);
    }

    private static int count(ServerLevel level, BlockPos pos) {
        var chest = (ChestBlockEntity) level.getBlockEntity(pos);
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) count += chest.getItem(slot).getCount();
        return count;
    }

    /** For the slot-number check: a filled furnace two blocks east of whoever joins. */
    private static void furnaceForSlots(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        var player = event.getEntity();
        BlockPos furnace = player.blockPosition().east(2);
        var level = player.level();
        level.setBlockAndUpdate(furnace, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
        if (level.getBlockEntity(furnace) instanceof net.minecraft.world.Container container) {
            container.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_ORE, 5));
            container.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 3));
        }
    }

    /** For the hall check: a small hall two blocks east of whoever joins. */
    private static void hallForHall(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        var player = event.getEntity();
        player.level().setBlockAndUpdate(player.blockPosition().east(2),
                dev.devpanda.factorynetwork.registry.FnBlocks.HALLS
                        .get(dev.devpanda.factorynetwork.hall.HallTier.SMALL).get().defaultBlockState());
    }

    /** Far from spawn, so nothing but the hall block itself keeps its chunk loaded. */
    private static final BlockPos HALL_DOOR = new BlockPos(2000, 64, 2000);

    private static String hallRoundTrip(net.minecraft.server.level.ServerLevel level) {
        BlockPos door = HALL_DOOR;
        level.getChunk(door);
        level.setBlockAndUpdate(door.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(door, dev.devpanda.factorynetwork.registry.FnBlocks.HALLS
                .get(dev.devpanda.factorynetwork.hall.HallTier.SMALL).get().defaultBlockState());
        var block = (dev.devpanda.factorynetwork.hall.HallBlockEntity) level.getBlockEntity(door);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        player.setPos(door.getCenter().add(0, 1, 0));
        dev.devpanda.factorynetwork.hall.HallDoor.enter(player, block);
        int id = block.hall();
        String inside = player.level().dimension().identifier() + " " + player.blockPosition();
        var hall = (net.minecraft.server.level.ServerLevel) player.level();
        String wall = hall.getBlockState(dev.devpanda.factorynetwork.hall.Halls.origin(id)).getBlock().toString();
        boolean inRoom = dev.devpanda.factorynetwork.hall.Halls.inside(id,
                dev.devpanda.factorynetwork.hall.HallTier.SMALL, player.blockPosition());
        dev.devpanda.factorynetwork.hall.HallDoor.leave(player, player.blockPosition().below());
        String after = player.level().dimension().identifier() + " " + player.blockPosition();
        var drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(door), level, door, block);
        Object dropped = drops.isEmpty() ? null : drops.getFirst().get(dev.devpanda.factorynetwork.registry.FnComponents.HALL.get());
        return "FN_HALL id=" + id + " inside=" + inside + " inRoom=" + inRoom + " wall=" + wall
                + " after=" + after + " door=" + door + " dropNumber=" + dropped;
    }

    /** A storage link at the door of hall 1 fills the store of the network inside. */
    private static String hallCouple(net.minecraft.server.level.ServerLevel level) {
        var reg = dev.devpanda.factorynetwork.registry.FnBlocks.class;
        var hall = level.getServer().getLevel(dev.devpanda.factorynetwork.hall.Halls.DIMENSION);
        int id = ((dev.devpanda.factorynetwork.hall.HallBlockEntity) level.getBlockEntity(HALL_DOOR)).hall();
        BlockPos low = dev.devpanda.factorynetwork.hall.Halls.origin(id);
        BlockPos controllerPos = low.offset(2, 1, 2);
        BlockPos drivePos = low.offset(3, 1, 2);
        hall.getChunk(controllerPos);
        hall.setBlockAndUpdate(controllerPos, dev.devpanda.factorynetwork.registry.FnBlocks.CONTROLLER.get().defaultBlockState());
        hall.setBlockAndUpdate(drivePos, dev.devpanda.factorynetwork.registry.FnBlocks.DRIVE.get().defaultBlockState());
        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity) hall.getBlockEntity(drivePos);
        if (drive.getItem(0).isEmpty()) {
            drive.setItem(0, new net.minecraft.world.item.ItemStack(dev.devpanda.factorynetwork.registry.FnItems.CELLS
                    .get(dev.devpanda.factorynetwork.storage.CellTier.K1).get()));
        }
        var controller = (dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity) hall.getBlockEntity(controllerPos);
        controller.rebuildNetwork();

        BlockPos linkPos = new BlockPos(21, 64, 20);
        level.setBlockAndUpdate(linkPos, dev.devpanda.factorynetwork.registry.FnBlocks.STORAGE_LINK.get().defaultBlockState());
        var link = (dev.devpanda.factorynetwork.block.entity.StorageLinkBlockEntity) level.getBlockEntity(linkPos);
        link.neighbourChanged();
        link.serverTick();
        var adapter = link.adapter();
        if (adapter == null || adapter.items() == null) {
            return "FN_HALL_COUPLE adapter=" + (adapter == null ? "none" : adapter.systemName()) + " items=none controllers="
                    + dev.devpanda.factorynetwork.network.ControllerRegistry.in(hall) + " at=" + controllerPos
                    + " be=" + hall.getBlockEntity(controllerPos);
        }
        var iron = dev.devpanda.factorynetwork.storage.ItemKey.bare(net.minecraft.world.item.Items.IRON_INGOT);
        long before = controller.storage().count(iron);
        long in = adapter.items().insert(iron, 64, false);
        long out = adapter.items().extract(iron, 10, false);
        long after = controller.storage().count(iron);
        if (drive.getItem(1).isEmpty()) {
            drive.setItem(1, new net.minecraft.world.item.ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                    .FLUID_CELLS.values().iterator().next().get()));
            controller.rebuildNetwork();
        }
        var fluids = adapter.fluids();
        long waterIn = fluids == null ? -1 : fluids.insert(net.minecraft.world.level.material.Fluids.WATER, 1000, false);
        long waterOut = fluids == null ? -1 : fluids.extract(net.minecraft.world.level.material.Fluids.WATER, 250, false);
        long water = controller.fluids().count(net.minecraft.world.level.material.Fluids.WATER);
        return "FN_HALL_COUPLE hall=" + id + " adapter=" + adapter.systemName() + " before=" + before + " in=" + in
                + " out=" + out + " after=" + after + " waterIn=" + waterIn + " waterOut=" + waterOut + " water=" + water;
    }
}
