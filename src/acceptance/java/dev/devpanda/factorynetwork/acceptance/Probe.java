package dev.devpanda.factorynetwork.acceptance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Places every block that has a block entity and asks what a connector
 * would find there: items, fluids, energy, on which sides.
 *
 * <p>One line per block, {@code FN_PROBE id items fluids energy}, each a
 * side mask (d u n s w e, then "x" for the side-less query) or "-".
 */
final class Probe {

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final BlockPos AT = new BlockPos(0, -50, 300);

    private Probe() {
    }

    static void run(ServerLevel level) {
        level.setChunkForced(AT.getX() >> 4, AT.getZ() >> 4, true);
        List<String> lines = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            // A few without a block entity that still hold something.
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            boolean holder = path.equals("composter") || path.endsWith("cauldron");
            if (!(block instanceof EntityBlock) && !holder) {
                continue;
            }
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            try {
                level.setBlock(AT, block.defaultBlockState(), 2);
                String items = mask(level, Capabilities.Item.BLOCK);
                String fluids = mask(level, Capabilities.Fluid.BLOCK);
                String energy = mask(level, Capabilities.Energy.BLOCK);
                lines.add(id + " " + items + " " + fluids + " " + energy);
            } catch (RuntimeException failed) {
                lines.add(id + " error " + failed.getClass().getSimpleName());
            } finally {
                level.setBlock(AT, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        lines.forEach(line -> LOGGER.info("FN_PROBE {}", line));
    }

    private static <T> String mask(ServerLevel level,
                                   net.neoforged.neoforge.capabilities.BlockCapability<T, Direction> capability) {
        StringBuilder found = new StringBuilder();
        for (Direction side : Direction.values()) {
            found.append(level.getCapability(capability, AT, side) != null
                    ? side.getName().charAt(0) : '.');
        }
        found.append(level.getCapability(capability, AT, null) != null ? 'x' : '.');
        return found.indexOf("d") < 0 && found.toString().replace(".", "").isEmpty() ? "-" : found.toString();
    }
}
