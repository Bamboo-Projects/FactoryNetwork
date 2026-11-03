package dev.devpanda.factorynetwork.acceptance;

import dev.devpanda.factorynetwork.block.RackBlock;
import dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Builds the scenes of the in-game guide in the world and saves each as a
 * structure file for GuideME.
 *
 * <p>Built by code and not by hand: a connector is a part on a cable with
 * its name in the block entity, and a structure file written by hand would
 * have to know that format. The saved files go to {@code scenes/} next to
 * the server and are copied into {@code assets/factorynetwork/guide/scenes}.
 */
final class SceneMaker {

    /** Each scene with its size and how it is built from its lower corner. */
    private record Scene(Vec3i size, BiConsumer<ServerLevel, BlockPos> build) {
    }

    private static final Map<String, Scene> SCENES = new java.util.LinkedHashMap<>();

    static {
        // The first network from the guide page "Die erste Anlage".
        SCENES.put("erste_anlage", new Scene(new Vec3i(7, 4, 3), (level, o) -> {
            BlockPos controller = o.offset(1, 0, 1);
            set(level, controller, FnBlocks.CONTROLLER.get().defaultBlockState());
            rack(level, controller.west());
            set(level, controller.north(), FnBlocks.DRIVE.get().defaultBlockState());
            cable(level, controller.east());
            connector(level, controller.east(2), Direction.UP, "lager");
            set(level, controller.east(2).above(), Blocks.CHEST.defaultBlockState());
            connector(level, controller.east(3), Direction.UP, "ofen_aus");
            set(level, controller.east(3).above(), Blocks.FURNACE.defaultBlockState());
            cable(level, controller.east(4));
            cable(level, controller.east(4).above());
            cable(level, controller.east(4).above(2));
            connector(level, controller.east(3).above(2), Direction.DOWN, "ofen_ein");
        }));
        // Several connectors on one cable block.
        SCENES.put("kabel_connector", new Scene(new Vec3i(3, 3, 3), (level, o) -> {
            BlockPos center = o.offset(1, 1, 1);
            set(level, center, FnBlocks.CABLE.get().defaultBlockState());
            if (level.getBlockEntity(center) instanceof CableBusBlockEntity bus) {
                bus.addPart(Direction.UP).setLabel("kiste");
                bus.addPart(Direction.NORTH).setLabel("ofen");
                bus.addPart(Direction.EAST).setLabel("fass");
            }
            set(level, center.above(), Blocks.CHEST.defaultBlockState());
            set(level, center.north(), Blocks.FURNACE.defaultBlockState());
            set(level, center.east(), Blocks.BARREL.defaultBlockState());
            set(level, center.west(), FnBlocks.CABLE.get().defaultBlockState());
        }));
        // A gateway names the installation behind it.
        SCENES.put("gateway", new Scene(new Vec3i(6, 2, 3), (level, o) -> {
            BlockPos controller = o.offset(0, 0, 1);
            set(level, controller, FnBlocks.CONTROLLER.get().defaultBlockState());
            cable(level, controller.east());
            set(level, controller.east(2), FnBlocks.GATEWAY.get().defaultBlockState()
                    .trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                            Direction.EAST));
            if (level.getBlockEntity(controller.east(2))
                    instanceof dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity gateway) {
                gateway.setInstance("werk_1");
            }
            connector(level, controller.east(3), Direction.UP, "eingang");
            set(level, controller.east(3).above(), Blocks.CHEST.defaultBlockState());
            connector(level, controller.east(4), Direction.UP, "ausgang");
            set(level, controller.east(4).above(), Blocks.CHEST.defaultBlockState());
        }));
        // Two bridges with one pair: no cable in between.
        SCENES.put("bruecke", new Scene(new Vec3i(9, 2, 3), (level, o) -> {
            BlockPos controller = o.offset(0, 0, 1);
            set(level, controller, FnBlocks.CONTROLLER.get().defaultBlockState());
            cable(level, controller.east());
            ItemStack pair = dev.devpanda.factorynetwork.item.EntanglementItem.newPair();
            bridge(level, controller.east(2), pair.split(1));
            bridge(level, controller.east(6), pair);
            cable(level, controller.east(7));
            connector(level, controller.east(8), Direction.UP, "fern");
            set(level, controller.east(8).above(), Blocks.CHEST.defaultBlockState());
        }));
        // Inside a hall: a port in the wall, the cable runs on.
        SCENES.put("halle_port", new Scene(new Vec3i(5, 4, 3), (level, o) -> {
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 4; y++) {
                    set(level, o.offset(x, y, 0), FnBlocks.HALL_WALL.get().defaultBlockState());
                }
            }
            set(level, o.offset(2, 1, 0), FnBlocks.HALL_PORT.get().defaultBlockState());
            connector(level, o.offset(2, 1, 1), Direction.EAST, "ofen");
            set(level, o.offset(3, 1, 1), Blocks.FURNACE.defaultBlockState());
            cable(level, o.offset(1, 1, 1));
            connector(level, o.offset(1, 1, 2), Direction.UP, "lager");
            set(level, o.offset(1, 2, 2), Blocks.CHEST.defaultBlockState());
        }));
        // Outside: the hall block on the cable.
        SCENES.put("halle_block", new Scene(new Vec3i(4, 2, 3), (level, o) -> {
            BlockPos controller = o.offset(0, 0, 1);
            set(level, controller, FnBlocks.CONTROLLER.get().defaultBlockState());
            cable(level, controller.east());
            cable(level, controller.east(2));
            set(level, controller.east(3), FnBlocks.HALLS.get(
                    dev.devpanda.factorynetwork.hall.HallTier.SMALL).get().defaultBlockState());
        }));
    }

    private SceneMaker() {
    }

    static java.util.Set<String> names() {
        return SCENES.keySet();
    }

    /** Builds one scene far from everything else and saves it. */
    static String make(ServerLevel level, String name) {
        Scene scene = SCENES.get(name);
        if (scene == null) {
            return "FN_SCENE unknown " + name + ", known: " + SCENES.keySet();
        }
        int index = List.copyOf(SCENES.keySet()).indexOf(name);
        BlockPos origin = new BlockPos(5000 + index * 32, 100, 5000);
        level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
        level.setChunkForced((origin.getX() + 16) >> 4, origin.getZ() >> 4, true);
        BlockPos far = origin.offset(scene.size()).offset(-1, -1, -1);
        for (BlockPos pos : BlockPos.betweenClosed(origin, far)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        scene.build().accept(level, origin);
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, scene.size(), false, List.of(Blocks.AIR));
        CompoundTag tag = template.save(new CompoundTag());
        Path out = Path.of("scenes", name + ".snbt");
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, NbtUtils.structureToSnbt(tag), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "FN_SCENE " + name + " failed: " + e;
        }
        return "FN_SCENE " + name + " saved " + out.toAbsolutePath() + " at " + origin.toShortString();
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
    }

    private static void cable(ServerLevel level, BlockPos pos) {
        set(level, pos, FnBlocks.CABLE.get().defaultBlockState());
    }

    private static void connector(ServerLevel level, BlockPos pos, Direction facing, String name) {
        set(level, pos, FnBlocks.CABLE.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof CableBusBlockEntity bus) {
            bus.addPart(facing).setLabel(name);
        }
    }

    private static void rack(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, FnBlocks.RACK.get().defaultBlockState(), 2);
        level.setBlockAndUpdate(pos.above(), FnBlocks.RACK.get().defaultBlockState()
                .setValue(RackBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void bridge(ServerLevel level, BlockPos pos, ItemStack half) {
        set(level, pos, FnBlocks.BRIDGE.get().defaultBlockState());
        if (level.getBlockEntity(pos)
                instanceof dev.devpanda.factorynetwork.block.entity.BridgeBlockEntity bridge) {
            bridge.setItem(0, half);
        }
    }
}
